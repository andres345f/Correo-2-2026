package com.example.correo.service;

import com.example.correo.dto.Resultado;
import com.example.correo.entity.Cliente;
import com.example.correo.entity.Factura;
import com.example.correo.entity.Pago;
import com.example.correo.entity.Persona;
import com.example.correo.entity.Venta;
import com.example.correo.entity.enums.EstadoPago;
import com.example.correo.entity.enums.EstadoVenta;
import com.example.correo.entity.enums.MetodoPago;
import com.example.correo.repository.CobroConsultasRepository;
import com.example.correo.service.puerto.PasarelaPago;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** CU6: intentos de cobro, reversion y factura. */
@Service
public class CobroService {

	private final CobroConsultasRepository consultas;

	private final Datos datos;
	private final PasarelaPago pagoFacil;

	public CobroService(Datos datos, PasarelaPago pagoFacil, CobroConsultasRepository consultas) {
		this.consultas = consultas;
		this.datos = datos;
		this.pagoFacil = pagoFacil;
	}

	private long cobrados(Venta venta) {
		return consultas.contarPagosPorEstado(venta.getId(), EstadoPago.PAGADO);
	}

	private void exigirSinCobro(Venta venta) {
		if (cobrados(venta) > 0) {
			throw Datos.regla("La venta ya tiene un cobro exitoso: no se cobra dos veces.");
		}
	}

	public Resultado pagar(Actor actor, Args p) {
		MetodoPago metodo = p.enumerado(1, MetodoPago.class);
		String referencia = p.opcional(2);
		// La exclusion es por venta: dos confirmaciones simultaneas se atienden en fila.
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		if (referencia != null) {
			Pago previo = consultas.pagosPorReferencia(metodo, referencia).stream().findFirst().orElse(null);
			if (previo != null) {
				if (!previo.getVenta().getId().equals(venta.getId())) {
					throw Datos.regla("Esa referencia ya se uso en otra venta.");
				}
				return Resultado.exito("Ese intento ya estaba registrado con id " + previo.getId() + ": " + previo.getEstadoPago() + ".");
			}
		}
		if (venta.getEstado() == EstadoVenta.PENDIENTE || venta.getEstado() == EstadoVenta.CANCELADO) {
			throw Datos.regla("No se cobra una venta en estado " + venta.getEstado() + ".");
		}
		EstadoPago resultado = p.enumerado(3, EstadoPago.class);
		if (resultado == EstadoPago.PAGADO) {
			exigirSinCobro(venta);
		}
		Pago intento = new Pago();
		intento.setVenta(venta);
		intento.setMetodoPago(metodo);
		intento.setReferenciaPago(referencia);
		intento.setMonto(venta.getTotal());
		intento.setEstadoPago(resultado);
		intento.setFechaPago(resultado == EstadoPago.PAGADO ? LocalDateTime.now() : null);
		datos.guardar(intento);
		return Resultado.exito("Intento " + intento.getId() + " por " + intento.getMonto() + " Bs: " + resultado + ".");
	}

	/** Tambien concilia un cobro que la pasarela confirma despues de cancelar la orden. */
	public Resultado confirmarIntento(Actor actor, Args p) {
		Pago intento = datos.buscar(Pago.class, p.id(0));
		Venta venta = datos.bloquear(Venta.class, intento.getVenta().getId());
		datos.actualizar(intento);
		EstadoPago resultado = p.enumerado(1, EstadoPago.class);
		if (intento.getEstadoPago() == resultado) {
			return Resultado.exito("El intento ya estaba " + resultado + ".");
		}
		if (intento.getEstadoPago() != EstadoPago.PENDIENTE || resultado == EstadoPago.PENDIENTE) {
			throw Datos.regla("Solo se resuelve un intento PENDIENTE, a PAGADO o RECHAZADO.");
		}
		if (resultado == EstadoPago.PAGADO) {
			exigirSinCobro(venta);
			intento.setFechaPago(LocalDateTime.now());
		}
		intento.setEstadoPago(resultado);
		return Resultado.exito("Intento " + intento.getId() + ": " + resultado + (venta.getEstado() == EstadoVenta.CANCELADO && resultado == EstadoPago.PAGADO ? ". La venta esta cancelada: registre su reverso con REVPAG." : "."));
	}

	private void exigirPropia(Actor actor, Venta venta) {
		if (!actor.esPersonal() && (venta.getCliente() == null || !venta.getCliente().getId().equals(datos.cliente(actor).getId()))) {
			throw new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO, "Solo puede operar sobre sus propios pedidos.");
		}
	}

	private ComandoInvalidoException falloPagoFacil(Exception e) {
		if (e instanceof InterruptedException) {
			Thread.currentThread().interrupt();
		}
		return Datos.regla("PagoFacil no respondio correctamente: " + e.getMessage());
	}

	/** El intento queda PENDIENTE hasta que PagoFacil confirme el cobro (ACTPAG). */
	public Resultado generarQr(Actor actor, Args p) {
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		exigirPropia(actor, venta);
		if (Set.of(EstadoVenta.PENDIENTE, EstadoVenta.CANCELADO, EstadoVenta.ENTREGADO).contains(venta.getEstado())) {
			throw Datos.regla("No se genera QR para una venta en estado " + venta.getEstado() + ".");
		}
		exigirSinCobro(venta);
		Cliente cliente = venta.getCliente();
		Persona persona = cliente == null ? null : cliente.getPersona();
		PasarelaPago.QrResult qr;
		try {
			qr = pagoFacil.generarQr(venta.getCodigo() + "-" + System.currentTimeMillis(), venta.getTotal(), "Pedido " + venta.getCodigo(), cliente == null ? 0 : cliente.getId(),
					persona == null ? "Cliente" : persona.getNombre() + " " + persona.getApellido(), persona == null ? null : persona.getCi(), persona == null ? null : persona.getTelefono(),
					cliente == null ? null : cliente.getUsuario().getCorreo());
		} catch (IOException | InterruptedException e) {
			throw falloPagoFacil(e);
		}
		Pago intento = new Pago();
		intento.setVenta(venta);
		intento.setMetodoPago(MetodoPago.QR);
		intento.setReferenciaPago(qr.transactionId());
		intento.setMonto(venta.getTotal());
		intento.setEstadoPago(EstadoPago.PENDIENTE);
		datos.guardar(intento);

		String detalle = "QR generado para " + venta.getCodigo() + " por " + venta.getTotal() + " Bs. Intento de pago: " + intento.getId() + ". Tras pagar, envie ACTPAG[\"" + intento.getId() + "\"].";
		try {
			Base64.getMimeDecoder().decode(qr.qrImage());
		} catch (IllegalArgumentException e) {
			throw Datos.regla("PagoFacil devolvio un QR ilegible.");
		}
		return Resultado.qr(detalle, qr.qrImage());
	}

	/** Trae de PagoFacil el resultado de un intento QR y lo concilia. */
	public Resultado actualizarDesdePagoFacil(Actor actor, Args p) {
		Pago intento = datos.buscar(Pago.class, p.id(0));
		Venta venta = datos.bloquear(Venta.class, intento.getVenta().getId());
		datos.actualizar(intento);
		exigirPropia(actor, venta);
		if (intento.getMetodoPago() != MetodoPago.QR || intento.getReferenciaPago() == null) {
			throw Datos.regla("Ese intento no tiene una transaccion de PagoFacil.");
		}
		if (intento.getEstadoPago() != EstadoPago.PENDIENTE) {
			return Resultado.exito("El intento ya estaba " + intento.getEstadoPago() + ".");
		}
		PasarelaPago.QueryResult resultado;
		try {
			resultado = pagoFacil.consultarTransaccion(intento.getReferenciaPago());
		} catch (IOException | InterruptedException e) {
			throw falloPagoFacil(e);
		}
		switch (resultado.status()) {
			case "PAGADO" -> {
				exigirSinCobro(venta);
				intento.setEstadoPago(EstadoPago.PAGADO);
				intento.setFechaPago(LocalDateTime.now());
				return Resultado.exito("Pago confirmado por PagoFacil: " + intento.getMonto() + " Bs." + (venta.getEstado() == EstadoVenta.CANCELADO ? " La venta esta cancelada: registre su reverso con REVPAG." : ""));
			}
			case "CANCELADO" -> {
				intento.setEstadoPago(EstadoPago.RECHAZADO);
				return Resultado.exito("PagoFacil informa que el pago fue rechazado o cancelado.");
			}
			default -> {
				return Resultado.exito("El pago sigue PENDIENTE en PagoFacil.");
			}
		}
	}

	/** El pago sigue PAGADO como hecho historico; la reversion se fecha aparte. */
	public Resultado revertir(Actor actor, Args p) {
		Pago intento = datos.buscar(Pago.class, p.id(0));
		Venta venta = datos.bloquear(Venta.class, intento.getVenta().getId());
		datos.actualizar(intento);
		if (intento.getFechaReversion() != null) {
			return Resultado.exito("Ese cobro ya estaba revertido.");
		}
		if (intento.getEstadoPago() != EstadoPago.PAGADO) {
			throw Datos.regla("Solo se revierte un intento PAGADO.");
		}
		if (venta.getEstado() != EstadoVenta.CANCELADO) {
			throw Datos.regla("El cobro solo se revierte con la venta cancelada antes de entregar.");
		}
		intento.setFechaReversion(LocalDateTime.now());
		intento.setReferenciaReversion(p.opcional(1));
		return Resultado.exito("Cobro revertido por " + intento.getMonto() + " Bs. La mercancia despachada no vuelve sola: use REIVEN si regreso.");
	}

	public Resultado facturar(Actor actor, Args p) {
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		Factura previa = consultas.facturasDeVenta(venta.getId()).stream().findFirst().orElse(null);
		if (previa != null) {
			return Resultado.exito("Esa venta ya tiene la factura " + previa.getNumero() + ".");
		}
		if (Set.of(EstadoVenta.PENDIENTE, EstadoVenta.CANCELADO).contains(venta.getEstado())) {
			throw Datos.regla("No se factura una venta en estado " + venta.getEstado() + ".");
		}
		String nit = p.vacio(1) && venta.getCliente() != null ? venta.getCliente().getNitCi() : p.opcional(1);
		String razon = p.vacio(2) && venta.getCliente() != null ? venta.getCliente().getRazonSocial() : p.opcional(2);
		if (nit == null || razon == null) {
			throw Datos.regla("Indique NIT o CI y razon social.");
		}
		Factura factura = new Factura();
		factura.setVenta(venta);
		factura.setNumero("TMP-" + UUID.randomUUID());
		factura.setNitCi(nit);
		factura.setRazonSocial(razon);
		factura.setMontoTotal(venta.getTotal());
		factura.setEstado("EMITIDA");
		factura.setFechaEmision(LocalDateTime.now());
		datos.guardar(factura);
		factura.setNumero("F-" + factura.getId());
		return Resultado.exito("Factura " + factura.getNumero() + " emitida por " + factura.getMontoTotal() + " Bs.");
	}

}

package com.example.correo.service;

import com.example.correo.dto.Resultado;
import com.example.correo.entity.DetalleVenta;
import com.example.correo.entity.Devolucion;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.Pago;
import com.example.correo.entity.Venta;
import com.example.correo.entity.enums.EstadoDevolucion;
import com.example.correo.entity.enums.EstadoPago;
import com.example.correo.entity.enums.EstadoVenta;
import com.example.correo.entity.enums.MotivoDevolucion;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import com.example.correo.entity.enums.ResolucionDevolucion;
import com.example.correo.repository.DevolucionConsultasRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** CU6: reclamos. Dinero y mercancia se deciden y se siguen por separado. */
@Service
public class DevolucionService {

	private final DevolucionConsultasRepository consultas;

	public static final String CONTABILIZA = "CONTABILIZA_REEMBOLSO";
	public static final String RUTA_MONTO = "monto_reembolso";

	private final Datos datos;
	private final InventarioService inventario;
	private final Auditoria auditoria;

	public DevolucionService(Datos datos, InventarioService inventario, Auditoria auditoria, DevolucionConsultasRepository consultas) {
		this.consultas = consultas;
		this.datos = datos;
		this.inventario = inventario;
		this.auditoria = auditoria;
	}

	public Resultado reportar(Actor actor, Args p) {
		DetalleVenta linea = datos.buscar(DetalleVenta.class, p.id(0));
		if (linea.getVenta().getEstado() != EstadoVenta.ENTREGADO) {
			throw Datos.regla("Solo se reclama una venta ya entregada.");
		}
		MotivoDevolucion motivo = p.enumerado(2, MotivoDevolucion.class);
		if (motivo == MotivoDevolucion.OTRO_JUSTIFICADO && p.vacio(3)) {
			throw Datos.regla("OTRO_JUSTIFICADO necesita una descripcion.");
		}
		Devolucion caso = new Devolucion();
		caso.setDetalleVenta(linea);
		caso.setCantidad(p.positivo(1));
		caso.setMotivo(motivo);
		caso.setDescripcion(p.opcional(3));
		caso.setEstado(EstadoDevolucion.REPORTADA);
		caso.setFechaReporte(LocalDateTime.now());
		caso.setEmpleadoFeder(datos.empleado(actor));
		datos.guardar(caso);
		return Resultado.exito("Reclamo registrado con id " + caso.getId() + ".");
	}

	/** Orden de bloqueo: venta, caso. Asi los acumulados por venta se comprueban en fila. */
	private Devolucion bloquear(Integer id) {
		Devolucion caso = datos.buscar(Devolucion.class, id);
		datos.bloquear(Venta.class, caso.getDetalleVenta().getVenta().getId());
		return datos.bloquear(Devolucion.class, id);
	}

	private void exigirEstado(Devolucion caso, EstadoDevolucion esperado) {
		if (caso.getEstado() != esperado) {
			throw Datos.regla("El reclamo debe estar " + esperado + "; esta " + caso.getEstado() + ".");
		}
	}

	public Resultado revisar(Actor actor, Args p) {
		Devolucion caso = bloquear(p.id(0));
		exigirEstado(caso, EstadoDevolucion.REPORTADA);
		caso.setEstado(EstadoDevolucion.EN_REVISION);
		return Resultado.exito("Reclamo " + caso.getId() + " en revision.");
	}

	public Resultado rechazar(Actor actor, Args p) {
		Devolucion caso = bloquear(p.id(0));
		exigirEstado(caso, EstadoDevolucion.EN_REVISION);
		caso.setEstado(EstadoDevolucion.RECHAZADA);
		caso.setJustificacion(p.texto(1));
		return Resultado.exito("Reclamo " + caso.getId() + " rechazado.");
	}

	/** Aprobar exige una salida real; lo aprobado no supera lo que salio, aunque no reingrese. */
	public Resultado aprobar(Actor actor, Args p) {
		Devolucion caso = bloquear(p.id(0));
		exigirEstado(caso, EstadoDevolucion.EN_REVISION);
		MovimientoFeder despacho = datos.bloquear(MovimientoFeder.class, p.id(1));
		if (despacho.getMotivo() != MotivoMovimientoFeder.VENTA || despacho.getDetalleVenta() == null || !despacho.getDetalleVenta().getId().equals(caso.getDetalleVenta().getId())) {
			throw Datos.regla("Ese movimiento no es una salida por venta de esta linea.");
		}
		BigDecimal aprobado = consultas.cantidadAprobadaDelDespacho(despacho.getId(), List.of(EstadoDevolucion.APROBADA, EstadoDevolucion.RESUELTA));
		if (aprobado.add(caso.getCantidad()).compareTo(despacho.getCantidad()) > 0) {
			throw Datos.regla("De esa salida solo quedan " + despacho.getCantidad().subtract(aprobado) + " unidades reclamables.");
		}
		caso.setDespacho(despacho);
		caso.setJustificacion(p.texto(2));
		caso.setEstado(EstadoDevolucion.APROBADA);
		return Resultado.exito("Reclamo " + caso.getId() + " aprobado por " + caso.getCantidad() + " unidades.");
	}

	private BigDecimal asentado(Devolucion caso, MotivoMovimientoFeder motivo) {
		return consultas.cantidadAsentada(caso.getId(), motivo);
	}

	/** Solo lo fisicamente devuelto, apto y de lote vigente vuelve a ser vendible. */
	public Resultado recibir(Actor actor, Args p) {
		String clave = p.clave(3);
		if (inventario.claveUsada(clave)) {
			return Resultado.exito("Esa recepcion ya estaba registrada.");
		}
		Devolucion caso = bloquear(p.id(0));
		if (caso.getEstado() != EstadoDevolucion.APROBADA) {
			throw Datos.regla("Solo se recibe mercancia de un reclamo APROBADO.");
		}
		boolean apto = p.booleano(2);
		BigDecimal cantidad = p.positivo(1);
		if (cantidad.add(asentado(caso, MotivoMovimientoFeder.DEVOLUCION)).compareTo(caso.getCantidad()) > 0) {
			throw Datos.regla("Supera las unidades autorizadas del reclamo.");
		}
		caso.setAptoReventa(apto);
		if (!apto) {
			return Resultado.exito("Recepcion registrada como no apta: el saldo vendible no cambia.");
		}
		LoteFeder lote = datos.bloquear(LoteFeder.class, caso.getDespacho().getLoteFeder().getId());
		if (lote.getFechaVencimiento() != null && !lote.getFechaVencimiento().isAfter(LocalDate.now())) {
			throw Datos.regla("El lote de origen ya vencio: la unidad no puede volver a ser vendible.");
		}
		inventario.asentar(lote, datos.empleado(actor), MotivoMovimientoFeder.DEVOLUCION, cantidad, clave, null, null, caso);
		lote.setCantidadActual(lote.getCantidadActual().add(cantidad));
		inventario.evaluarAlerta(lote.getProducto().getId());
		return Resultado.exito("Reingresadas " + cantidad + " unidades al lote " + lote.getId() + ".");
	}

	public Resultado reponer(Actor actor, Args p) {
		String clave = p.clave(2);
		if (inventario.claveUsada(clave)) {
			return Resultado.exito("Esa reposicion ya estaba registrada.");
		}
		Devolucion caso = bloquear(p.id(0));
		if (caso.getEstado() != EstadoDevolucion.APROBADA) {
			throw Datos.regla("Solo se repone un reclamo APROBADO.");
		}
		LoteFeder lote = datos.bloquear(LoteFeder.class, p.id(1));
		if (!lote.getProducto().getId().equals(caso.getDetalleVenta().getProducto().getId())) {
			throw Datos.regla("El reemplazo debe ser del mismo producto.");
		}
		if (lote.getFechaVencimiento() != null && !lote.getFechaVencimiento().isAfter(LocalDate.now())) {
			throw Datos.regla("Ese lote ya vencio.");
		}
		inventario.exigirLibre(lote, caso.getCantidad());
		inventario.asentar(lote, datos.empleado(actor), MotivoMovimientoFeder.REPOSICION, caso.getCantidad(), clave, null, null, caso);
		lote.setCantidadActual(lote.getCantidadActual().subtract(caso.getCantidad()));
		caso.setResolucion(ResolucionDevolucion.REPOSICION);
		caso.setEstado(EstadoDevolucion.RESUELTA);
		inventario.evaluarAlerta(lote.getProducto().getId());
		return Resultado.exito("Reposicion registrada: reclamo " + caso.getId() + " resuelto. Saldo fisico del lote: " + lote.getCantidadActual() + ".");
	}

	private boolean contabilizado(Devolucion caso) {
		return consultas.contarEventosContables(CONTABILIZA, caso.getId()) > 0;
	}

	private void contabilizar(Actor actor, Devolucion caso, BigDecimal monto) {
		if (!contabilizado(caso)) {
			auditoria.registrar(datos.firmante(actor), CONTABILIZA, "Devolucion", caso.getId(), List.of(new Auditoria.Cambio(RUTA_MONTO, null, monto.toString())));
		}
	}

	/** Fecha la correccion del ingreso; la salida de caja la fija RMBDEV al liquidar. */
	public Resultado contabilizarReembolso(Actor actor, Args p) {
		Devolucion caso = bloquear(p.id(0));
		if (caso.getEstado() != EstadoDevolucion.APROBADA) {
			throw Datos.regla("Solo se contabiliza un reclamo APROBADO.");
		}
		if (contabilizado(caso)) {
			return Resultado.exito("Ese reembolso ya estaba contabilizado.");
		}
		BigDecimal monto = p.positivo(1);
		exigirCobroDisponible(caso, monto);
		contabilizar(actor, caso, monto);
		return Resultado.exito("Reembolso de " + monto + " Bs contabilizado.");
	}

	private void exigirCobroDisponible(Devolucion caso, BigDecimal monto) {
		Integer venta = caso.getDetalleVenta().getVenta().getId();
		BigDecimal cobrado = consultas.pagosSinReversion(venta, EstadoPago.PAGADO).stream().findFirst().map(Pago::getMonto).orElse(BigDecimal.ZERO);
		BigDecimal reintegrado = consultas.totalReembolsadoDeVenta(venta);
		if (monto.compareTo(cobrado.subtract(reintegrado)) > 0) {
			throw Datos.regla("El reembolso supera lo cobrado pendiente de reintegro: " + cobrado.subtract(reintegrado) + " Bs.");
		}
	}

	public Resultado reembolsar(Actor actor, Args p) {
		Devolucion caso = bloquear(p.id(0));
		if (caso.getEstado() == EstadoDevolucion.RESUELTA && caso.getResolucion() == ResolucionDevolucion.REEMBOLSO) {
			return Resultado.exito("Ese reembolso ya estaba liquidado.");
		}
		if (caso.getEstado() != EstadoDevolucion.APROBADA) {
			throw Datos.regla("Solo se reembolsa un reclamo APROBADO.");
		}
		BigDecimal monto = p.positivo(1);
		exigirCobroDisponible(caso, monto);
		contabilizar(actor, caso, monto);
		caso.setResolucion(ResolucionDevolucion.REEMBOLSO);
		caso.setMontoReembolso(monto);
		caso.setFechaReembolso(LocalDateTime.now());
		if (!p.vacio(2)) {
			caso.setJustificacion(p.texto(2));
		}
		caso.setEstado(EstadoDevolucion.RESUELTA);
		return Resultado.exito("Reembolso de " + monto + " Bs liquidado: reclamo " + caso.getId() + " resuelto.");
	}

}

package com.example.correo.service;

import com.example.correo.dto.Listado;
import com.example.correo.dto.Resultado;
import com.example.correo.entity.Asociacion;
import com.example.correo.entity.DetalleVenta;
import com.example.correo.entity.EncargadoAsociacion;
import com.example.correo.entity.Inventario;
import com.example.correo.entity.LoteAsociacion;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.MovimientoInventario;
import com.example.correo.entity.OfertaAsociacion;
import com.example.correo.entity.Persona;
import com.example.correo.entity.Produccion;
import com.example.correo.entity.Producto;
import com.example.correo.entity.Productor;
import com.example.correo.entity.enums.EstadoProduccion;
import com.example.correo.entity.enums.EstadoValidacionLote;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import com.example.correo.entity.enums.TipoInventario;
import com.example.correo.entity.enums.TipoMovimientoInventario;
import com.example.correo.repository.LoteConsultasRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** CU3: produccion, lotes del proveedor, validacion, oferta y trazabilidad. */
@Service
public class LoteService {

	private final LoteConsultasRepository consultas;

	private final Datos datos;

	public LoteService(Datos datos, LoteConsultasRepository consultas) {
		this.consultas = consultas;
		this.datos = datos;
	}

	private void exigirSobreProductor(Actor actor, Productor productor) {
		boolean propio = productor.getUsuario() != null && actor.usuario() != null && productor.getUsuario().getId().equals(actor.usuario().getId());
		if (!actor.esPersonal() && !propio && datos.mandato(actor, productor.getAsociacion().getId()).isEmpty()) {
			throw new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO, "Solo puede operar sobre su propia produccion o la de su asociacion.");
		}
	}

	private void exigirSobreLote(Actor actor, LoteAsociacion lote) {
		boolean propio = lote.getProductor() != null && lote.getProductor().getUsuario() != null && actor.usuario() != null
				&& lote.getProductor().getUsuario().getId().equals(actor.usuario().getId());
		if (!actor.esPersonal() && !propio && datos.mandato(actor, lote.getAsociacion().getId()).isEmpty()) {
			throw new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO, "Solo puede operar sobre lotes propios o de su asociacion.");
		}
	}

	public Resultado registrarProduccion(Actor actor, Args p) {
		Productor productor = datos.buscar(Productor.class, p.id(0));
		exigirSobreProductor(actor, productor);
		if (p.fecha(4).isBefore(p.fecha(3))) {
			throw Datos.regla("La cosecha estimada no puede ser anterior a la siembra.");
		}
		Produccion campana = new Produccion();
		campana.setProductor(productor);
		campana.setProducto(datos.buscar(Producto.class, p.id(1)));
		campana.setCantidadEstimada(p.positivo(2));
		campana.setFechaSiembra(p.fecha(3));
		campana.setFechaCosechaEstimada(p.fecha(4));
		campana.setObservacion(p.opcional(5));
		campana.setEstado(EstadoProduccion.EN_CURSO);
		campana.setFechaRegistro(LocalDateTime.now());
		campana.setUsuario(datos.firmante(actor));
		datos.guardar(campana);
		return Resultado.exito("Campana registrada con id " + campana.getId() + ". Una estimacion todavia no habilita ventas.");
	}

	public Resultado cambiarEstadoProduccion(Actor actor, Args p) {
		Produccion campana = datos.bloquear(Produccion.class, p.id(0));
		exigirSobreProductor(actor, campana.getProductor());
		campana.setEstado(p.enumerado(1, EstadoProduccion.class));
		return Resultado.exito("Campana " + campana.getId() + " en estado " + campana.getEstado() + ".");
	}

	public Resultado registrarLote(Actor actor, Args p) {
		String clave = p.clave(8);
		MovimientoInventario previo = movimiento(clave);
		if (previo != null) {
			return Resultado.exito("Lote ya registrado con id " + previo.getLoteAsociacion().getId() + ".");
		}
		Productor productor;
		Producto producto;
		Produccion campana = null;
		if (!p.vacio(4)) {
			campana = datos.buscar(Produccion.class, p.id(4));
			productor = campana.getProductor();
			producto = campana.getProducto();
		} else {
			productor = datos.buscar(Productor.class, p.id(5));
			producto = datos.buscar(Producto.class, p.id(6));
		}
		exigirSobreProductor(actor, productor);
		Inventario destino = datos.buscar(Inventario.class, p.id(7));
		exigirCustodiaProveedor(destino, productor, productor.getAsociacion());

		BigDecimal cantidad = p.positivo(1);
		LoteAsociacion lote = new LoteAsociacion();
		lote.setCodigoLote(p.texto(0));
		lote.setCantidadInicial(cantidad);
		lote.setCantidadDisponible(cantidad);
		lote.setPrecioOfrecido(p.noNegativo(2));
		lote.setFechaVencimiento(p.fechaOpcional(3));
		lote.setEstadoValidacion(EstadoValidacionLote.PENDIENTE);
		lote.setFechaRegistro(LocalDateTime.now());
		lote.setUsuario(datos.firmante(actor));
		lote.setProduccion(campana);
		lote.setProductor(productor);
		lote.setProducto(producto);
		lote.setAsociacion(productor.getAsociacion());
		lote.setMunicipio(productor.getAsociacion().getMunicipio());
		lote.setInventario(destino);
		datos.guardar(lote);
		asentar(actor, lote, TipoMovimientoInventario.REGISTRO, cantidad, null, destino, null, clave);
		return Resultado.exito("Lote registrado con id " + lote.getId() + ", " + cantidad + " unidades en " + destino.getNombre() + ".");
	}

	private void exigirCustodiaProveedor(Inventario inventario, Productor productor, Asociacion asociacion) {
		boolean valido = Boolean.TRUE.equals(inventario.getActivo()) && (
				(inventario.getTipo() == TipoInventario.PRODUCTOR && productor != null && inventario.getProductor().getId().equals(productor.getId()))
				|| (inventario.getTipo() == TipoInventario.ASOCIACION && inventario.getAsociacion().getId().equals(asociacion.getId())));
		if (!valido) {
			throw Datos.regla("El inventario debe estar activo y ser del productor o de la asociacion de origen.");
		}
	}

	private MovimientoInventario movimiento(String clave) {
		return consultas.movimientosConClave(clave).stream().findFirst().orElse(null);
	}

	private void asentar(Actor actor, LoteAsociacion lote, TipoMovimientoInventario tipo, BigDecimal cantidad, Inventario origen, Inventario destino, String observacion, String clave) {
		MovimientoInventario asiento = new MovimientoInventario();
		asiento.setLoteAsociacion(lote);
		asiento.setTipo(tipo);
		asiento.setCantidad(cantidad);
		asiento.setInventarioOrigen(origen);
		asiento.setInventarioDestino(destino);
		asiento.setObservacion(observacion);
		asiento.setClaveOperacion(clave);
		asiento.setFecha(LocalDateTime.now());
		asiento.setUsuario(datos.firmante(actor));
		datos.guardar(asiento);
	}

	public Resultado trasladar(Actor actor, Args p) {
		String clave = p.clave(2);
		if (movimiento(clave) != null) {
			return Resultado.exito("Ese traslado ya estaba registrado.");
		}
		LoteAsociacion lote = datos.bloquear(LoteAsociacion.class, p.id(0));
		exigirSobreLote(actor, lote);
		Inventario destino = datos.buscar(Inventario.class, p.id(1));
		exigirCustodiaProveedor(destino, lote.getProductor(), lote.getAsociacion());
		if (lote.getInventario().getId().equals(destino.getId())) {
			throw Datos.regla("El lote ya esta en ese inventario.");
		}
		if (lote.getCantidadDisponible().signum() <= 0) {
			throw Datos.regla("El lote no tiene saldo para trasladar.");
		}
		// Un traslado de custodia mueve siempre el remanente completo.
		asentar(actor, lote, TipoMovimientoInventario.TRANSFERENCIA, lote.getCantidadDisponible(), lote.getInventario(), destino, null, clave);
		lote.setInventario(destino);
		return Resultado.exito("Lote " + lote.getId() + " trasladado a " + destino.getNombre() + ": " + lote.getCantidadDisponible() + " unidades.");
	}

	public Resultado validar(Actor actor, Args p) {
		LoteAsociacion lote = datos.bloquear(LoteAsociacion.class, p.id(0));
		EstadoValidacionLote decision = p.enumerado(1, EstadoValidacionLote.class);
		if (decision == EstadoValidacionLote.PENDIENTE) {
			throw Datos.regla("La decision debe ser VALIDADO o RECHAZADO.");
		}
		EncargadoAsociacion mandato = datos.mandato(actor, lote.getAsociacion().getId())
				.orElseThrow(() -> Datos.regla("Solo el encargado vigente de la asociacion del lote puede validarlo."));
		if (lote.getEstadoValidacion() != EstadoValidacionLote.PENDIENTE) {
			throw Datos.regla("El lote ya fue decidido: " + lote.getEstadoValidacion() + ".");
		}
		if (decision == EstadoValidacionLote.VALIDADO && Boolean.TRUE.equals(lote.getProducto().getManejaVencimiento()) && lote.getFechaVencimiento() == null) {
			throw Datos.regla("Este producto maneja vencimiento: el lote necesita su fecha para validarse.");
		}
		lote.setEstadoValidacion(decision);
		lote.setEncargadoAsociacion(mandato);
		lote.setFechaValidacion(LocalDateTime.now());
		return Resultado.exito("Lote " + lote.getId() + " " + decision + ".");
	}

	public Resultado merma(Actor actor, Args p) {
		String clave = p.clave(3);
		if (movimiento(clave) != null) {
			return Resultado.exito("Esa merma ya estaba registrada.");
		}
		LoteAsociacion lote = datos.bloquear(LoteAsociacion.class, p.id(0));
		exigirSobreLote(actor, lote);
		BigDecimal cantidad = p.positivo(1);
		descontar(lote, cantidad);
		asentar(actor, lote, TipoMovimientoInventario.MERMA, cantidad, lote.getInventario(), null, p.opcional(2), clave);
		return Resultado.exito("Merma registrada. Saldo del lote: " + lote.getCantidadDisponible() + ".");
	}

	public Resultado ajustar(Actor actor, Args p) {
		String clave = p.clave(3);
		if (movimiento(clave) != null) {
			return Resultado.exito("Ese ajuste ya estaba registrado.");
		}
		LoteAsociacion lote = datos.bloquear(LoteAsociacion.class, p.id(0));
		exigirSobreLote(actor, lote);
		BigDecimal delta = p.decimal(1);
		if (delta.signum() == 0) {
			throw Datos.regla("El ajuste no puede ser cero.");
		}
		if (delta.signum() > 0) {
			lote.setCantidadDisponible(lote.getCantidadDisponible().add(delta));
			asentar(actor, lote, TipoMovimientoInventario.AJUSTE, delta, null, lote.getInventario(), p.opcional(2), clave);
		} else {
			descontar(lote, delta.abs());
			asentar(actor, lote, TipoMovimientoInventario.AJUSTE, delta.abs(), lote.getInventario(), null, p.opcional(2), clave);
		}
		return Resultado.exito("Ajuste registrado. Saldo del lote: " + lote.getCantidadDisponible() + ".");
	}

	private void descontar(LoteAsociacion lote, BigDecimal cantidad) {
		if (cantidad.compareTo(lote.getCantidadDisponible()) > 0) {
			throw Datos.regla("La salida (" + cantidad + ") supera el saldo del lote (" + lote.getCantidadDisponible() + ").");
		}
		lote.setCantidadDisponible(lote.getCantidadDisponible().subtract(cantidad));
	}

	public Resultado publicarOferta(Actor actor, Args p) {
		EncargadoAsociacion mandato = datos.mandatoActual(actor);
		Producto producto = datos.buscar(Producto.class, p.id(0));
		OfertaAsociacion oferta = consultas.ofertasDeAsociacionYProducto(mandato.getAsociacion().getId(), producto.getId()).stream().findFirst().orElse(null);
		boolean nueva = oferta == null;
		if (nueva) {
			oferta = new OfertaAsociacion();
			oferta.setAsociacion(mandato.getAsociacion());
			oferta.setProducto(producto);
			oferta.setActivo(true);
		}
		oferta.setTiempoEntregaHoras(p.entero(1));
		oferta.setPrecioPrevisto(p.decimalOpcional(2));
		oferta.setCantidadPrevista(p.decimalOpcional(3));
		oferta.setDisponibleDesde(p.fechaOpcional(4));
		oferta.setEncargadoAsociacion(mandato);
		oferta.setFechaActualizacion(LocalDateTime.now());
		if (nueva) {
			datos.guardar(oferta);
		}
		return Resultado.exito("Oferta actualizada con id " + oferta.getId() + ".");
	}

	/** Cierra el mandato abierto y abre el nuevo en la misma operacion. */
	public Resultado relevarEncargado(Actor actor, Args p) {
		Asociacion asociacion = datos.bloquear(Asociacion.class, p.id(0));
		Persona persona = datos.buscar(Persona.class, p.id(1));
		if (persona.getUsuario() == null) {
			throw Datos.regla("El encargado necesita una cuenta: registre usuario en PER primero.");
		}
		LocalDateTime ahora = LocalDateTime.now();
		consultas.mandatosAbiertosDeAsociacion(asociacion.getId()).forEach(e -> e.setHasta(ahora));
		datos.sincronizar();
		EncargadoAsociacion mandato = new EncargadoAsociacion();
		mandato.setAsociacion(asociacion);
		mandato.setPersona(persona);
		mandato.setUsuario(persona.getUsuario());
		mandato.setDesde(ahora);
		mandato.setActivo(true);
		datos.guardar(mandato);
		return Resultado.exito("Mandato " + mandato.getId() + " abierto para " + persona.getNombre() + " " + persona.getApellido() + ".");
	}

	public Resultado trazarLoteFeder(Actor actor, Args p) {
		LoteFeder lote = datos.buscar(LoteFeder.class, p.id(0));
		List<List<String>> filas = new ArrayList<>();
		agregarOrigen(filas, lote);
		return Resultado.tabla("Origen del lote FEDER " + lote.getId(), new Listado(List.of("dato", "valor"), filas));
	}

	public Resultado trazarLinea(Actor actor, Args p) {
		DetalleVenta linea = datos.buscar(DetalleVenta.class, p.id(0));
		boolean duena = linea.getVenta().getCliente() != null && actor.usuario() != null && linea.getVenta().getCliente().getUsuario().getId().equals(actor.usuario().getId());
		if (!actor.esPersonal() && !duena) {
			throw new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO, "Solo puede consultar sus propias compras.");
		}
		List<List<String>> filas = new ArrayList<>();
		consultas.movimientosDeLinea(linea.getId(), MotivoMovimientoFeder.VENTA).forEach(m -> {
					filas.add(List.of("despacho " + m.getId() + " (unidades)", m.getCantidad().toString()));
					agregarOrigen(filas, m.getLoteFeder());
				});
		return Resultado.tabla("Origen de la linea " + linea.getId(), new Listado(List.of("dato", "valor"), filas));
	}

	private void agregarOrigen(List<List<String>> filas, LoteFeder lote) {
		LoteAsociacion origen = lote.getDetalleCompra().getLoteAsociacion();
		Persona persona = origen.getProductor() == null ? null : origen.getProductor().getPersona();
		filas.add(List.of("lote FEDER", lote.getCodigoLote()));
		filas.add(List.of("compra", String.valueOf(lote.getDetalleCompra().getCompra().getId())));
		filas.add(List.of("lote de origen", origen.getCodigoLote()));
		filas.add(List.of("producto", origen.getProducto().getNombre()));
		filas.add(List.of("asociacion", origen.getAsociacion().getNombre()));
		filas.add(List.of("municipio", origen.getMunicipio().getNombre()));
		filas.add(List.of("productor", persona == null ? "" : persona.getNombre() + " " + persona.getApellido()));
		filas.add(List.of("campana", origen.getProduccion() == null ? "origen directo" : String.valueOf(origen.getProduccion().getId())));
	}

	public Resultado custodiaLote(Actor actor, Args p) {
		LoteAsociacion lote = datos.buscar(LoteAsociacion.class, p.id(0));
		exigirSobreLote(actor, lote);
		List<List<String>> filas = consultas.movimientosDeLote(lote.getId()).stream()
				.map(m -> List.of(String.valueOf(m.getFecha()), m.getTipo().name(), m.getCantidad().toString(),
						m.getInventarioOrigen() == null ? "" : m.getInventarioOrigen().getNombre(),
						m.getInventarioDestino() == null ? "" : m.getInventarioDestino().getNombre()))
				.toList();
		return Resultado.tabla("Movimientos del lote " + lote.getId(), new Listado(List.of("fecha", "tipo", "cantidad", "origen", "destino"), filas));
	}

	public Resultado misLotes(Actor actor, Args p) {
		Integer usuario = datos.firmante(actor).getId();
		List<LoteAsociacion> lotes = consultas.lotesDeUsuario(usuario);
		List<List<String>> filas = lotes.stream().map(l -> List.of(String.valueOf(l.getId()), l.getCodigoLote(), l.getProducto().getNombre(),
				l.getCantidadInicial().toString(), l.getCantidadDisponible().toString(), l.getEstadoValidacion().name(), l.getInventario().getNombre())).toList();
		return Resultado.tabla("Mis lotes", new Listado(List.of("id", "codigo", "producto", "inicial", "saldo", "validacion", "custodia"), filas));
	}

	public Resultado misProducciones(Actor actor, Args p) {
		Integer usuario = datos.firmante(actor).getId();
		List<List<String>> filas = consultas.produccionesDeUsuario(usuario).stream()
				.map(c -> List.of(String.valueOf(c.getId()), c.getProducto().getNombre(), c.getCantidadEstimada().toString(),
						String.valueOf(c.getFechaSiembra()), String.valueOf(c.getFechaCosechaEstimada()), c.getEstado().name()))
				.toList();
		return Resultado.tabla("Mis campanas", new Listado(List.of("id", "producto", "estimado", "siembra", "cosecha", "estado"), filas));
	}

}

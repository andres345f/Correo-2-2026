package com.example.correo.service;

import com.example.correo.dto.Listado;
import com.example.correo.dto.Resultado;
import com.example.correo.entity.AlertaStock;
import com.example.correo.entity.Compra;
import com.example.correo.entity.DetalleCompra;
import com.example.correo.entity.EmpleadoFeder;
import com.example.correo.entity.Inventario;
import com.example.correo.entity.LoteAsociacion;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.MovimientoInventario;
import com.example.correo.entity.Producto;
import com.example.correo.entity.enums.EstadoValidacionLote;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import com.example.correo.entity.enums.TipoInventario;
import com.example.correo.entity.enums.TipoMovimientoFeder;
import com.example.correo.entity.enums.TipoMovimientoInventario;
import com.example.correo.entity.enums.TipoUbicacion;
import com.example.correo.repository.DetalleCompraRepository;
import com.example.correo.repository.InventarioConsultasRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU4: compras, recepcion, libro comercial, stock vendible, alertas y catalogo. */
@Service
public class InventarioService {

	private final InventarioConsultasRepository consultas;

	private final Datos datos;
	private final DetalleCompraRepository detalles;

	public InventarioService(Datos datos, DetalleCompraRepository detalles, InventarioConsultasRepository consultas) {
		this.consultas = consultas;
		this.datos = datos;
		this.detalles = detalles;
	}

	public Resultado crearCompra(Actor actor, Args p) {
		Compra compra = new Compra();
		compra.setAsociacion(datos.buscar(com.example.correo.entity.Asociacion.class, p.id(0)));
		compra.setObservacion(p.opcional(1));
		compra.setFecha(LocalDate.now());
		compra.setEmpleadoFeder(datos.empleado(actor));
		compra.setTotal(BigDecimal.ZERO);
		datos.guardar(compra);
		return Resultado.exito("Compra creada con id " + compra.getId() + ".");
	}

	public Resultado agregarLineaCompra(Actor actor, Args p) {
		Compra compra = datos.bloquear(Compra.class, p.id(0));
		LoteAsociacion lote = datos.buscar(LoteAsociacion.class, p.id(1));
		if (!lote.getAsociacion().getId().equals(compra.getAsociacion().getId())) {
			throw Datos.regla("El lote no es de la asociacion proveedora de esta compra.");
		}
		DetalleCompra detalle = new DetalleCompra();
		detalle.setCompra(compra);
		detalle.setLoteAsociacion(lote);
		detalle.setCantidad(p.positivo(2));
		detalle.setPrecioPagado(p.noNegativo(3));
		detalle.setSubtotal(detalle.getCantidad().multiply(detalle.getPrecioPagado()).setScale(2, RoundingMode.HALF_UP));
		datos.guardar(detalle);
		compra.setTotal(detalles.sumarSubtotales(compra.getId()));
		return Resultado.exito("Linea creada con id " + detalle.getId() + ": subtotal " + detalle.getSubtotal() + ". Total de la compra: " + compra.getTotal() + ".");
	}

	public Resultado quitarLineaCompra(Actor actor, Args p) {
		DetalleCompra detalle = datos.buscar(DetalleCompra.class, p.id(0));
		Compra compra = datos.bloquear(Compra.class, detalle.getCompra().getId());
		if (loteDe(detalle.getId()) != null) {
			throw Datos.regla("Esa linea ya fue recibida: conserva su cantidad y precio.");
		}
		datos.eliminar(detalle);
		datos.sincronizar();
		compra.setTotal(detalles.sumarSubtotales(compra.getId()));
		return Resultado.exito("Linea quitada. Total de la compra: " + compra.getTotal() + ".");
	}

	private LoteFeder loteDe(Integer detalle) {
		return consultas.lotesDeDetalleCompra(detalle).stream().findFirst().orElse(null);
	}

	/** Salida del proveedor, lote comercial y entrada a FEDER: los tres se confirman o se revierten juntos. */
	public Resultado recibir(Actor actor, Args p) {
		Integer idDetalle = p.id(0);
		Integer idLote = consultas.loteAsociacionDeDetalle(idDetalle).stream().findFirst()
				.orElseThrow(() -> new ComandoInvalidoException(CodigoError.REGISTRO_NO_ENCONTRADO, "No existe DetalleCompra con id " + idDetalle + "."));
		// Se bloquea antes de leer saldo: dos compras no pueden quedarse con las mismas unidades.
		LoteAsociacion origen = datos.bloquear(LoteAsociacion.class, idLote);
		DetalleCompra detalle = datos.buscar(DetalleCompra.class, idDetalle);
		LoteFeder recibido = loteDe(idDetalle);
		if (recibido != null) {
			return Resultado.exito("Esa linea ya estaba recibida en el lote FEDER " + recibido.getId() + ".");
		}
		Inventario almacen = datos.buscar(Inventario.class, p.id(1));
		if (almacen.getTipo() != TipoInventario.FEDER || !Boolean.TRUE.equals(almacen.getActivo()) || almacen.getUbicacion().getTipo() != TipoUbicacion.ALMACEN_FEDER) {
			throw Datos.regla("El destino debe ser un inventario FEDER activo en un almacen.");
		}
		if (origen.getEstadoValidacion() != EstadoValidacionLote.VALIDADO) {
			throw Datos.regla("El lote de origen no esta validado.");
		}
		Inventario custodia = origen.getInventario();
		if (custodia.getTipo() != TipoInventario.ASOCIACION || !custodia.getAsociacion().getId().equals(detalle.getCompra().getAsociacion().getId())) {
			throw Datos.regla("El lote debe estar en el centro de acopio de la asociacion proveedora.");
		}
		if (detalle.getCantidad().compareTo(origen.getCantidadDisponible()) > 0) {
			throw Datos.regla("El lote solo tiene " + origen.getCantidadDisponible() + " unidades disponibles.");
		}
		EmpleadoFeder empleado = datos.empleado(actor);
		origen.setCantidadDisponible(origen.getCantidadDisponible().subtract(detalle.getCantidad()));

		MovimientoInventario salida = new MovimientoInventario();
		salida.setLoteAsociacion(origen);
		salida.setTipo(TipoMovimientoInventario.TRANSFERENCIA);
		salida.setCantidad(detalle.getCantidad());
		salida.setInventarioOrigen(custodia);
		salida.setDetalleCompra(detalle);
		salida.setClaveOperacion("REC-D" + idDetalle);
		salida.setFecha(LocalDateTime.now());
		salida.setUsuario(datos.firmante(actor));
		datos.guardar(salida);

		LoteFeder lote = new LoteFeder();
		lote.setCodigoLote("F-" + idDetalle);
		lote.setDetalleCompra(detalle);
		lote.setInventario(almacen);
		lote.setProducto(origen.getProducto());
		lote.setFechaIngreso(LocalDate.now());
		lote.setFechaVencimiento(origen.getFechaVencimiento());
		lote.setPrecioAdquisicion(detalle.getPrecioPagado());
		lote.setCantidadActual(BigDecimal.ZERO);
		lote.setCantidadReservada(BigDecimal.ZERO);
		datos.guardar(lote);
		asentar(lote, empleado, MotivoMovimientoFeder.COMPRA, detalle.getCantidad(), "ENT-D" + idDetalle, null, null, null);
		lote.setCantidadActual(detalle.getCantidad());
		evaluarAlerta(lote.getProducto().getId());
		return Resultado.exito("Recibidas " + detalle.getCantidad() + " unidades en el lote FEDER " + lote.getId() + ". Quedan " + origen.getCantidadDisponible() + " en el proveedor.");
	}

	public MovimientoFeder asentar(LoteFeder lote, EmpleadoFeder empleado, MotivoMovimientoFeder motivo, BigDecimal cantidad, String clave, String observacion,
			com.example.correo.entity.DetalleVenta linea, com.example.correo.entity.Devolucion devolucion) {
		MovimientoFeder asiento = new MovimientoFeder();
		asiento.setLoteFeder(lote);
		asiento.setEmpleadoFeder(empleado);
		asiento.setMotivo(motivo);
		asiento.setTipo(tipoSegun(motivo));
		asiento.setCantidad(cantidad);
		asiento.setClaveOperacion(clave);
		asiento.setObservacion(observacion);
		asiento.setDetalleVenta(linea);
		asiento.setDevolucion(devolucion);
		asiento.setFecha(LocalDateTime.now());
		datos.guardar(asiento);
		return asiento;
	}

	private TipoMovimientoFeder tipoSegun(MotivoMovimientoFeder motivo) {
		return switch (motivo) {
			case COMPRA, DEVOLUCION -> TipoMovimientoFeder.ENTRADA;
			case VENTA, REPOSICION -> TipoMovimientoFeder.SALIDA;
			case VENCIDO, DANADO, DEFECTUOSO -> TipoMovimientoFeder.BAJA;
			case AJUSTE -> TipoMovimientoFeder.AJUSTE;
		};
	}

	public boolean claveUsada(String clave) {
		return consultas.contarMovimientosConClave(clave) > 0;
	}

	public Resultado darDeBaja(Actor actor, Args p) {
		String clave = p.clave(4);
		if (claveUsada(clave)) {
			return Resultado.exito("Esa baja ya estaba registrada.");
		}
		MotivoMovimientoFeder motivo = p.enumerado(2, MotivoMovimientoFeder.class);
		if (!Set.of(MotivoMovimientoFeder.VENCIDO, MotivoMovimientoFeder.DANADO, MotivoMovimientoFeder.DEFECTUOSO).contains(motivo)) {
			throw Datos.regla("El motivo de baja debe ser VENCIDO, DANADO o DEFECTUOSO.");
		}
		LoteFeder lote = datos.bloquear(LoteFeder.class, p.id(0));
		BigDecimal cantidad = p.positivo(1);
		exigirLibre(lote, cantidad);
		asentar(lote, datos.empleado(actor), motivo, cantidad, clave, p.opcional(3), null, null);
		lote.setCantidadActual(lote.getCantidadActual().subtract(cantidad));
		evaluarAlerta(lote.getProducto().getId());
		return Resultado.exito("Baja registrada. Saldo fisico del lote: " + lote.getCantidadActual() + ".");
	}

	public Resultado ajustarLote(Actor actor, Args p) {
		String clave = p.clave(3);
		if (claveUsada(clave)) {
			return Resultado.exito("Ese ajuste ya estaba registrado.");
		}
		LoteFeder lote = datos.bloquear(LoteFeder.class, p.id(0));
		BigDecimal delta = p.decimal(1);
		if (delta.signum() == 0) {
			throw Datos.regla("El ajuste no puede ser cero.");
		}
		if (delta.signum() < 0) {
			exigirLibre(lote, delta.abs());
		}
		asentar(lote, datos.empleado(actor), MotivoMovimientoFeder.AJUSTE, delta, clave, p.opcional(2), null, null);
		lote.setCantidadActual(lote.getCantidadActual().add(delta));
		evaluarAlerta(lote.getProducto().getId());
		return Resultado.exito("Ajuste registrado. Saldo fisico del lote: " + lote.getCantidadActual() + ".");
	}

	public void exigirLibre(LoteFeder lote, BigDecimal cantidad) {
		BigDecimal libre = lote.getCantidadActual().subtract(lote.getCantidadReservada());
		if (cantidad.compareTo(libre) > 0) {
			throw Datos.regla("El lote " + lote.getId() + " solo tiene " + libre + " unidades libres.");
		}
	}

	/** Consolida un lote en otro almacen FEDER: el total de FEDER no cambia. */
	public Resultado moverLote(Actor actor, Args p) {
		LoteFeder lote = datos.bloquear(LoteFeder.class, p.id(0));
		Inventario destino = datos.buscar(Inventario.class, p.id(1));
		if (destino.getTipo() != TipoInventario.FEDER || !Boolean.TRUE.equals(destino.getActivo())) {
			throw Datos.regla("El destino debe ser un inventario FEDER activo.");
		}
		lote.setInventario(destino);
		return Resultado.exito("Lote " + lote.getId() + " movido a " + destino.getNombre() + ".");
	}

	public BigDecimal disponible(Integer producto) {
		return consultas.saldoDisponible(producto, LocalDate.now());
	}

	/** Abre una alerta al llegar al minimo; mientras siga abierta, el episodio es el mismo. */
	public void evaluarAlerta(Integer idProducto) {
		Producto producto = datos.bloquear(Producto.class, idProducto);
		datos.sincronizar();
		BigDecimal disponible = disponible(idProducto);
		if (producto.getStockMinimo() == null || disponible.compareTo(producto.getStockMinimo()) > 0) {
			return;
		}
		long abiertas = consultas.contarAlertasAbiertas(idProducto);
		if (abiertas == 0) {
			AlertaStock alerta = new AlertaStock();
			alerta.setProducto(producto);
			alerta.setStockActual(disponible);
			alerta.setStockMinimo(producto.getStockMinimo());
			alerta.setFechaGenerada(LocalDateTime.now());
			alerta.setAtendida(false);
			datos.guardar(alerta);
		}
	}

	/** Los lotes que vencen dejan de ser vendibles sin que ningun comando lo avise. */
	@Scheduled(fixedDelay = 300000)
	@Transactional
	public void revisarStock() {
		consultas.idsProductosActivos().forEach(this::evaluarAlerta);
	}

	public Resultado atenderAlerta(Actor actor, Args p) {
		AlertaStock alerta = datos.bloquear(AlertaStock.class, p.id(0));
		if (Boolean.TRUE.equals(alerta.getAtendida())) {
			return Resultado.exito("Esa alerta ya estaba atendida.");
		}
		alerta.setAtendida(true);
		alerta.setFechaAtendida(LocalDateTime.now());
		alerta.setEmpleadoFeder(datos.empleado(actor));
		return Resultado.exito("Alerta " + alerta.getId() + " atendida.");
	}

	public Resultado buscarCatalogo(Actor actor, Args p) {
		String nombre = filtro(p.opcional(0));
		String categoria = filtro(p.opcional(1));
		String presentacion = filtro(p.opcional(2));
		String municipio = filtro(p.opcional(3));
		List<Integer> conProcedencia = municipio == null ? null : consultas.idsProductosPorMunicipio(municipio, LocalDate.now());
		List<List<String>> filas = consultas.productosActivos().stream()
				.filter(c -> coincide(c.getNombre(), nombre) && coincide(c.getCategoria().getNombre(), categoria) && coincide(c.getPresentacion(), presentacion))
				.filter(c -> conProcedencia == null || conProcedencia.contains(c.getId()))
				.map(c -> List.of(String.valueOf(c.getId()), c.getNombre(), c.getCategoria().getNombre(), Datos.texto(c.getPresentacion()),
						Datos.texto(c.getUnidadMedida()), Datos.texto(c.getImagenUrl()), c.getPrecioVenta().toString(), disponible(c.getId()).toString()))
				.toList();
		return Resultado.tabla("Catalogo", new Listado(List.of("id", "nombre", "categoria", "presentacion", "unidad", "imagen", "precio", "disponible"), filas));
	}

	private String filtro(String valor) {
		return valor == null || "*".equals(valor) ? null : "%" + valor.toLowerCase(Locale.ROOT) + "%";
	}

	private boolean coincide(String valor, String filtro) {
		return filtro == null || (valor != null && valor.toLowerCase(Locale.ROOT).contains(filtro.substring(1, filtro.length() - 1)));
	}

	public Resultado stockProducto(Actor actor, Args p) {
		Producto producto = datos.buscar(Producto.class, p.id(0));
		List<List<String>> filas = consultas.lotesDeProducto(producto.getId()).stream()
				.map(l -> List.of(String.valueOf(l.getId()), l.getCodigoLote(), l.getCantidadActual().toString(), l.getCantidadReservada().toString(),
						l.getCantidadActual().subtract(l.getCantidadReservada()).toString(), Datos.texto(l.getFechaVencimiento()), l.getInventario().getNombre()))
				.toList();
		return Resultado.tabla("Stock de " + producto.getNombre() + ": " + disponible(producto.getId()) + " vendibles",
				new Listado(List.of("lote", "codigo", "fisico", "reservado", "libre", "vencimiento", "almacen"), filas));
	}

	/** Para reponer: lo validado que ya existe y lo que las asociaciones prevén. */
	public Resultado opcionesDeReposicion(Actor actor, Args p) {
		Integer producto = datos.buscar(Producto.class, p.id(0)).getId();
		List<List<String>> filas = new java.util.ArrayList<>(consultas.lotesDeProveedorPorEstado(producto, EstadoValidacionLote.VALIDADO).stream()
				.map(l -> List.of("lote " + l.getId(), l.getAsociacion().getNombre(), l.getCantidadDisponible().toString(), l.getPrecioOfrecido().toString(), "hoy"))
				.toList());
		consultas.ofertasActivasDeProducto(producto)
				.forEach(o -> filas.add(List.of("prevision", o.getAsociacion().getNombre(), o.getCantidadPrevista().toString(), Datos.texto(o.getPrecioPrevisto()), String.valueOf(o.getDisponibleDesde()))));
		return Resultado.tabla("Opciones de reposicion", new Listado(List.of("origen", "asociacion", "cantidad", "precio", "disponible desde"), filas));
	}

}

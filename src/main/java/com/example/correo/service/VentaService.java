package com.example.correo.service;

import com.example.correo.dto.Listado;
import com.example.correo.dto.Resultado;
import com.example.correo.entity.Cliente;
import com.example.correo.entity.DetalleVenta;
import com.example.correo.entity.EmpleadoFeder;
import com.example.correo.entity.Entrega;
import com.example.correo.entity.HistorialEstadoVenta;
import com.example.correo.entity.Inventario;
import com.example.correo.entity.ItemCarrito;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.Producto;
import com.example.correo.entity.ReservaStock;
import com.example.correo.entity.Venta;
import com.example.correo.entity.enums.EstadoEntrega;
import com.example.correo.entity.enums.EstadoReservaStock;
import com.example.correo.entity.enums.EstadoVenta;
import com.example.correo.entity.enums.ModalidadEntrega;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import com.example.correo.entity.enums.TipoInventario;
import com.example.correo.entity.enums.TipoVenta;
import com.example.correo.repository.DetalleVentaRepository;
import com.example.correo.repository.VentaConsultasRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU5 y CU6: carrito, venta, reservas, estados, despacho y entrega. */
@Service
public class VentaService {

	private final VentaConsultasRepository consultas;

	private static final int HORAS_RESERVA = 48;
	private static final Set<EstadoVenta> DESPACHABLES = Set.of(EstadoVenta.CONFIRMADO, EstadoVenta.EN_PREPARACION, EstadoVenta.LISTO);

	private final Datos datos;
	private final InventarioService inventario;
	private final NotificacionService notificaciones;
	private final DetalleVentaRepository lineas;

	public VentaService(Datos datos, InventarioService inventario, NotificacionService notificaciones, DetalleVentaRepository lineas, VentaConsultasRepository consultas) {
		this.consultas = consultas;
		this.datos = datos;
		this.inventario = inventario;
		this.notificaciones = notificaciones;
		this.lineas = lineas;
	}

	// ---------- Carrito ----------

	private Optional<ItemCarrito> item(Cliente cliente, Integer producto) {
		return consultas.itemsDeClienteYProducto(cliente.getId(), producto).stream().findFirst();
	}

	public Resultado agregarAlCarrito(Actor actor, Args p) {
		Cliente cliente = datos.cliente(actor);
		Producto producto = datos.buscar(Producto.class, p.id(0));
		if (!Boolean.TRUE.equals(producto.getActivo())) {
			throw Datos.regla("Ese producto no esta activo en el catalogo.");
		}
		BigDecimal cantidad = p.positivo(1);
		Optional<ItemCarrito> existente = item(cliente, producto.getId());
		if (existente.isPresent()) {
			existente.get().setCantidad(existente.get().getCantidad().add(cantidad));
		} else {
			ItemCarrito nuevo = new ItemCarrito();
			nuevo.setCliente(cliente);
			nuevo.setProducto(producto);
			nuevo.setCantidad(cantidad);
			nuevo.setFechaAgregado(LocalDateTime.now());
			datos.guardar(nuevo);
		}
		return Resultado.exito("Agregado al carrito. El carrito no aparta unidades: se comprueban al confirmar.");
	}

	public Resultado cambiarCarrito(Actor actor, Args p) {
		ItemCarrito existente = item(datos.cliente(actor), p.id(0)).orElseThrow(() -> Datos.regla("Ese producto no esta en su carrito."));
		existente.setCantidad(p.positivo(1));
		return Resultado.exito("Cantidad actualizada a " + existente.getCantidad() + ".");
	}

	public Resultado quitarDelCarrito(Actor actor, Args p) {
		datos.eliminar(item(datos.cliente(actor), p.id(0)).orElseThrow(() -> Datos.regla("Ese producto no esta en su carrito.")));
		return Resultado.exito("Producto quitado del carrito.");
	}

	private List<ItemCarrito> carrito(Cliente cliente) {
		return consultas.carritoDeCliente(cliente.getId());
	}

	public Resultado verCarrito(Actor actor, Args p) {
		List<ItemCarrito> items = carrito(datos.cliente(actor));
		BigDecimal total = BigDecimal.ZERO;
		List<List<String>> filas = new ArrayList<>();
		for (ItemCarrito i : items) {
			BigDecimal subtotal = importe(i.getCantidad(), i.getProducto().getPrecioVenta());
			total = total.add(subtotal);
			filas.add(List.of(String.valueOf(i.getProducto().getId()), i.getProducto().getNombre(), i.getCantidad().toString(), i.getProducto().getPrecioVenta().toString(), subtotal.toString()));
		}
		return Resultado.tabla("Su carrito: " + total + " Bs", new Listado(List.of("producto", "nombre", "cantidad", "precio", "subtotal"), filas));
	}

	private BigDecimal importe(BigDecimal cantidad, BigDecimal precio) {
		return cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP);
	}

	// ---------- Creacion ----------

	private Venta nuevaVenta(TipoVenta tipo, Cliente cliente, EmpleadoFeder empleado) {
		Venta venta = new Venta();
		// El codigo definitivo usa el id, que recien existe tras insertar.
		venta.setCodigo("TMP-" + UUID.randomUUID());
		venta.setTipo(tipo);
		venta.setCliente(cliente);
		venta.setEmpleadoFeder(empleado);
		venta.setEstado(EstadoVenta.PENDIENTE);
		venta.setFecha(LocalDateTime.now());
		venta.setFechaActualizacion(venta.getFecha());
		venta.setCostoEnvio(BigDecimal.ZERO);
		venta.setSubtotal(BigDecimal.ZERO);
		venta.setTotal(BigDecimal.ZERO);
		datos.guardar(venta);
		venta.setCodigo("V-" + venta.getId());
		registrarPaso(venta, null, EstadoVenta.PENDIENTE, empleado);
		return venta;
	}

	private DetalleVenta agregarLinea(Venta venta, Producto producto, BigDecimal cantidad) {
		DetalleVenta linea = new DetalleVenta();
		linea.setVenta(venta);
		linea.setProducto(producto);
		linea.setCantidad(cantidad);
		linea.setPrecioUnitario(producto.getPrecioVenta());
		linea.setSubtotal(importe(cantidad, producto.getPrecioVenta()));
		datos.guardar(linea);
		return linea;
	}

	private void recalcular(Venta venta) {
		venta.setSubtotal(lineas.sumarSubtotales(venta.getId()));
		venta.setTotal(venta.getSubtotal().add(Datos.cero(venta.getCostoEnvio())));
	}

	/** CU5: el precio se congela aqui; las unidades se apartan recien al confirmar. */
	public Resultado pedirDesdeCarrito(Actor actor, Args p) {
		Cliente cliente = datos.cliente(actor);
		List<ItemCarrito> items = carrito(cliente);
		if (items.isEmpty()) {
			throw Datos.regla("Su carrito esta vacio.");
		}
		ModalidadEntrega modalidad = p.enumerado(0, ModalidadEntrega.class);
		if (modalidad == ModalidadEntrega.DELIVERY_PROPIO && (p.vacio(1) || p.vacio(3))) {
			throw Datos.regla("Para delivery indique direccion y telefono de contacto.");
		}
		for (ItemCarrito i : items) {
			if (!Boolean.TRUE.equals(i.getProducto().getActivo())) {
				throw Datos.regla(i.getProducto().getNombre() + " ya no esta activo.");
			}
			BigDecimal disponible = inventario.disponible(i.getProducto().getId());
			if (i.getCantidad().compareTo(disponible) > 0) {
				throw Datos.regla("Solo hay " + disponible + " de " + i.getProducto().getNombre() + ".");
			}
		}
		Venta venta = nuevaVenta(TipoVenta.ENCARGO, cliente, null);
		items.forEach(i -> agregarLinea(venta, i.getProducto(), i.getCantidad()));
		Entrega entrega = new Entrega();
		entrega.setVenta(venta);
		entrega.setModalidad(modalidad);
		entrega.setEstado(EstadoEntrega.PENDIENTE);
		entrega.setInventario(almacenPrincipal());
		entrega.setDireccion(p.opcional(1));
		entrega.setReferencia(p.opcional(2));
		entrega.setTelefonoContacto(p.opcional(3));
		datos.guardar(entrega);
		items.forEach(datos::eliminar);
		recalcular(venta);
		return Resultado.exito("Pedido " + venta.getCodigo() + " creado con id " + venta.getId() + ": productos " + venta.getSubtotal() + " Bs. El personal lo confirmara.");
	}

	private Inventario almacenPrincipal() {
		return consultas.almacenesPrincipales(TipoInventario.FEDER).stream().findFirst()
				.orElseThrow(() -> Datos.regla("No hay un almacen FEDER principal activo."));
	}

	public Resultado crearVentaInmediata(Actor actor, Args p) {
		Integer idCliente = p.idOpcional(0);
		Venta venta = nuevaVenta(TipoVenta.INMEDIATA, idCliente == null ? null : datos.buscar(Cliente.class, idCliente), datos.empleado(actor));
		return Resultado.exito("Venta " + venta.getCodigo() + " creada con id " + venta.getId() + ".");
	}

	private Venta ventaPendiente(Integer id) {
		Venta venta = datos.bloquear(Venta.class, id);
		if (venta.getEstado() != EstadoVenta.PENDIENTE) {
			throw Datos.regla("La venta ya no esta PENDIENTE: sus lineas estan congeladas.");
		}
		return venta;
	}

	public Resultado agregarLineaVenta(Actor actor, Args p) {
		Venta venta = ventaPendiente(p.id(0));
		Producto producto = datos.buscar(Producto.class, p.id(1));
		if (!Boolean.TRUE.equals(producto.getActivo())) {
			throw Datos.regla("Ese producto no esta activo en el catalogo.");
		}
		DetalleVenta linea = agregarLinea(venta, producto, p.positivo(2));
		recalcular(venta);
		return Resultado.exito("Linea creada con id " + linea.getId() + ". Total de la venta: " + venta.getTotal() + " Bs.");
	}

	public Resultado quitarLineaVenta(Actor actor, Args p) {
		DetalleVenta linea = datos.buscar(DetalleVenta.class, p.id(0));
		Venta venta = ventaPendiente(linea.getVenta().getId());
		datos.eliminar(linea);
		datos.sincronizar();
		recalcular(venta);
		return Resultado.exito("Linea quitada. Total de la venta: " + venta.getTotal() + " Bs.");
	}

	// ---------- Estados ----------

	private Optional<Entrega> entrega(Venta venta) {
		return consultas.entregasDeVenta(venta.getId()).stream().findFirst();
	}

	private boolean esDelivery(Venta venta) {
		return entrega(venta).map(e -> e.getModalidad() == ModalidadEntrega.DELIVERY_PROPIO).orElse(false);
	}

	private boolean permitida(Venta venta, EstadoVenta a) {
		return switch (venta.getEstado()) {
			case PENDIENTE -> a == EstadoVenta.CONFIRMADO || a == EstadoVenta.CANCELADO;
			case CONFIRMADO -> a == EstadoVenta.CANCELADO || a == (venta.getTipo() == TipoVenta.ENCARGO ? EstadoVenta.EN_PREPARACION : EstadoVenta.ENTREGADO);
			case EN_PREPARACION -> a == EstadoVenta.LISTO || a == EstadoVenta.CANCELADO;
			case LISTO -> a == EstadoVenta.CANCELADO || a == (esDelivery(venta) ? EstadoVenta.EN_RUTA : EstadoVenta.ENTREGADO);
			case EN_RUTA -> a == EstadoVenta.ENTREGADO || a == EstadoVenta.CANCELADO;
			default -> false;
		};
	}

	private void registrarPaso(Venta venta, EstadoVenta anterior, EstadoVenta nuevo, EmpleadoFeder empleado) {
		Integer ultima = consultas.ultimaSecuenciaDeVenta(venta.getId());
		HistorialEstadoVenta paso = new HistorialEstadoVenta();
		paso.setVenta(venta);
		paso.setSecuencia(ultima + 1);
		paso.setEstadoAnterior(anterior);
		paso.setEstadoNuevo(nuevo);
		paso.setEmpleadoFeder(empleado);
		paso.setFecha(LocalDateTime.now());
		datos.guardar(paso);
	}

	private void transicion(Venta venta, EstadoVenta nuevo, EmpleadoFeder empleado) {
		if (!permitida(venta, nuevo)) {
			throw Datos.regla("Una venta " + venta.getTipo() + " en " + venta.getEstado() + " no puede pasar a " + nuevo + ".");
		}
		EstadoVenta anterior = venta.getEstado();
		registrarPaso(venta, anterior, nuevo, empleado);
		venta.setEstado(nuevo);
		venta.setFechaActualizacion(LocalDateTime.now());
		venta.setEmpleadoUltimoCambio(empleado);
		notificaciones.avisarEstado(venta, nuevo);
	}

	public Resultado avanzarEstado(Actor actor, Args p) {
		EstadoVenta nuevo = p.enumerado(1, EstadoVenta.class);
		if (nuevo != EstadoVenta.EN_PREPARACION && nuevo != EstadoVenta.LISTO) {
			throw Datos.regla("Con este comando solo se pasa a EN_PREPARACION o LISTO. Los demas estados tienen su propio comando.");
		}
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		transicion(venta, nuevo, datos.empleado(actor));
		return Resultado.exito("Venta " + venta.getCodigo() + " en estado " + nuevo + ".");
	}

	// ---------- Reservas ----------

	/** Orden global de bloqueo en toda operacion: venta, lineas por id, lotes por id. */
	private List<DetalleVenta> bloquearLineas(Venta venta) {
		return consultas.bloquearLineasDeVenta(venta.getId());
	}

	private Map<Integer, LoteFeder> bloquearLotes(List<Integer> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return consultas.bloquearLotesPorIds(ids).stream()
				.collect(Collectors.toMap(LoteFeder::getId, Function.identity()));
	}

	private BigDecimal despachado(DetalleVenta linea) {
		return consultas.cantidadDespachada(linea.getId(), MotivoMovimientoFeder.VENTA);
	}

	private List<ReservaStock> activas(Venta venta) {
		return consultas.reservasDeVentaPorEstado(venta.getId(), EstadoReservaStock.ACTIVA);
	}

	private boolean vendible(LoteFeder lote) {
		return Boolean.TRUE.equals(lote.getInventario().getActivo()) && Boolean.TRUE.equals(lote.getProducto().getActivo())
				&& (lote.getFechaVencimiento() == null || lote.getFechaVencimiento().isAfter(LocalDate.now()));
	}

	private List<Integer> lotesDeProductos(List<DetalleVenta> lineasVenta) {
		List<Integer> productos = lineasVenta.stream().map(l -> l.getProducto().getId()).distinct().toList();
		return consultas.idsLotesDeProductos(productos);
	}

	/** Toma primero lo que vence antes; si algo no alcanza, la excepcion revierte todo. */
	private void reservar(Venta venta) {
		List<DetalleVenta> lineasVenta = bloquearLineas(venta);
		if (lineasVenta.isEmpty()) {
			throw Datos.regla("La venta no tiene lineas.");
		}
		Map<Integer, LoteFeder> lotes = bloquearLotes(lotesDeProductos(lineasVenta));
		liberarVencidas(activas(venta), lotes);
		datos.sincronizar();
		LocalDateTime ahora = LocalDateTime.now();
		List<ReservaStock> vigentes = activas(venta);
		for (DetalleVenta linea : lineasVenta) {
			BigDecimal reservado = vigentes.stream().filter(r -> r.getDetalleVenta().getId().equals(linea.getId())).map(ReservaStock::getCantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
			BigDecimal pendiente = linea.getCantidad().subtract(despachado(linea)).subtract(reservado);
			List<LoteFeder> candidatos = lotes.values().stream()
					.filter(l -> l.getProducto().getId().equals(linea.getProducto().getId()) && vendible(l))
					.sorted(Comparator.comparing(LoteFeder::getFechaVencimiento, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(LoteFeder::getId))
					.toList();
			for (LoteFeder lote : candidatos) {
				if (pendiente.signum() <= 0) {
					break;
				}
				BigDecimal tomar = pendiente.min(lote.getCantidadActual().subtract(lote.getCantidadReservada()));
				if (tomar.signum() > 0) {
					apartar(linea, lote, tomar, ahora, vigentes);
					pendiente = pendiente.subtract(tomar);
				}
			}
			if (pendiente.signum() > 0) {
				throw Datos.regla("No hay stock suficiente de " + linea.getProducto().getNombre() + ": faltan " + pendiente + ".");
			}
		}
		lineasVenta.stream().map(l -> l.getProducto().getId()).distinct().sorted().forEach(inventario::evaluarAlerta);
	}

	private void apartar(DetalleVenta linea, LoteFeder lote, BigDecimal cantidad, LocalDateTime ahora, List<ReservaStock> vigentes) {
		lote.setCantidadReservada(lote.getCantidadReservada().add(cantidad));
		Optional<ReservaStock> misma = vigentes.stream().filter(r -> r.getDetalleVenta().getId().equals(linea.getId()) && r.getLoteFeder().getId().equals(lote.getId())).findFirst();
		if (misma.isPresent()) {
			misma.get().setCantidad(misma.get().getCantidad().add(cantidad));
			return;
		}
		ReservaStock reserva = new ReservaStock();
		reserva.setDetalleVenta(linea);
		reserva.setLoteFeder(lote);
		reserva.setCantidad(cantidad);
		reserva.setEstado(EstadoReservaStock.ACTIVA);
		reserva.setFechaReserva(ahora);
		reserva.setFechaExpiracion(ahora.plusHours(HORAS_RESERVA));
		datos.guardar(reserva);
	}

	private void cerrar(ReservaStock reserva, EstadoReservaStock estado, LoteFeder lote) {
		reserva.setEstado(estado);
		reserva.setFechaCierre(LocalDateTime.now());
		lote.setCantidadReservada(lote.getCantidadReservada().subtract(reserva.getCantidad()));
	}

	private void liberarVencidas(List<ReservaStock> reservas, Map<Integer, LoteFeder> lotes) {
		LocalDateTime ahora = LocalDateTime.now();
		reservas.stream().filter(r -> !r.getFechaExpiracion().isAfter(ahora)).forEach(r -> cerrar(r, EstadoReservaStock.LIBERADA, lotes.get(r.getLoteFeder().getId())));
	}

	@Scheduled(fixedDelay = 60000)
	@Transactional
	public void liberarReservasVencidas() {
		List<Integer> ventas = consultas.ventasConReservasVencidas(EstadoReservaStock.ACTIVA, LocalDateTime.now());
		for (Integer id : ventas) {
			Venta venta = datos.bloquear(Venta.class, id);
			List<DetalleVenta> lineasVenta = bloquearLineas(venta);
			List<ReservaStock> reservas = activas(venta);
			liberarVencidas(reservas, bloquearLotes(reservas.stream().map(r -> r.getLoteFeder().getId()).distinct().toList()));
			lineasVenta.stream().map(l -> l.getProducto().getId()).distinct().sorted().forEach(inventario::evaluarAlerta);
		}
	}

	/** CU6: fija el envio cobrado, comprueba stock y aparta unidades concretas. */
	public Resultado confirmar(Actor actor, Args p) {
		Venta venta = ventaPendiente(p.id(0));
		boolean delivery = esDelivery(venta);
		BigDecimal costo = p.vacio(1) ? BigDecimal.ZERO : p.noNegativo(1);
		if (!delivery && costo.signum() != 0) {
			throw Datos.regla("Sin delivery el costo de envio debe ser cero.");
		}
		EmpleadoFeder empleado = datos.empleado(actor);
		venta.setCostoEnvio(costo);
		venta.setEmpleadoFeder(empleado);
		recalcular(venta);
		reservar(venta);
		transicion(venta, EstadoVenta.CONFIRMADO, empleado);
		return Resultado.exito("Venta " + venta.getCodigo() + " confirmada: productos " + venta.getSubtotal() + " Bs, envio " + costo + " Bs, total " + venta.getTotal() + " Bs.");
	}

	/** Vuelve a apartar lo que se libero al vencer la reserva. */
	public Resultado reReservar(Actor actor, Args p) {
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		if (!DESPACHABLES.contains(venta.getEstado())) {
			throw Datos.regla("Solo se reserva una venta confirmada y sin despachar.");
		}
		reservar(venta);
		return Resultado.exito("Reservas de la venta " + venta.getCodigo() + " actualizadas.");
	}

	// ---------- Despacho y entrega ----------

	/** Consumir la reserva y asentar la salida ocurren en el mismo commit. */
	public Resultado despachar(Actor actor, Args p) {
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		if (!DESPACHABLES.contains(venta.getEstado())) {
			throw Datos.regla("La venta en estado " + venta.getEstado() + " no se puede despachar.");
		}
		List<DetalleVenta> lineasVenta = bloquearLineas(venta);
		List<Integer> idsLotes = consultas.idsLotesReservadosDeVenta(venta.getId(), EstadoReservaStock.ACTIVA);
		Map<Integer, LoteFeder> lotes = bloquearLotes(idsLotes);
		List<ReservaStock> reservas = activas(venta);
		if (reservas.isEmpty() && completa(lineasVenta)) {
			return Resultado.exito("La venta " + venta.getCodigo() + " ya estaba despachada.");
		}
		Optional<Entrega> envio = entrega(venta);
		LocalDateTime ahora = LocalDateTime.now();
		EmpleadoFeder empleado = datos.empleado(actor);
		for (ReservaStock reserva : reservas) {
			if (!reserva.getFechaExpiracion().isAfter(ahora)) {
				throw Datos.regla("La reserva " + reserva.getId() + " vencio: use RESVEN para volver a reservar.");
			}
			LoteFeder lote = lotes.get(reserva.getLoteFeder().getId());
			if (envio.isPresent() && !lote.getInventario().getId().equals(envio.get().getInventario().getId())) {
				throw Datos.regla("El lote " + lote.getId() + " no esta en el almacen de la entrega: consolide con MOVLOF.");
			}
			inventario.asentar(lote, empleado, MotivoMovimientoFeder.VENTA, reserva.getCantidad(), "DES-R" + reserva.getId(), null, reserva.getDetalleVenta(), null);
			lote.setCantidadActual(lote.getCantidadActual().subtract(reserva.getCantidad()));
			cerrar(reserva, EstadoReservaStock.CONSUMIDA, lote);
		}
		datos.sincronizar();
		if (!completa(lineasVenta)) {
			throw Datos.regla("Hay lineas sin reservar por completo: use RESVEN.");
		}
		return Resultado.exito("Venta " + venta.getCodigo() + " despachada.");
	}

	private boolean completa(List<DetalleVenta> lineasVenta) {
		return lineasVenta.stream().allMatch(l -> despachado(l).compareTo(l.getCantidad()) >= 0);
	}

	private void exigirDespachada(Venta venta) {
		if (!completa(bloquearLineas(venta))) {
			throw Datos.regla("Todas las lineas deben estar despachadas: use DESVEN.");
		}
	}

	public Resultado salirAReparto(Actor actor, Args p) {
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		Entrega envio = entrega(venta).filter(e -> e.getModalidad() == ModalidadEntrega.DELIVERY_PROPIO).orElseThrow(() -> Datos.regla("Esa venta no es con delivery."));
		boolean reintento = venta.getEstado() == EstadoVenta.EN_RUTA && envio.getEstado() == EstadoEntrega.FALLIDA;
		exigirDespachada(venta);
		EmpleadoFeder repartidor = datos.buscar(EmpleadoFeder.class, p.id(1));
		EmpleadoFeder empleado = datos.empleado(actor);
		if (!reintento) {
			transicion(venta, EstadoVenta.EN_RUTA, empleado);
		}
		envio.setEmpleadoFeder(repartidor);
		envio.setFechaSalida(LocalDateTime.now());
		envio.setEstado(EstadoEntrega.EN_RUTA);
		return Resultado.exito("Venta " + venta.getCodigo() + " en ruta.");
	}

	/** La entrega efectiva y la venta ENTREGADO se cierran juntas. */
	public Resultado cerrarEntrega(Actor actor, Args p) {
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		if (venta.getEstado() == EstadoVenta.ENTREGADO) {
			return Resultado.exito("La venta " + venta.getCodigo() + " ya estaba entregada.");
		}
		exigirDespachada(venta);
		Optional<Entrega> envio = entrega(venta);
		if (envio.isPresent() && envio.get().getModalidad() == ModalidadEntrega.DELIVERY_PROPIO && envio.get().getEstado() != EstadoEntrega.EN_RUTA) {
			throw Datos.regla("El delivery debe estar EN_RUTA: use SALENT.");
		}
		transicion(venta, EstadoVenta.ENTREGADO, datos.empleado(actor));
		BigDecimal costo = p.vacio(1) ? null : p.noNegativo(1);
		envio.ifPresent(e -> {
			e.setEstado(EstadoEntrega.ENTREGADA);
			e.setFechaEntrega(LocalDateTime.now());
			if (costo != null) {
				e.setCostoReal(costo);
			}
		});
		return Resultado.exito("Venta " + venta.getCodigo() + " entregada.");
	}

	public Resultado marcarEntregaFallida(Actor actor, Args p) {
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		Entrega envio = entrega(venta).filter(e -> e.getEstado() == EstadoEntrega.EN_RUTA).orElseThrow(() -> Datos.regla("Esa venta no tiene un reparto EN_RUTA."));
		envio.setEstado(EstadoEntrega.FALLIDA);
		envio.setObservacion(p.opcional(1));
		return Resultado.exito("Reparto fallido: la venta sigue sin ingreso reconocido. Use SALENT para reintentar o CANVEN para cancelar.");
	}

	public Resultado registrarCostoReparto(Actor actor, Args p) {
		Venta venta = datos.buscar(Venta.class, p.id(0));
		Entrega envio = entrega(venta).orElseThrow(() -> Datos.regla("Esa venta no tiene entrega."));
		envio.setCostoReal(p.noNegativo(1));
		return Resultado.exito("Costo real registrado: " + envio.getCostoReal() + " Bs.");
	}

	/** Libera lo reservado. El cobro se revierte con REVPAG y lo ya despachado con REIVEN. */
	public Resultado cancelar(Actor actor, Args p) {
		Venta venta = datos.bloquear(Venta.class, p.id(0));
		if (venta.getEstado() == EstadoVenta.CANCELADO) {
			return Resultado.exito("La venta " + venta.getCodigo() + " ya estaba cancelada.");
		}
		EmpleadoFeder empleado = null;
		if (actor.esPersonal()) {
			empleado = datos.empleado(actor);
		} else {
			boolean duena = venta.getCliente() != null && venta.getCliente().getId().equals(datos.cliente(actor).getId());
			if (!duena || (venta.getEstado() != EstadoVenta.PENDIENTE && venta.getEstado() != EstadoVenta.CONFIRMADO)) {
				throw new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO, "Solo puede cancelar sus pedidos pendientes o confirmados.");
			}
		}
		List<DetalleVenta> lineasVenta = bloquearLineas(venta);
		List<ReservaStock> reservas = activas(venta);
		Map<Integer, LoteFeder> lotes = bloquearLotes(reservas.stream().map(r -> r.getLoteFeder().getId()).distinct().toList());
		reservas.forEach(r -> cerrar(r, EstadoReservaStock.LIBERADA, lotes.get(r.getLoteFeder().getId())));
		transicion(venta, EstadoVenta.CANCELADO, empleado);
		entrega(venta).filter(e -> e.getEstado() != EstadoEntrega.FALLIDA).ifPresent(e -> {
			e.setEstado(EstadoEntrega.FALLIDA);
			e.setObservacion("Venta cancelada");
		});
		datos.sincronizar();
		lineasVenta.stream().map(l -> l.getProducto().getId()).distinct().sorted().forEach(inventario::evaluarAlerta);
		boolean salio = lineasVenta.stream().anyMatch(l -> despachado(l).signum() > 0);
		return Resultado.exito("Venta " + venta.getCodigo() + " cancelada." + (salio ? " Salieron unidades: registre con REIVEN solo lo que vuelva en condicion vendible." : ""));
	}

	/** Lo despachado de una venta cancelada solo vuelve al saldo si regreso fisicamente y sirve. */
	public Resultado reingresarCancelada(Actor actor, Args p) {
		String clave = p.clave(3);
		if (inventario.claveUsada(clave)) {
			return Resultado.exito("Ese reingreso ya estaba registrado.");
		}
		DetalleVenta linea = datos.buscar(DetalleVenta.class, p.id(0));
		Venta venta = datos.bloquear(Venta.class, linea.getVenta().getId());
		if (venta.getEstado() != EstadoVenta.CANCELADO) {
			throw Datos.regla("Solo se reingresa lo despachado de una venta cancelada.");
		}
		bloquearLineas(venta);
		LoteFeder lote = bloquearLotes(List.of(p.id(1))).get(p.id(1));
		if (lote == null) {
			throw new ComandoInvalidoException(CodigoError.REGISTRO_NO_ENCONTRADO, "No existe LoteFeder con id " + p.id(1) + ".");
		}
		BigDecimal salido = sumarDeLinea(linea, lote, MotivoMovimientoFeder.VENTA);
		BigDecimal yaVuelto = sumarDeLinea(linea, lote, MotivoMovimientoFeder.AJUSTE);
		BigDecimal cantidad = p.positivo(2);
		if (cantidad.add(yaVuelto).compareTo(salido) > 0) {
			throw Datos.regla("Solo se pueden reingresar " + salido.subtract(yaVuelto) + " unidades de ese despacho.");
		}
		inventario.asentar(lote, datos.empleado(actor), MotivoMovimientoFeder.AJUSTE, cantidad, clave, "Reingreso de venta cancelada", linea, null);
		lote.setCantidadActual(lote.getCantidadActual().add(cantidad));
		return Resultado.exito("Reingresadas " + cantidad + " unidades. Saldo fisico del lote: " + lote.getCantidadActual() + ".");
	}

	private BigDecimal sumarDeLinea(DetalleVenta linea, LoteFeder lote, MotivoMovimientoFeder motivo) {
		return consultas.cantidadMovidaDeLineaYLote(linea.getId(), lote.getId(), motivo);
	}

	// ---------- Consultas ----------

	public Resultado misVentas(Actor actor, Args p) {
		Cliente cliente = datos.cliente(actor);
		List<List<String>> filas = consultas.ventasDeCliente(cliente.getId()).stream()
				.map(v -> List.of(String.valueOf(v.getId()), v.getCodigo(), v.getTipo().name(), v.getEstado().name(), v.getTotal().toString(), String.valueOf(v.getFecha()))).toList();
		return Resultado.tabla("Mis pedidos", new Listado(List.of("id", "codigo", "tipo", "estado", "total", "fecha"), filas));
	}

	public Resultado verVenta(Actor actor, Args p) {
		Venta venta = datos.buscar(Venta.class, p.id(0));
		if (!actor.esPersonal() && (venta.getCliente() == null || !venta.getCliente().getId().equals(datos.cliente(actor).getId()))) {
			throw new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO, "Solo puede consultar sus propios pedidos.");
		}
		List<List<String>> filas = new ArrayList<>(List.of(List.of("codigo", venta.getCodigo()), List.of("estado", venta.getEstado().name()),
				List.of("productos", venta.getSubtotal() + " Bs"), List.of("envio", venta.getCostoEnvio() + " Bs"), List.of("total", venta.getTotal() + " Bs")));
		consultas.lineasDeVenta(venta.getId())
				.forEach(d -> filas.add(List.of("linea " + d.getId() + ": " + d.getProducto().getNombre(), d.getCantidad() + " x " + d.getPrecioUnitario() + " = " + d.getSubtotal())));
		consultas.historialDeVenta(venta.getId())
				.forEach(h -> filas.add(List.of("paso " + h.getSecuencia(), h.getEstadoNuevo() + " (" + h.getFecha() + ")")));
		consultas.pagosDeVenta(venta.getId())
				.forEach(g -> filas.add(List.of("pago " + g.getId(), g.getMetodoPago() + " " + g.getMonto() + " " + g.getEstadoPago() + (g.getFechaReversion() != null ? " (revertido)" : ""))));
		entrega(venta).ifPresent(e -> filas.add(List.of("entrega", e.getModalidad() + " " + e.getEstado())));
		consultas.facturasDeVenta(venta.getId()).stream().findFirst()
				.ifPresent(f -> filas.add(List.of("factura", f.getNumero())));
		return Resultado.tabla("Venta " + venta.getCodigo(), new Listado(List.of("dato", "valor"), new ArrayList<>(new LinkedHashSet<>(filas))));
	}

}

package com.example.correo.service;

import com.example.correo.dto.Resultado;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.correo.service.PermisosService.CLIENTE;
import static com.example.correo.service.PermisosService.ENCARGADO;
import static com.example.correo.service.PermisosService.PERSONAL;
import static com.example.correo.service.PermisosService.PRODUCTOR;

/** Catalogo de comandos de negocio: cada uno corre en una transaccion junto con su bitacora. */
@Service
public class OperacionesService {

	@FunctionalInterface
	public interface Accion {
		Resultado ejecutar(Actor actor, Args args);
	}

	public record Operacion(String codigo, String grupo, String descripcion, Set<String> roles, List<String> parametros, Accion accion) {
		boolean esConsulta() {
			return Set.of("VER", "MIS", "BUS", "STK", "ORI", "OPC", "INF").contains(codigo.substring(0, 3));
		}
	}

	private static final String CU1 = "CU1";
	private static final String CU3 = "CU3";
	private static final String CU4 = "CU4";
	private static final String CU5 = "CU5";
	private static final String CU6 = "CU6";
	private static final String CU7 = "CU7";
	private static final String CU8 = "CU8";
	private static final Set<String> STAFF = Set.of(PERSONAL);
	private static final Set<String> CAMPO = Set.of(PERSONAL, PRODUCTOR, ENCARGADO);
	private static final Set<String> TODOS = Set.of(PERSONAL, PRODUCTOR, ENCARGADO, CLIENTE);
	private static final Set<String> CLIENTES = Set.of(CLIENTE);
	private static final List<String> FILTRO_REPORTE = List.of("desde", "hasta", "agruparPor", "formato");

	private final Map<String, Operacion> operaciones = new LinkedHashMap<>();
	private final Auditoria auditoria;

	public OperacionesService(LoteService lotes, InventarioService inventario, VentaService ventas, CobroService cobros, NotificacionService avisos,
			DevolucionService devoluciones, IndicadorService indicadores, ReporteService reportes, Auditoria auditoria) {
		this.auditoria = auditoria;

		op("RELENC", CU1, "Relevar al encargado de una asociacion", Set.of(), List.of("asociacion", "persona"), lotes::relevarEncargado);

		op("REGPRD", CU3, "Registrar una campana", CAMPO, List.of("productor", "producto", "cantidadEstimada", "fechaSiembra", "fechaCosecha", "observacion"), lotes::registrarProduccion);
		op("ESTPRD", CU3, "Cambiar el estado de una campana", CAMPO, List.of("produccion", "estado"), lotes::cambiarEstadoProduccion);
		op("MISPRD", CU3, "Ver mis campanas", Set.of(PRODUCTOR, ENCARGADO), List.of("todo"), lotes::misProducciones);
		op("REGLOA", CU3, "Registrar un lote fisico", CAMPO, List.of("codigo", "cantidad", "precioOfrecido", "vencimiento", "produccion", "productor", "producto", "inventario", "claveOperacion"), lotes::registrarLote);
		op("TRALOA", CU3, "Trasladar todo el remanente de un lote", CAMPO, List.of("lote", "inventarioDestino", "claveOperacion"), lotes::trasladar);
		op("VALLOA", CU3, "Validar o rechazar un lote", Set.of(ENCARGADO), List.of("lote", "decision"), lotes::validar);
		op("MERLOA", CU3, "Registrar merma de un lote", CAMPO, List.of("lote", "cantidad", "observacion", "claveOperacion"), lotes::merma);
		op("AJULOA", CU3, "Ajustar el saldo de un lote (+ o -)", CAMPO, List.of("lote", "cantidad", "observacion", "claveOperacion"), lotes::ajustar);
		op("MISLOA", CU3, "Ver mis lotes", Set.of(PRODUCTOR, ENCARGADO), List.of("todo"), lotes::misLotes);
		op("ORILOA", CU3, "Ver los movimientos de un lote", CAMPO, List.of("lote"), lotes::custodiaLote);
		op("PUBOFE", CU3, "Publicar la oferta de su asociacion", Set.of(ENCARGADO), List.of("producto", "tiempoEntregaHoras", "precioPrevisto", "cantidadPrevista", "disponibleDesde"), lotes::publicarOferta);
		op("ORILOF", CU3, "Ver el origen de un lote FEDER", STAFF, List.of("loteFeder"), lotes::trazarLoteFeder);
		op("ORIDVE", CU3, "Ver el origen de una linea vendida", Set.of(PERSONAL, CLIENTE), List.of("detalleVenta"), lotes::trazarLinea);

		op("BUSPRO", CU4, "Buscar en el catalogo", TODOS, List.of("nombre", "categoria", "presentacion", "municipio"), inventario::buscarCatalogo);
		op("STKPRO", CU4, "Ver stock por lote de un producto", STAFF, List.of("producto"), inventario::stockProducto);
		op("OPCPRO", CU4, "Ver de donde reponer un producto", STAFF, List.of("producto"), inventario::opcionesDeReposicion);
		op("CRECOM", CU4, "Crear una compra", STAFF, List.of("asociacion", "observacion"), inventario::crearCompra);
		op("AGRDCO", CU4, "Agregar una linea a la compra", STAFF, List.of("compra", "loteAsociacion", "cantidad", "precioPagado"), inventario::agregarLineaCompra);
		op("QUIDCO", CU4, "Quitar una linea sin recibir", STAFF, List.of("detalleCompra"), inventario::quitarLineaCompra);
		op("RECDCO", CU4, "Recibir una linea de compra", STAFF, List.of("detalleCompra", "inventarioFeder"), inventario::recibir);
		op("BAJLOF", CU4, "Dar de baja unidades de un lote", STAFF, List.of("lote", "cantidad", "motivo", "observacion", "claveOperacion"), inventario::darDeBaja);
		op("AJULOF", CU4, "Ajustar el saldo fisico (+ o -)", STAFF, List.of("lote", "cantidad", "observacion", "claveOperacion"), inventario::ajustarLote);
		op("MOVLOF", CU4, "Consolidar un lote en otro almacen", STAFF, List.of("lote", "inventarioFeder"), inventario::moverLote);
		op("ATEALE", CU4, "Atender una alerta de stock", STAFF, List.of("alerta"), inventario::atenderAlerta);

		op("AGRCAR", CU5, "Agregar al carrito", CLIENTES, List.of("producto", "cantidad"), ventas::agregarAlCarrito);
		op("CAMCAR", CU5, "Cambiar cantidad en el carrito", CLIENTES, List.of("producto", "cantidad"), ventas::cambiarCarrito);
		op("QUICAR", CU5, "Quitar del carrito", CLIENTES, List.of("producto"), ventas::quitarDelCarrito);
		op("VERCAR", CU5, "Ver mi carrito", CLIENTES, List.of("todo"), ventas::verCarrito);
		op("PEDCAR", CU5, "Crear el pedido desde el carrito", CLIENTES, List.of("modalidad", "direccion", "referencia", "telefono"), ventas::pedirDesdeCarrito);
		op("CREVEN", CU5, "Crear una venta inmediata", STAFF, List.of("cliente"), ventas::crearVentaInmediata);
		op("AGRDVE", CU5, "Agregar una linea a la venta", STAFF, List.of("venta", "producto", "cantidad"), ventas::agregarLineaVenta);
		op("QUIDVE", CU5, "Quitar una linea de la venta", STAFF, List.of("detalleVenta"), ventas::quitarLineaVenta);
		op("MISVEN", CU5, "Ver mis pedidos", CLIENTES, List.of("todo"), ventas::misVentas);
		op("VERVEN", CU5, "Ver el detalle de una venta", Set.of(PERSONAL, CLIENTE), List.of("venta"), ventas::verVenta);

		op("CONVEN", CU6, "Confirmar: fija envio y reserva stock", STAFF, List.of("venta", "costoEnvio"), ventas::confirmar);
		op("RESVEN", CU6, "Volver a reservar lo vencido", STAFF, List.of("venta"), ventas::reReservar);
		op("ESTVEN", CU6, "Pasar a EN_PREPARACION o LISTO", STAFF, List.of("venta", "estado"), ventas::avanzarEstado);
		op("DESVEN", CU6, "Despachar lo reservado", STAFF, List.of("venta"), ventas::despachar);
		op("SALENT", CU6, "Salir a reparto", STAFF, List.of("venta", "repartidor"), ventas::salirAReparto);
		op("CIEENT", CU6, "Cerrar la entrega efectiva", STAFF, List.of("venta", "costoReal"), ventas::cerrarEntrega);
		op("FALENT", CU6, "Marcar un reparto fallido", STAFF, List.of("venta", "observacion"), ventas::marcarEntregaFallida);
		op("COSENT", CU6, "Registrar el costo real del reparto", STAFF, List.of("venta", "costoReal"), ventas::registrarCostoReparto);
		op("CANVEN", CU6, "Cancelar antes de entregar", Set.of(PERSONAL, CLIENTE), List.of("venta"), ventas::cancelar);
		op("REIVEN", CU6, "Reingresar lo despachado de una venta cancelada", STAFF, List.of("detalleVenta", "loteFeder", "cantidad", "claveOperacion"), ventas::reingresarCancelada);
		op("PAGVEN", CU6, "Registrar un intento de cobro", STAFF, List.of("venta", "metodo", "referencia", "resultado"), cobros::pagar);
		op("GQRVEN", CU6, "Generar el QR de pago de PagoFacil", Set.of(PERSONAL, CLIENTE), List.of("venta"), cobros::generarQr);
		op("ACTPAG", CU6, "Consultar en PagoFacil un intento QR", Set.of(PERSONAL, CLIENTE), List.of("pago"), cobros::actualizarDesdePagoFacil);
		op("CONPAG", CU6, "Resolver un intento pendiente", STAFF, List.of("pago", "resultado"), cobros::confirmarIntento);
		op("REVPAG", CU6, "Revertir el cobro de una venta cancelada", STAFF, List.of("pago", "referencia"), cobros::revertir);
		op("FACVEN", CU6, "Emitir la factura", STAFF, List.of("venta", "nitCi", "razonSocial"), cobros::facturar);
		op("AVINOT", CU6, "Enviar un aviso a un cliente", STAFF, List.of("cliente", "venta", "canal", "asunto", "mensaje"), avisos::avisar);
		op("VERNOT", CU6, "Ver mis avisos", CLIENTES, List.of("todo"), avisos::misAvisos);
		op("LEENOT", CU6, "Marcar un aviso como leido", CLIENTES, List.of("notificacion"), avisos::marcarLeido);
		op("REPDEV", CU6, "Reportar un reclamo", STAFF, List.of("detalleVenta", "cantidad", "motivo", "descripcion"), devoluciones::reportar);
		op("REVDEV", CU6, "Pasar un reclamo a revision", STAFF, List.of("devolucion"), devoluciones::revisar);
		op("APRDEV", CU6, "Aprobar contra un despacho (MOF)", STAFF, List.of("devolucion", "movimientoDespacho", "justificacion"), devoluciones::aprobar);
		op("RCHDEV", CU6, "Rechazar un reclamo", STAFF, List.of("devolucion", "justificacion"), devoluciones::rechazar);
		op("RECDEV", CU6, "Recibir la mercancia devuelta", STAFF, List.of("devolucion", "cantidad", "aptoReventa", "claveOperacion"), devoluciones::recibir);
		op("RPSDEV", CU6, "Resolver con reposicion", STAFF, List.of("devolucion", "loteFeder", "claveOperacion"), devoluciones::reponer);
		op("CTBDEV", CU6, "Contabilizar un reembolso antes de pagarlo", STAFF, List.of("devolucion", "monto"), devoluciones::contabilizarReembolso);
		op("RMBDEV", CU6, "Resolver con reembolso liquidado", STAFF, List.of("devolucion", "monto", "justificacion"), devoluciones::reembolsar);

		op("CALIND", CU7, "Calcular un indicador al corte", STAFF, List.of("metrica", "dimension", "desde", "hasta", "ambito"), indicadores::calcular);

		op("INFVEN", CU8, "Reporte de ventas entregadas", STAFF, FILTRO_REPORTE, reportes::ventas);
		op("INFINV", CU8, "Reporte de inventario FEDER", STAFF, FILTRO_REPORTE, reportes::inventario);
		op("INFLOA", CU8, "Reporte de lotes de proveedores", STAFF, FILTRO_REPORTE, reportes::lotes);
		op("INFPAG", CU8, "Reporte de cobros", STAFF, FILTRO_REPORTE, reportes::pagos);
		op("INFENT", CU8, "Reporte de entregas", STAFF, FILTRO_REPORTE, reportes::entregas);
		op("INFDEV", CU8, "Reporte de devoluciones", STAFF, FILTRO_REPORTE, reportes::devoluciones);
		op("INFIND", CU8, "Reporte de indicadores guardados", STAFF, List.of("desde", "hasta", "tipo", "formato"), reportes::indicadoresGuardados);
	}

	private void op(String codigo, String grupo, String descripcion, Set<String> roles, List<String> parametros, Accion accion) {
		operaciones.put(codigo, new Operacion(codigo, grupo, descripcion, roles, parametros, accion));
	}

	public Operacion buscar(String codigo) {
		return operaciones.get(codigo);
	}

	public Collection<Operacion> todas() {
		return operaciones.values();
	}

	@Transactional
	public Resultado ejecutar(Operacion operacion, Actor actor, List<String> valores) {
		if (valores.size() != operacion.parametros().size()) {
			throw new ComandoInvalidoException(CodigoError.CANTIDAD_PARAMETROS, "Se esperaban " + operacion.parametros().size() + " parametros (" + String.join(", ", operacion.parametros()) + ") y llegaron " + valores.size() + ".");
		}
		Resultado respuesta = operacion.accion().ejecutar(actor, new Args(operacion.parametros(), valores));
		if (!operacion.esConsulta()) {
			Integer entidad = valores.stream().filter(v -> v.matches("\\d{1,9}")).findFirst().map(Integer::valueOf).orElse(0);
			auditoria.registrar(actor.usuario(), operacion.codigo(), CatalogoTablas.TABLAS.get(operacion.codigo().substring(3)).getSimpleName(), entidad,
					List.of(new Auditoria.Cambio("parametros", null, String.join(" | ", valores))));
		}
		return respuesta;
	}

}

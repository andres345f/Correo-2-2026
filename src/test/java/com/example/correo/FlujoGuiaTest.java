package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.correo.presentation.ComandoService;
import com.example.correo.presentation.ComandoService.Adjunto;
import com.example.correo.presentation.ComandoService.Respuesta;
import com.example.correo.service.NotificacionService;
import com.example.correo.infrastructure.PagoFacilGateway;
import com.example.correo.service.VentaService;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Recorre por comandos el ejemplo de la guia de FEDER, con sus cifras. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlujoGuiaTest extends BaseTemporalTest {

	private static final String ELENA = "elena@feder.test";
	private static final String ROSA = "rosa@feder.test";
	private static final String ANA = "ana@feder.test";
	private static final String LUIS = "luis@feder.test";
	private static final String ERROR_REGLA = "Error 554 5.7.0";
	private static final String ERROR_ACCESO = "Error 550 5.7.1";
	private static final Pattern ID = Pattern.compile("id (\\d+)");
	private static final Pattern VALOR = Pattern.compile(": (-?[\\d.]+) (Bs|unidades)\\.");
	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private ComandoService comandos;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private NotificacionService notificaciones;

	@Autowired
	private VentaService ventas;

	private String municipio;
	private String asociacion;
	private String empleado;
	private String categoria;
	private String inventarioElena;
	private String acopio;
	private String almacen;
	private String productor;

	private Respuesta enviar(String remitente, String comando) {
		return comandos.procesar(comando, remitente);
	}

	private String id(String remitente, String comando) {
		Respuesta respuesta = enviar(remitente, comando);
		Matcher id = ID.matcher(respuesta.cuerpo());
		assertTrue(!respuesta.esError() && id.find(), comando + " -> " + respuesta.asunto() + ": " + respuesta.cuerpo());
		return id.group(1);
	}

	private void ok(String remitente, String comando) {
		Respuesta respuesta = enviar(remitente, comando);
		assertFalse(respuesta.esError(), comando + " -> " + respuesta.cuerpo());
	}

	private void falla(String esperado, String remitente, String comando) {
		assertEquals(esperado, enviar(remitente, comando).asunto(), comando);
	}

	private static String c(Object... datos) {
		StringBuilder texto = new StringBuilder("[");
		for (int i = 0; i < datos.length; i++) {
			texto.append(i == 0 ? "" : ",").append('"').append(datos[i]).append('"');
		}
		return texto.append("]").toString();
	}

	private static String clave() {
		return "k" + SEQ.incrementAndGet();
	}

	private void assertIgual(String esperado, BigDecimal real) {
		assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + ", real " + real);
	}

	private BigDecimal num(String sql, Object... args) {
		return jdbc.queryForObject(sql, BigDecimal.class, args);
	}

	private String cuenta(String correo, String rol) {
		String usuario = id(ADMIN, "INSUSU" + c(correo, "clave"));
		Integer idRol = jdbc.queryForObject("select rolid from rol where nombre = ?", Integer.class, rol);
		id(ADMIN, "INSURO" + c(usuario, idRol));
		return usuario;
	}

	private String persona(String usuario, String nombre) {
		return id(ADMIN, "INSPER" + c("Apellido", "ci-" + nombre, nombre, "700", usuario));
	}

	@BeforeEach
	void correoSimulado() {
		when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
	}

	@BeforeAll
	void actores() {
		municipio = id(ADMIN, "INSMUN" + c("Santa Cruz", "San Jose"));
		asociacion = id(ADMIN, "INSASO" + c("a@b.c", "Calle 1", "Las Palmas", "7001", municipio));
		categoria = id(ADMIN, "INSCAT" + c("Aceites", "Aceites"));

		String usuarioElena = cuenta(ELENA, "PRODUCTOR");
		productor = id(ADMIN, "INSPTR" + c("Palmar", asociacion, persona(usuarioElena, "Elena"), usuarioElena));
		ok(ADMIN, "RELENC" + c(asociacion, persona(cuenta(ROSA, "ENCARGADO"), "Rosa")));
		String usuarioAna = cuenta(ANA, "CLIENTE");
		id(ADMIN, "INSCLI" + c("Av 1", "123", "Ana SRL", usuarioAna, persona(usuarioAna, "Ana")));
		String usuarioLuis = cuenta(LUIS, "PERSONAL");
		empleado = id(ADMIN, "INSEMP" + c("Almacen", "2026-01-01", usuarioLuis, persona(usuarioLuis, "Luis")));

		String usuarioOtroQr = cuenta("otro.qr@feder.test", "CLIENTE");
		id(ADMIN, "INSCLI" + c("Av 3", "8", "OtroQr", usuarioOtroQr, persona(usuarioOtroQr, "OtroQr")));

		String parcela = id(ADMIN, "INSUBI" + c("", "Km 5", "", "", "Parcela", "", "PARCELA", municipio));
		inventarioElena = id(ADMIN, "INSINV" + c("I-P", "false", "Parcela Elena", "PRODUCTOR", "", productor, parcela));
		String centro = id(ADMIN, "INSUBI" + c("", "Km 2", "", "", "Acopio", "", "CENTRO_ACOPIO", municipio));
		acopio = id(ADMIN, "INSINV" + c("I-A", "false", "Acopio", "ASOCIACION", asociacion, "", centro));
		String deposito = id(ADMIN, "INSUBI" + c("", "Av 1", "", "", "Almacen", "", "ALMACEN_FEDER", municipio));
		almacen = id(ADMIN, "INSINV" + c("I-F", "true", "Central", "FEDER", "", "", deposito));
	}

	private String producto(String minimo) {
		return id(ADMIN, "INSPRO" + c("Aceite", "", "true", "Aceite " + SEQ.incrementAndGet(), "25", "500 ml", minimo, "botella", categoria));
	}

	private String loteProveedor(String producto, String cantidad) {
		String lote = id(ELENA, "REGLOA" + c("L-" + SEQ.incrementAndGet(), cantidad, "8", "2030-01-01", "", productor, producto, inventarioElena, clave()));
		ok(ELENA, "TRALOA" + c(lote, acopio, clave()));
		ok(ROSA, "VALLOA" + c(lote, "VALIDADO"));
		return lote;
	}

	private String loteFeder(String producto, String cantidad) {
		String compra = id(LUIS, "CRECOM" + c(asociacion, ""));
		String detalle = id(LUIS, "AGRDCO" + c(compra, loteProveedor(producto, cantidad), cantidad, "10"));
		ok(LUIS, "RECDCO" + c(detalle, almacen));
		return jdbc.queryForObject("select lote_federid::text from lote_feder where detalle_compraid = ?::int", String.class, detalle);
	}

	private String pedido(String producto, String cantidad, String modalidad) {
		ok(ANA, "AGRCAR" + c(producto, cantidad));
		return id(ANA, "PEDCAR" + c(modalidad, "Av 1", "", "700"));
	}

	private String linea(String venta) {
		return jdbc.queryForObject("select min(detalle_ventaid)::text from detalle_venta where ventaid = ?::int", String.class, venta);
	}

	private String despacho(String venta) {
		return jdbc.queryForObject("select min(m.movimiento_federid)::text from movimiento_feder m join detalle_venta d using (detalle_ventaid) where d.ventaid = ?::int and m.motivo = 'VENTA'", String.class, venta);
	}

	private String ventaEntregada(String producto) {
		String venta = pedido(producto, "2", "DELIVERY_PROPIO");
		ok(LUIS, "CONVEN" + c(venta, "6"));
		ok(LUIS, "PAGVEN" + c(venta, "QR", "ref-" + venta, "PAGADO"));
		ok(LUIS, "ESTVEN" + c(venta, "EN_PREPARACION"));
		ok(LUIS, "ESTVEN" + c(venta, "LISTO"));
		ok(LUIS, "DESVEN" + c(venta));
		ok(LUIS, "SALENT" + c(venta, empleado));
		ok(LUIS, "CIEENT" + c(venta, "4"));
		return venta;
	}

	private BigDecimal fisico(String lote) {
		return num("select cantidad_actual from lote_feder where lote_federid = ?::int", lote);
	}

	private BigDecimal reservado(String lote) {
		return num("select cantidad_reservada from lote_feder where lote_federid = ?::int", lote);
	}

	private BigDecimal indicador(String metrica, String dimension, String ambito) {
		String hoy = LocalDate.now().toString();
		Respuesta respuesta = enviar(LUIS, "CALIND" + c(metrica, dimension, hoy, hoy, ambito));
		Matcher valor = VALOR.matcher(respuesta.cuerpo());
		assertTrue(valor.find(), respuesta.cuerpo());
		return new BigDecimal(valor.group(1));
	}

	@Test
	void recorridoCompletoConLasCifrasDeLaGuia() {
		String producto = producto("38");
		String campana = id(ELENA, "REGPRD" + c(productor, producto, "100", "2026-01-01", "2026-06-01", ""));
		String claveRegistro = clave();
		String registro = "REGLOA" + c("L-G", "100", "8", "2030-01-01", campana, "", "", inventarioElena, claveRegistro);
		String loteOrigen = id(ELENA, registro);
		assertEquals(loteOrigen, id(ELENA, registro), "reintentar con la misma clave no crea otro lote");
		ok(ELENA, "TRALOA" + c(loteOrigen, acopio, clave()));
		assertEquals(acopio, jdbc.queryForObject("select inventarioid::text from lote_asociacion where lote_asociacionid = ?::int", String.class, loteOrigen));
		String compra = id(LUIS, "CRECOM" + c(asociacion, ""));
		String detalle = id(LUIS, "AGRDCO" + c(compra, loteOrigen, "40", "10"));
		assertIgual("400", num("select total from compra where compraid = ?::int", compra));
		falla(ERROR_REGLA, LUIS, "RECDCO" + c(detalle, almacen));

		falla(ERROR_ACCESO, LUIS, "VALLOA" + c(loteOrigen, "VALIDADO"));
		ok(ROSA, "VALLOA" + c(loteOrigen, "VALIDADO"));
		ok(ROSA, "PUBOFE" + c(producto, "24", "", "30", "2026-12-01"));
		ok(LUIS, "RECDCO" + c(detalle, almacen));
		ok(LUIS, "RECDCO" + c(detalle, almacen));
		assertIgual("60", num("select cantidad_disponible from lote_asociacion where lote_asociacionid = ?::int", loteOrigen));
		String lote = jdbc.queryForObject("select lote_federid::text from lote_feder where detalle_compraid = ?::int", String.class, detalle);
		assertIgual("40", fisico(lote));

		ok(ANA, "AGRCAR" + c(producto, "2"));
		assertTrue(enviar(ANA, "VERCAR" + c("*")).asunto().contains("50.00 Bs"));
		assertIgual("0", reservado(lote));
		String venta = id(ANA, "PEDCAR" + c("DELIVERY_PROPIO", "Av 1", "", "700"));
		ok(LUIS, "CONVEN" + c(venta, "6"));
		assertIgual("56", num("select total from venta where ventaid = ?::int", venta));
		assertIgual("40", fisico(lote));
		assertIgual("2", reservado(lote));
		assertIgual("38", num("select stock_actual from alerta_stock where productoid = ?::int", producto));

		ok(LUIS, "PAGVEN" + c(venta, "QR", "qr-mal-" + venta, "RECHAZADO"));
		ok(LUIS, "PAGVEN" + c(venta, "QR", "qr-bien-" + venta, "PAGADO"));
		falla(ERROR_REGLA, LUIS, "PAGVEN" + c(venta, "EFECTIVO", "", "PAGADO"));
		assertIgual("0", indicador("INGRESO_RECONOCIDO", "PRODUCTO", producto));

		ok(LUIS, "ESTVEN" + c(venta, "EN_PREPARACION"));
		ok(LUIS, "ESTVEN" + c(venta, "LISTO"));
		ok(LUIS, "DESVEN" + c(venta));
		ok(LUIS, "DESVEN" + c(venta));
		assertIgual("38", fisico(lote));
		assertIgual("0", reservado(lote));
		assertEquals(1, jdbc.queryForObject("select count(*) from alerta_stock where productoid = ?::int", Integer.class, producto), "mismo episodio");

		falla(ERROR_REGLA, LUIS, "CIEENT" + c(venta, "4"));
		ok(LUIS, "SALENT" + c(venta, empleado));
		ok(LUIS, "CIEENT" + c(venta, "4"));
		ok(LUIS, "CIEENT" + c(venta, "4"));
		assertIgual("38", fisico(lote));
		assertEquals("ENTREGADO", jdbc.queryForObject("select estado::text from venta where ventaid = ?::int", String.class, venta));
		assertEquals(6, jdbc.queryForObject("select max(secuencia) from historial_estado_venta where ventaid = ?::int", Integer.class, venta));

		ok(LUIS, "FACVEN" + c(venta, "", ""));
		assertEquals("Ana SRL", jdbc.queryForObject("select razon_social from factura where ventaid = ?::int", String.class, venta));

		notificaciones.enviarPendientes();
		assertEquals(0, jdbc.queryForObject("select count(*) from notificacion where ventaid = ?::int and estado_envio <> 'ENVIADA'", Integer.class, venta));
		assertTrue(jdbc.queryForObject("select count(*) from notificacion where ventaid = ?::int", Integer.class, venta) >= 4);

		assertIgual("50", indicador("INGRESO_RECONOCIDO", "PRODUCTO", producto));
		assertIgual("32", indicador("MARGEN", "PRODUCTO", producto));
		assertIgual("2", indicador("UNIDADES_VENDIDAS", "PRODUCTO", producto));
		assertIgual("38", indicador("INVENTARIO", "PRODUCTO", producto));

		BigDecimal cajaAntes = indicador("CAJA_NETA", "GLOBAL", "");
		String reclamo = id(LUIS, "REPDEV" + c(linea(venta), "1", "PRODUCTO_DANADO", ""));
		ok(LUIS, "REVDEV" + c(reclamo));
		ok(LUIS, "APRDEV" + c(reclamo, despacho(venta), "Botella rota"));
		String excesivo = id(LUIS, "REPDEV" + c(linea(venta), "2", "PRODUCTO_DANADO", ""));
		ok(LUIS, "REVDEV" + c(excesivo));
		falla(ERROR_REGLA, LUIS, "APRDEV" + c(excesivo, despacho(venta), "Demasiado"));
		ok(LUIS, "RECDEV" + c(reclamo, "1", "false", clave()));
		assertIgual("38", fisico(lote));
		falla(ERROR_REGLA, LUIS, "RMBDEV" + c(reclamo, "57", ""));
		ok(LUIS, "RMBDEV" + c(reclamo, "25", ""));
		ok(LUIS, "RMBDEV" + c(reclamo, "25", ""));
		assertEquals("RESUELTA", jdbc.queryForObject("select estado::text from devolucion where devolucionid = ?::int", String.class, reclamo));
		assertIgual("38", fisico(lote));
		assertIgual("-25", indicador("CAJA_NETA", "GLOBAL", "").subtract(cajaAntes));
		assertIgual("25", indicador("INGRESO_RECONOCIDO", "PRODUCTO", producto));

		assertTrue(enviar(LUIS, "ORIDVE" + c(linea(venta))).cuerpo().contains("Las Palmas"));
		assertEquals(1, jdbc.queryForObject("select count(*) from bitacora where accion = 'CONTABILIZA_REEMBOLSO' and entidad_id = ?::int", Integer.class, reclamo), "una sola correccion del ingreso");
	}

	@Test
	void reposicionDescuentaDelLoteYReingresoAptoSuma() {
		String producto = producto("0");
		String lote = loteFeder(producto, "40");
		String venta = ventaEntregada(producto);
		assertIgual("38", fisico(lote));
		String reclamo = id(LUIS, "REPDEV" + c(linea(venta), "1", "PRODUCTO_DEFECTUOSO", ""));
		ok(LUIS, "REVDEV" + c(reclamo));
		ok(LUIS, "APRDEV" + c(reclamo, despacho(venta), "Defecto"));
		String claveRecepcion = clave();
		ok(LUIS, "RECDEV" + c(reclamo, "1", "true", claveRecepcion));
		ok(LUIS, "RECDEV" + c(reclamo, "1", "true", claveRecepcion));
		assertIgual("39", fisico(lote));
		ok(LUIS, "RPSDEV" + c(reclamo, lote, clave()));
		assertIgual("38", fisico(lote));
		assertIgual("2", indicador("UNIDADES_VENDIDAS", "PRODUCTO", producto));
		assertIgual("32", indicador("MARGEN", "PRODUCTO", producto));
	}

	@Test
	void dosVentasNoSeLlevanLaUltimaBotella() throws Exception {
		String producto = producto("0");
		String lote = loteFeder(producto, "1");
		String ventaUno = id(LUIS, "CREVEN" + c(""));
		ok(LUIS, "AGRDVE" + c(ventaUno, producto, "1"));
		String ventaDos = id(LUIS, "CREVEN" + c(""));
		ok(LUIS, "AGRDVE" + c(ventaDos, producto, "1"));

		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		List<Future<Respuesta>> resultados = new ArrayList<>();
		for (String venta : List.of(ventaUno, ventaDos)) {
			Callable<Respuesta> tarea = () -> {
				largada.await();
				return enviar(LUIS, "CONVEN" + c(venta, ""));
			};
			resultados.add(hilos.submit(tarea));
		}
		largada.countDown();
		int exitos = 0;
		for (Future<Respuesta> resultado : resultados) {
			exitos += resultado.get().esError() ? 0 : 1;
		}
		hilos.shutdown();
		assertEquals(1, exitos);
		assertIgual("1", reservado(lote));
		assertEquals(1, jdbc.queryForObject("select count(*) from reserva_stock r join lote_feder l using (lote_federid) where l.lote_federid = ?::int", Integer.class, Integer.valueOf(lote)));
	}

	@Test
	void reservaVencidaNoSeConsumeYVuelveALibre() {
		String producto = producto("0");
		String lote = loteFeder(producto, "5");
		String venta = id(LUIS, "CREVEN" + c(""));
		ok(LUIS, "AGRDVE" + c(venta, producto, "2"));
		ok(LUIS, "CONVEN" + c(venta, ""));
		jdbc.update("update reserva_stock set fecha_reserva = now() - interval '3 days', fecha_expiracion = now() - interval '1 hour' where lote_federid = ?::int", lote);
		falla(ERROR_REGLA, LUIS, "DESVEN" + c(venta));
		assertIgual("5", fisico(lote));
		ventas.liberarReservasVencidas();
		assertIgual("0", reservado(lote));
		ok(LUIS, "RESVEN" + c(venta));
		assertIgual("2", reservado(lote));
		ok(LUIS, "DESVEN" + c(venta));
		assertIgual("3", fisico(lote));
		ok(LUIS, "CIEENT" + c(venta, ""));
		assertEquals("ENTREGADO", jdbc.queryForObject("select estado::text from venta where ventaid = ?::int", String.class, venta));
	}

	@Test
	void lineaNoSeDespachaDosVecesEntreVariosLotes() {
		String producto = producto("0");
		String primero = loteFeder(producto, "2");
		String segundo = loteFeder(producto, "2");
		String venta = id(LUIS, "CREVEN" + c(""));
		ok(LUIS, "AGRDVE" + c(venta, producto, "3"));
		ok(LUIS, "CONVEN" + c(venta, ""));
		assertIgual("3", reservado(primero).add(reservado(segundo)));
		ok(LUIS, "DESVEN" + c(venta));
		ok(LUIS, "DESVEN" + c(venta));
		assertIgual("1", fisico(primero).add(fisico(segundo)));
	}

	@Test
	void cancelarDespuesDeDespacharNoReponeSolo() {
		String producto = producto("0");
		String lote = loteFeder(producto, "10");
		String venta = pedido(producto, "2", "DELIVERY_PROPIO");
		ok(LUIS, "CONVEN" + c(venta, "6"));
		ok(LUIS, "PAGVEN" + c(venta, "TARJETA", "t-" + venta, "PAGADO"));
		String intento = jdbc.queryForObject("select pagoid::text from pago where ventaid = ?::int", String.class, venta);
		ok(LUIS, "ESTVEN" + c(venta, "EN_PREPARACION"));
		ok(LUIS, "ESTVEN" + c(venta, "LISTO"));
		ok(LUIS, "DESVEN" + c(venta));
		ok(LUIS, "SALENT" + c(venta, empleado));
		ok(LUIS, "FALENT" + c(venta, "No estaba"));
		falla(ERROR_REGLA, LUIS, "CIEENT" + c(venta, "4"));
		falla(ERROR_REGLA, LUIS, "REVPAG" + c(intento, "r1"));
		ok(LUIS, "CANVEN" + c(venta));
		assertIgual("8", fisico(lote));
		ok(LUIS, "REVPAG" + c(intento, "r1"));
		ok(LUIS, "REVPAG" + c(intento, "r1"));
		assertEquals("PAGADO", jdbc.queryForObject("select estado_pago::text from pago where pagoid = ?::int", String.class, intento));
		falla(ERROR_REGLA, LUIS, "REIVEN" + c(linea(venta), lote, "3", clave()));
		ok(LUIS, "REIVEN" + c(linea(venta), lote, "1", clave()));
		assertIgual("9", fisico(lote));
		assertIgual("0", indicador("INGRESO_RECONOCIDO", "PRODUCTO", producto));
	}

	@Test
	void margenNoSePublicaSinCostoDeReparto() {
		String producto = producto("0");
		loteFeder(producto, "5");
		String venta = pedido(producto, "1", "RECOJO");
		ok(LUIS, "CONVEN" + c(venta, ""));
		falla(ERROR_REGLA, LUIS, "CONVEN" + c(venta, "1"));
		ok(LUIS, "ESTVEN" + c(venta, "EN_PREPARACION"));
		ok(LUIS, "ESTVEN" + c(venta, "LISTO"));
		ok(LUIS, "DESVEN" + c(venta));
		ok(LUIS, "CIEENT" + c(venta, ""));
		String hoy = LocalDate.now().toString();
		falla(ERROR_REGLA, LUIS, "CALIND" + c("MARGEN", "PRODUCTO", hoy, hoy, producto));
		ok(LUIS, "COSENT" + c(venta, "0"));
		assertIgual("15", indicador("MARGEN", "PRODUCTO", producto));
	}

	@Test
	void cadaUnoOperaSoloSobreLoSuyo() {
		String usuarioOtro = cuenta("otro@feder.test", "CLIENTE");
		id(ADMIN, "INSCLI" + c("Av 2", "9", "Otro", usuarioOtro, persona(usuarioOtro, "Otro")));
		String usuarioIntruso = cuenta("intruso@feder.test", "PRODUCTOR");
		id(ADMIN, "INSPTR" + c("X", asociacion, persona(usuarioIntruso, "Intruso"), usuarioIntruso));

		String producto = producto("0");
		loteFeder(producto, "5");
		String venta = pedido(producto, "1", "RECOJO");
		falla(ERROR_ACCESO, "otro@feder.test", "VERVEN" + c(venta));
		falla(ERROR_ACCESO, "otro@feder.test", "CANVEN" + c(venta));
		ok(ANA, "VERVEN" + c(venta));
		falla(ERROR_ACCESO, ANA, "CONVEN" + c(venta, ""));
		falla(ERROR_ACCESO, "intruso@feder.test", "REGPRD" + c(productor, producto, "1", "2026-01-01", "2026-02-01", ""));
		falla(ERROR_ACCESO, ANA, "INSVEN" + c("x"));
		falla(ERROR_ACCESO, ADMIN, "MODLOF" + c("1"));
		falla(ERROR_ACCESO, ADMIN, "DELBIT" + c("1"));
		ok(ANA, "CANVEN" + c(venta));
		ok(ANA, "CANVEN" + c(venta));
	}

	@Test
	void reglasDePerfilYCatalogo() {
		String usuario = id(ADMIN, "INSUSU" + c("sinpersona@feder.test", "clave"));
		String personaAjena = persona(id(ADMIN, "INSUSU" + c("ajena@feder.test", "clave")), "Ajena");
		falla(ERROR_REGLA, ADMIN, "INSCLI" + c("Av", "1", "x", usuario, personaAjena));

		String centro = jdbc.queryForObject("select ubicacionid::text from inventario where inventarioid = ?::int", String.class, acopio);
		falla(ERROR_REGLA, ADMIN, "INSINV" + c("I-Z", "false", "Z", "FEDER", "", "", centro));

		String producto = producto("0");
		loteFeder(producto, "7");
		String nombre = jdbc.queryForObject("select nombre from producto where productoid = ?::int", String.class, producto);
		assertTrue(enviar(ANA, "BUSPRO" + c(nombre, "aceites", "", "san jose")).cuerpo().contains("disponible=7.00"));
		assertTrue(enviar(ANA, "BUSPRO" + c(nombre, "", "", "Otro")).cuerpo().contains("Sin registros"));
	}

	@Test
	void reportesEnPdfYExcelConLaMismaConsulta() throws Exception {
		String producto = producto("0");
		loteFeder(producto, "10");
		ventaEntregada(producto);
		String nombre = jdbc.queryForObject("select nombre from producto where productoid = ?::int", String.class, producto);

		Respuesta todos = enviar(LUIS, "INFVEN" + c("", "", "PRODUCTO", ""));
		assertFalse(todos.esError(), todos.cuerpo());
		assertTrue(todos.cuerpo().contains(nombre) && todos.html().contains("Corte:"));
		assertEquals(2, todos.adjuntos().size());
		Adjunto pdf = todos.adjuntos().get(0);
		assertEquals("%PDF", new String(pdf.datos(), 0, 4, StandardCharsets.ISO_8859_1));
		boolean hallado = false;
		try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(todos.adjuntos().get(1).datos()))) {
			for (Row fila : libro.getSheetAt(0)) {
				if (fila.getCell(0) != null && nombre.equals(fila.getCell(0).getStringCellValue())) {
					hallado = true;
					assertEquals(2.0, fila.getCell(1).getNumericCellValue());
					assertEquals(50.0, fila.getCell(2).getNumericCellValue());
					assertEquals(32.0, fila.getCell(5).getNumericCellValue());
				}
			}
		}
		assertTrue(hallado);

		assertEquals(1, enviar(LUIS, "INFVEN" + c("", "", "MUNICIPIO", "PDF")).adjuntos().size());
		assertEquals(0, enviar(LUIS, "INFVEN" + c("", "", "DIA", "HTML")).adjuntos().size());
		falla(ERROR_REGLA, LUIS, "INFVEN" + c("", "", "COLOR", "HTML"));
		for (String comando : List.of("INFINV", "INFLOA", "INFPAG", "INFENT", "INFDEV", "INFIND")) {
			ok(LUIS, comando + c("", "", "", "TODOS"));
		}
		falla(ERROR_ACCESO, ANA, "INFVEN" + c("", "", "", ""));
	}

	@Test
	void cobroConQrSeConciliaConPagoFacil() throws Exception {
		String producto = producto("0");
		loteFeder(producto, "5");
		String venta = pedido(producto, "2", "DELIVERY_PROPIO");
		ok(LUIS, "CONVEN" + c(venta, "6"));
		when(pagoFacil.generarQr(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new PagoFacilGateway.QrResult("aGk=", "tx-" + venta));

		falla(ERROR_ACCESO, "otro.qr@feder.test", "GQRVEN" + c(venta));
		Respuesta qr = enviar(ANA, "GQRVEN" + c(venta));
		assertFalse(qr.esError(), qr.cuerpo());
		assertEquals("qrpago", qr.adjuntos().get(0).contentId());
		assertTrue(qr.html().contains("cid:qrpago"));
		String intento = jdbc.queryForObject("select pagoid::text from pago where ventaid = ?::int", String.class, venta);
		assertIgual("56", num("select monto from pago where pagoid = ?::int", intento));
		assertEquals("PENDIENTE", jdbc.queryForObject("select estado_pago::text from pago where pagoid = ?::int", String.class, intento));

		when(pagoFacil.consultarTransaccion("tx-" + venta)).thenReturn(new PagoFacilGateway.QueryResult("PENDIENTE", "", null, null));
		assertTrue(enviar(ANA, "ACTPAG" + c(intento)).cuerpo().contains("PENDIENTE"));

		when(pagoFacil.consultarTransaccion("tx-" + venta)).thenReturn(new PagoFacilGateway.QueryResult("PAGADO", "", "Ana", "BNB"));
		ok(ANA, "ACTPAG" + c(intento));
		ok(ANA, "ACTPAG" + c(intento));
		assertEquals("PAGADO", jdbc.queryForObject("select estado_pago::text from pago where pagoid = ?::int", String.class, intento));
		assertEquals(1, jdbc.queryForObject("select count(*) from pago where ventaid = ?::int and fecha_pago is not null", Integer.class, venta));
		falla(ERROR_REGLA, ANA, "GQRVEN" + c(venta));
		verify(pagoFacil, times(2)).consultarTransaccion("tx-" + venta);
	}

	@Test
	void falloDePagoFacilNoDejaIntentoGuardado() throws Exception {
		String producto = producto("0");
		loteFeder(producto, "5");
		String venta = pedido(producto, "1", "RECOJO");
		ok(LUIS, "CONVEN" + c(venta, ""));
		when(pagoFacil.generarQr(any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new java.io.IOException("sin red"));
		assertTrue(enviar(LUIS, "GQRVEN" + c(venta)).cuerpo().contains("sin red"));
		assertEquals(0, jdbc.queryForObject("select count(*) from pago where ventaid = ?::int", Integer.class, venta));
	}

	@Test
	void guiaHelpListaLosComandosYCabeEnUnCorreo() {
		String html = enviar("nadie@x.y", "HELP").html();
		for (String comando : List.of("REGLOA", "RECDCO", "PEDCAR", "CONVEN", "RMBDEV", "CALIND", "INFVEN")) {
			assertTrue(html.contains(comando), comando);
		}
		assertTrue(html.getBytes(StandardCharsets.UTF_8).length < 102_000, "bytes: " + html.getBytes(StandardCharsets.UTF_8).length);
	}

}

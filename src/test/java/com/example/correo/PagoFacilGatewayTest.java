package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.correo.service.ComandoInvalidoException;
import com.example.correo.infrastructure.PagoFacilGateway;
import com.sun.net.httpserver.HttpServer;

// Habla HTTP con un servidor local que imita a PagoFacil: no usa el servicio real.
class PagoFacilGatewayTest {

	private final Map<String, String> respuestas = new ConcurrentHashMap<>();
	private final Map<String, String> recibido = new ConcurrentHashMap<>();
	private HttpServer servidor;
	private PagoFacilGateway gateway;

	@BeforeEach
	void iniciar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		for (String ruta : List.of("/login", "/list-enabled-services", "/generate-qr", "/query-transaction")) {
			servidor.createContext(ruta, intercambio -> {
				recibido.put(ruta, new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				recibido.put(ruta + "#auth", String.valueOf(intercambio.getRequestHeaders().getFirst("Authorization")));
				byte[] cuerpo = respuestas.get(ruta).getBytes(StandardCharsets.UTF_8);
				intercambio.sendResponseHeaders(200, cuerpo.length);
				intercambio.getResponseBody().write(cuerpo);
				intercambio.close();
			});
		}
		servidor.start();
		respuestas.put("/login", "{\"error\":0,\"values\":{\"accessToken\":\"tok-1\"}}");
		respuestas.put("/list-enabled-services", "{\"values\":[{\"paymentMethodId\":7,\"paymentMethodName\":\"Tarjeta\"},{\"paymentMethodId\":9,\"paymentMethodName\":\"QR Simple\"}]}");
		gateway = gatewayCon("servicio", "secreto");
	}

	private PagoFacilGateway gatewayCon(String servicio, String secreto) {
		return new PagoFacilGateway("http://localhost:" + servidor.getAddress().getPort(), servicio, secreto, "");
	}

	@AfterEach
	void terminar() {
		servidor.stop(0);
	}

	@Test
	void generaElQrConElMetodoHabilitado() throws Exception {
		respuestas.put("/generate-qr", "{\"error\":0,\"values\":{\"transactionId\":\"987\",\"qrBase64\":\"aGk\\/b2s=\"}}");

		PagoFacilGateway.QrResult qr = gateway.generarQr("V1-1", new BigDecimal("56.00"), "Pedido \"V-1\"", 3, "Ana", "123", "700", "ana@x.y");

		assertEquals("987", qr.transactionId());
		assertEquals("aGk/b2s=", qr.qrImage());
		String cuerpo = recibido.get("/generate-qr");
		assertTrue(cuerpo.contains("\"paymentMethod\":9"), cuerpo);
		assertTrue(cuerpo.contains("\"amount\":56.00"), cuerpo);
		assertTrue(cuerpo.contains("Pedido \\\"V-1\\\""), cuerpo);
		assertEquals("Bearer tok-1", recibido.get("/generate-qr#auth"));
	}

	@Test
	void traduceLosEstadosDeLaTransaccion() throws Exception {
		respuestas.put("/query-transaction", "{\"error\":0,\"values\":{\"paymentStatus\":2,\"paymentStatusDescription\":\"Completado\",\"payerName\":\"Ana\",\"payerBank\":\"BNB\"}}");
		PagoFacilGateway.QueryResult pagado = gateway.consultarTransaccion("987");
		assertEquals("PAGADO", pagado.status());
		assertEquals("BNB", pagado.payerBank());
		assertTrue(recibido.get("/query-transaction").contains("\"pagofacilTransactionId\":\"987\""));

		respuestas.put("/query-transaction", "{\"error\":0,\"values\":{\"paymentStatus\":1,\"paymentStatusDescription\":\"En espera\"}}");
		assertEquals("PENDIENTE", gateway.consultarTransaccion("987").status());

		respuestas.put("/query-transaction", "{\"error\":0,\"values\":{\"paymentStatus\":3,\"paymentStatusDescription\":\"Rechazado\"}}");
		assertEquals("CANCELADO", gateway.consultarTransaccion("987").status());

		respuestas.put("/query-transaction", "{\"error\":1,\"message\":\"No existe\"}");
		assertEquals("No existe", assertThrows(IOException.class, () -> gateway.consultarTransaccion("987")).getMessage());
	}

	@Test
	void sinCredencialesLoDiceSinLlamarAlServicio() {
		ComandoInvalidoException error = assertThrows(ComandoInvalidoException.class, () -> gatewayCon("", "").consultarTransaccion("1"));
		assertTrue(error.getMessage().contains("PAGOFACIL_TOKEN_SERVICE"));
		assertTrue(recibido.isEmpty());
	}

}

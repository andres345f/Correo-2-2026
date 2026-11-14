package com.example.correo.infrastructure;

import com.example.correo.service.Datos;
import com.example.correo.service.puerto.PasarelaPago;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Cliente HTTP de PagoFacil: genera el QR y consulta el estado de la transaccion. */
@Service
public class PagoFacilGateway implements PasarelaPago {

	private static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(30);
	private static final int METODO_QR_POR_DEFECTO = 4;

	private final String baseUrl;
	private final String tokenService;
	private final String tokenSecret;
	private final String callbackUrl;
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIEMPO_MAXIMO).build();

	public PagoFacilGateway(@Value("${app.pagofacil.base-url}") String baseUrl,
			@Value("${app.pagofacil.token-service:}") String tokenService,
			@Value("${app.pagofacil.token-secret:}") String tokenSecret,
			@Value("${app.pagofacil.callback-url:}") String callbackUrl) {
		this.baseUrl = baseUrl.trim();
		this.tokenService = tokenService.trim();
		this.tokenSecret = tokenSecret.trim();
		this.callbackUrl = callbackUrl.trim();
	}

	private HttpRequest.Builder peticion(String ruta) {
		return HttpRequest.newBuilder().uri(URI.create(baseUrl + ruta)).timeout(TIEMPO_MAXIMO).header("Accept", "application/json");
	}

	/** Reintenta los fallos de red y los 5xx; un 4xx se devuelve para que lo maneje quien llama. */
	private HttpResponse<String> enviar(HttpRequest request, int reintentos, String operacion) throws IOException, InterruptedException {
		for (int intento = 0;; intento++) {
			try {
				HttpResponse<String> respuesta = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
				if (respuesta.statusCode() < 500 || intento >= reintentos) {
					return respuesta;
				}
				org.slf4j.LoggerFactory.getLogger(PagoFacilGateway.class).info("PagoFacilGateway: " + operacion + " intento " + (intento + 1) + " falló con HTTP " + respuesta.statusCode() + ", reintentando...");
			} catch (IOException e) {
				if (intento >= reintentos) {
					throw e;
				}
				org.slf4j.LoggerFactory.getLogger(PagoFacilGateway.class).info("PagoFacilGateway: " + operacion + " intento " + (intento + 1) + " falló: " + e.getMessage() + ", reintentando...");
			}
			Thread.sleep(1000L * (intento + 1));
		}
	}

	private String obtenerToken() throws IOException, InterruptedException {
		if (tokenService.isEmpty() || tokenSecret.isEmpty()) {
			throw Datos.regla("PagoFacil no esta configurado: agregue PAGOFACIL_TOKEN_SERVICE y PAGOFACIL_TOKEN_SECRET en .env.");
		}
		HttpRequest request = peticion("/login").header("tcTokenService", tokenService).header("tcTokenSecret", tokenSecret)
				.POST(HttpRequest.BodyPublishers.noBody()).build();
		String token = texto(enviar(request, 2, "login").body(), "accessToken");
		if (token == null || token.isEmpty()) {
			throw new IOException("PagoFacil no devolvio el token de acceso.");
		}
		return token;
	}

	private int metodoQr(String token) {
		try {
			HttpRequest request = peticion("/list-enabled-services").header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.noBody()).build();
			HttpResponse<String> respuesta = enviar(request, 1, "list-enabled-services");
			if (respuesta.statusCode() == 200) {
				for (String bloque : respuesta.body().split("\\{")) {
					String id = crudo("{" + bloque, "paymentMethodId");
					String nombre = texto("{" + bloque, "paymentMethodName");
					if (id != null && nombre != null && nombre.toUpperCase().contains("QR")) {
						org.slf4j.LoggerFactory.getLogger(PagoFacilGateway.class).info("PagoFacilGateway: Encontrado método QR habilitado: ID=" + id + ", Nombre=" + nombre);
						return Integer.parseInt(id);
					}
				}
			}
		} catch (IOException | NumberFormatException e) {
			org.slf4j.LoggerFactory.getLogger(PagoFacilGateway.class).info("PagoFacilGateway: Advertencia al listar servicios: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		org.slf4j.LoggerFactory.getLogger(PagoFacilGateway.class).info("PagoFacilGateway: Usando ID de método por defecto (" + METODO_QR_POR_DEFECTO + ")");
		return METODO_QR_POR_DEFECTO;
	}

	public QrResult generarQr(String numeroPago, BigDecimal monto, String concepto, Integer cliente, String nombre, String documento, String telefono, String correo)
			throws IOException, InterruptedException {
		String token = obtenerToken();
		String json = String.format("{\"paymentMethod\":%d,\"clientName\":\"%s\",\"documentType\":1,\"documentId\":\"%s\",\"phoneNumber\":\"%s\",\"email\":\"%s\","
				+ "\"paymentNumber\":\"%s\",\"amount\":%s,\"currency\":2,\"clientCode\":\"%d\",\"callbackUrl\":\"%s\","
				+ "\"orderDetail\":[{\"serial\":1,\"product\":\"%s\",\"quantity\":1,\"price\":%s,\"discount\":0,\"total\":%s}]}",
				metodoQr(token), escapar(nombre), escapar(valor(documento, String.valueOf(cliente))), escapar(valor(telefono, "0")), escapar(valor(correo, "no-email@pago.com")),
				escapar(numeroPago), monto.toPlainString(), cliente, escapar(callbackUrl), escapar(concepto), monto.toPlainString(), monto.toPlainString());
		HttpRequest request = peticion("/generate-qr").header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofString(json)).build();
		String respuesta = enviar(request, 2, "generate-qr").body();

		String qr = primero(respuesta, "qrImage", "qrBase64");
		String transaccion = primero(respuesta, "transactionId", "idTransaccion", "codigoTransaccion", "id");
		if (qr == null || transaccion == null) {
			org.slf4j.LoggerFactory.getLogger(PagoFacilGateway.class).info("PagoFacilGateway: respuesta sin QR: " + resumen(respuesta));
			throw new IOException("PagoFacil no devolvio el QR.");
		}
		return new QrResult(qr.replace("\\/", "/").replace("\\n", "").replace("\\r", "").replace(" ", ""), transaccion);
	}

	public QueryResult consultarTransaccion(String transaccion) throws IOException, InterruptedException {
		String token = obtenerToken();
		HttpRequest request = peticion("/query-transaction").header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofString("{\"pagofacilTransactionId\":\"" + escapar(transaccion) + "\"}")).build();
		String json = enviar(request, 2, "query-transaction").body();
		String error = crudo(json, "error");
		if (error != null && !error.equals("0")) {
			org.slf4j.LoggerFactory.getLogger(PagoFacilGateway.class).info("PagoFacilGateway: error de la API: " + resumen(json));
			String mensaje = texto(json, "message");
			throw new IOException(mensaje != null ? mensaje : "PagoFacil rechazo la consulta.");
		}
		String estado = crudo(json, "paymentStatus");
		String descripcion = texto(json, "paymentStatusDescription");
		String pagador = texto(json, "payerName");
		String normalizado = normalizar(estado, descripcion == null ? "" : descripcion, pagador);
		org.slf4j.LoggerFactory.getLogger(PagoFacilGateway.class).info("PagoFacilGateway: Transacción " + transaccion + " paymentStatus=" + estado + " -> " + normalizado);
		return new QueryResult(normalizado, descripcion, pagador, texto(json, "payerBank"));
	}

	/** Mismos criterios que el proyecto anterior: un pagador asignado prueba que se pago. */
	private String normalizar(String estado, String descripcion, String pagador) {
		if (estado == null) {
			return "PENDIENTE";
		}
		String detalle = descripcion.toLowerCase();
		if (estado.equals("2") || estado.equals("5") || (pagador != null && !pagador.isBlank()) || detalle.contains("complet")
				|| detalle.contains("procesado") || detalle.contains("pagado") || detalle.contains("revisión") || detalle.contains("revision")) {
			return "PAGADO";
		}
		if (estado.equals("3") || detalle.contains("rechaz") || detalle.contains("cancel") || detalle.contains("anulado")) {
			return "CANCELADO";
		}
		return "PENDIENTE";
	}

	private String valor(String texto, String porDefecto) {
		return texto == null || texto.isBlank() ? porDefecto : texto.trim();
	}

	private String resumen(String json) {
		return json.length() <= 300 ? json : json.substring(0, 300) + "...";
	}

	private String primero(String json, String... claves) {
		for (String clave : claves) {
			String encontrado = texto(json, clave);
			if (encontrado != null) {
				return encontrado;
			}
		}
		return null;
	}

	private String texto(String json, String clave) {
		Matcher coincidencia = Pattern.compile("\"" + clave + "\"[\\s:]+\"([^\"]+)\"").matcher(json);
		return coincidencia.find() ? coincidencia.group(1) : crudo(json, clave);
	}

	private String crudo(String json, String clave) {
		Matcher coincidencia = Pattern.compile("\"" + clave + "\"[\\s:]+([^,}\\s]+)").matcher(json);
		if (!coincidencia.find()) {
			return null;
		}
		String valor = coincidencia.group(1).trim();
		return valor.startsWith("\"") && valor.endsWith("\"") && valor.length() > 1 ? valor.substring(1, valor.length() - 1) : valor;
	}

	private String escapar(String texto) {
		return texto == null ? "" : texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f")
				.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
	}

}

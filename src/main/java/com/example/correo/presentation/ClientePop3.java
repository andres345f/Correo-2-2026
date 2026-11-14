package com.example.correo.presentation;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Cliente POP3 sobre java.net.Socket. Cada sesion saca un solo correo: asi una
 * caida del programa deja los demas en el buzon.
 */
public class ClientePop3 {

	private static final Pattern RESPUESTA_STAT = Pattern.compile("^\\+OK (\\d+)\\b.*");

	private final String host;
	private final int puerto;
	private final String usuario;
	private final String password;
	private final int tiempoMaximoMs;
	private final boolean ssl;

	public ClientePop3(String host, int puerto, String usuario, String password, int tiempoMaximoMs) {
		this(host, puerto, usuario, password, tiempoMaximoMs, false);
	}

	/** Con ssl=true la conexion va cifrada desde el inicio, como pide Gmail (pop.gmail.com:995). */
	public ClientePop3(String host, int puerto, String usuario, String password, int tiempoMaximoMs, boolean ssl) {
		this.host = host;
		this.puerto = puerto;
		this.usuario = usuario;
		this.password = password;
		this.tiempoMaximoMs = tiempoMaximoMs;
		this.ssl = ssl;
	}

	private Socket crearSocket() throws IOException {
		if (!ssl) {
			return new Socket();
		}
		SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
		// Comprueba que el certificado sea del host: sin esto se aceptaria cualquier servidor.
		SSLParameters parametros = socket.getSSLParameters();
		parametros.setEndpointIdentificationAlgorithm("HTTPS");
		socket.setSSLParameters(parametros);
		return socket;
	}

	/**
	 * Devuelve los encabezados del correo mas antiguo, o null si el buzon esta
	 * vacio. POP3 solo aplica el borrado con el QUIT: si este metodo termina bien,
	 * el servidor confirmo el borrado; si lanza IOException, la orden no debe
	 * ejecutarse.
	 */
	public byte[] extraerPrimero() throws IOException {
		Terminal.servidor("Conectando al servidor POP3 para revisar correos...");
		try (Socket socket = crearSocket()) {
			socket.connect(new InetSocketAddress(host, puerto), tiempoMaximoMs);
			socket.setSoTimeout(tiempoMaximoMs);
			// ISO-8859-1 conserva cada byte tal cual; MIME decodifica despues.
			BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
			DataOutputStream salida = new DataOutputStream(socket.getOutputStream());

			Terminal.servidor(leerRespuesta(entrada));
			enviar(salida, entrada, "USER " + usuario);
			enviar(salida, entrada, "PASS " + password);
			int cantidad = cantidadDeCorreos(enviar(salida, entrada, "STAT"));
			Terminal.servidor("Total de correos encontrados: " + cantidad);
			if (cantidad == 0) {
				cerrarSesion(salida, entrada);
				return null;
			}
			enviar(salida, entrada, "RETR 1");
			byte[] encabezados = leerEncabezados(entrada);
			enviar(salida, entrada, "DELE 1");
			cerrarSesion(salida, entrada);
			return encabezados;
		}
	}

	private void cerrarSesion(DataOutputStream salida, BufferedReader entrada) throws IOException {
		enviar(salida, entrada, "QUIT");
		Terminal.servidor("Conexión finalizada POP.");
	}

	private String enviar(DataOutputStream salida, BufferedReader entrada, String comando) throws IOException {
		// La contrasena nunca se imprime.
		Terminal.cliente(comando.startsWith("PASS ") ? "PASS ********" : comando);
		salida.writeBytes(comando + "\r\n");
		salida.flush();
		return leerRespuesta(entrada);
	}

	private String leerRespuesta(BufferedReader entrada) throws IOException {
		String respuesta = leerLinea(entrada);
		if (!respuesta.startsWith("+OK")) {
			throw new IOException("El servidor POP3 respondio: " + respuesta);
		}
		return respuesta;
	}

	private int cantidadDeCorreos(String respuesta) throws IOException {
		Matcher stat = RESPUESTA_STAT.matcher(respuesta);
		if (!stat.matches()) {
			throw new IOException("Respuesta STAT invalida: " + respuesta);
		}
		return Integer.parseInt(stat.group(1));
	}

	// Solo guarda los encabezados: la orden va en el asunto, y un adjunto grande no ocupa memoria.
	private byte[] leerEncabezados(BufferedReader entrada) throws IOException {
		StringBuilder encabezados = new StringBuilder();
		boolean enCuerpo = false;
		String linea;
		while (!(linea = leerLinea(entrada)).equals(".")) {
			if (linea.startsWith(".")) {
				linea = linea.substring(1);
			}
			enCuerpo = enCuerpo || linea.isEmpty();
			if (!enCuerpo) {
				encabezados.append(linea).append("\r\n");
			}
		}
		return encabezados.append("\r\n").toString().getBytes(StandardCharsets.ISO_8859_1);
	}

	private String leerLinea(BufferedReader entrada) throws IOException {
		String linea = entrada.readLine();
		if (linea == null) {
			throw new IOException("El servidor POP3 cerro la conexion.");
		}
		return linea;
	}

}

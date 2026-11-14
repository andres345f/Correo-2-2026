package com.example.correo.presentation;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Cliente SMTP sobre java.net.Socket: Tecnoweb sin autenticacion o Gmail por SSL con AUTH LOGIN. */
@Component
public class ClienteSmtp implements JavaMailSender {

	private static final String HOST_GMAIL = "smtp.gmail.com";
	private static final int PUERTO_GMAIL = 465;
	private static final int TIEMPO_MAXIMO_MS = 30000;

	private final String host;
	private final int puerto;
	private final boolean google;
	private final String usuarioGmail;
	private final String claveGmail;

	@Autowired
	public ClienteSmtp(@Value("${app.mail.host}") String host, @Value("${app.smtp.puerto:25}") int puerto,
			@Value("${app.smtp.proveedor:TECNOWEB}") String proveedor,
			@Value("${app.smtp.gmail.user:}") String usuarioGmail,
			@Value("${app.smtp.gmail.app-password:}") String claveGmail) {
		this.google = "GOOGLE".equalsIgnoreCase(proveedor.trim());
		if (google && (usuarioGmail.isBlank() || claveGmail.isBlank())) {
			throw new IllegalStateException("SMTP_PROVEEDOR=GOOGLE requiere SMTP_GMAIL_USER y SMTP_GMAIL_APP_PASSWORD en .env.");
		}
		this.host = google ? HOST_GMAIL : host;
		this.puerto = google ? PUERTO_GMAIL : puerto;
		this.usuarioGmail = usuarioGmail;
		this.claveGmail = claveGmail;
	}

	public String nombre() {
		return google ? "Google" : "Tecnoweb";
	}

	public String direccion() {
		return host + ":" + puerto;
	}

	/** Abre la conexion y la cierra sin enviar nada. */
	public void probar() throws IOException {
		try (Socket socket = abrir()) {
			BufferedReader entrada = lector(socket);
			OutputStream salida = new BufferedOutputStream(socket.getOutputStream());
			leer(entrada, "220");
			comando(salida, entrada, "QUIT", "");
		}
	}

	private Socket abrir() throws IOException {
		Socket socket = google ? SSLSocketFactory.getDefault().createSocket() : new Socket();
		socket.connect(new InetSocketAddress(host, puerto), TIEMPO_MAXIMO_MS);
		socket.setSoTimeout(TIEMPO_MAXIMO_MS);
		return socket;
	}

	private BufferedReader lector(Socket socket) throws IOException {
		return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
	}

	private void enviar(MimeMessage mensaje) {
		try {
			if (google) {
				mensaje.setFrom(new InternetAddress(usuarioGmail));
			}
			if (mensaje.getSentDate() == null) {
				mensaje.setSentDate(new Date());
			}
			mensaje.saveChanges();
			ByteArrayOutputStream contenido = new ByteArrayOutputStream();
			mensaje.writeTo(contenido);
			Address[] destinos = mensaje.getAllRecipients();
			Terminal.linea("Enviando correo vía " + nombre() + " SMTP a " + InternetAddress.toString(destinos));
			try (Socket socket = abrir()) {
				conversar(socket, ((InternetAddress) mensaje.getFrom()[0]).getAddress(), destinos, contenido.toByteArray());
			}
		} catch (IOException | MessagingException e) {
			throw new MailSendException(e.getMessage(), e);
		}
	}

	private void conversar(Socket socket, String origen, Address[] destinos, byte[] contenido) throws IOException {
		BufferedReader entrada = lector(socket);
		OutputStream salida = new BufferedOutputStream(socket.getOutputStream());
		leer(entrada, "220");
		comando(salida, entrada, "EHLO localhost", "250");
		if (google) {
			comando(salida, entrada, "AUTH LOGIN", "334");
			secreto(salida, entrada, usuarioGmail, "334");
			secreto(salida, entrada, claveGmail, "235");
		}
		comando(salida, entrada, "MAIL FROM:<" + origen + ">", "250");
		for (Address destino : destinos) {
			comando(salida, entrada, "RCPT TO:<" + ((InternetAddress) destino).getAddress() + ">", "250");
		}
		comando(salida, entrada, "DATA", "354");
		escribirContenido(salida, contenido);
		salida.write(".\r\n".getBytes(StandardCharsets.ISO_8859_1));
		salida.flush();
		leer(entrada, "250");
		comando(salida, entrada, "QUIT", "");
	}

	private void comando(OutputStream salida, BufferedReader entrada, String comando, String esperado) throws IOException {
		Terminal.cliente(comando);
		escribirLinea(salida, comando);
		leer(entrada, esperado);
	}

	/** Usuario y clave viajan en Base64, que se puede revertir: en la terminal nunca aparecen. */
	private void secreto(OutputStream salida, BufferedReader entrada, String valor, String esperado) throws IOException {
		Terminal.cliente("********");
		escribirLinea(salida, Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8)));
		leer(entrada, esperado);
	}

	private void escribirLinea(OutputStream salida, String linea) throws IOException {
		salida.write((linea + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
		salida.flush();
	}

	/** Lee una respuesta, que puede ocupar varias lineas; la ultima lleva un espacio tras el codigo. */
	private void leer(BufferedReader entrada, String esperado) throws IOException {
		String primera = null;
		String linea;
		do {
			linea = entrada.readLine();
			if (linea == null) {
				throw new IOException("El servidor SMTP cerro la conexion.");
			}
			primera = primera == null ? linea : primera;
		} while (linea.length() > 3 && linea.charAt(3) == '-');
		Terminal.servidor(primera);
		if (!primera.startsWith(esperado)) {
			throw new MailSendException(linea.trim());
		}
	}

	/** Normaliza los saltos a CRLF y duplica el punto inicial, para que ninguna linea termine el DATA. */
	private void escribirContenido(OutputStream salida, byte[] contenido) throws IOException {
		boolean inicioDeLinea = true;
		for (int i = 0; i < contenido.length; i++) {
			byte actual = contenido[i];
			if (actual == '\r' && i + 1 < contenido.length && contenido[i + 1] == '\n') {
				continue;
			}
			if (actual == '\n') {
				salida.write('\r');
				salida.write('\n');
				inicioDeLinea = true;
				continue;
			}
			if (inicioDeLinea && actual == '.') {
				salida.write('.');
			}
			salida.write(actual);
			inicioDeLinea = false;
		}
		if (!inicioDeLinea) {
			salida.write('\r');
			salida.write('\n');
		}
	}

	@Override
	public MimeMessage createMimeMessage() {
		return new MimeMessage(Session.getInstance(new Properties()));
	}

	@Override
	public MimeMessage createMimeMessage(InputStream contenido) throws MailException {
		try {
			return new MimeMessage(Session.getInstance(new Properties()), contenido);
		} catch (MessagingException e) {
			throw new MailParseException(e);
		}
	}

	@Override
	public void send(MimeMessage... mensajes) throws MailException {
		for (MimeMessage mensaje : mensajes) {
			enviar(mensaje);
		}
	}

	@Override
	public void send(SimpleMailMessage... mensajes) throws MailException {
		throw new UnsupportedOperationException("El sistema solo envia mensajes MIME.");
	}

}

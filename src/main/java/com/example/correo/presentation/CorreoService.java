package com.example.correo.presentation;

import com.example.correo.service.CodigoError;
import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Revisa el buzon por POP3, ejecuta cada comando y responde al remitente por SMTP. */
@Service
public class CorreoService {

	private static final int PUERTO_POP3 = 110;
	private static final String HOST_POP_GMAIL = "pop.gmail.com";
	private static final int PUERTO_POP_GMAIL = 995;
	private static final long INTERVALO_MS = 5000;
	private static final String CHARSET = "UTF-8";
	private static final int TIEMPO_MAXIMO_MS = 30000;
	// Encabezados en UTF-8 crudo (SMTPUTF8) se leen como UTF-8; si no lo son, quedan U+FFFD y el asunto se rechaza.
	private static final Session LECTURA = Session.getInstance(propiedadesLectura());

	private static java.util.Properties propiedadesLectura() {
		java.util.Properties propiedades = new java.util.Properties();
		propiedades.setProperty("mail.mime.allowutf8", "true");
		return propiedades;
	}

	private final ComandoService comandos;
	private final JavaMailSender mailSender;
	private final String host;
	private final String usuario;
	private final String password;
	private final String remitente;
	private final boolean popGoogle;
	private final String usuarioGmail;
	private final String claveGmail;

	public CorreoService(ComandoService comandos, JavaMailSender mailSender,
			@Value("${app.mail.host}") String host,
			@Value("${app.mail.username}") String usuario,
			@Value("${app.mail.password}") String password,
			@Value("${app.mail.from}") String remitente,
			@Value("${app.pop.proveedor:TECNOWEB}") String proveedorPop,
			@Value("${app.smtp.gmail.user:}") String usuarioGmail,
			@Value("${app.smtp.gmail.app-password:}") String claveGmail) {
		this.popGoogle = "GOOGLE".equalsIgnoreCase(proveedorPop.trim());
		if (popGoogle && (usuarioGmail.isBlank() || claveGmail.isBlank())) {
			throw new IllegalStateException("POP_PROVEEDOR=GOOGLE requiere SMTP_GMAIL_USER y SMTP_GMAIL_APP_PASSWORD en .env.");
		}
		this.comandos = comandos;
		this.mailSender = mailSender;
		this.host = host;
		this.usuario = usuario;
		this.password = password;
		this.remitente = remitente;
		this.usuarioGmail = usuarioGmail;
		this.claveGmail = claveGmail;
	}

	/** Para el mensaje de arranque: que buzon se esta leyendo. */
	public String servidorPop() {
		return popGoogle ? "Google (" + HOST_POP_GMAIL + ":" + PUERTO_POP_GMAIL + ", " + usuarioGmail + ")" : host;
	}

	@Scheduled(initialDelay = INTERVALO_MS, fixedDelay = INTERVALO_MS)
	public void revisarBuzon() {
		ClientePop3 cliente = buzon();
		try {
			byte[] encabezados;
			// Cada correo se saca del buzon y solo entonces se ejecuta: nunca se repite una orden.
			while ((encabezados = cliente.extraerPrimero()) != null) {
				try {
					atender(encabezados);
				} catch (Exception e) {
					Terminal.servidor("Error al procesar el correo: " + e.getMessage() + " [" + CodigoError.CORREO_ILEGIBLE.etiqueta() + "]");
				}
			}
		} catch (IOException e) {
			Terminal.servidor(describirFalloPop(e) + " [" + CodigoError.BUZON_NO_DISPONIBLE.etiqueta() + "]");
		}
	}

	private String describirFalloPop(IOException e) {
		if (e instanceof SocketTimeoutException) {
			return "Error de conexión POP - El servidor no respondió a tiempo: " + e.getMessage();
		}
		if (e instanceof ConnectException || e instanceof UnknownHostException) {
			return "Error de E/S al conectar al servidor POP: " + e.getMessage();
		}
		if (e instanceof SocketException) {
			return "Error de conexión POP - Socket cerrado o reset por el servidor: " + e.getMessage();
		}
		return "Error en comunicación POP3: " + e.getMessage();
	}

	/** El asunto tal cual, salvo los comandos de USU: sus parametros traen la contrasena. */
	private String visible(String asunto) {
		if (asunto == null) {
			return "";
		}
		String limpio = asunto.replaceAll("\\p{Cc}", "");
		return limpio.toUpperCase().contains("USU[") ? limpio.split("\\[", 2)[0] + "[***]" : limpio;
	}

	/** El encabezado sin decodificar; en USU se oculta, porque Base64 se revierte y trae la contrasena. */
	private String crudoVisible(String crudo, String asunto) {
		if (crudo == null) {
			return "(sin Subject)";
		}
		if (visible(asunto).endsWith("[***]")) {
			return "[oculto: comando USU]";
		}
		return crudo.replaceAll("\\r?\\n", "");
	}

	/** Cada caracter no ASCII con su codigo Unicode: prueba el decodificado aunque la consola muestre mal. */
	private String noAscii(String texto) {
		StringBuilder lista = new StringBuilder();
		texto.codePoints().filter(c -> c > 127).distinct()
				.forEach(c -> lista.append(lista.isEmpty() ? "" : " ").append(Character.toString(c)).append(String.format("=U+%04X", c)));
		return lista.isEmpty() ? "ninguno" : lista.toString();
	}

	protected ClientePop3 buzon() {
		if (popGoogle) {
			return new ClientePop3(HOST_POP_GMAIL, PUERTO_POP_GMAIL, usuarioGmail, claveGmail, TIEMPO_MAXIMO_MS, true);
		}
		return new ClientePop3(host, PUERTO_POP3, usuario, password, TIEMPO_MAXIMO_MS);
	}

	private void atender(byte[] encabezados) throws MessagingException {
		MimeMessage mensaje = new MimeMessage(LECTURA, new ByteArrayInputStream(encabezados));
		String origen = direccion(mensaje.getFrom());
		if (origen == null || esAutomatico(origen)) {
			Terminal.servidor("Correo ignorado de: " + origen);
			return;
		}
		String asunto = mensaje.getSubject();
		// Solo el codigo del comando: los parametros pueden traer una contrasena.
		String comando = asunto == null ? "" : asunto.split("\\[", 2)[0].replaceAll("\\p{Cc}", "");
		Terminal.servidor("Subject crudo (como llego del servidor): " + crudoVisible(mensaje.getHeader("Subject", null), asunto));
		Terminal.servidor("Parseando subject decodificado: " + visible(asunto) + " de: " + origen);
		Terminal.servidor("Caracteres no ASCII: " + noAscii(visible(asunto)));
		Terminal.servidor("Procesando comando: " + comando);
		Terminal.servidor("Ejecutando operación: " + comando);
		ComandoService.Respuesta respuesta = comandos.procesar(asunto, origen);
		String detalle = ComandoService.ASUNTO_AYUDA.equals(respuesta.asunto()) ? "Guia enviada." : respuesta.cuerpo();
		Terminal.servidor((respuesta.esError() ? "Error en el comando " + comando + ": " : "Resultado: ") + respuesta.asunto() + "\n" + detalle);

		MimeMessage correo = construirRespuesta(origen, respuesta);
		try {
			mailSender.send(correo);
			Terminal.servidor("Correo enviado a " + origen);
		} catch (MailException e) {
			String causa = causaDeEnvio(e);
			// Si el servidor SMTP respondio, su codigo ya es el estandar; si no, no hubo conexion.
			String codigo = causa.matches("(?s)\\d{3} .*") ? "" : CodigoError.RESPUESTA_NO_ENVIADA.codigo() + " ";
			Terminal.servidor("No se pudo enviar el correo a " + origen + ", error: " + codigo + causa + " [RESPUESTA_NO_ENVIADA]");
		}
	}

	/** multipart/alternative: primero el texto y al final el HTML, que es el preferido. */
	private MimeMessage construirRespuesta(String origen, ComandoService.Respuesta respuesta) throws MessagingException {
		MimeMultipart alternativas = new MimeMultipart("alternative");
		alternativas.addBodyPart(parte(respuesta.cuerpo(), "plain"));
		alternativas.addBodyPart(parte(respuesta.html(), "html"));

		MimeMessage correo = mailSender.createMimeMessage();
		correo.setFrom(new InternetAddress(remitente));
		correo.setRecipient(Message.RecipientType.TO, new InternetAddress(origen, true));
		correo.setSubject(respuesta.asunto(), CHARSET);
		if (respuesta.adjuntos().isEmpty()) {
			correo.setContent(alternativas);
			return correo;
		}
		MimeBodyPart texto = new MimeBodyPart();
		texto.setContent(alternativas);
		// "related" permite que el HTML muestre una imagen adjunta, como el QR de pago.
		boolean conImagenEnLinea = respuesta.adjuntos().stream().anyMatch(a -> a.contentId() != null);
		MimeMultipart mezcla = new MimeMultipart(conImagenEnLinea ? "related" : "mixed");
		mezcla.addBodyPart(texto);
		for (ComandoService.Adjunto adjunto : respuesta.adjuntos()) {
			MimeBodyPart parte = new MimeBodyPart();
			parte.setDataHandler(new DataHandler(new ByteArrayDataSource(adjunto.datos(), adjunto.tipo())));
			parte.setFileName(adjunto.nombre());
			if (adjunto.contentId() != null) {
				parte.setContentID("<" + adjunto.contentId() + ">");
				parte.setDisposition(MimeBodyPart.INLINE);
			}
			parte.setHeader("Content-Transfer-Encoding", "base64");
			mezcla.addBodyPart(parte);
		}
		correo.setContent(mezcla);
		return correo;
	}

	// Quoted-printable deja solo ASCII en lineas cortas: no se dana en servidores de 7 bits.
	private MimeBodyPart parte(String contenido, String subtipo) throws MessagingException {
		MimeBodyPart parte = new MimeBodyPart();
		parte.setText(contenido, CHARSET, subtipo);
		parte.setHeader("Content-Transfer-Encoding", "quoted-printable");
		return parte;
	}

	/** Extrae la respuesta real del servidor SMTP, que viene anidada en varias excepciones. */
	private String causaDeEnvio(MailException e) {
		Throwable causa = e instanceof MailSendException envio && envio.getMessageExceptions().length > 0
				? envio.getMessageExceptions()[0]
				: NestedExceptionUtils.getMostSpecificCause(e);
		while (causa instanceof MessagingException mensajeria && mensajeria.getNextException() != null) {
			causa = mensajeria.getNextException();
		}
		return causa.getMessage().trim();
	}

	private String direccion(Address[] direcciones) {
		return direcciones != null && direcciones.length > 0 && direcciones[0] instanceof InternetAddress direccion
				? direccion.getAddress()
				: null;
	}

	// Responder a estos remitentes crearia un bucle infinito de correos.
	// La cuenta de Gmail del sistema tambien: con SMTP Google es quien firma las respuestas.
	private boolean esAutomatico(String origen) {
		String correo = origen.toLowerCase();
		return correo.equalsIgnoreCase(remitente) || (!usuarioGmail.isBlank() && correo.equalsIgnoreCase(usuarioGmail.trim()))
				|| correo.startsWith("mailer-daemon@")
				|| correo.startsWith("postmaster@");
	}

}

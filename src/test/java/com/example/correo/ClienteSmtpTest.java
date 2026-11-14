package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;

import com.example.correo.presentation.ClienteSmtp;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

// El servidor es un ServerSocket local que responde segun cada orden.
@ExtendWith(OutputCaptureExtension.class)
class ClienteSmtpTest {

	private static final String RECHAZO = "550 5.7.1 <a@gmail.com>... Relaying denied";

	private CompletableFuture<List<String>> servidor(ServerSocket escucha, String respuestaRcpt) {
		CompletableFuture<List<String>> recibido = new CompletableFuture<>();
		Thread hilo = new Thread(() -> {
			List<String> lineas = new ArrayList<>();
			try (Socket cliente = escucha.accept()) {
				BufferedReader entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream(), StandardCharsets.ISO_8859_1));
				OutputStream salida = cliente.getOutputStream();
				responder(salida, "220 prueba");
				boolean enDatos = false;
				String linea;
				while ((linea = entrada.readLine()) != null) {
					lineas.add(linea);
					if (enDatos) {
						if (linea.equals(".")) {
							enDatos = false;
							responder(salida, "250 aceptado");
						}
					} else if (linea.startsWith("EHLO")) {
						responder(salida, "250-prueba\r\n250 HELP");
					} else if (linea.startsWith("RCPT")) {
						responder(salida, respuestaRcpt);
					} else if (linea.equals("DATA")) {
						enDatos = true;
						responder(salida, "354 adelante");
					} else if (linea.equals("QUIT")) {
						responder(salida, "221 adios");
						break;
					} else {
						responder(salida, "250 OK");
					}
				}
				recibido.complete(lineas);
			} catch (IOException e) {
				recibido.complete(lineas);
			}
		});
		hilo.setDaemon(true);
		hilo.start();
		return recibido;
	}

	private void responder(OutputStream salida, String texto) throws IOException {
		salida.write((texto + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
		salida.flush();
	}

	private MimeMessage mensaje(ClienteSmtp smtp, String cuerpo) throws Exception {
		MimeMessage mensaje = smtp.createMimeMessage();
		mensaje.setFrom(new InternetAddress("sistema@tecnoweb.org.bo"));
		mensaje.setRecipient(Message.RecipientType.TO, new InternetAddress("a@gmail.com"));
		mensaje.setSubject("Prueba");
		mensaje.setText(cuerpo);
		return mensaje;
	}

	@Test
	void enviaPorSocketYDuplicaElPuntoInicial(CapturedOutput terminal) throws Exception {
		try (ServerSocket escucha = new ServerSocket(0)) {
			CompletableFuture<List<String>> recibido = servidor(escucha, "250 OK");
			ClienteSmtp smtp = new ClienteSmtp("localhost", escucha.getLocalPort(), "TECNOWEB", "", "");

			smtp.send(mensaje(smtp, "primera\n.con punto\n."));

			List<String> lineas = recibido.get(10, TimeUnit.SECONDS);
			assertTrue(lineas.contains("MAIL FROM:<sistema@tecnoweb.org.bo>"), lineas.toString());
			assertTrue(lineas.contains("RCPT TO:<a@gmail.com>"), lineas.toString());
			assertTrue(lineas.contains("..con punto"), lineas.toString());
			assertEquals(".", lineas.get(lineas.size() - 2), "el mensaje termina en un solo punto");
			assertTrue(terminal.getOut().contains("Tecnoweb SMTP a a@gmail.com"), terminal.getOut());
			assertTrue(terminal.getOut().contains("S : 220 prueba") && terminal.getOut().contains("C : RCPT TO:<a@gmail.com>"), terminal.getOut());
		}
	}

	@Test
	void informaElRechazoDelServidor(CapturedOutput terminal) throws Exception {
		try (ServerSocket escucha = new ServerSocket(0)) {
			servidor(escucha, RECHAZO);
			ClienteSmtp smtp = new ClienteSmtp("localhost", escucha.getLocalPort(), "TECNOWEB", "", "");

			MailSendException error = assertThrows(MailSendException.class, () -> smtp.send(mensaje(smtp, "hola")));

			assertTrue(error.getMessage().startsWith("550 5.7.1"), error.getMessage());
			assertTrue(terminal.getOut().contains("S : " + RECHAZO), terminal.getOut());
		}
	}

	@Test
	void googleExigeSusCredenciales() {
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> new ClienteSmtp("h", 25, "GOOGLE", "", ""));
		assertTrue(error.getMessage().contains("SMTP_GMAIL_APP_PASSWORD"));
	}

}

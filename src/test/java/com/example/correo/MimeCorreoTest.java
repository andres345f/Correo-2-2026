package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.correo.entity.Categoria;
import com.example.correo.presentation.ClienteSmtp;
import com.example.correo.presentation.ClientePop3;
import com.example.correo.service.CodigoError;
import com.example.correo.presentation.ComandoService;
import com.example.correo.presentation.ComandoService.Adjunto;
import com.example.correo.presentation.ComandoService.Respuesta;
import com.example.correo.presentation.CorreoService;
import com.example.correo.service.CrudService;
import com.example.correo.service.OperacionesService;
import com.example.correo.service.PermisosService;

import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

// No usa la red real ni la base de datos: POP3 y SMTP hablan por sockets en localhost.
@ExtendWith(OutputCaptureExtension.class)
class MimeCorreoTest {

	private static final String REMITENTE = "sistema@tecnoweb.org.bo";
	private static final Respuesta OK = new Respuesta("Exito", "x", "<p>x</p>");

	private enum Corte {
		NINGUNO, RETR, RETR_A_MEDIAS, RETR_ERROR, DELE, DELE_ERROR, QUIT, QUIT_ERROR, QUIT_APLICADO_SIN_RESPUESTA, SIN_RESPUESTA
	}

	/** Servidor POP3 de prueba: como uno real, solo aplica los DELE si la sesion llega al QUIT. */
	private static final class ServidorPop3 implements AutoCloseable {

		private final ServerSocket servidor = new ServerSocket(0);
		private final List<String> correos = new CopyOnWriteArrayList<>();
		private final AtomicReference<Corte> corte = new AtomicReference<>(Corte.NINGUNO);

		ServidorPop3() throws IOException {
			Thread hilo = new Thread(this::aceptar);
			hilo.setDaemon(true);
			hilo.start();
		}

		private void aceptar() {
			try {
				while (true) {
					Socket cliente = servidor.accept();
					Thread sesion = new Thread(() -> atender(cliente));
					sesion.setDaemon(true);
					sesion.start();
				}
			} catch (IOException e) {
				// Servidor cerrado al terminar el test.
			}
		}

		private boolean toca(Corte esperado) {
			return corte.compareAndSet(esperado, Corte.NINGUNO);
		}

		private void atender(Socket socket) {
			Set<Integer> borrados = new HashSet<>();
			try (socket) {
				BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
				OutputStream salida = socket.getOutputStream();
				responder(salida, "+OK listo");
				String linea;
				while ((linea = entrada.readLine()) != null) {
					if (linea.startsWith("STAT")) {
						responder(salida, "+OK " + correos.size() + " 100");
					} else if (linea.startsWith("RETR")) {
						if (toca(Corte.SIN_RESPUESTA)) {
							entrada.readLine();
							return;
						}
						if (toca(Corte.RETR)) {
							return;
						}
						if (toca(Corte.RETR_ERROR)) {
							responder(salida, "-ERR no se pudo leer");
							continue;
						}
						if (toca(Corte.RETR_A_MEDIAS)) {
							responder(salida, "+OK\r\nFrom: a@b.bo");
							return;
						}
						responder(salida, "+OK mensaje");
						for (String parte : correos.get(0).split("\r\n", -1)) {
							responder(salida, parte.startsWith(".") ? "." + parte : parte);
						}
						responder(salida, ".");
					} else if (linea.startsWith("DELE")) {
						if (toca(Corte.DELE)) {
							return;
						}
						if (toca(Corte.DELE_ERROR)) {
							responder(salida, "-ERR no se pudo borrar");
							continue;
						}
						borrados.add(Integer.parseInt(linea.substring(5).trim()));
						responder(salida, "+OK");
					} else if (linea.startsWith("QUIT")) {
						if (toca(Corte.QUIT)) {
							return;
						}
						if (toca(Corte.QUIT_ERROR)) {
							responder(salida, "-ERR no se aplicaron los borrados");
							return;
						}
						boolean sinRespuesta = toca(Corte.QUIT_APLICADO_SIN_RESPUESTA);
						borrados.forEach(numero -> correos.remove(numero - 1));
						if (!sinRespuesta) {
							responder(salida, "+OK adios");
						}
						return;
					} else {
						responder(salida, "+OK");
					}
				}
			} catch (IOException e) {
				// El cliente abandono la sesion.
			}
		}

		private void responder(OutputStream salida, String texto) throws IOException {
			salida.write((texto + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
			salida.flush();
		}

		@Override
		public void close() throws IOException {
			servidor.close();
		}
	}

	/** Servidor SMTP minimo: responde el dialogo 220/EHLO/MAIL/RCPT/DATA/QUIT y guarda los bytes de DATA. */
	private static final class ServidorSmtp implements AutoCloseable {

		private final ServerSocket servidor = new ServerSocket(0);
		private final CompletableFuture<byte[]> datos = new CompletableFuture<>();

		ServidorSmtp(boolean ochoBits) throws IOException {
			Thread hilo = new Thread(() -> atender(ochoBits));
			hilo.setDaemon(true);
			hilo.start();
		}

		private void atender(boolean ochoBits) {
			try (Socket cliente = servidor.accept()) {
				BufferedReader entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream(), StandardCharsets.ISO_8859_1));
				OutputStream salida = cliente.getOutputStream();
				responder(salida, "220 prueba ESMTP");
				String linea;
				while ((linea = entrada.readLine()) != null) {
					String orden = linea.toUpperCase();
					if (orden.startsWith("EHLO")) {
						responder(salida, "250-prueba\r\n250 " + (ochoBits ? "8BITMIME" : "HELP"));
					} else if (orden.equals("DATA")) {
						responder(salida, "354 Enter mail, end with \".\" on a line by itself");
						datos.complete(leerDatos(entrada));
						responder(salida, "250 Message accepted for delivery");
					} else if (orden.equals("QUIT")) {
						responder(salida, "221 closing connection");
						return;
					} else {
						responder(salida, "250 OK");
					}
				}
			} catch (IOException e) {
				datos.completeExceptionally(e);
			}
		}

		private byte[] leerDatos(BufferedReader entrada) throws IOException {
			StringBuilder mensaje = new StringBuilder();
			String linea;
			while (!".".equals(linea = entrada.readLine())) {
				mensaje.append(linea.startsWith(".") ? linea.substring(1) : linea).append("\r\n");
			}
			return mensaje.toString().getBytes(StandardCharsets.ISO_8859_1);
		}

		private void responder(OutputStream salida, String texto) throws IOException {
			salida.write((texto + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
			salida.flush();
		}

		@Override
		public void close() throws IOException {
			servidor.close();
		}
	}

	private ServidorPop3 pop;

	@BeforeEach
	void iniciar() throws IOException {
		pop = new ServidorPop3();
	}

	@AfterEach
	void terminar() throws IOException {
		pop.close();
	}

	private void recibir(String asunto, String cuerpo) {
		pop.correos.add("From: Cliente <cliente@tecnoweb.org.bo>\r\nSubject: " + asunto + "\r\n\r\n" + cuerpo);
	}

	private void recibir(String asunto) {
		recibir(asunto, "cuerpo");
	}

	private CorreoService servicio(ComandoService comandos, JavaMailSender sender) {
		int puerto = pop.servidor.getLocalPort();
		return new CorreoService(comandos, sender, "localhost", "usuario", "clave", REMITENTE, "TECNOWEB", "", "") {
			@Override
			protected ClientePop3 buzon() {
				return new ClientePop3("localhost", puerto, "usuario", "clave", 3000);
			}
		};
	}

	private JavaMailSender senderSimulado() {
		JavaMailSender sender = mock(JavaMailSender.class);
		when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
		return sender;
	}

	private void comprobarEntregaHelp(boolean ochoBits) throws Exception {
		CrudService crud = mock(CrudService.class, CALLS_REAL_METHODS);
		byte[] datos;
		try (ServidorSmtp smtp = new ServidorSmtp(ochoBits)) {
			ClienteSmtp sender = new ClienteSmtp("localhost", smtp.servidor.getLocalPort(), "TECNOWEB", "", "");
			recibir("HELP");
			servicio(new ComandoService(crud, mock(PermisosService.class), mock(OperacionesService.class)), sender).revisarBuzon();
			datos = smtp.datos.get(10, TimeUnit.SECONDS);
		}

		String crudo = new String(datos, StandardCharsets.ISO_8859_1);
		for (String linea : crudo.split("\r\n")) {
			assertTrue(linea.length() <= 998, "linea de " + linea.length() + " octetos");
		}
		if (!ochoBits) {
			for (byte octeto : datos) {
				assertTrue(octeto >= 0, "un servidor sin 8BITMIME solo admite ASCII");
			}
		}
		assertTrue(crudo.contains("MIME-Version: 1.0"));
		assertTrue(crudo.contains("Subject: =?UTF-8?"), "el asunto con tilde debe ir como encoded-word");

		MimeMessage recibido = new MimeMessage((Session) null, new ByteArrayInputStream(datos));
		assertEquals(ComandoService.ASUNTO_AYUDA, recibido.getSubject());
		assertTrue(recibido.getContentType().startsWith("multipart/alternative"), recibido.getContentType());
		MimeMultipart partes = (MimeMultipart) recibido.getContent();
		assertEquals(2, partes.getCount());
		BodyPart texto = partes.getBodyPart(0);
		BodyPart html = partes.getBodyPart(1);
		assertTrue(texto.isMimeType("text/plain"));
		assertTrue(html.isMimeType("text/html"));
		assertTrue(java.util.regex.Pattern.compile("<h2\\b[^>]*>1\\. Cómo enviar una orden</h2>")
				.matcher((String) html.getContent()).find());
		String plano = (String) texto.getContent();
		assertTrue(plano.contains("Cómo enviar una orden") && plano.contains("INSCAT["), "misma guia en texto");
		assertFalse(plano.contains("<td>"));
	}

	@Test
	void helpViajaIntactoPorServidorSin8BitMime() throws Exception {
		comprobarEntregaHelp(false);
	}

	@Test
	void helpViajaIntactoPorServidorCon8BitMime() throws Exception {
		comprobarEntregaHelp(true);
	}

	@Test
	void popGoogleExigeSusCredenciales() {
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> new CorreoService(mock(ComandoService.class),
				senderSimulado(), "h", "u", "p", REMITENTE, "GOOGLE", "", ""));
		assertTrue(error.getMessage().contains("SMTP_GMAIL_APP_PASSWORD"));
	}

	@Test
	void ignoraLosCorreosDeLaPropiaCuentaGmail() {
		ComandoService comandos = mock(ComandoService.class);
		int puerto = pop.servidor.getLocalPort();
		CorreoService servicio = new CorreoService(comandos, senderSimulado(), "localhost", "usuario", "clave", REMITENTE,
				"TECNOWEB", "Sistema@Gmail.com", "clave") {
			@Override
			protected ClientePop3 buzon() {
				return new ClientePop3("localhost", puerto, "usuario", "clave", 3000);
			}
		};
		pop.correos.add("From: <sistema@gmail.com>\r\nSubject: HELP\r\n\r\ncuerpo");

		servicio.revisarBuzon();

		verify(comandos, never()).procesar(any(), any());
	}

	@Test
	void decodificaEncodedWordsDelAsunto() {
		String comando = "INSCAT[\"Caña\",\"Frutas\"]";
		String base64 = Base64.getEncoder().encodeToString(comando.getBytes(StandardCharsets.UTF_8));
		for (String asunto : List.of(
				"=?UTF-8?B?" + base64 + "?=",
				"=?UTF-8?Q?INSCAT[\"Ca=C3=B1a\",\"Frutas\"]?=",
				"=?ISO-8859-1?Q?INSCAT[\"Ca=F1a\",\"Frutas\"]?=",
				"=?UTF-8?Q?INSCAT[\"Ca=C3=B1a\",?=\r\n =?UTF-8?Q?\"Frutas\"]?=")) {
			CrudService crud = mock(CrudService.class);
			doReturn(1).when(crud).insertar(any(), any(), any());
			recibir(asunto);
			servicio(new ComandoService(crud, mock(PermisosService.class), mock(OperacionesService.class)), senderSimulado()).revisarBuzon();
			verify(crud).insertar(eq(Categoria.class), eq(List.of("Caña", "Frutas")), any());
		}
	}

	@Test
	void asuntoDanadoNoLlegaALaBaseDeDatos() {
		for (String asunto : List.of(
				"=?UTF-8?Q?INSCAT[\"Ca=C3\",\"x\"]?=",
				"=?UTF-8?Q?INSCAT[\"a=00\",\"x\"]?=",
				"=?UTF-8?Q?INSCAT[\"a=0D=0ABcc:_x\",\"x\"]?=",
				"INSCAT[\"=?UTF-8?Q?Ca=C3=B1a?=\",\"x\"]")) {
			CrudService crud = mock(CrudService.class);
			JavaMailSender sender = senderSimulado();
			recibir(asunto);
			servicio(new ComandoService(crud, mock(PermisosService.class), mock(OperacionesService.class)), sender).revisarBuzon();
			verify(crud, never()).insertar(any(), any(), any());
			ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
			verify(sender).send(enviado.capture());
			try {
				assertEquals("Error " + CodigoError.ASUNTO_DANADO.codigo(), enviado.getValue().getSubject(), asunto);
			} catch (jakarta.mail.MessagingException e) {
				throw new AssertionError(e);
			}
		}
	}

	@Test
	void trazaElProtocoloSinMostrarContrasenas(CapturedOutput terminal) throws IOException {
		ComandoService comandos = mock(ComandoService.class);
		when(comandos.procesar(any(), any())).thenReturn(OK);
		CorreoService servicio = servicio(comandos, senderSimulado());

		servicio.revisarBuzon();
		String salida = terminal.getOut();
		for (String paso : List.of("S : Conectando al servidor POP3", "C : USER usuario", "C : PASS ********", "C : STAT", "S : Total de correos encontrados: 0", "C : QUIT", "finalizada POP.")) {
			assertTrue(salida.contains(paso), paso);
		}
		assertFalse(salida.contains("PASS clave"));

		recibir("INSUSU[\"a@b.c\",\"secreto\"]");
		recibir("LISCAT[\"7\"]");
		servicio.revisarBuzon();
		salida = terminal.getOut();
		assertTrue(salida.contains("C : RETR 1") && salida.contains("C : DELE 1"), salida);
		assertTrue(salida.contains("S : Procesando comando: INSUSU"), salida);
		assertTrue(salida.contains("S : Parseando subject decodificado: LISCAT[\"7\"] de: cliente@tecnoweb.org.bo"), salida);
		assertFalse(salida.contains("secreto"), "los parametros de USU traen la contrasena");

		// En Base64 la contrasena se podria revertir: el crudo de USU tampoco se muestra.
		String usuB64 = "=?UTF-8?B?" + Base64.getEncoder().encodeToString("INSUSU[\"a@b.c\",\"clave\"]".getBytes(StandardCharsets.UTF_8)) + "?=";
		recibir(usuB64);
		servicio.revisarBuzon();
		assertFalse(terminal.getOut().contains(usuB64.substring(10, 30)), terminal.getOut());
		assertTrue(terminal.getOut().contains("S : Subject crudo (como llego del servidor): [oculto: comando USU]"), terminal.getOut());

		pop.close();
		servicio.revisarBuzon();
		assertTrue(terminal.getOut().contains("Error de E/S al conectar al servidor POP"), terminal.getOut());
		assertTrue(terminal.getOut().contains("421 4.4.0 BUZON_NO_DISPONIBLE"), terminal.getOut());
	}

	@Test
	void muestraElAsuntoCrudoDecodificadoYSusCodigosUnicode(CapturedOutput terminal) {
		ComandoService comandos = mock(ComandoService.class);
		when(comandos.procesar(any(), any())).thenReturn(OK);
		recibir("=?UTF-8?Q?INSCAT[\"Ca=C3=B1a_=E2=82=AC\",?=\r\n =?UTF-8?Q?\"x\"]?=");
		recibir("LISCAT[\"*\"]");

		servicio(comandos, senderSimulado()).revisarBuzon();

		String salida = terminal.getOut();
		assertTrue(salida.contains("S : Subject crudo (como llego del servidor): =?UTF-8?Q?INSCAT[\"Ca=C3=B1a_=E2=82=AC\",?= =?UTF-8?Q?\"x\"]?="), salida);
		assertTrue(salida.contains("S : Parseando subject decodificado: INSCAT[\"Caña €\",\"x\"]"), salida);
		assertTrue(salida.contains("S : Caracteres no ASCII: ñ=U+00F1 €=U+20AC"), salida);
		assertTrue(salida.contains("S : Caracteres no ASCII: ninguno"), salida);
	}

	@Test
	void adjuntaLosArchivosDeLosReportes() throws Exception {
		ComandoService comandos = mock(ComandoService.class);
		when(comandos.procesar(any(), any())).thenReturn(new Respuesta("Reporte", "t", "<p>t</p>", List.of(new Adjunto("r.pdf", "application/pdf", new byte[] { 1, 2 }))));
		JavaMailSender sender = senderSimulado();
		recibir("INFVEN[\"\",\"\",\"\",\"PDF\"]");

		servicio(comandos, sender).revisarBuzon();

		ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
		verify(sender).send(enviado.capture());
		MimeMultipart mezcla = (MimeMultipart) enviado.getValue().getContent();
		assertEquals(2, mezcla.getCount());
		assertEquals("r.pdf", mezcla.getBodyPart(1).getFileName());
	}

	@Test
	void ejecutaSoloDespuesDeConfirmarElBorrado() {
		ComandoService comandos = mock(ComandoService.class);
		List<Integer> pendientesAlEjecutar = new ArrayList<>();
		when(comandos.procesar(any(), any())).thenAnswer(i -> {
			pendientesAlEjecutar.add(pop.correos.size());
			return OK;
		});
		recibir("LISCAT[\"1\"]");
		recibir("LISCAT[\"2\"]");

		servicio(comandos, senderSimulado()).revisarBuzon();

		assertEquals(List.of(1, 0), pendientesAlEjecutar, "cada orden se ejecuta con su correo ya borrado");
		verify(comandos).procesar(eq("LISCAT[\"1\"]"), any());
		verify(comandos).procesar(eq("LISCAT[\"2\"]"), any());
	}

	@Test
	void cualquierCorteAntesDeConfirmarNoEjecutaYSeReintentaUnaVez() {
		for (Corte corte : List.of(Corte.RETR, Corte.RETR_A_MEDIAS, Corte.RETR_ERROR, Corte.DELE, Corte.DELE_ERROR,
				Corte.QUIT, Corte.QUIT_ERROR, Corte.SIN_RESPUESTA)) {
			pop.correos.clear();
			ComandoService comandos = mock(ComandoService.class);
			when(comandos.procesar(any(), any())).thenReturn(OK);
			CorreoService servicio = servicio(comandos, senderSimulado());
			recibir("HELP");
			pop.corte.set(corte);

			servicio.revisarBuzon();
			verify(comandos, never()).procesar(any(), any());
			assertEquals(1, pop.correos.size(), corte + ": el correo debe seguir en el buzon");

			servicio.revisarBuzon();
			verify(comandos, times(1)).procesar(eq("HELP"), any());
			assertEquals(0, pop.correos.size(), corte + ": el reintento debe vaciar el buzon");

			servicio.revisarBuzon();
			verify(comandos, times(1)).procesar(eq("HELP"), any());
		}
	}

	@Test
	void siSePierdeLaConfirmacionDelBorradoNoSeEjecuta() {
		ComandoService comandos = mock(ComandoService.class);
		recibir("HELP");
		pop.corte.set(Corte.QUIT_APLICADO_SIN_RESPUESTA);

		servicio(comandos, senderSimulado()).revisarBuzon();

		verify(comandos, never()).procesar(any(), any());
		assertEquals(0, pop.correos.size());
	}

	@Test
	void caidaDelProgramaDejaLosDemasCorreosEnElBuzon() {
		ComandoService comandos = mock(ComandoService.class);
		when(comandos.procesar(any(), any())).thenThrow(new OutOfMemoryError("simula una caida"));
		recibir("LISCAT[\"1\"]");
		recibir("LISCAT[\"2\"]");
		recibir("LISCAT[\"3\"]");

		assertThrows(OutOfMemoryError.class, () -> servicio(comandos, senderSimulado()).revisarBuzon());

		verify(comandos, times(1)).procesar(any(), any());
		assertEquals(2, pop.correos.size());
	}

	@Test
	void ordenConErrorNoDetieneLasSiguientes() {
		ComandoService comandos = mock(ComandoService.class);
		when(comandos.procesar(eq("LISCAT[\"1\"]"), any())).thenThrow(new IllegalStateException("fallo"));
		when(comandos.procesar(eq("LISCAT[\"2\"]"), any())).thenReturn(OK);
		recibir("LISCAT[\"1\"]");
		recibir("LISCAT[\"2\"]");

		servicio(comandos, senderSimulado()).revisarBuzon();

		verify(comandos).procesar(eq("LISCAT[\"2\"]"), any());
		assertEquals(0, pop.correos.size());
	}

	@Test
	void cuerpoConPuntosNoAlteraElMensaje() {
		ComandoService comandos = mock(ComandoService.class);
		when(comandos.procesar(any(), any())).thenReturn(OK);
		recibir("LISCAT[\"1\"]", "uno\r\n.\r\n..dos\r\n.tres");
		recibir("LISCAT[\"2\"]");

		servicio(comandos, senderSimulado()).revisarBuzon();

		verify(comandos).procesar(eq("LISCAT[\"1\"]"), any());
		verify(comandos).procesar(eq("LISCAT[\"2\"]"), any());
		assertEquals(0, pop.correos.size());
	}

	// Todo lo que un usuario puede escribir en un parametro (sin comillas dobles, que separan parametros).
	private static final String LATINOS = "ñÑ áéíóú ÁÉÍÓÚ üÜ ¿? ¡! °ºª £¥ « » © ® ½ ² µ § ¶";
	private static final String SIMBOLOS = LATINOS + " €$%&<>'#@*()+-=/\\:;.,_{}[]|~^` – — “” ‘’ … ™ 漢字 😀";

	private static String comandoCon(String texto) {
		return "INSCAT[\"" + texto + "\",\"Frutas\"]";
	}

	private void comprobarQueLlega(String asuntoCrudo, String esperado) {
		CrudService crud = mock(CrudService.class);
		doReturn(1).when(crud).insertar(any(), any(), any());
		pop.correos.clear();
		recibir(asuntoCrudo);
		servicio(new ComandoService(crud, mock(PermisosService.class), mock(OperacionesService.class)), senderSimulado()).revisarBuzon();
		verify(crud).insertar(eq(Categoria.class), eq(List.of(esperado, "Frutas")), any());
	}

	@Test
	void todosLosSimbolosDelAsuntoLleganIntactosAlComando() throws Exception {
		String comando = comandoCon(SIMBOLOS);
		for (String codificacion : List.of("B", "Q")) {
			// encodeText divide en varias encoded-words plegadas si el asunto es largo.
			comprobarQueLlega(jakarta.mail.internet.MimeUtility.encodeText(comando, "UTF-8", codificacion), SIMBOLOS);
		}
		String latino = comandoCon(LATINOS);
		comprobarQueLlega(jakarta.mail.internet.MimeUtility.encodeText(latino, "ISO-8859-1", "Q"), LATINOS);
		comprobarQueLlega(jakarta.mail.internet.MimeUtility.encodeText(comandoCon(LATINOS + " €"), "windows-1252", "B"), LATINOS + " €");
		// Charset en minusculas, como lo mandan algunos clientes.
		comprobarQueLlega("=?utf-8?b?" + Base64.getEncoder().encodeToString(comando.getBytes(StandardCharsets.UTF_8)) + "?=", SIMBOLOS);
		// Sin codificar: solo ASCII.
		String ascii = "$%&<>'#@*()+-=/\\:;.,_{}[]|~^`!?";
		comprobarQueLlega(comandoCon(ascii), ascii);
	}

	@Test
	void asuntoUtf8SinCodificarLlegaIntacto() {
		// Servidores con SMTPUTF8 pueden entregar el asunto en UTF-8 crudo, sin encoded-words.
		for (String texto : List.of("Caña dulce", SIMBOLOS)) {
			String comando = comandoCon(texto);
			comprobarQueLlega(new String(comando.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1), texto);
		}
	}

	@Test
	void asuntoLatin1SinCodificarSeRechazaEnVezDeGuardarseMal() {
		// Bytes 8 bits que no son UTF-8 valido: no se puede saber el charset, no se ejecuta.
		CrudService crud = mock(CrudService.class);
		JavaMailSender sender = senderSimulado();
		recibir(comandoCon("Caña"));
		servicio(new ComandoService(crud, mock(PermisosService.class), mock(OperacionesService.class)), sender).revisarBuzon();
		verify(crud, never()).insertar(any(), any(), any());
		verify(sender).send(any(MimeMessage.class));
	}

	private MimeMessage responderPorSmtp(ComandoService comandos, String asunto, boolean ochoBits) throws Exception {
		byte[] datos;
		try (ServidorSmtp smtp = new ServidorSmtp(ochoBits)) {
			ClienteSmtp sender = new ClienteSmtp("localhost", smtp.servidor.getLocalPort(), "TECNOWEB", "", "");
			recibir(asunto);
			servicio(comandos, sender).revisarBuzon();
			datos = smtp.datos.get(10, TimeUnit.SECONDS);
		}
		for (String linea : new String(datos, StandardCharsets.ISO_8859_1).split("\r\n")) {
			assertTrue(linea.length() <= 998, "linea de " + linea.length() + " octetos");
		}
		for (byte octeto : datos) {
			assertTrue(octeto >= 0, "la respuesta debe ser ASCII puro para cualquier servidor");
		}
		return new MimeMessage((Session) null, new ByteArrayInputStream(datos));
	}

	@Test
	void listadoConSimbolosVuelveIntactoYEscapado() throws Exception {
		String valor = SIMBOLOS + " <b>negrita</b> & \"comillas\"";
		for (boolean ochoBits : List.of(false, true)) {
			CrudService crud = mock(CrudService.class);
			when(crud.listar(any(), any())).thenReturn(new com.example.correo.dto.Listado(List.of("nombre"), List.of(List.of(valor))));
			ComandoService comandos = new ComandoService(crud, mock(PermisosService.class), mock(OperacionesService.class));

			MimeMessage recibido = responderPorSmtp(comandos, "LISCAT[\"1\"]", ochoBits);

			assertEquals("Listando Categoria", recibido.getSubject());
			MimeMultipart partes = (MimeMultipart) recibido.getContent();
			String plano = (String) partes.getBodyPart(0).getContent();
			String html = (String) partes.getBodyPart(1).getContent();
			assertTrue(plano.contains("nombre=" + valor), plano);
			assertTrue(html.contains(SIMBOLOS.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")), html);
			assertTrue(html.contains("&lt;b&gt;negrita&lt;/b&gt; &amp; \"comillas\""), html);
			assertFalse(html.contains("<b>negrita"), "el HTML de los datos debe ir escapado");
		}
	}

	@Test
	void adjuntosViajanConSuTipoMimeYSusBytes() throws Exception {
		byte[] binario = new byte[1024];
		for (int i = 0; i < binario.length; i++) {
			binario[i] = (byte) i; // todos los valores de 0 a 255, incluidos \r \n y .
		}
		List<Adjunto> adjuntos = List.of(
				new Adjunto("ventas.pdf", "application/pdf", binario),
				new Adjunto("ventas.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", binario),
				new Adjunto("qr.png", "image/png", binario, "qrpago"));
		String asunto = "Reporte " + SIMBOLOS;
		for (boolean enLinea : List.of(true, false)) {
			List<Adjunto> enviados = enLinea ? adjuntos : adjuntos.subList(0, 2);
			ComandoService comandos = mock(ComandoService.class);
			when(comandos.procesar(any(), any())).thenReturn(new Respuesta(asunto, "texto " + SIMBOLOS,
					"<p>" + SIMBOLOS.replace("&", "&amp;").replace("<", "&lt;") + "</p><img src=\"cid:qrpago\">", enviados));

			MimeMessage recibido = responderPorSmtp(comandos, "INFVEN[\"\",\"\",\"\",\"TODOS\"]", false);

			assertEquals(asunto, recibido.getSubject());
			assertTrue(recibido.isMimeType(enLinea ? "multipart/related" : "multipart/mixed"), recibido.getContentType());
			MimeMultipart mezcla = (MimeMultipart) recibido.getContent();
			assertEquals(1 + enviados.size(), mezcla.getCount());
			MimeMultipart alternativas = (MimeMultipart) mezcla.getBodyPart(0).getContent();
			assertTrue(alternativas.getBodyPart(0).isMimeType("text/plain"));
			assertTrue(alternativas.getBodyPart(1).isMimeType("text/html"));
			assertEquals("texto " + SIMBOLOS, alternativas.getBodyPart(0).getContent());
			for (int i = 0; i < enviados.size(); i++) {
				BodyPart parte = mezcla.getBodyPart(i + 1);
				Adjunto esperado = enviados.get(i);
				assertTrue(parte.isMimeType(esperado.tipo()), parte.getContentType());
				assertEquals(esperado.nombre(), parte.getFileName());
				assertTrue(java.util.Arrays.equals(binario, parte.getInputStream().readAllBytes()), esperado.nombre() + " llego alterado");
				if (esperado.contentId() != null) {
					assertEquals("<qrpago>", ((jakarta.mail.internet.MimeBodyPart) parte).getContentID());
					assertEquals(jakarta.mail.Part.INLINE, parte.getDisposition());
				}
			}
		}
	}

	@Test
	void buzonApagadoNoRompeLaRevision() throws IOException {
		ComandoService comandos = mock(ComandoService.class);
		CorreoService servicio = servicio(comandos, senderSimulado());
		pop.close();

		servicio.revisarBuzon();

		verify(comandos, never()).procesar(any(), any());
	}

}

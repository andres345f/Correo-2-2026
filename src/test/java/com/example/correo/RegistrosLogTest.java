package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.example.correo.presentation.ClientePop3;
import com.example.correo.presentation.ComandoService;
import com.example.correo.presentation.CorreoService;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Cada correo deja una linea en access.log; los rechazos y excepciones quedan en error.log. */
@Transactional
class RegistrosLogTest extends BaseTemporalTest {

	private static final Path ACCESS = Path.of("target/test-logs/access.log");
	private static final Path ERROR = Path.of("target/test-logs/error.log");

	@Autowired
	private ComandoService comandos;

	/** Lo escrito en el archivo despues de la posicion dada: ignora corridas anteriores. */
	private static String desde(Path archivo, long posicion) throws IOException {
		if (!Files.exists(archivo)) {
			return "";
		}
		byte[] todo = Files.readAllBytes(archivo);
		return new String(todo, (int) posicion, todo.length - (int) posicion, StandardCharsets.UTF_8);
	}

	private static long tamano(Path archivo) throws IOException {
		return Files.exists(archivo) ? Files.size(archivo) : 0;
	}

	/** Buzon en memoria: entrega los asuntos en orden, todos enviados por el administrador. */
	private CorreoService servicio(String... asuntos) {
		Deque<byte[]> correos = new ArrayDeque<>();
		for (String asunto : asuntos) {
			correos.add(("From: <" + ADMIN + ">\r\nSubject: " + asunto + "\r\n\r\ncuerpo").getBytes(StandardCharsets.UTF_8));
		}
		when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
		return new CorreoService(comandos, mailSender, "localhost", "x", "x", "sistema@feder.test", "TECNOWEB", "", "") {
			@Override
			protected ClientePop3 buzon() {
				return new ClientePop3("localhost", 1, "x", "x", 1000) {
					@Override
					public byte[] extraerPrimero() {
						return correos.poll();
					}
				};
			}
		};
	}

	@Test
	void registraCadaCorreoYCadaErrorSinContrasenas() throws IOException {
		long inicioAccess = tamano(ACCESS);
		long inicioError = tamano(ERROR);

		// El duplicado va al final: deja la transaccion de la prueba abortada.
		servicio("LISCAT[\"*\"]", "LISXXX[\"*\"]", "MODUSU [\"999999\",\"\",\"ClaveSecreta123\"]",
				"INSROL[\"d\",\"ADMINISTRADOR\"]").revisarBuzon();

		String access = desde(ACCESS, inicioAccess);
		String error = desde(ERROR, inicioError);

		assertTrue(access.contains("de=" + ADMIN + " | comando=LISCAT | resultado=Listando"), access);
		assertTrue(access.contains("comando=LISXXX | resultado=Error"), access);
		assertTrue(access.contains("comando=MODUSU | resultado=Error"), access);
		assertTrue(access.contains("comando=INSROL | resultado=Error"), access);
		assertTrue(access.contains("respuesta enviada"), access);

		assertTrue(error.contains("asunto=LISXXX[\"*\"]") && error.contains("COMANDO_INEXISTENTE"), error);
		assertTrue(error.contains("asunto=MODUSU[***]"), error);
		// El duplicado deja la traza de la excepcion de la base de datos.
		assertTrue(error.contains("Excepcion al ejecutar") && error.contains("DataIntegrityViolationException"), error);

		assertFalse(access.contains("ClaveSecreta123") || error.contains("ClaveSecreta123"), "La contrasena no debe quedar en los logs");
		assertFalse(error.contains("asunto=LISCAT"), "Un comando correcto no va a error.log");
	}
}

package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** Reglas de dependencia comprobadas sin arrancar Spring ni conectarse a una base. */
class ArquitecturaCapasTest {

	private static final Path RAIZ = Path.of("src/main/java/com/example/correo");

	private void prohibir(String paquete, String expresion, String motivo) throws IOException {
		Pattern patron = Pattern.compile(expresion);
		try (var archivos = Files.walk(RAIZ.resolve(paquete))) {
			for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
				String codigo = Files.readString(archivo);
				assertFalse(patron.matcher(codigo).find(), archivo + ": " + motivo);
			}
		}
	}

	@Test
	void negocioNoDependeDePresentacionNiDeTransportes() throws IOException {
		prohibir("service", "com\\.example\\.correo\\.(presentation|infrastructure)|jakarta\\.mail|org\\.springframework\\.mail|org\\.apache\\.poi|com\\.lowagie|java\\.net\\.",
				"El negocio devuelve DTO y usa puertos para las integraciones; no genera correos ni archivos.");
	}

	@Test
	void accesoJpaSoloEnRepositorios() throws IOException {
		for (String paquete : List.of("service", "presentation", "dto", "infrastructure")) {
			prohibir(paquete, "\\b(EntityManager|EntityManagerFactory|JdbcTemplate|JpaRepository|PersistenceContext)\\b|\\.create(Query|NativeQuery)\\s*\\(",
					"Las consultas, sesiones y operaciones de persistencia pertenecen a repository.");
		}
	}

	@Test
	void repositoriosNoDependenDeCapasSuperiores() throws IOException {
		prohibir("repository", "com\\.example\\.correo\\.(service|presentation|infrastructure)",
				"Los repositorios no llaman servicios, no autorizan comandos y no generan respuestas.");
	}

	@Test
	void presentacionAccedeALosDatosMedianteNegocio() throws IOException {
		prohibir("presentation", "com\\.example\\.correo\\.repository|javax\\.sql|java\\.sql\\.Connection",
				"La entrada por correo debe pasar por los servicios de negocio.");
	}

	@Test
	void contratosDeResultadoSonIndependientes() throws IOException {
		prohibir("dto", "com\\.example\\.correo\\.(service|repository|presentation|infrastructure)|org\\.springframework|jakarta\\.",
				"Los DTO solo representan datos, sin dependencias de frameworks ni de las capas.");
	}
}

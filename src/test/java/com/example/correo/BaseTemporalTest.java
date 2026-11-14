package com.example.correo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.correo.infrastructure.PagoFacilGateway;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * PostgreSQL temporal con db.sql y restricciones.sql: estas pruebas no tocan la base
 * de .env. Sin tareas programadas ni envio real: tampoco tocan el buzon.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=none", "app.admin.correo=" + BaseTemporalTest.ADMIN,
		"app.mail.host=localhost", "app.mail.username=x", "app.mail.password=x", "app.mail.from=sistema@feder.test",
		"spring.mail.host=localhost", "app.mail.lectura-activa=false" })
abstract class BaseTemporalTest {

	static final String ADMIN = "admin@feder.test";

	private static EmbeddedPostgres postgres;

	@MockitoBean
	protected JavaMailSender mailSender;

	@MockitoBean
	protected PagoFacilGateway pagoFacil;

	@DynamicPropertySource
	static void baseTemporal(DynamicPropertyRegistry registro) throws IOException, SQLException {
		if (postgres == null) {
			postgres = EmbeddedPostgres.builder().start();
			try (Connection conexion = postgres.getPostgresDatabase().getConnection(); Statement sql = conexion.createStatement()) {
				sql.execute(Files.readString(Path.of("db.sql")));
				sql.execute(Files.readString(Path.of("restricciones.sql")));
			}
		}
		registro.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
		registro.add("spring.datasource.username", () -> "postgres");
		registro.add("spring.datasource.password", () -> "postgres");
	}

}

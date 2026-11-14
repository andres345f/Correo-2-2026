package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.correo.config.EstadoInicial;
import com.example.correo.presentation.ClienteSmtp;

@ExtendWith(OutputCaptureExtension.class)
class EstadoInicialTest {

	private DataSource baseConectada() throws SQLException {
		DatabaseMetaData datos = mock(DatabaseMetaData.class);
		when(datos.getURL()).thenReturn("jdbc:postgresql://servidor/base?password=secreta");
		when(datos.getDatabaseProductName()).thenReturn("PostgreSQL");
		when(datos.getDatabaseProductVersion()).thenReturn("14");
		Connection conexion = mock(Connection.class);
		when(conexion.getMetaData()).thenReturn(datos);
		DataSource base = mock(DataSource.class);
		when(base.getConnection()).thenReturn(conexion);
		return base;
	}

	@Test
	void informaLoConectadoSinMostrarCredenciales(CapturedOutput terminal) throws Exception {
		ClienteSmtp smtp = mock(ClienteSmtp.class);
		when(smtp.nombre()).thenReturn("Tecnoweb");
		when(smtp.direccion()).thenReturn("mail.test:25");

		new EstadoInicial(baseConectada(), smtp, "admin@x.y", "pop.test").informar();

		assertTrue(terminal.getOut().contains("Base de datos: conectada a jdbc:postgresql://servidor/base (PostgreSQL 14)"), terminal.getOut());
		assertTrue(terminal.getOut().contains("Correo saliente SMTP: conectado a mail.test:25"), terminal.getOut());
		assertTrue(terminal.getOut().contains("Administrador inicial: admin@x.y"), terminal.getOut());
		assertTrue(terminal.getOut().contains("S : Iniciando daemon POP3..."), terminal.getOut());
		assertFalse(terminal.getOut().contains("secreta"));
	}

	@Test
	void avisaLoQueFallaSinDetenerElArranque(CapturedOutput terminal) throws Exception {
		DataSource base = mock(DataSource.class);
		when(base.getConnection()).thenThrow(new SQLException("sin red"));
		ClienteSmtp smtp = mock(ClienteSmtp.class);
		doThrow(new IOException("rechazado")).when(smtp).probar();

		new EstadoInicial(base, smtp, "", "pop.test").informar();

		assertTrue(terminal.getOut().contains("Base de datos: sin conexion. sin red"), terminal.getOut());
		assertTrue(terminal.getOut().contains("Correo saliente SMTP: sin conexion"), terminal.getOut());
		assertTrue(terminal.getOut().contains("Agregue APP_ADMIN_CORREO"), terminal.getOut());
	}

}

package com.example.correo.config;

import com.example.correo.presentation.ClienteSmtp;
import com.example.correo.presentation.Terminal;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Al arrancar, informa en la terminal a que quedo conectado el sistema. */
@Component
@ConditionalOnProperty(name = "app.mail.lectura-activa", matchIfMissing = true)
public class EstadoInicial {

	private static final String LINEA = "=================================================";

	private final DataSource baseDatos;
	private final ClienteSmtp smtp;
	private final String administrador;
	private final String servidorPop;

	public EstadoInicial(DataSource baseDatos, ClienteSmtp smtp, @Value("${app.admin.correo:}") String administrador, @Value("#{@correoService.servidorPop()}") String servidorPop) {
		this.baseDatos = baseDatos;
		this.smtp = smtp;
		this.administrador = administrador;
		this.servidorPop = servidorPop;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void informar() {
		Terminal.linea(LINEA);
		Terminal.linea("  INICIANDO SISTEMA FEDER (EMAIL)");
		Terminal.linea(LINEA);
		informarBaseDatos();
		informarSmtp();
		if (administrador.isBlank()) {
			Terminal.servidor("Administrador inicial: sin configurar. Agregue APP_ADMIN_CORREO en .env y reinicie.");
		} else {
			Terminal.servidor("Administrador inicial: " + administrador);
		}
		Terminal.servidor("Iniciando con POP3: " + servidorPop);
		Terminal.servidor("Iniciando daemon POP3...");
	}

	private void informarBaseDatos() {
		try (Connection conexion = baseDatos.getConnection()) {
			DatabaseMetaData datos = conexion.getMetaData();
			// Sin los parametros de la URL: pueden traer credenciales.
			Terminal.servidor("Base de datos: conectada a " + datos.getURL().split("\\?", 2)[0] + " (" + datos.getDatabaseProductName() + " " + datos.getDatabaseProductVersion() + ")");
		} catch (SQLException e) {
			Terminal.servidor("Base de datos: sin conexion. " + e.getMessage());
		}
	}

	private void informarSmtp() {
		Terminal.servidor("Iniciando con SMTP: " + smtp.nombre());
		Terminal.servidor("[ClienteSMTP] Usando servidor SMTP " + smtp.direccion());
		try {
			smtp.probar();
			Terminal.servidor("Correo saliente SMTP: conectado a " + smtp.direccion());
		} catch (IOException e) {
			Terminal.servidor("Correo saliente SMTP: sin conexion a " + smtp.direccion() + ". " + e.getMessage());
		}
	}

}

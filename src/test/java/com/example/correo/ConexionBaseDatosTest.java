package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.JpaRepository;

// Solo lectura: ddl-auto=none evita que el test modifique el esquema de la BD.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class ConexionBaseDatosTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private List<JpaRepository<?, ?>> repositories;

	@Test
	void abreConexion() throws SQLException {
		try (Connection conexion = dataSource.getConnection()) {
			assertTrue(conexion.isValid(5));
			System.out.println("Conectado a: " + conexion.getMetaData().getURL()
					+ " (" + conexion.getMetaData().getDatabaseProductName()
					+ " " + conexion.getMetaData().getDatabaseProductVersion() + ")");
		}
	}

	@Test
	void todosLosRepositoriesConsultanSuTabla() {
		assertEquals(36, repositories.size());
		assertAll(repositories.stream().map(repository -> (Executable) () -> {
			String nombre = repository.getClass().getInterfaces()[0].getSimpleName();
			System.out.println(nombre + ": " + repository.count() + " filas");
		}));
	}

}

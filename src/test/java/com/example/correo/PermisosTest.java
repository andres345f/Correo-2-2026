package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.correo.presentation.ComandoService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermisosTest extends BaseTemporalTest {

	private static final String ERROR_ACCESO = "Error 550 5.7.1";
	private static final String ERROR_RESTRICCION = "Error 554 5.0.0";
	private static final Pattern ID = Pattern.compile("creado con id (\\d+)");

	@Autowired
	private ComandoService comandos;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeAll
	void datosBase() {
		cuentaConRol("cliente.p@feder.test", "CLIENTE");
		cuentaConRol("personal.p@feder.test", "PERSONAL");
	}

	private ComandoService.Respuesta enviar(String asunto, String remitente) {
		return comandos.procesar(asunto, remitente);
	}

	private String crear(String asunto, String remitente) {
		ComandoService.Respuesta respuesta = enviar(asunto, remitente);
		Matcher id = ID.matcher(respuesta.cuerpo());
		assertTrue(id.find(), asunto + " -> " + respuesta.asunto() + ": " + respuesta.cuerpo());
		return id.group(1);
	}

	private String cuentaConRol(String correo, String rol) {
		String usuario = crear("INSUSU[\"" + correo + "\",\"clave\"]", ADMIN);
		Integer idRol = jdbc.queryForObject("select rolid from rol where nombre = ?", Integer.class, rol);
		crear("INSURO[\"" + usuario + "\",\"" + idRol + "\"]", ADMIN);
		return usuario;
	}

	@Test
	void helpNoRequiereCuentaYLoDemasSi() {
		assertEquals(ComandoService.ASUNTO_AYUDA, enviar("HELP", "nadie@x.y").asunto());
		assertEquals(ERROR_ACCESO, enviar("LISCAT[\"*\"]", "nadie@x.y").asunto());
		assertEquals(ERROR_ACCESO, enviar("LISCAT[\"*\"]", null).asunto());
		assertEquals(ERROR_ACCESO, enviar("BUSPRO[\"\",\"\",\"\",\"\"]", "nadie@x.y").asunto());
	}

	@Test
	void cadaRolVeSoloLoSuyo() {
		assertEquals("Listando Categoria", enviar("LISCAT[\"*\"]", "CLIENTE.P@feder.test").asunto());
		assertEquals(ERROR_ACCESO, enviar("INSCAT[\"x\",\"Prohibida\"]", "cliente.p@feder.test").asunto());
		assertEquals(ERROR_ACCESO, enviar("LISVEN[\"*\"]", "cliente.p@feder.test").asunto());
		assertEquals("Listando Venta", enviar("LISVEN[\"*\"]", "personal.p@feder.test").asunto());
		assertEquals(ERROR_ACCESO, enviar("LISUSU[\"*\"]", "personal.p@feder.test").asunto());
		assertEquals("Listando Usuario", enviar("LISUSU[\"*\"]", ADMIN).asunto());
	}

	@Test
	void cuentaDesactivadaPierdeElAcceso() {
		String usuario = cuentaConRol("baja.p@feder.test", "PERSONAL");
		assertEquals("Listando Venta", enviar("LISVEN[\"*\"]", "baja.p@feder.test").asunto());
		assertFalse(enviar("DELUSU[\"" + usuario + "\"]", ADMIN).esError());
		assertEquals(ERROR_ACCESO, enviar("LISVEN[\"*\"]", "baja.p@feder.test").asunto());
	}

	@Test
	void laBaseRechazaDatosQueContradicenElModelo() {
		String categoria = crear("INSCAT[\"d\",\"Base\"]", ADMIN);
		crear("INSUSU[\"ADMIN2@x.y\",\"a\"]", ADMIN);
		assertEquals(ERROR_RESTRICCION, enviar("INSUSU[\"admin2@x.y\",\"a\"]", ADMIN).asunto());
		assertEquals(ERROR_RESTRICCION, enviar("INSPRO[\"\",\"\",\"true\",\"Malo\",\"-1\",\"\",\"0\",\"\",\"" + categoria + "\"]", ADMIN).asunto());
	}

	@Test
	void cadaCambioQuedaEnLaBitacoraSinLaContrasena() {
		String usuario = cuentaConRol("audita.p@feder.test", "ADMINISTRADOR");
		String categoria = crear("INSCAT[\"Antes\",\"Auditada\"]", "audita.p@feder.test");
		assertFalse(enviar("MODCAT[\"" + categoria + "\",\"Despues\",\"\"]", "audita.p@feder.test").esError());
		assertEquals("Antes", jdbc.queryForObject("""
				select c.valor_anterior from cambio_bitacora c join bitacora b using (bitacoraid)
				where b.accion = 'MOD' and b.entidad = 'Categoria' and b.entidad_id = ?::int and b.usuarioid = ?::int and c.ruta_campo = 'descripcion'""", String.class, categoria, usuario));
		crear("INSUSU[\"nueva.p@feder.test\",\"secreta\"]", "audita.p@feder.test");
		assertEquals(0, jdbc.queryForObject("select count(*) from cambio_bitacora where ruta_campo = 'passwordHash' or valor_nuevo like '%secreta%'", Integer.class));
		assertEquals(ERROR_ACCESO, enviar("INSBIT[\"a\",\"b\",\"1\",\"" + usuario + "\"]", "audita.p@feder.test").asunto());
	}

}

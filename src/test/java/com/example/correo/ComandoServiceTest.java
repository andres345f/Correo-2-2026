package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Transactional;

import com.example.correo.entity.Ubicacion;
import com.example.correo.entity.Usuario;
import com.example.correo.service.CodigoError;
import com.example.correo.presentation.ComandoService;
import com.example.correo.presentation.ComandoService.Respuesta;
import com.example.correo.service.CrudService;
import com.example.correo.service.OperacionesService;
import com.example.correo.service.PermisosService;

import jakarta.persistence.EntityManager;

// @Transactional deshace todo al terminar cada test.
@Transactional
class ComandoServiceTest extends BaseTemporalTest {

	@Autowired
	private ComandoService comandos;

	@Autowired
	private EntityManager em;

	private Respuesta enviar(String comando) {
		return comandos.procesar(comando, ADMIN);
	}

	private String crear(String comando) {
		Respuesta respuesta = enviar(comando);
		assertEquals("Exito", respuesta.asunto(), respuesta.cuerpo());
		return respuesta.cuerpo().replaceAll("\\D", "");
	}

	private void assertError(CodigoError esperado, String comando) {
		Respuesta respuesta = enviar(comando);
		assertEquals("Error " + esperado.codigo(), respuesta.asunto(), respuesta.cuerpo());
		assertTrue(respuesta.cuerpo().contains(esperado.name()), respuesta.cuerpo());
	}

	@Test
	void aceptaEspaciosMinusculasYComillasEscapadas() {
		String id = crear("  inscat  [  \"Jugo \\\"natural\\\" 100%\"  ,   \"Bebidas\"  ]  ");
		String fila = enviar("LISCAT [ \"" + id + "\" ]").cuerpo();
		assertTrue(fila.contains("descripcion=Jugo \"natural\" 100%") && fila.contains("nombre=Bebidas"), fila);

		// \\ es una barra; una barra sola que no escapa nada se conserva tal cual.
		String otro = crear("INSCAT[\"C:\\\\ruta y a\\b\",\"Barras\"]");
		assertTrue(enviar("LISCAT[\"" + otro + "\"]").cuerpo().contains("descripcion=C:\\ruta y a\\b"));

		// Espacio duro (U+00A0) de algunos clientes de correo y caracteres UTF-8.
		String utf = crear("INSCAT\u00A0[\"Café, maní y ñandú\",\"Señal\"]");
		String filaUtf = enviar("LISCAT[\"" + utf + "\"]").cuerpo();
		assertTrue(filaUtf.contains("descripcion=Café, maní y ñandú") && filaUtf.contains("nombre=Señal"), filaUtf);
	}

	@Test
	void rechazaComillasMalCerradas() {
		assertError(CodigoError.PARAMETROS_MAL_ESCRITOS, "INSCAT[\"sin cerrar,\"Frutas\"]");
		assertError(CodigoError.PARAMETROS_MAL_ESCRITOS, "INSCAT[\"a\" \"b\"]");
		assertError(CodigoError.PARAMETROS_MAL_ESCRITOS, "INSCAT[\"a\",]");
		assertError(CodigoError.PARAMETROS_MAL_ESCRITOS, "INSCAT[\"termina en \\\"]");
		assertError(CodigoError.PARAMETROS_MAL_ESCRITOS, "INSCAT[\"a\",\"b\"x]");
	}

	@Test
	void cicloCompletoConBajaLogica() {
		String id = crear("INSCAT[\"Fruta fresca\",\"Frutas\"]");
		assertTrue(enviar("LISCAT[\"" + id + "\"]").cuerpo().contains("nombre=Frutas"));

		assertEquals("Exito", enviar("MODCAT[\"" + id + "\",\"\",\"Citricos\"]").asunto());
		em.flush();
		String fila = enviar("LISCAT[\"" + id + "\"]").cuerpo();
		assertTrue(fila.contains("nombre=Citricos") && fila.contains("descripcion=Fruta fresca"), fila);

		assertEquals("Exito", enviar("DELCAT[\"" + id + "\"]").asunto());
		em.flush();
		assertTrue(enviar("LISCAT[\"" + id + "\"]").cuerpo().contains("activo=false"));
	}

	@Test
	void borradoFisicoSinColumnaActivo() {
		String id = crear("INSPER[\"Perez\",\"4715292\",\"Juan Carlos\",\"71055123\",\"\"]");
		assertEquals("Exito", enviar("DELPER[\"" + id + "\"]").asunto());
		em.flush();
		assertError(CodigoError.REGISTRO_NO_ENCONTRADO, "LISPER[\"" + id + "\"]");
	}

	@Test
	void traduceClaveForaneaAlBorrar() {
		String usuario = crear("INSUSU[\"cliente@b.bo\",\"clave\"]");
		String persona = crear("INSPER[\"Perez\",\"4715292\",\"Juan\",\"71055123\",\"" + usuario + "\"]");
		crear("INSCLI[\"Calle 1\",\"4715292\",\"Perez\",\"" + usuario + "\",\"" + persona + "\"]");
		// Cada correo corre en su propia transaccion: sin esto Hibernate ve al cliente en memoria.
		em.flush();
		em.clear();
		Respuesta respuesta = enviar("DELPER[\"" + persona + "\"]");
		assertEquals("Error " + CodigoError.RESTRICCION_BD.codigo(), respuesta.asunto());
		assertTrue(respuesta.cuerpo().contains("relacionado"), respuesta.cuerpo());
		assertFalse(respuesta.cuerpo().contains("violates") || respuesta.cuerpo().contains("viola"));
	}

	@Test
	void traduceFallosDeInfraestructura() {
		CrudService crud = mock(CrudService.class);
		ComandoService servicio = new ComandoService(crud, mock(PermisosService.class), mock(OperacionesService.class));

		when(crud.listar(any(), any())).thenThrow(new CannotCreateTransactionException("sin conexion"));
		assertEquals("Error " + CodigoError.BD_NO_DISPONIBLE.codigo(), servicio.procesar("LISROL[\"*\"]", ADMIN).asunto());

		reset(crud);
		when(crud.listar(any(), any())).thenThrow(
				new DataIntegrityViolationException("x", new SQLException("duplicada", "23505")));
		assertTrue(servicio.procesar("LISROL[\"*\"]", ADMIN).cuerpo().contains("Ya existe"));

		reset(crud);
		when(crud.listar(any(), any())).thenThrow(new IllegalStateException("detalle interno"));
		Respuesta inesperado = servicio.procesar("LISROL[\"*\"]", ADMIN);
		assertEquals("Error " + CodigoError.ERROR_INTERNO.codigo(), inesperado.asunto());
		assertFalse(inesperado.cuerpo().contains("detalle interno"));
	}

	@Test
	void guardaEnumRelacionYPunto() {
		String municipio = crear("INSMUN[\"Santa Cruz\",\"Warnes\"]");
		String id = crear("INSUBI[\"Comunidad\",\"Calle 1\",\"-17.5\",\"-63.1\",\"Parcela 1\",\"Ref\",\"PARCELA\",\""
				+ municipio + "\"]");
		em.flush();
		em.clear();
		Ubicacion ubicacion = em.find(Ubicacion.class, Integer.valueOf(id));
		assertEquals("(-63.1,-17.5)", ubicacion.getGeom());
		assertEquals(Integer.valueOf(municipio), ubicacion.getMunicipio().getId());
	}

	@Test
	void contrasenaConHashYOcultaEnListado() {
		String id = crear("INSUSU[\"a@b.bo\",\"secreta\"]");
		em.flush();
		assertTrue(em.find(Usuario.class, Integer.valueOf(id)).getPasswordHash().startsWith("$2"));
		assertFalse(enviar("LISUSU[\"" + id + "\"]").cuerpo().contains("$2"));
	}

	@Test
	void listaTodasLasTablas() {
		ComandoService.TABLAS.keySet().forEach(codigo -> {
			Respuesta respuesta = enviar("LIS" + codigo + "[\"*\"]");
			assertTrue(respuesta.asunto().startsWith("Listando"), codigo + ": " + respuesta.cuerpo());
		});
	}

	@Test
	void informaErrores() {
		assertError(CodigoError.FORMATO_INVALIDO, "hola");
		assertError(CodigoError.FORMATO_INVALIDO, null);
		assertError(CodigoError.FORMATO_INVALIDO, "INSROL");
		assertError(CodigoError.COMANDO_INEXISTENTE, "LISXXX[\"*\"]");
		assertError(CodigoError.COMANDO_INEXISTENTE, "BORPER[\"1\"]");
		assertError(CodigoError.PARAMETROS_MAL_ESCRITOS, "INSCAT[sin comillas]");
		assertError(CodigoError.PARAMETROS_MAL_ESCRITOS, "INSCAT[]");

		assertError(CodigoError.CANTIDAD_PARAMETROS, "LISCAT[\"1\",\"2\"]");
		Respuesta cantidad = enviar("INSCAT[\"solo uno\"]");
		assertEquals("Error " + CodigoError.CANTIDAD_PARAMETROS.codigo(), cantidad.asunto());
		assertTrue(cantidad.cuerpo().contains("descripcion, nombre"), cantidad.cuerpo());

		assertError(CodigoError.VALOR_INVALIDO, "INSCAT[\"\",\"\"]");
		assertError(CodigoError.VALOR_INVALIDO, "INSPRO[\"\",\"\",\"\",\"\",\"abc\",\"\",\"\",\"\",\"\"]");
		assertError(CodigoError.VALOR_INVALIDO, "INSPRO[\"\",\"\",\"\",\"\",\"1.234\",\"\",\"\",\"\",\"\"]");
		assertError(CodigoError.VALOR_INVALIDO, "INSROL[\"d\",\"" + "x".repeat(151) + "\"]");
		assertTrue(enviar("INSUBI[\"\",\"\",\"\",\"\",\"\",\"\",\"X\",\"\"]").cuerpo().contains("PARCELA"));

		assertError(CodigoError.ID_INVALIDO, "LISPER[\"abc\"]");
		assertError(CodigoError.ID_INVALIDO, "DELPER[\"*\"]");
		assertError(CodigoError.REGISTRO_NO_ENCONTRADO, "MODCAT[\"999999\",\"a\",\"b\"]");
		assertTrue(enviar("INSPRO[\"\",\"\",\"\",\"n\",\"1\",\"\",\"\",\"\",\"999999\"]").cuerpo().contains("No existe Categoria"));
	}

}

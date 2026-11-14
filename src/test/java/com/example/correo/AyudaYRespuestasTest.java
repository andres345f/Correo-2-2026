package com.example.correo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.correo.service.CodigoError;
import com.example.correo.service.ComandoInvalidoException;
import com.example.correo.presentation.ComandoService;
import com.example.correo.presentation.ComandoService.Respuesta;
import com.example.correo.service.CrudService;
import com.example.correo.service.OperacionesService;
import com.example.correo.service.PermisosService;

import jakarta.persistence.Entity;

// No usa Spring ni la base de datos: CrudService es un mock que conserva su logica de reflexion.
class AyudaYRespuestasTest {

	private static final Pattern EJEMPLO = Pattern.compile("<code\\b[^>]*>(INS|MOD)([A-Z]{3})\\[(.*?)]</code>");
	private static final Pattern DATO = Pattern.compile("\"([^\"]*)\"");

	private final CrudService crud = mock(CrudService.class, CALLS_REAL_METHODS);
	private final ComandoService comandos = new ComandoService(crud, mock(PermisosService.class), mock(OperacionesService.class));

	@Test
	void helpFuncionaSinImportarMayusculas() {
		for (String asunto : List.of("HELP", "help", "  Help  ")) {
			Respuesta respuesta = comandos.procesar(asunto, "a@b.c");
			assertFalse(respuesta.esError(), asunto);
			assertTrue(respuesta.html().contains("Cómo enviar una orden"), asunto);
			assertFalse(respuesta.html().contains("<style"));
			assertTrue(respuesta.html().contains("<body style="));
			assertTrue(respuesta.cuerpo().startsWith("Guía de comandos"));
			assertFalse(respuesta.cuerpo().contains("font-family"));
		}
	}

	@Test
	void guiaIncluyeCadaTablaUnaVezConSusComandos() {
		String html = comandos.procesar("HELP", "a@b.c").html();
		ComandoService.TABLAS.keySet().forEach(codigo -> {
			assertEquals(2, html.split("<h3\\b[^>]*>" + codigo + " - ", -1).length, "seccion de " + codigo);
			for (String comando : List.of("LIS" + codigo + "[\"*\"]", "LIS" + codigo + "[\"1\"]")) {
				assertTrue(html.contains(comando), comando);
			}
			boolean escribible = !PermisosService.SOLO_LECTURA.contains(codigo);
			for (String comando : List.of("INS" + codigo + "[", "MOD" + codigo + "[\"1\"", "DEL" + codigo + "[\"1\"]")) {
				assertEquals(escribible, html.contains(comando), comando);
			}
		});
		assertTrue(html.contains("PARCELA"), "debe listar los valores de los enumerados");
	}

	@Test
	void ejemplosDeLaGuiaSonValidos() {
		Matcher ejemplos = EJEMPLO.matcher(comandos.procesar("HELP", "a@b.c").html());
		int revisados = 0;
		while (ejemplos.find()) {
			List<String> datos = DATO.matcher(ejemplos.group(3)).results().map(m -> m.group(1)).toList();
			Class<?> tabla = ComandoService.TABLAS.get(ejemplos.group(2));
			List<Field> campos = crud.parametros(tabla);
			boolean esModificacion = ejemplos.group(1).equals("MOD");
			List<String> valores = esModificacion ? datos.subList(1, datos.size()) : datos;
			assertEquals(campos.size(), valores.size(), ejemplos.group());

			for (int i = 0; i < campos.size(); i++) {
				boolean relacion = campos.get(i).getType().isAnnotationPresent(Entity.class);
				if (!relacion && !valores.get(i).isEmpty()) {
					// Lanza excepcion si el valor de ejemplo no cumple el tipo o la longitud del campo.
					ReflectionTestUtils.invokeMethod(crud, "convertir", campos.get(i), valores.get(i));
				}
			}
			revisados++;
		}
		assertTrue(revisados >= 28, "se revisaron " + revisados);
	}

	@Test
	void guiaNoSeRecortaEnGmail() {
		assertTrue(comandos.procesar("HELP", "a@b.c").html().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 102_000);
	}

	@Test
	void listadoEnTextoYTablaHtmlEscapada() {
		doReturn(new com.example.correo.dto.Listado(List.of("id", "nombre"), List.of(List.of("1", "<b>Fruta</b>"))))
				.when(crud).listar(any(), any());
		Respuesta respuesta = comandos.procesar("LISCAT[\"1\"]", "a@b.c");
		assertEquals("id=1 | nombre=<b>Fruta</b>", respuesta.cuerpo());
		assertTrue(respuesta.html().contains("<td>&lt;b&gt;Fruta&lt;/b&gt;</td>"), respuesta.html());
	}

	@Test
	void errorIndicaHelpYEscapaElValorRecibido() {
		doThrow(new ComandoInvalidoException(CodigoError.VALOR_INVALIDO, "<script>x</script>"))
				.when(crud).listar(any(), any());
		Respuesta respuesta = comandos.procesar("LISCAT[\"1\"]", "a@b.c");
		assertTrue(respuesta.esError());
		assertTrue(respuesta.cuerpo().contains("HELP"));
		assertFalse(respuesta.html().contains("<script>"));
	}

}

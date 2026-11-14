package com.example.correo.presentation;

import com.example.correo.service.CrudService;
import com.example.correo.service.OperacionesService;
import com.example.correo.service.PermisosService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Guia que se envia con el comando HELP. Los datos de cada tabla salen de sus
 * entidades, asi la guia no queda desactualizada si cambia una tabla.
 */
final class AyudaComandos {

	private record Grupo(String titulo, List<String> codigos) {
	}

	private static final List<Grupo> GRUPOS = List.of(
			new Grupo("CU1. Usuarios", List.of("USU", "ROL", "URO", "PER", "EMP", "ENC", "BIT", "CAB")),
			new Grupo("CU2. Productores y comunidades agrícolas", List.of("ASO", "PTR", "MUN", "UBI")),
			new Grupo("CU3. Producción agrícola, lotes y trazabilidad", List.of("PRD", "LOA", "OFE")),
			new Grupo("CU4. Catálogo, inventarios y alertas",
					List.of("CAT", "PRO", "INV", "LOF", "MOI", "MOF", "ALE", "RES", "COM", "DCO")),
			new Grupo("CU5. Comercio electrónico, carrito y ventas", List.of("CLI", "CAR", "VEN", "DVE", "HIS")),
			new Grupo("CU6. Pedidos, pagos, entregas y notificaciones", List.of("PAG", "FAC", "ENT", "DEV", "NOT")),
			new Grupo("CU7. Indicadores financieros", List.of("IND")),
			new Grupo("CU8. Reportes y estadísticas", List.of()));

	private static final String INTRODUCCION = """
			<p>Este sistema se maneja enviando correos. Cada correo es una orden y el sistema le responde con el resultado.
			Puede pedir esta guía cuando quiera enviando un correo con el asunto <code>HELP</code>.</p>

			<h2>1. Cómo enviar una orden</h2>
			<ol>
			<li>Escriba un <b>correo nuevo</b> a la misma dirección que le envió esta guía. No use «Responder», porque el asunto empezaría con «Re:» y la orden no se reconocería.</li>
			<li>Escriba la orden en el <b>Asunto</b>. El cuerpo del correo no se lee: puede dejarlo vacío.</li>
			<li>Envíe el correo y espere la respuesta. El buzón se revisa cada 5 segundos.</li>
			</ol>
			<p>Toda orden tiene tres partes pegadas, sin espacios entre ellas:</p>
			<p><code>ACCIÓN</code> (3 letras) + <code>TABLA</code> (3 letras) + <code>["dato","dato"]</code> &nbsp; Ejemplo: <code>INSCAT["Productos frescos","Frutas"]</code></p>

			<h2>2. Las cuatro acciones</h2>
			<table border="1" cellpadding="6" cellspacing="0">
			<tr><th>Acción</th><th>Para qué sirve</th><th>Qué datos lleva</th><th>Ejemplo</th></tr>
			<tr><td><code>LIS</code></td><td>Ver registros</td><td><code>"*"</code> para ver todos, o el número (ID) de uno</td><td><code>LISCAT["*"]</code><br><code>LISCAT["1"]</code></td></tr>
			<tr><td><code>INS</code></td><td>Crear un registro</td><td>Todos los datos de la tabla, en orden</td><td><code>INSCAT["Productos frescos","Frutas"]</code></td></tr>
			<tr><td><code>MOD</code></td><td>Cambiar un registro</td><td>Primero el ID y luego todos los datos, en orden. Lo que deje vacío no cambia</td><td><code>MODCAT["1","","Citricos"]</code></td></tr>
			<tr><td><code>DEL</code></td><td>Quitar un registro</td><td>El ID</td><td><code>DELCAT["1"]</code></td></tr>
			</table>

			<h2>3. Reglas para escribir los datos</h2>
			<ul>
			<li>La acción y la tabla van en <b>MAYÚSCULAS</b>.</li>
			<li>Cada dato va entre <b>comillas dobles rectas</b> (<code>"</code>) y se separa con una coma. Si su teléfono cambia las comillas por otras curvas, corríjalas. Un dato no puede contener comillas.</li>
			<li>Hay que respetar el <b>orden</b> y la <b>cantidad</b> de datos que indica cada tabla.</li>
			<li>Si no tiene un dato, escríbalo vacío (<code>""</code>), pero no quite su lugar. Al crear queda en blanco; al modificar conserva el valor anterior.</li>
			<li>El <b>ID</b> es el número que identifica a un registro: el sistema lo asigna y lo informa al crearlo. También aparece al listar con <code>LIS</code>.</li>
			<li>Si un dato pide el ID de otra tabla, ese registro <b>debe existir antes</b>. Por ejemplo, cree primero la categoría y después el producto.</li>
			<li>El ID, las fechas de registro y el estado activo los llena el sistema: no se escriben.</li>
			<li>Las tablas con saldos, estados o historia solo se consultan con <code>LIS</code>: cambian con los <b>comandos de negocio</b> que aparecen en cada caso de uso. Tienen la misma forma: <code>CODIGO["dato","dato"]</code>.</li>
			<li>Si un comando pide <code>claveOperacion</code>, invente un texto único y reutilícelo solo para reintentar ese mismo hecho.</li>
			<li>Cada orden se autoriza según los <b>roles</b> de la cuenta que tiene su dirección de correo. Sin cuenta activa con roles, solo funciona <code>HELP</code>.</li>
			<li>Si algo está mal, la respuesta tendrá un asunto que empieza con <b>Error</b> y explicará qué corregir.</li>
			</ul>

			<h2>4. Ejemplo paso a paso</h2>
			<p>Envíe un correo por cada paso. Los ID de su respuesta pueden ser otros: use los que le informe el sistema.</p>
			<table border="1" cellpadding="6" cellspacing="0">
			<tr><th>Paso</th><th>Qué se quiere</th><th>Asunto del correo</th></tr>
			<tr><td>1</td><td>Crear una categoría. La respuesta informa su ID (aquí, 1)</td><td><code>INSCAT["Productos frescos","Frutas"]</code></td></tr>
			<tr><td>2</td><td>Ver todas las categorías</td><td><code>LISCAT["*"]</code></td></tr>
			<tr><td>3</td><td>Crear un producto de la categoría 1 (el último dato)</td><td><code>INSPRO["Naranja dulce","","true","Naranja","12.50","Caja","5","kg","1"]</code></td></tr>
			<tr><td>4</td><td>Cambiar solo el nombre de la categoría 1</td><td><code>MODCAT["1","","Citricos"]</code></td></tr>
			<tr><td>5</td><td>Quitar la categoría 1</td><td><code>DELCAT["1"]</code></td></tr>
			</table>

			<h2>5. Todas las tablas</h2>
			<p>Los comandos de cada tabla ya tienen el formato correcto: cópielos y cambie los valores de ejemplo por los suyos.</p>
			""";

	private final CrudService crud;
	private final OperacionesService operaciones;

	AyudaComandos(CrudService crud, OperacionesService operaciones) {
		this.crud = crud;
		this.operaciones = operaciones;
	}

	String html() {
		StringBuilder contenido = new StringBuilder(INTRODUCCION);
		for (Grupo grupo : GRUPOS) {
			contenido.append("<h2>").append(HtmlCorreo.escapar(grupo.titulo())).append("</h2>");
			contenido.append(comandosDeNegocio(grupo.titulo().substring(0, 3)));
			grupo.codigos().forEach(codigo -> contenido.append(seccion(codigo)));
		}
		return HtmlCorreo.pagina("Guía de comandos", contenido.toString());
	}

	private String comandosDeNegocio(String caso) {
		List<OperacionesService.Operacion> delCaso = operaciones.todas().stream().filter(o -> o.grupo().equals(caso)).toList();
		if (delCaso.isEmpty()) {
			return "";
		}
		List<List<String>> filas = delCaso.stream().map(o -> List.of(
				HtmlCorreo.codigo(o.codigo() + o.parametros().stream().map(d -> "\"" + d + "\"").collect(Collectors.joining(",", "[", "]"))),
				HtmlCorreo.escapar(o.descripcion()),
				HtmlCorreo.escapar(o.roles().isEmpty() ? "ADMINISTRADOR" : String.join(", ", new java.util.TreeSet<>(o.roles()))))).toList();
		return "<h3>Comandos de negocio</h3>" + HtmlCorreo.tablaConHtml(List.of("Comando", "Qué hace", "Roles"), filas);
	}

	private String seccion(String codigo) {
		Class<?> tabla = ComandoService.TABLAS.get(codigo);
		if (PermisosService.SOLO_LECTURA.contains(codigo)) {
			return "<h3>" + codigo + " - " + tabla.getSimpleName() + "</h3><p>Solo consulta: " + comando("LIS", codigo, List.of("*")) + " o " + comando("LIS", codigo, List.of("1")) + "</p>";
		}
		List<Field> campos = crud.parametros(tabla);

		List<List<String>> filas = new ArrayList<>();
		for (int i = 0; i < campos.size(); i++) {
			filas.add(List.of(String.valueOf(i + 1), HtmlCorreo.codigo(campos.get(i).getName()), comoEscribir(campos.get(i))));
		}

		List<String> ejemplos = campos.stream().map(this::ejemplo).toList();
		List<String> modificacion = new ArrayList<>(List.of("1", ejemplos.get(0)));
		campos.stream().skip(1).forEach(c -> modificacion.add(""));
		boolean bajaLogica = Arrays.stream(tabla.getDeclaredFields()).anyMatch(c -> c.getName().equals("activo"));

		return "<h3>" + codigo + " - " + tabla.getSimpleName() + "</h3>"
				+ HtmlCorreo.tablaConHtml(List.of("Orden", "Dato", "Cómo escribirlo"), filas)
				+ "<p>Ver todos: " + comando("LIS", codigo, List.of("*"))
				+ "<br>Ver el registro 1: " + comando("LIS", codigo, List.of("1"))
				+ "<br>Crear: " + comando("INS", codigo, ejemplos)
				+ "<br>Cambiar solo " + campos.get(0).getName() + " del registro 1: " + comando("MOD", codigo, modificacion)
				+ "<br>" + (bajaLogica ? "Desactivar" : "Eliminar") + " el registro 1: " + comando("DEL", codigo, List.of("1"))
				+ "</p>";
	}

	private String comando(String accion, String codigo, List<String> datos) {
		return HtmlCorreo.codigo(accion + codigo + datos.stream().map(d -> "\"" + d + "\"").collect(Collectors.joining(",", "[", "]")));
	}

	private String comoEscribir(Field campo) {
		Class<?> tipo = campo.getType();
		if ("passwordHash".equals(campo.getName())) {
			return "La contraseña, hasta 72 caracteres. Se guarda cifrada";
		}
		if (tipo == String.class) {
			Column columna = campo.getAnnotation(Column.class);
			return columna != null && columna.columnDefinition().isEmpty() ? "Texto, hasta " + columna.length() + " caracteres" : "Texto";
		}
		if (tipo == Integer.class) {
			return "Número entero. Ejemplo: 10";
		}
		if (tipo == BigDecimal.class) {
			Column columna = campo.getAnnotation(Column.class);
			return "Número con punto decimal. Ejemplo: 12.50" + (columna != null && columna.precision() > 0
					? " (hasta " + (columna.precision() - columna.scale()) + " enteros y " + columna.scale() + " decimales)"
					: "");
		}
		if (tipo == Boolean.class) {
			return HtmlCorreo.codigo("true") + " (sí) o " + HtmlCorreo.codigo("false") + " (no)";
		}
		if (tipo == LocalDate.class) {
			return "Fecha año-mes-día. Ejemplo: 2026-11-08";
		}
		if (tipo == LocalDateTime.class) {
			return "Fecha y hora. Ejemplo: 2026-11-08 14:30";
		}
		if (tipo.isEnum()) {
			return "Uno de estos valores: " + Arrays.stream(tipo.getEnumConstants())
					.map(v -> HtmlCorreo.codigo(v.toString())).collect(Collectors.joining(", "));
		}
		String relacion = codigoDe(tipo);
		return "ID de un registro de " + tipo.getSimpleName() + ". Búsquelo con " + HtmlCorreo.codigo("LIS" + relacion + "[\"*\"]");
	}

	private String ejemplo(Field campo) {
		Class<?> tipo = campo.getType();
		if (tipo == Integer.class) {
			return "10";
		}
		if (tipo == BigDecimal.class) {
			return "12.50";
		}
		if (tipo == Boolean.class) {
			return "true";
		}
		if (tipo == LocalDate.class) {
			return "2026-11-08";
		}
		if (tipo == LocalDateTime.class) {
			return "2026-11-08 14:30";
		}
		if (tipo.isEnum()) {
			return tipo.getEnumConstants()[0].toString();
		}
		return tipo.isAnnotationPresent(Entity.class) ? "1" : "texto";
	}

	private String codigoDe(Class<?> tabla) {
		return ComandoService.TABLAS.entrySet().stream()
				.filter(entrada -> entrada.getValue() == tabla)
				.map(java.util.Map.Entry::getKey)
				.findFirst()
				.orElseThrow();
	}

}

package com.example.correo.presentation;

import com.example.correo.dto.Listado;
import com.example.correo.entity.Usuario;
import com.example.correo.service.Actor;
import com.example.correo.service.CatalogoTablas;
import com.example.correo.service.CodigoError;
import com.example.correo.service.ComandoInvalidoException;
import com.example.correo.service.CrudService;
import com.example.correo.service.OperacionesService;
import com.example.correo.service.PermisosService;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

/** Interpreta el asunto del correo, ejecuta el comando y arma la respuesta. */
@Service
public class ComandoService {

	/** contentId permite mostrar el adjunto dentro del HTML con src="cid:...". */
	public record Adjunto(String nombre, String tipo, byte[] datos, String contentId) {
		public Adjunto(String nombre, String tipo, byte[] datos) {
			this(nombre, tipo, datos, null);
		}
	}

	public record Respuesta(String asunto, String cuerpo, String html, List<Adjunto> adjuntos) {
		public Respuesta(String asunto, String cuerpo, String html) {
			this(asunto, cuerpo, html, List.of());
		}

		public boolean esError() {
			return asunto.startsWith("Error");
		}

		static Respuesta exito(String detalle) {
			String parrafo = "<p>" + HtmlCorreo.escapar(detalle).replace("\n", "<br>") + "</p>";
			return new Respuesta("Exito", detalle, HtmlCorreo.pagina("Exito", parrafo));
		}

		static Respuesta tabla(String asunto, Listado listado) {
			String contenido = listado.filas().isEmpty() ? "<p>Sin registros.</p>" : HtmlCorreo.tabla(listado.columnas(), listado.filas());
			return new Respuesta(asunto, PresentadorRespuesta.texto(listado), HtmlCorreo.pagina(asunto, contenido));
		}
	}

	public static final Map<String, Class<?>> TABLAS = CatalogoTablas.TABLAS;

	private static final Pattern COMANDO = Pattern.compile("^([A-Z]{3})([A-Z]{3})\\[(.*)]$");
	private static final Pattern LISTA = Pattern.compile("^\"[^\"]*\"(\\s*,\\s*\"[^\"]*\")*$");
	private static final Pattern PARAMETRO = Pattern.compile("\"([^\"]*)\"");
	// Rastros de un asunto danado: caracteres de control, bytes que no corresponden al
	// charset (U+FFFD) o una encoded-word (RFC 2047) que quedo sin decodificar.
	private static final Pattern ASUNTO_ILEGIBLE = Pattern
			.compile("[\\p{Cc}&&[^\\t]]|\\uFFFD|=\\?[^?\\s]+\\?[BbQq]\\?[^?\\s]*\\?=");
	public static final String ASUNTO_AYUDA = "Guía de comandos";

	private final CrudService crud;
	private final AyudaComandos ayuda;
	private final PermisosService permisos;
	private final OperacionesService operaciones;

	public ComandoService(CrudService crud, PermisosService permisos, OperacionesService operaciones) {
		this.crud = crud;
		this.ayuda = new AyudaComandos(crud, operaciones);
		this.permisos = permisos;
		this.operaciones = operaciones;
	}

	public Respuesta procesar(String asunto, String remitente) {
		String texto = asunto == null ? "" : asunto.trim();
		try {
			return ejecutar(texto, remitente);
		} catch (ComandoInvalidoException e) {
			return error(e.getCodigo(), e.getMessage());
		} catch (Exception e) {
			return errorDeEjecucion(e);
		}
	}

	/** Convierte una excepcion tecnica en un mensaje claro; la causa real queda en la terminal. */
	private Respuesta errorDeEjecucion(Exception e) {
		CodigoError codigo;
		String mensaje;
		if (e instanceof DataIntegrityViolationException) {
			codigo = CodigoError.RESTRICCION_BD;
			mensaje = switch (estadoSql(e)) {
				case "23503" -> "El registro esta relacionado con otros datos: no se puede eliminar ni cambiar esa referencia.";
				case "23505" -> "Ya existe un registro con esos datos.";
				default -> "La base de datos rechazo los datos enviados.";
			};
		} else if (e instanceof CannotCreateTransactionException || e instanceof DataAccessResourceFailureException
				|| e instanceof TransientDataAccessException) {
			codigo = CodigoError.BD_NO_DISPONIBLE;
			mensaje = "La base de datos no esta disponible. Intente mas tarde.";
		} else {
			codigo = CodigoError.ERROR_INTERNO;
			mensaje = "Ocurrio un error inesperado al ejecutar la orden.";
		}
		Terminal.servidor("Error de ejecución [" + codigo.etiqueta() + "] SQLSTATE " + estadoSql(e) + " | " + NestedExceptionUtils.getMostSpecificCause(e).getMessage());
		return error(codigo, mensaje);
	}

	private String estadoSql(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				return sql.getSQLState();
			}
		}
		return "N/A";
	}

	private Respuesta error(CodigoError codigo, String mensaje) {
		return mensaje("Error " + codigo.codigo(), "Codigo: " + codigo.etiqueta() + "\nDetalle: " + mensaje
				+ "\nAyuda: envie un correo con el asunto HELP para ver como usar cada comando.");
	}

	private Respuesta mensaje(String asunto, String cuerpo) {
		String parrafo = "<p>" + HtmlCorreo.escapar(cuerpo).replace("\n", "<br>") + "</p>";
		return new Respuesta(asunto, cuerpo, HtmlCorreo.pagina(asunto, parrafo));
	}

	private Respuesta listado(Class<?> tabla, Listado listado) {
		return Respuesta.tabla("Listando " + tabla.getSimpleName(), listado);
	}

	private Respuesta ejecutar(String texto, String remitente) {
		if (texto.equalsIgnoreCase("HELP")) {
			String guia = ayuda.html();
			return new Respuesta(ASUNTO_AYUDA, HtmlCorreo.aTexto(guia), guia);
		}
		if (ASUNTO_ILEGIBLE.matcher(texto).find()) {
			throw new ComandoInvalidoException(CodigoError.ASUNTO_DANADO,
					"El asunto llego danado o mal codificado y no se ejecuto. Escriba la orden de nuevo en un correo nuevo.");
		}
		Matcher comando = COMANDO.matcher(texto);
		if (!comando.matches()) {
			throw new ComandoInvalidoException(CodigoError.FORMATO_INVALIDO,
					"El asunto no es un comando. Use ACCIONTABLA[\"parametro\",\"parametro\"], por ejemplo LISPER[\"*\"].");
		}
		String verbo = comando.group(1);
		Class<?> tabla = TABLAS.get(comando.group(2));
		if (tabla == null) {
			throw new ComandoInvalidoException(CodigoError.COMANDO_INEXISTENTE,
					"El comando " + verbo + comando.group(2) + " no existe: la tabla " + comando.group(2) + " no es valida.");
		}
		Actor actor = permisos.actor(remitente);
		OperacionesService.Operacion operacion = operaciones.buscar(verbo + comando.group(2));
		if (operacion != null) {
			permisos.exigirRol(actor, operacion.codigo(), operacion.roles());
		} else {
			permisos.autorizar(actor, verbo, comando.group(2));
		}
		if (!LISTA.matcher(comando.group(3).trim()).matches()) {
			throw new ComandoInvalidoException(CodigoError.PARAMETROS_MAL_ESCRITOS,
					"Los parametros deben ir entre comillas dobles y separados por comas.");
		}
		List<String> parametros = PARAMETRO.matcher(comando.group(3)).results()
				.map(MatchResult::group)
				.map(p -> p.substring(1, p.length() - 1).trim())
				.toList();

		if (operacion != null) {
			return new PresentadorRespuesta().presentar(operaciones.ejecutar(operacion, actor, parametros));
		}
		Usuario autor = actor == null ? null : actor.usuario();
		return switch (verbo) {
			case "LIS" -> listado(tabla, crud.listar(tabla, unico(parametros)));
			case "INS" -> mensaje("Exito", "Registro creado con id " + crud.insertar(tabla, parametros, autor) + ".");
			case "MOD" -> {
				crud.modificar(tabla, parametros.get(0), parametros.subList(1, parametros.size()), autor);
				yield mensaje("Exito", "Registro " + parametros.get(0) + " modificado.");
			}
			case "DEL" -> {
				crud.eliminar(tabla, unico(parametros), autor);
				yield mensaje("Exito", "Registro " + parametros.get(0) + " eliminado.");
			}
			default -> throw new ComandoInvalidoException(CodigoError.COMANDO_INEXISTENTE,
					"La accion " + verbo + " no existe para esa tabla. Envie HELP para ver los comandos.");
		};
	}

	private String unico(List<String> parametros) {
		if (parametros.size() != 1) {
			throw new ComandoInvalidoException(CodigoError.CANTIDAD_PARAMETROS, "Este comando recibe un solo parametro.");
		}
		return parametros.get(0);
	}

}

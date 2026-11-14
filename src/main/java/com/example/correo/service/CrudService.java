package com.example.correo.service;

import com.example.correo.dto.Listado;
import com.example.correo.entity.Cliente;
import com.example.correo.entity.EmpleadoFeder;
import com.example.correo.entity.EncargadoAsociacion;
import com.example.correo.entity.Inventario;
import com.example.correo.entity.Persona;
import com.example.correo.entity.Producto;
import com.example.correo.entity.Usuario;
import com.example.correo.entity.enums.TipoUbicacion;
import com.example.correo.repository.CrudRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

/**
 * CRUD comun a todas las tablas. Los parametros de cada comando salen de los
 * campos de la entidad, en el orden en que estan declarados.
 */
@Service
public class CrudService {

	// Campos que el sistema llena solo: no se piden en el asunto del correo.
	private static final Set<String> FECHAS_AUTOMATICAS = Set.of("fecha", "fechaRegistro", "fechaCreacion",
			"fechaActualizacion", "fechaAgregado", "fechaAsignacion", "fechaGenerada", "fechaReporte",
			"fechaEmision", "fechaCalculo", "fechaReserva");
	private static final Set<String> NO_PARAMETROS = Set.of("activo", "geom", "ultimoAcceso");

	private final CrudRepository repository;
	private final PasswordEncoder encoder = new BCryptPasswordEncoder();
	private final Auditoria auditoria;
	private final InventarioService inventario;

	public CrudService(CrudRepository repository, Auditoria auditoria, InventarioService inventario) {
		this.repository = repository;
		this.auditoria = auditoria;
		this.inventario = inventario;
	}

	public List<Field> parametros(Class<?> tabla) {
		return Arrays.stream(tabla.getDeclaredFields())
				.filter(c -> !c.isAnnotationPresent(Id.class) && !c.isAnnotationPresent(Version.class))
				.filter(c -> !NO_PARAMETROS.contains(c.getName()) && !esFechaAutomatica(c))
				.toList();
	}

	@Transactional(readOnly = true)
	public Listado listar(Class<?> tabla, String filtro) {
		List<Object> filas = "*".equals(filtro)
				? repository.todos(tabla)
				: List.of(buscar(tabla, filtro));
		List<Field> columnas = Arrays.stream(tabla.getDeclaredFields())
				.filter(c -> !"passwordHash".equals(c.getName()))
				.toList();
		return new Listado(columnas.stream().map(Field::getName).toList(),
				filas.stream().map(fila -> columnas.stream().map(c -> texto(c, leer(fila, c))).toList()).toList());
	}

	@Transactional
	public Object insertar(Class<?> tabla, List<String> valores, Usuario autor) {
		List<Field> campos = parametros(tabla);
		exigirCantidad(campos, valores.size());
		if (valores.stream().allMatch(String::isEmpty)) {
			throw new ComandoInvalidoException(CodigoError.VALOR_INVALIDO, "Debe enviar al menos un valor.");
		}
		Object entidad = BeanUtils.instantiateClass(tabla);
		for (int i = 0; i < campos.size(); i++) {
			if (!valores.get(i).isEmpty()) {
				asignar(entidad, campos.get(i), valores.get(i));
			}
		}
		for (Field campo : tabla.getDeclaredFields()) {
			if (esFechaAutomatica(campo)) {
				escribir(entidad, campo, LocalDateTime.now());
			}
		}
		Field activo = campo(tabla, "activo");
		if (activo != null) {
			escribir(entidad, activo, true);
		}
		calcularGeom(entidad);
		validarReglas(entidad);
		repository.guardar(tabla, entidad);
		Object id = leer(entidad, campo(tabla, "id"));
		auditoria.registrar(autor, "INS", tabla.getSimpleName(), (Integer) id, cambios(campos, valores, entidad, null));
		return id;
	}

	@Transactional
	public void modificar(Class<?> tabla, String id, List<String> valores, Usuario autor) {
		List<Field> campos = parametros(tabla);
		exigirCantidad(campos, valores.size());
		Object entidad = buscar(tabla, id);
		List<String> anteriores = campos.stream().map(c -> texto(c, leer(entidad, c))).toList();
		for (int i = 0; i < campos.size(); i++) {
			if (!valores.get(i).isEmpty()) {
				asignar(entidad, campos.get(i), valores.get(i));
			}
		}
		Field actualizacion = campo(tabla, "fechaActualizacion");
		if (actualizacion != null) {
			escribir(entidad, actualizacion, LocalDateTime.now());
		}
		calcularGeom(entidad);
		validarReglas(entidad);
		repository.sincronizar(tabla);
		auditoria.registrar(autor, "MOD", tabla.getSimpleName(), Integer.valueOf(id), cambios(campos, valores, entidad, anteriores));
		if (entidad instanceof Producto producto) {
			inventario.evaluarAlerta(producto.getId());
		}
	}

	/** Baja logica si la tabla tiene columna activo; si no, borrado fisico. */
	@Transactional
	public void eliminar(Class<?> tabla, String id, Usuario autor) {
		Object entidad = buscar(tabla, id);
		Field activo = campo(tabla, "activo");
		if (activo != null) {
			escribir(entidad, activo, false);
		} else {
			repository.eliminar(tabla, entidad);
		}
		repository.sincronizar(tabla);
		auditoria.registrar(autor, "DEL", tabla.getSimpleName(), Integer.valueOf(id), List.of());
	}

	/** Diferencias del comando; la contrasena nunca se escribe en la bitacora. */
	private List<Auditoria.Cambio> cambios(List<Field> campos, List<String> valores, Object entidad, List<String> anteriores) {
		List<Auditoria.Cambio> cambios = new ArrayList<>();
		for (int i = 0; i < campos.size(); i++) {
			Field campo = campos.get(i);
			if (valores.get(i).isEmpty() || "passwordHash".equals(campo.getName())) {
				continue;
			}
			String anterior = anteriores == null ? null : anteriores.get(i);
			String nuevo = texto(campo, leer(entidad, campo));
			if (!nuevo.equals(anterior)) {
				cambios.add(new Auditoria.Cambio(campo.getName(), anterior, nuevo));
			}
		}
		return cambios;
	}

	private void validarReglas(Object entidad) {
		switch (entidad) {
			case Cliente c -> exigirMismaPersona(c.getPersona(), c.getUsuario());
			case EmpleadoFeder e -> exigirMismaPersona(e.getPersona(), e.getUsuario());
			case EncargadoAsociacion e -> exigirMismaPersona(e.getPersona(), e.getUsuario());
			case Inventario i -> exigirUbicacionDelTitular(i);
			default -> {
			}
		}
	}

	private void exigirMismaPersona(Persona persona, Usuario usuario) {
		if (persona != null && usuario != null && (persona.getUsuario() == null || !persona.getUsuario().getId().equals(usuario.getId()))) {
			throw Datos.regla("La cuenta debe ser la de esa persona: registre usuario en PER primero.");
		}
	}

	private void exigirUbicacionDelTitular(Inventario inventario) {
		if (inventario.getTipo() == null || inventario.getUbicacion() == null) {
			return;
		}
		TipoUbicacion esperada = switch (inventario.getTipo()) {
			case PRODUCTOR -> TipoUbicacion.PARCELA;
			case ASOCIACION -> TipoUbicacion.CENTRO_ACOPIO;
			case FEDER -> TipoUbicacion.ALMACEN_FEDER;
		};
		if (inventario.getUbicacion().getTipo() != esperada) {
			throw Datos.regla("Un inventario " + inventario.getTipo() + " debe estar en una ubicacion " + esperada + ".");
		}
	}

	private void exigirCantidad(List<Field> campos, int recibidos) {
		if (recibidos != campos.size()) {
			String esperados = campos.stream().map(Field::getName).collect(Collectors.joining(", "));
			throw new ComandoInvalidoException(CodigoError.CANTIDAD_PARAMETROS, "Se esperaban " + campos.size() + " parametros ("
					+ esperados + ") y llegaron " + recibidos + ".");
		}
	}

	private void asignar(Object entidad, Field campo, String valor) {
		escribir(entidad, campo, "passwordHash".equals(campo.getName()) ? cifrar(valor) : convertir(campo, valor));
	}

	private String cifrar(String contrasena) {
		// BCrypt solo admite hasta 72 bytes.
		if (contrasena.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ComandoInvalidoException(CodigoError.VALOR_INVALIDO, "La contrasena no puede superar 72 caracteres.");
		}
		return encoder.encode(contrasena);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object convertir(Field campo, String valor) {
		Class<?> tipo = campo.getType();
		try {
			if (tipo == String.class) {
				return validarLongitud(campo, valor);
			}
			if (tipo == Integer.class) {
				return Integer.valueOf(valor);
			}
			if (tipo == BigDecimal.class) {
				return validarNumero(campo, new BigDecimal(valor));
			}
			if (tipo == Boolean.class) {
				if (!valor.equalsIgnoreCase("true") && !valor.equalsIgnoreCase("false")) {
					throw new IllegalArgumentException();
				}
				return Boolean.valueOf(valor);
			}
			if (tipo == LocalDate.class) {
				return LocalDate.parse(valor);
			}
			if (tipo == LocalDateTime.class) {
				return LocalDateTime.parse(valor.replace(' ', 'T'));
			}
			if (tipo.isEnum()) {
				return Enum.valueOf((Class) tipo, valor.toUpperCase());
			}
		} catch (IllegalArgumentException | DateTimeException e) {
			throw new ComandoInvalidoException(CodigoError.VALOR_INVALIDO, "Valor invalido para " + campo.getName() + ": \""
					+ valor + "\" (se esperaba " + esperado(tipo) + ").");
		}
		return buscar(tipo, valor);
	}

	private String esperado(Class<?> tipo) {
		if (tipo.isEnum()) {
			return "uno de " + Arrays.toString(tipo.getEnumConstants());
		}
		if (tipo == LocalDate.class) {
			return "fecha AAAA-MM-DD";
		}
		if (tipo == LocalDateTime.class) {
			return "fecha y hora AAAA-MM-DD HH:MM";
		}
		return tipo == Boolean.class ? "true o false" : tipo == Integer.class ? "numero entero" : "numero";
	}

	private String validarLongitud(Field campo, String valor) {
		Column columna = campo.getAnnotation(Column.class);
		if (columna != null && columna.columnDefinition().isEmpty() && valor.length() > columna.length()) {
			throw new ComandoInvalidoException(CodigoError.VALOR_INVALIDO,
					campo.getName() + " admite hasta " + columna.length() + " caracteres.");
		}
		return valor;
	}

	private BigDecimal validarNumero(Field campo, BigDecimal numero) {
		Column columna = campo.getAnnotation(Column.class);
		if (columna != null && columna.precision() > 0 && (numero.scale() > columna.scale()
				|| numero.precision() - numero.scale() > columna.precision() - columna.scale())) {
			throw new ComandoInvalidoException(CodigoError.VALOR_INVALIDO, campo.getName() + " admite hasta "
					+ (columna.precision() - columna.scale()) + " enteros y " + columna.scale() + " decimales.");
		}
		return numero;
	}

	private Object buscar(Class<?> tabla, String id) {
		int clave;
		try {
			clave = Integer.parseInt(id);
		} catch (NumberFormatException e) {
			throw new ComandoInvalidoException(CodigoError.ID_INVALIDO, "ID invalido: \"" + id + "\".");
		}
		return repository.buscar(tabla, clave).orElseThrow(() -> new ComandoInvalidoException(CodigoError.REGISTRO_NO_ENCONTRADO,
				"No existe " + tabla.getSimpleName() + " con id " + id + "."));
	}

	private String texto(Field campo, Object valor) {
		if (valor == null) {
			return "";
		}
		// Las relaciones se muestran por su ID, sin cargar el registro relacionado.
		return campo.getType().isAnnotationPresent(Entity.class)
				? String.valueOf(repository.identificador(valor))
				: valor.toString();
	}

	/** El punto de PostgreSQL se forma con (longitud,latitud). */
	private void calcularGeom(Object entidad) {
		Class<?> tabla = entidad.getClass();
		Field geom = campo(tabla, "geom");
		if (geom == null) {
			return;
		}
		Object latitud = leer(entidad, campo(tabla, "latitud"));
		Object longitud = leer(entidad, campo(tabla, "longitud"));
		if (latitud != null && longitud != null) {
			escribir(entidad, geom, "(" + longitud + "," + latitud + ")");
		}
	}

	private boolean esFechaAutomatica(Field campo) {
		return FECHAS_AUTOMATICAS.contains(campo.getName()) && campo.getType() == LocalDateTime.class;
	}

	private Field campo(Class<?> tabla, String nombre) {
		return ReflectionUtils.findField(tabla, nombre);
	}

	private Object leer(Object entidad, Field campo) {
		ReflectionUtils.makeAccessible(campo);
		return ReflectionUtils.getField(campo, entidad);
	}

	private void escribir(Object entidad, Field campo, Object valor) {
		ReflectionUtils.makeAccessible(campo);
		ReflectionUtils.setField(campo, entidad, valor);
	}

}

package com.example.correo.service;

import com.example.correo.repository.UsuarioRolRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Decide si el remitente del correo puede ejecutar un comando, segun sus roles activos. */
@Service
public class PermisosService {

	public static final String ADMINISTRADOR = "ADMINISTRADOR";
	public static final String PERSONAL = "PERSONAL";
	public static final String ENCARGADO = "ENCARGADO";
	public static final String PRODUCTOR = "PRODUCTOR";
	public static final String CLIENTE = "CLIENTE";

	private static final Set<String> SOLO_ADMINISTRADOR = Set.of("USU", "ROL", "URO", "PER", "EMP", "ENC", "BIT", "CAB");
	private static final Set<String> CATALOGO = Set.of("CAT", "PRO");

	/** Guardan saldos, estados o historia: solo cambian mediante los comandos de negocio. */
	public static final Set<String> SOLO_LECTURA = Set.of("ALE", "BIT", "CAB", "CAR", "COM", "DCO", "DEV", "DVE", "ENT", "FAC", "HIS", "IND",
			"LOA", "LOF", "MOF", "MOI", "NOT", "OFE", "PAG", "PRD", "RES", "VEN");

	private final UsuarioRolRepository usuarioRoles;
	private final String correoAdministrador;

	public PermisosService(UsuarioRolRepository usuarioRoles, @Value("${app.admin.correo:}") String correoAdministrador) {
		this.usuarioRoles = usuarioRoles;
		this.correoAdministrador = correoAdministrador;
	}

	public Actor actor(String remitente) {
		if (remitente == null) {
			return new Actor(null, null, Set.of());
		}
		Set<String> roles = new HashSet<>(usuarioRoles.rolesActivos(remitente).stream().map(String::toUpperCase).collect(Collectors.toSet()));
		if (remitente.equalsIgnoreCase(correoAdministrador)) {
			roles.add(ADMINISTRADOR);
		}
		return new Actor(remitente, usuarioRoles.usuarioActivo(remitente).orElse(null), roles);
	}

	/** Permiso para LIS, INS, MOD y DEL. */
	public void autorizar(Actor actor, String accion, String tabla) {
		if (!"LIS".equals(accion) && Set.of("INS", "MOD", "DEL").contains(accion) && SOLO_LECTURA.contains(tabla)) {
			throw new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO, "La tabla " + tabla + " solo cambia con los comandos de negocio: envie HELP para verlos.");
		}
		boolean permitido = actor.tiene(ADMINISTRADOR)
				|| (actor.tiene(PERSONAL) && !SOLO_ADMINISTRADOR.contains(tabla))
				|| (!actor.roles().isEmpty() && "LIS".equals(accion) && CATALOGO.contains(tabla));
		if (!permitido) {
			throw denegado(actor, accion + " en " + tabla);
		}
	}

	/** Permiso para un comando de negocio; el administrador siempre puede intentarlo. */
	public void exigirRol(Actor actor, String comando, Set<String> roles) {
		if (!actor.tiene(ADMINISTRADOR) && roles.stream().noneMatch(actor::tiene)) {
			throw denegado(actor, comando);
		}
	}

	private ComandoInvalidoException denegado(Actor actor, String que) {
		return new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO,
				actor.roles().isEmpty() ? "Su correo no pertenece a una cuenta activa con roles." : "Sus roles no permiten " + que + ".");
	}

}

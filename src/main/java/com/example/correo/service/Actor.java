package com.example.correo.service;

import com.example.correo.entity.Usuario;
import java.util.Set;

/** Quien envia el correo. usuario es null si no tiene cuenta (por ejemplo, el administrador inicial). */
public record Actor(String correo, Usuario usuario, Set<String> roles) {

	public boolean tiene(String rol) {
		return roles.contains(rol);
	}

	public boolean esPersonal() {
		return tiene(PermisosService.ADMINISTRADOR) || tiene(PermisosService.PERSONAL);
	}

}

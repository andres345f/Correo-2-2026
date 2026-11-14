package com.example.correo.repository;

import com.example.correo.entity.Usuario;
import com.example.correo.entity.UsuarioRol;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Integer> {

	@Query("select ur.rol.nombre from UsuarioRol ur where lower(ur.usuario.correo) = lower(:correo) and ur.usuario.activo = true and ur.rol.activo = true")
	List<String> rolesActivos(@Param("correo") String correo);

	@Query("select u from Usuario u where lower(u.correo) = lower(:correo) and u.activo = true")
	Optional<Usuario> usuarioActivo(@Param("correo") String correo);
}

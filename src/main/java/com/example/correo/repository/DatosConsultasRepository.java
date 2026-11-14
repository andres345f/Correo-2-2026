package com.example.correo.repository;

import com.example.correo.entity.Cliente;
import com.example.correo.entity.EmpleadoFeder;
import com.example.correo.entity.EncargadoAsociacion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Consultas de Datos; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class DatosConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public List<EncargadoAsociacion> consultarMandato(Integer u, Integer a, LocalDateTime t) {
		return em.createQuery("""
				select e from EncargadoAsociacion e where e.usuario.id = :u and e.asociacion.id = :a
				and e.desde <= :t and (e.hasta is null or e.hasta > :t)""", EncargadoAsociacion.class).setParameter("u", u).setParameter("a", a).setParameter("t", t).getResultList();
	}

	public List<EncargadoAsociacion> consultarMandatoActual(Integer u, LocalDateTime t) {
		return em.createQuery("""
				select e from EncargadoAsociacion e where e.usuario.id = :u
				and e.desde <= :t and (e.hasta is null or e.hasta > :t)""", EncargadoAsociacion.class).setParameter("u", u).setParameter("t", t).getResultList();
	}
	public Optional<EmpleadoFeder> empleadoActivo(Integer usuario) {
		return em.createQuery("select e from EmpleadoFeder e where e.usuario.id = :u and e.activo = true", EmpleadoFeder.class).setParameter("u", usuario).getResultList().stream().findFirst();
	}
	public Optional<Cliente> clienteActivo(Integer usuario) {
		return em.createQuery("select e from Cliente e where e.usuario.id = :u and e.activo = true", Cliente.class).setParameter("u", usuario).getResultList().stream().findFirst();
	}
}

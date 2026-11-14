package com.example.correo.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/** Operaciones comunes de persistencia; participan en la transaccion del servicio. */
@Repository
public class PersistenciaRepository {
	@PersistenceContext
	private EntityManager em;

	public <T> T buscar(Class<T> tipo, Integer id) { return em.find(tipo, id); }
	public <T> T bloquear(Class<T> tipo, Integer id) {
		T entidad = em.find(tipo, id, LockModeType.PESSIMISTIC_WRITE);
		if (entidad != null) em.refresh(entidad);
		return entidad;
	}
	public void guardar(Object entidad) { em.persist(entidad); }
	public void eliminar(Object entidad) { em.remove(entidad); }
	public void sincronizar() { em.flush(); }
	public void actualizar(Object entidad) { em.refresh(entidad); }
}

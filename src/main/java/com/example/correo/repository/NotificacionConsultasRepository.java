package com.example.correo.repository;

import com.example.correo.entity.Notificacion;
import com.example.correo.entity.enums.CanalNotificacion;
import com.example.correo.entity.enums.EstadoEnvio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Consultas de NotificacionService; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class NotificacionConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public List<Notificacion> avisosDeCliente(Integer c) {
		return em.createQuery("select n from Notificacion n where n.cliente.id = :c order by n.id desc", Notificacion.class).setParameter("c", c).getResultList();
	}

	public List<Notificacion> avisosParaEnviar(CanalNotificacion c, EstadoEnvio e) {
		return em.createQuery("select n from Notificacion n where n.canal = :c and n.estadoEnvio = :e order by n.id", Notificacion.class).setParameter("c", c).setParameter("e", e).setMaxResults(20).getResultList();
	}
}

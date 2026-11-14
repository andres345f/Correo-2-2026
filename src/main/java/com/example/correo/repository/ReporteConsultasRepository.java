package com.example.correo.repository;

import com.example.correo.entity.Devolucion;
import com.example.correo.entity.Entrega;
import com.example.correo.entity.IndicadorFinanciero;
import com.example.correo.entity.LoteAsociacion;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.Pago;
import com.example.correo.entity.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Consultas de ReporteService; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class ReporteConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public List<LoteFeder> lotesFeder() {
		return em.createQuery("select l from LoteFeder l", LoteFeder.class).getResultList();
	}

	public List<Producto> productos() {
		return em.createQuery("select p from Producto p", Producto.class).getResultList();
	}

	public List<LoteAsociacion> lotesRegistradosEnPeriodo(LocalDateTime i, LocalDateTime f) {
		return em.createQuery("select l from LoteAsociacion l where l.fechaRegistro >= :i and l.fechaRegistro < :f", LoteAsociacion.class).setParameter("i", i).setParameter("f", f).getResultList();
	}

	public List<Pago> pagosEnPeriodo(LocalDateTime i, LocalDateTime f) {
		return em.createQuery("select g from Pago g where g.fechaPago >= :i and g.fechaPago < :f", Pago.class).setParameter("i", i).setParameter("f", f).getResultList();
	}

	public List<Entrega> entregasDeVentasEnPeriodo(LocalDateTime i, LocalDateTime f) {
		return em.createQuery("select e from Entrega e where e.venta.fecha >= :i and e.venta.fecha < :f", Entrega.class).setParameter("i", i).setParameter("f", f).getResultList();
	}

	public List<Devolucion> devolucionesEnPeriodo(LocalDateTime i, LocalDateTime f) {
		return em.createQuery("select d from Devolucion d where d.fechaReporte >= :i and d.fechaReporte < :f", Devolucion.class).setParameter("i", i).setParameter("f", f).getResultList();
	}

	public List<IndicadorFinanciero> indicadoresEnPeriodo(LocalDate d, LocalDate h) {
		return em.createQuery("select i from IndicadorFinanciero i where i.periodoDesde >= :d and i.periodoHasta <= :h order by i.id", IndicadorFinanciero.class).setParameter("d", d).setParameter("h", h).getResultList();
	}
}

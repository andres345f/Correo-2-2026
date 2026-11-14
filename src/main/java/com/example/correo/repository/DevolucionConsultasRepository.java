package com.example.correo.repository;

import com.example.correo.entity.Bitacora;
import com.example.correo.entity.Devolucion;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.Pago;
import com.example.correo.entity.enums.EstadoDevolucion;
import com.example.correo.entity.enums.EstadoPago;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Consultas de DevolucionService; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class DevolucionConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public BigDecimal cantidadAprobadaDelDespacho(Integer m, List<EstadoDevolucion> e) {
		return em.createQuery("select coalesce(sum(d.cantidad), 0) from Devolucion d where d.despacho.id = :m and d.estado in :e", BigDecimal.class).setParameter("m", m).setParameter("e", e).getSingleResult();
	}

	public BigDecimal cantidadAsentada(Integer d, MotivoMovimientoFeder m) {
		return em.createQuery("select coalesce(sum(m.cantidad), 0) from MovimientoFeder m where m.devolucion.id = :d and m.motivo = :m", BigDecimal.class).setParameter("d", d).setParameter("m", m).getSingleResult();
	}

	public Long contarEventosContables(String a, Integer d) {
		return em.createQuery("select count(b) from Bitacora b where b.accion = :a and b.entidad = 'Devolucion' and b.entidadId = :d", Long.class).setParameter("a", a).setParameter("d", d).getSingleResult();
	}

	public List<Pago> pagosSinReversion(Integer v, EstadoPago e) {
		return em.createQuery("select g from Pago g where g.venta.id = :v and g.estadoPago = :e and g.fechaReversion is null", Pago.class).setParameter("v", v).setParameter("e", e).getResultList();
	}

	public BigDecimal totalReembolsadoDeVenta(Integer v) {
		return em.createQuery("select coalesce(sum(d.montoReembolso), 0) from Devolucion d where d.detalleVenta.venta.id = :v and d.fechaReembolso is not null", BigDecimal.class).setParameter("v", v).getSingleResult();
	}
}

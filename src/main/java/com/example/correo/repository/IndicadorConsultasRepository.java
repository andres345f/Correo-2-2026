package com.example.correo.repository;

import com.example.correo.entity.Bitacora;
import com.example.correo.entity.CambioBitacora;
import com.example.correo.entity.Devolucion;
import com.example.correo.entity.Entrega;
import com.example.correo.entity.HistorialEstadoVenta;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.Pago;
import com.example.correo.entity.enums.EstadoPago;
import com.example.correo.entity.enums.EstadoVenta;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Consultas de IndicadorService; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class IndicadorConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public List<HistorialEstadoVenta> historialEnPeriodo(EstadoVenta e, LocalDateTime i, LocalDateTime f) {
		return em.createQuery("select h from HistorialEstadoVenta h where h.estadoNuevo = :e and h.fecha >= :i and h.fecha < :f order by h.id", HistorialEstadoVenta.class).setParameter("e", e).setParameter("i", i).setParameter("f", f).getResultList();
	}

	public List<MovimientoFeder> movimientosEnPeriodo(List<MotivoMovimientoFeder> motivos, LocalDateTime i, LocalDateTime f) {
		return em.createQuery("select m from MovimientoFeder m where m.motivo in :motivos and m.fecha >= :i and m.fecha < :f order by m.id", MovimientoFeder.class).setParameter("motivos", motivos).setParameter("i", i).setParameter("f", f).getResultList();
	}

	public List<Entrega> entregasDeVenta(Integer v) {
		return em.createQuery("select e from Entrega e where e.venta.id = :v", Entrega.class).setParameter("v", v).getResultList();
	}

	public List<MovimientoFeder> movimientosDeVenta(MotivoMovimientoFeder m, Integer v) {
		return em.createQuery("select m from MovimientoFeder m where m.motivo = :m and m.detalleVenta.venta.id = :v order by m.id", MovimientoFeder.class).setParameter("m", m).setParameter("v", v).getResultList();
	}

	public List<Bitacora> eventosContablesEnPeriodo(String a, LocalDateTime i, LocalDateTime f) {
		return em.createQuery("select b from Bitacora b where b.accion = :a and b.entidad = 'Devolucion' and b.fecha >= :i and b.fecha < :f order by b.id", Bitacora.class).setParameter("a", a).setParameter("i", i).setParameter("f", f).getResultList();
	}

	public CambioBitacora cambioDeEvento(Integer b, String r) {
		return em.createQuery("select c from CambioBitacora c where c.bitacora.id = :b and c.rutaCampo = :r", CambioBitacora.class).setParameter("b", b).setParameter("r", r).getSingleResult();
	}

	public List<MovimientoFeder> movimientosAntesDe(LocalDateTime f) {
		return em.createQuery("select m from MovimientoFeder m where m.fecha < :f", MovimientoFeder.class).setParameter("f", f).getResultList();
	}
	private BigDecimal suma(String jpql, LocalDateTime inicio, LocalDateTime fin, boolean conEstado) {
		var consulta = em.createQuery(jpql, BigDecimal.class).setParameter("i", inicio).setParameter("f", fin);
		if (conEstado) {
			consulta.setParameter("e", EstadoPago.PAGADO);
		}
		return consulta.getSingleResult();
	}

	public BigDecimal cobros(LocalDateTime inicio, LocalDateTime fin) { return suma("select coalesce(sum(g.monto), 0) from Pago g where g.estadoPago = :e and g.fechaPago >= :i and g.fechaPago < :f", inicio, fin, true); }
	public BigDecimal reversos(LocalDateTime inicio, LocalDateTime fin) { return suma("select coalesce(sum(g.monto), 0) from Pago g where g.estadoPago = :e and g.fechaReversion >= :i and g.fechaReversion < :f", inicio, fin, true); }
	public BigDecimal reembolsos(LocalDateTime inicio, LocalDateTime fin) { return suma("select coalesce(sum(d.montoReembolso), 0) from Devolucion d where d.fechaReembolso >= :i and d.fechaReembolso < :f", inicio, fin, false); }
}

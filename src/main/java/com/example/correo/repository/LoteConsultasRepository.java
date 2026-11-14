package com.example.correo.repository;

import com.example.correo.entity.EncargadoAsociacion;
import com.example.correo.entity.LoteAsociacion;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.MovimientoInventario;
import com.example.correo.entity.OfertaAsociacion;
import com.example.correo.entity.Produccion;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Consultas de LoteService; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class LoteConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public List<MovimientoInventario> movimientosConClave(String c) {
		return em.createQuery("select m from MovimientoInventario m where m.claveOperacion = :c", MovimientoInventario.class).setParameter("c", c).getResultList();
	}

	public List<OfertaAsociacion> ofertasDeAsociacionYProducto(Integer a, Integer p) {
		return em.createQuery("select o from OfertaAsociacion o where o.asociacion.id = :a and o.producto.id = :p", OfertaAsociacion.class).setParameter("a", a).setParameter("p", p).getResultList();
	}

	public List<EncargadoAsociacion> mandatosAbiertosDeAsociacion(Integer a) {
		return em.createQuery("select e from EncargadoAsociacion e where e.asociacion.id = :a and e.hasta is null", EncargadoAsociacion.class).setParameter("a", a).getResultList();
	}

	public List<MovimientoFeder> movimientosDeLinea(Integer d, MotivoMovimientoFeder v) {
		return em.createQuery("select m from MovimientoFeder m where m.detalleVenta.id = :d and m.motivo = :v order by m.id", MovimientoFeder.class).setParameter("d", d).setParameter("v", v).getResultList();
	}

	public List<MovimientoInventario> movimientosDeLote(Integer l) {
		return em.createQuery("select m from MovimientoInventario m where m.loteAsociacion.id = :l order by m.id", MovimientoInventario.class).setParameter("l", l).getResultList();
	}

	public List<LoteAsociacion> lotesDeUsuario(Integer u) {
		return em.createQuery("""
				select l from LoteAsociacion l where l.productor.usuario.id = :u or l.asociacion.id in
				(select e.asociacion.id from EncargadoAsociacion e where e.usuario.id = :u and e.hasta is null) order by l.id""", LoteAsociacion.class).setParameter("u", u).getResultList();
	}

	public List<Produccion> produccionesDeUsuario(Integer u) {
		return em.createQuery("""
				select c from Produccion c where c.productor.usuario.id = :u or c.productor.asociacion.id in
				(select e.asociacion.id from EncargadoAsociacion e where e.usuario.id = :u and e.hasta is null) order by c.id""", Produccion.class).setParameter("u", u).getResultList();
	}
}

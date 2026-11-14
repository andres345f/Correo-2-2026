package com.example.correo.repository;

import com.example.correo.entity.AlertaStock;
import com.example.correo.entity.DetalleCompra;
import com.example.correo.entity.LoteAsociacion;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.OfertaAsociacion;
import com.example.correo.entity.Producto;
import com.example.correo.entity.enums.EstadoValidacionLote;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Consultas de InventarioService; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class InventarioConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public List<LoteFeder> lotesDeDetalleCompra(Integer d) {
		return em.createQuery("select l from LoteFeder l where l.detalleCompra.id = :d", LoteFeder.class).setParameter("d", d).getResultList();
	}

	public List<Integer> loteAsociacionDeDetalle(Integer d) {
		return em.createQuery("select d.loteAsociacion.id from DetalleCompra d where d.id = :d", Integer.class).setParameter("d", d).getResultList();
	}

	public Long contarMovimientosConClave(String c) {
		return em.createQuery("select count(m) from MovimientoFeder m where m.claveOperacion = :c", Long.class).setParameter("c", c).getSingleResult();
	}

	public BigDecimal saldoDisponible(Integer p, LocalDate hoy) {
		return em.createQuery("""
				select coalesce(sum(l.cantidadActual - l.cantidadReservada), 0) from LoteFeder l
				where l.producto.id = :p and l.producto.activo = true and l.inventario.activo = true
				and (l.fechaVencimiento is null or l.fechaVencimiento > :hoy)""", BigDecimal.class).setParameter("p", p).setParameter("hoy", hoy).getSingleResult();
	}

	public Long contarAlertasAbiertas(Integer p) {
		return em.createQuery("select count(a) from AlertaStock a where a.producto.id = :p and a.atendida = false", Long.class).setParameter("p", p).getSingleResult();
	}

	public List<Integer> idsProductosActivos() {
		return em.createQuery("select p.id from Producto p where p.activo = true order by p.id", Integer.class).getResultList();
	}

	public List<Integer> idsProductosPorMunicipio(String m, LocalDate hoy) {
		return em.createQuery("""
				select distinct l.producto.id from LoteFeder l where lower(l.detalleCompra.loteAsociacion.municipio.nombre) like :m
				and l.cantidadActual > l.cantidadReservada and l.inventario.activo = true and (l.fechaVencimiento is null or l.fechaVencimiento > :hoy)""", Integer.class).setParameter("m", m).setParameter("hoy", hoy).getResultList();
	}

	public List<Producto> productosActivos() {
		return em.createQuery("select p from Producto p where p.activo = true order by p.nombre", Producto.class).getResultList();
	}

	public List<LoteFeder> lotesDeProducto(Integer p) {
		return em.createQuery("select l from LoteFeder l where l.producto.id = :p order by l.fechaVencimiento nulls last, l.id", LoteFeder.class).setParameter("p", p).getResultList();
	}

	public List<LoteAsociacion> lotesDeProveedorPorEstado(Integer p, EstadoValidacionLote v) {
		return em.createQuery("""
				select l from LoteAsociacion l where l.producto.id = :p and l.estadoValidacion = :v and l.cantidadDisponible > 0 order by l.id""", LoteAsociacion.class).setParameter("p", p).setParameter("v", v).getResultList();
	}

	public List<OfertaAsociacion> ofertasActivasDeProducto(Integer p) {
		return em.createQuery("select o from OfertaAsociacion o where o.producto.id = :p and o.activo = true and o.cantidadPrevista is not null order by o.id", OfertaAsociacion.class).setParameter("p", p).getResultList();
	}
}

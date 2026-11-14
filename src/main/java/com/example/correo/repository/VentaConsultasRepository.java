package com.example.correo.repository;

import com.example.correo.entity.DetalleVenta;
import com.example.correo.entity.Entrega;
import com.example.correo.entity.Factura;
import com.example.correo.entity.HistorialEstadoVenta;
import com.example.correo.entity.Inventario;
import com.example.correo.entity.ItemCarrito;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.Pago;
import com.example.correo.entity.ReservaStock;
import com.example.correo.entity.Venta;
import com.example.correo.entity.enums.EstadoReservaStock;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import com.example.correo.entity.enums.TipoInventario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Consultas de VentaService; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class VentaConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public List<ItemCarrito> itemsDeClienteYProducto(Integer c, Integer p) {
		return em.createQuery("select i from ItemCarrito i where i.cliente.id = :c and i.producto.id = :p", ItemCarrito.class).setParameter("c", c).setParameter("p", p).getResultList();
	}

	public List<ItemCarrito> carritoDeCliente(Integer c) {
		return em.createQuery("select i from ItemCarrito i where i.cliente.id = :c order by i.id", ItemCarrito.class).setParameter("c", c).getResultList();
	}

	public List<Inventario> almacenesPrincipales(TipoInventario t) {
		return em.createQuery("select i from Inventario i where i.tipo = :t and i.esPrincipal = true and i.activo = true", Inventario.class).setParameter("t", t).getResultList();
	}

	public List<Entrega> entregasDeVenta(Integer v) {
		return em.createQuery("select e from Entrega e where e.venta.id = :v", Entrega.class).setParameter("v", v).getResultList();
	}

	public Integer ultimaSecuenciaDeVenta(Integer v) {
		return em.createQuery("select coalesce(max(h.secuencia), 0) from HistorialEstadoVenta h where h.venta.id = :v", Integer.class).setParameter("v", v).getSingleResult();
	}

	public List<DetalleVenta> bloquearLineasDeVenta(Integer v) {
		return em.createQuery("select d from DetalleVenta d where d.venta.id = :v order by d.id", DetalleVenta.class).setParameter("v", v).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
	}

	public List<LoteFeder> bloquearLotesPorIds(List<Integer> ids) {
		return em.createQuery("select l from LoteFeder l where l.id in :ids order by l.id", LoteFeder.class).setParameter("ids", ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
	}

	public BigDecimal cantidadDespachada(Integer d, MotivoMovimientoFeder v) {
		return em.createQuery("select coalesce(sum(m.cantidad), 0) from MovimientoFeder m where m.detalleVenta.id = :d and m.motivo = :v", BigDecimal.class).setParameter("d", d).setParameter("v", v).getSingleResult();
	}

	public List<ReservaStock> reservasDeVentaPorEstado(Integer v, EstadoReservaStock e) {
		return em.createQuery("select r from ReservaStock r where r.detalleVenta.venta.id = :v and r.estado = :e order by r.id", ReservaStock.class).setParameter("v", v).setParameter("e", e).getResultList();
	}

	public List<Integer> idsLotesDeProductos(List<Integer> p) {
		return em.createQuery("select l.id from LoteFeder l where l.producto.id in :p", Integer.class).setParameter("p", p).getResultList();
	}

	public List<Integer> ventasConReservasVencidas(EstadoReservaStock e, LocalDateTime t) {
		return em.createQuery("""
				select distinct r.detalleVenta.venta.id from ReservaStock r where r.estado = :e and r.fechaExpiracion <= :t order by r.detalleVenta.venta.id""", Integer.class).setParameter("e", e).setParameter("t", t).getResultList();
	}

	public List<Integer> idsLotesReservadosDeVenta(Integer v, EstadoReservaStock e) {
		return em.createQuery("select distinct r.loteFeder.id from ReservaStock r where r.detalleVenta.venta.id = :v and r.estado = :e", Integer.class).setParameter("v", v).setParameter("e", e).getResultList();
	}

	public BigDecimal cantidadMovidaDeLineaYLote(Integer d, Integer l, MotivoMovimientoFeder m) {
		return em.createQuery("select coalesce(sum(m.cantidad), 0) from MovimientoFeder m where m.detalleVenta.id = :d and m.loteFeder.id = :l and m.motivo = :m", BigDecimal.class).setParameter("d", d).setParameter("l", l).setParameter("m", m).getSingleResult();
	}

	public List<Venta> ventasDeCliente(Integer c) {
		return em.createQuery("select v from Venta v where v.cliente.id = :c order by v.id", Venta.class).setParameter("c", c).getResultList();
	}

	public List<DetalleVenta> lineasDeVenta(Integer v) {
		return em.createQuery("select d from DetalleVenta d where d.venta.id = :v order by d.id", DetalleVenta.class).setParameter("v", v).getResultList();
	}

	public List<HistorialEstadoVenta> historialDeVenta(Integer v) {
		return em.createQuery("select h from HistorialEstadoVenta h where h.venta.id = :v order by h.secuencia", HistorialEstadoVenta.class).setParameter("v", v).getResultList();
	}

	public List<Pago> pagosDeVenta(Integer v) {
		return em.createQuery("select g from Pago g where g.venta.id = :v order by g.id", Pago.class).setParameter("v", v).getResultList();
	}

	public List<Factura> facturasDeVenta(Integer v) {
		return em.createQuery("select f from Factura f where f.venta.id = :v", Factura.class).setParameter("v", v).getResultList();
	}
}

package com.example.correo.repository;

import com.example.correo.entity.Factura;
import com.example.correo.entity.Pago;
import com.example.correo.entity.enums.EstadoPago;
import com.example.correo.entity.enums.MetodoPago;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Consultas de CobroService; conserva parametros, orden y bloqueos de las operaciones. */
@Repository
public class CobroConsultasRepository {
	@PersistenceContext
	private EntityManager em;

	public Long contarPagosPorEstado(Integer v, EstadoPago e) {
		return em.createQuery("select count(g) from Pago g where g.venta.id = :v and g.estadoPago = :e", Long.class).setParameter("v", v).setParameter("e", e).getSingleResult();
	}

	public List<Pago> pagosPorReferencia(MetodoPago m, String r) {
		return em.createQuery("select g from Pago g where g.metodoPago = :m and g.referenciaPago = :r", Pago.class).setParameter("m", m).setParameter("r", r).getResultList();
	}

	public List<Factura> facturasDeVenta(Integer v) {
		return em.createQuery("select f from Factura f where f.venta.id = :v", Factura.class).setParameter("v", v).getResultList();
	}
}

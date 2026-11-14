package com.example.correo.repository;

import com.example.correo.entity.DetalleVenta;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Integer> {

	@Query("select coalesce(sum(d.subtotal), 0) from DetalleVenta d where d.venta.id = :venta")
	BigDecimal sumarSubtotales(@Param("venta") Integer venta);
}

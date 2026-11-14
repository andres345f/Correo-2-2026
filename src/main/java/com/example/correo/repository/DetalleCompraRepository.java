package com.example.correo.repository;

import com.example.correo.entity.DetalleCompra;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, Integer> {

	@Query("select coalesce(sum(d.subtotal), 0) from DetalleCompra d where d.compra.id = :compra")
	BigDecimal sumarSubtotales(@Param("compra") Integer compra);
}

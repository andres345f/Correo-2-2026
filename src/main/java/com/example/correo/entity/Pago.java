package com.example.correo.entity;

import com.example.correo.entity.enums.EstadoPago;
import com.example.correo.entity.enums.MetodoPago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pago")
@Getter
@Setter
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "pagoid")
	private Integer id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado_pago", columnDefinition = "estado_pago")
	private EstadoPago estadoPago;

	@Column(name = "fecha_pago")
	private LocalDateTime fechaPago;

	@Column(name = "fecha_reversion")
	private LocalDateTime fechaReversion;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "metodo_pago", columnDefinition = "metodo_pago")
	private MetodoPago metodoPago;

	@Column(name = "monto", precision = 12, scale = 2)
	private BigDecimal monto;

	@Column(name = "referencia_pago", length = 100)
	private String referenciaPago;

	@Column(name = "referencia_reversion", length = 100)
	private String referenciaReversion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ventaid")
	private Venta venta;
}

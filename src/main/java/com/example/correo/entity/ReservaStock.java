package com.example.correo.entity;

import com.example.correo.entity.enums.EstadoReservaStock;

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
@Table(name = "reserva_stock")
@Getter
@Setter
public class ReservaStock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reserva_stockid")
	private Integer id;

	@Column(name = "cantidad", precision = 12, scale = 2)
	private BigDecimal cantidad;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado", columnDefinition = "estado_reserva_stock")
	private EstadoReservaStock estado;

	@Column(name = "fecha_cierre")
	private LocalDateTime fechaCierre;

	@Column(name = "fecha_expiracion")
	private LocalDateTime fechaExpiracion;

	@Column(name = "fecha_reserva")
	private LocalDateTime fechaReserva;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "detalle_ventaid")
	private DetalleVenta detalleVenta;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lote_federid")
	private LoteFeder loteFeder;
}

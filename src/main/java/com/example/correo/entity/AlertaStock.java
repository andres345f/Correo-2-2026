package com.example.correo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "alerta_stock")
@Getter
@Setter
public class AlertaStock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "alerta_stockid")
	private Integer id;

	@Column(name = "atendida")
	private Boolean atendida;

	@Column(name = "fecha_atendida")
	private LocalDateTime fechaAtendida;

	@Column(name = "fecha_generada")
	private LocalDateTime fechaGenerada;

	@Column(name = "stock_actual", precision = 12, scale = 2)
	private BigDecimal stockActual;

	@Column(name = "stock_minimo", precision = 12, scale = 2)
	private BigDecimal stockMinimo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empleado_federid")
	private EmpleadoFeder empleadoFeder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productoid")
	private Producto producto;
}

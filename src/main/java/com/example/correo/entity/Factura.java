package com.example.correo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "factura")
@Getter
@Setter
public class Factura {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "facturaid")
	private Integer id;

	@Column(name = "estado", length = 30)
	private String estado;

	@Column(name = "fecha_emision")
	private LocalDateTime fechaEmision;

	@Column(name = "monto_total", precision = 12, scale = 2)
	private BigDecimal montoTotal;

	@Column(name = "nit_ci", length = 20)
	private String nitCi;

	@Column(name = "numero", length = 50)
	private String numero;

	@Column(name = "razon_social", length = 150)
	private String razonSocial;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ventaid")
	private Venta venta;
}

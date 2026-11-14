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

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "detalle_compra")
@Getter
@Setter
public class DetalleCompra {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "detalle_compraid")
	private Integer id;

	@Column(name = "cantidad", precision = 12, scale = 2)
	private BigDecimal cantidad;

	@Column(name = "precio_pagado", precision = 12, scale = 2)
	private BigDecimal precioPagado;

	@Column(name = "subtotal", precision = 12, scale = 2)
	private BigDecimal subtotal;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "compraid")
	private Compra compra;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lote_asociacionid")
	private LoteAsociacion loteAsociacion;
}

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
@Table(name = "item_carrito")
@Getter
@Setter
public class ItemCarrito {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "item_carritoid")
	private Integer id;

	@Column(name = "cantidad", precision = 12, scale = 2)
	private BigDecimal cantidad;

	@Column(name = "fecha_agregado")
	private LocalDateTime fechaAgregado;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clienteid")
	private Cliente cliente;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productoid")
	private Producto producto;
}

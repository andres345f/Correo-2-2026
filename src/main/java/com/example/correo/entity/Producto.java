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
@Table(name = "producto")
@Getter
@Setter
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "productoid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "descripcion", columnDefinition = "text")
	private String descripcion;

	@Column(name = "fecha_creacion")
	private LocalDateTime fechaCreacion;

	@Column(name = "imagen_url", columnDefinition = "text")
	private String imagenUrl;

	@Column(name = "maneja_vencimiento")
	private Boolean manejaVencimiento;

	@Column(name = "nombre", length = 150)
	private String nombre;

	@Column(name = "precio_venta", precision = 12, scale = 2)
	private BigDecimal precioVenta;

	@Column(name = "presentacion", length = 50)
	private String presentacion;

	@Column(name = "stock_minimo", precision = 12, scale = 2)
	private BigDecimal stockMinimo;

	@Column(name = "unidad_medida", length = 255)
	private String unidadMedida;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "categoriaid")
	private Categoria categoria;
}

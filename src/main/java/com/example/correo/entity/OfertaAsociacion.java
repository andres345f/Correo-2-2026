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
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "oferta_asociacion")
@Getter
@Setter
public class OfertaAsociacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "oferta_asociacionid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "cantidad_prevista", precision = 12, scale = 2)
	private BigDecimal cantidadPrevista;

	@Column(name = "disponible_desde")
	private LocalDate disponibleDesde;

	@Column(name = "fecha_actualizacion")
	private LocalDateTime fechaActualizacion;

	@Column(name = "precio_previsto", precision = 12, scale = 2)
	private BigDecimal precioPrevisto;

	@Column(name = "tiempo_entrega_horas")
	private Integer tiempoEntregaHoras;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "asociacionid")
	private Asociacion asociacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productoid")
	private Producto producto;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "encargado_asociacionid")
	private EncargadoAsociacion encargadoAsociacion;
}

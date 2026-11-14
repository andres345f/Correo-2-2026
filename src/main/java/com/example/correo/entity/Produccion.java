package com.example.correo.entity;

import com.example.correo.entity.enums.EstadoProduccion;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "produccion")
@Getter
@Setter
public class Produccion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "produccionid")
	private Integer id;

	@Column(name = "cantidad_estimada", precision = 12, scale = 2)
	private BigDecimal cantidadEstimada;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado", columnDefinition = "estado_produccion")
	private EstadoProduccion estado;

	@Column(name = "fecha_cosecha_estimada")
	private LocalDate fechaCosechaEstimada;

	@Column(name = "fecha_registro")
	private LocalDateTime fechaRegistro;

	@Column(name = "fecha_siembra")
	private LocalDate fechaSiembra;

	@Column(name = "observacion", columnDefinition = "text")
	private String observacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuarioid")
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productorid")
	private Productor productor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productoid")
	private Producto producto;
}

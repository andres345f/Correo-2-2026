package com.example.correo.entity;

import com.example.correo.entity.enums.DimensionIndicador;

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
@Table(name = "indicador_financiero")
@Getter
@Setter
public class IndicadorFinanciero {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "indicador_financieroid")
	private Integer id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "dimension", columnDefinition = "dimension_indicador")
	private DimensionIndicador dimension;

	@Column(name = "fecha_calculo")
	private LocalDateTime fechaCalculo;

	@Column(name = "periodo_desde")
	private LocalDate periodoDesde;

	@Column(name = "periodo_hasta")
	private LocalDate periodoHasta;

	@Column(name = "tipo", length = 30)
	private String tipo;

	@Column(name = "unidad", length = 30)
	private String unidad;

	@Column(name = "valor", precision = 12, scale = 2)
	private BigDecimal valor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "asociacionid")
	private Asociacion asociacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "municipioid")
	private Municipio municipio;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productoid")
	private Producto producto;
}

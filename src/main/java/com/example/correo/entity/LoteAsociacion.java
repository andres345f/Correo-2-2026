package com.example.correo.entity;

import com.example.correo.entity.enums.EstadoValidacionLote;

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
@Table(name = "lote_asociacion")
@Getter
@Setter
public class LoteAsociacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "lote_asociacionid")
	private Integer id;

	@Column(name = "cantidad_disponible", precision = 12, scale = 2)
	private BigDecimal cantidadDisponible;

	@Column(name = "cantidad_inicial", precision = 12, scale = 2)
	private BigDecimal cantidadInicial;

	@Column(name = "codigo_lote", length = 50)
	private String codigoLote;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado_validacion", columnDefinition = "estado_validacion_lote")
	private EstadoValidacionLote estadoValidacion;

	@Column(name = "fecha_registro")
	private LocalDateTime fechaRegistro;

	@Column(name = "fecha_validacion")
	private LocalDateTime fechaValidacion;

	@Column(name = "fecha_vencimiento")
	private LocalDate fechaVencimiento;

	@Column(name = "precio_ofrecido", precision = 12, scale = 2)
	private BigDecimal precioOfrecido;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "asociacionid")
	private Asociacion asociacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "encargado_asociacionid")
	private EncargadoAsociacion encargadoAsociacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inventarioid")
	private Inventario inventario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuarioid")
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "municipioid")
	private Municipio municipio;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productorid")
	private Productor productor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productoid")
	private Producto producto;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "produccionid")
	private Produccion produccion;
}

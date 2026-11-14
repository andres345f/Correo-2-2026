package com.example.correo.entity;

import com.example.correo.entity.enums.MotivoMovimientoFeder;
import com.example.correo.entity.enums.TipoMovimientoFeder;

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
@Table(name = "movimiento_feder")
@Getter
@Setter
public class MovimientoFeder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "movimiento_federid")
	private Integer id;

	@Column(name = "cantidad", precision = 12, scale = 2)
	private BigDecimal cantidad;

	@Column(name = "clave_operacion", length = 50)
	private String claveOperacion;

	@Column(name = "fecha")
	private LocalDateTime fecha;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "motivo", columnDefinition = "motivo_movimiento_feder")
	private MotivoMovimientoFeder motivo;

	@Column(name = "observacion", columnDefinition = "text")
	private String observacion;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "tipo", columnDefinition = "tipo_movimiento_feder")
	private TipoMovimientoFeder tipo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "detalle_ventaid")
	private DetalleVenta detalleVenta;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "devolucionid")
	private Devolucion devolucion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empleado_federid")
	private EmpleadoFeder empleadoFeder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lote_federid")
	private LoteFeder loteFeder;
}

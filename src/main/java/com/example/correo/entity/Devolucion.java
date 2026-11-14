package com.example.correo.entity;

import com.example.correo.entity.enums.EstadoDevolucion;
import com.example.correo.entity.enums.MotivoDevolucion;
import com.example.correo.entity.enums.ResolucionDevolucion;

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
@Table(name = "devolucion")
@Getter
@Setter
public class Devolucion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "devolucionid")
	private Integer id;

	@Column(name = "apto_reventa")
	private Boolean aptoReventa;

	@Column(name = "cantidad", precision = 12, scale = 2)
	private BigDecimal cantidad;

	@Column(name = "descripcion", columnDefinition = "text")
	private String descripcion;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado", columnDefinition = "estado_devolucion")
	private EstadoDevolucion estado;

	@Column(name = "fecha_reembolso")
	private LocalDateTime fechaReembolso;

	@Column(name = "fecha_reporte")
	private LocalDateTime fechaReporte;

	@Column(name = "justificacion", columnDefinition = "text")
	private String justificacion;

	@Column(name = "monto_reembolso", precision = 12, scale = 2)
	private BigDecimal montoReembolso;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "motivo", columnDefinition = "motivo_devolucion")
	private MotivoDevolucion motivo;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "resolucion", columnDefinition = "resolucion_devolucion")
	private ResolucionDevolucion resolucion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "detalle_ventaid")
	private DetalleVenta detalleVenta;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empleado_federid")
	private EmpleadoFeder empleadoFeder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "despacho_movimientoid")
	private MovimientoFeder despacho;
}

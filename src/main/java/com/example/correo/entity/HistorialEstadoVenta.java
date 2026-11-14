package com.example.correo.entity;

import com.example.correo.entity.enums.EstadoVenta;

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

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "historial_estado_venta")
@Getter
@Setter
public class HistorialEstadoVenta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "historial_estado_ventaid")
	private Integer id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado_anterior", columnDefinition = "estado_venta")
	private EstadoVenta estadoAnterior;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado_nuevo", columnDefinition = "estado_venta")
	private EstadoVenta estadoNuevo;

	@Column(name = "fecha")
	private LocalDateTime fecha;

	@Column(name = "secuencia")
	private Integer secuencia;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empleado_federid")
	private EmpleadoFeder empleadoFeder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ventaid")
	private Venta venta;
}

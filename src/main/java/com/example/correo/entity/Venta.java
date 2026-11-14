package com.example.correo.entity;

import com.example.correo.entity.enums.EstadoVenta;
import com.example.correo.entity.enums.TipoVenta;

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
@Table(name = "venta")
@Getter
@Setter
public class Venta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ventaid")
	private Integer id;

	@Column(name = "codigo", length = 50)
	private String codigo;

	@Column(name = "costo_envio", precision = 12, scale = 2)
	private BigDecimal costoEnvio;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado", columnDefinition = "estado_venta")
	private EstadoVenta estado;

	@Column(name = "fecha")
	private LocalDateTime fecha;

	@Column(name = "fecha_actualizacion")
	private LocalDateTime fechaActualizacion;

	@Column(name = "subtotal", precision = 12, scale = 2)
	private BigDecimal subtotal;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "tipo", columnDefinition = "tipo_venta")
	private TipoVenta tipo;

	@Column(name = "total", precision = 12, scale = 2)
	private BigDecimal total;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clienteid")
	private Cliente cliente;

	// Corrección: antes empleado_federid servía para "atiende" y para "último cambio", lo que obligaba
	// a que fueran el mismo empleado. Esta columna queda para "atiende" y el último cambio tiene la suya.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empleado_federid")
	private EmpleadoFeder empleadoFeder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empleado_ultimo_cambioid")
	private EmpleadoFeder empleadoUltimoCambio;
}

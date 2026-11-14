package com.example.correo.entity;

import com.example.correo.entity.enums.EstadoEntrega;
import com.example.correo.entity.enums.ModalidadEntrega;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "entrega")
@Getter
@Setter
public class Entrega {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "entregaid")
	private Integer id;

	@Column(name = "costo_real", precision = 12, scale = 2)
	private BigDecimal costoReal;

	@Column(name = "direccion", length = 255)
	private String direccion;

	@Column(name = "distancia_km", precision = 8, scale = 2)
	private BigDecimal distanciaKm;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado", columnDefinition = "estado_entrega")
	private EstadoEntrega estado;

	@Column(name = "fecha_entrega")
	private LocalDateTime fechaEntrega;

	@Column(name = "fecha_programada")
	private LocalDateTime fechaProgramada;

	@Column(name = "fecha_salida")
	private LocalDateTime fechaSalida;

	@ColumnTransformer(read = "geom::text", write = "?::point")
	@Column(name = "geom", columnDefinition = "point")
	private String geom;

	@Column(name = "latitud", precision = 9, scale = 6)
	private BigDecimal latitud;

	@Column(name = "longitud", precision = 9, scale = 6)
	private BigDecimal longitud;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "modalidad", columnDefinition = "modalidad_entrega")
	private ModalidadEntrega modalidad;

	@Column(name = "observacion", columnDefinition = "text")
	private String observacion;

	@Column(name = "referencia", length = 255)
	private String referencia;

	@Column(name = "telefono_contacto", length = 20)
	private String telefonoContacto;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empleado_federid")
	private EmpleadoFeder empleadoFeder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inventarioid")
	private Inventario inventario;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ventaid")
	private Venta venta;
}

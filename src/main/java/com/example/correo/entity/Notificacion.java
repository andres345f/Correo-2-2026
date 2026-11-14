package com.example.correo.entity;

import com.example.correo.entity.enums.CanalNotificacion;
import com.example.correo.entity.enums.EstadoEnvio;

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
@Table(name = "notificacion")
@Getter
@Setter
public class Notificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "notificacionid")
	private Integer id;

	@Column(name = "asunto", length = 150)
	private String asunto;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "canal", columnDefinition = "canal_notificacion")
	private CanalNotificacion canal;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "estado_envio", columnDefinition = "estado_envio")
	private EstadoEnvio estadoEnvio;

	@Column(name = "fecha")
	private LocalDateTime fecha;

	@Column(name = "fecha_lectura")
	private LocalDateTime fechaLectura;

	@Column(name = "leido")
	private Boolean leido;

	@Column(name = "mensaje", columnDefinition = "text")
	private String mensaje;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "clienteid")
	private Cliente cliente;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ventaid")
	private Venta venta;
}

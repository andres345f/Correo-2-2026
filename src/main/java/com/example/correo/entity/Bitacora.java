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

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bitacora")
@Getter
@Setter
public class Bitacora {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "bitacoraid")
	private Integer id;

	@Column(name = "accion", length = 50)
	private String accion;

	@Column(name = "entidad", length = 50)
	private String entidad;

	@Column(name = "entidad_id")
	private Integer entidadId;

	@Column(name = "fecha")
	private LocalDateTime fecha;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuarioid")
	private Usuario usuario;
}

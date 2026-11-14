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
@Table(name = "productor")
@Getter
@Setter
public class Productor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "productorid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "comunidad", length = 100)
	private String comunidad;

	@Column(name = "fecha_registro")
	private LocalDateTime fechaRegistro;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "asociacionid")
	private Asociacion asociacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "personaid")
	private Persona persona;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuarioid")
	private Usuario usuario;
}

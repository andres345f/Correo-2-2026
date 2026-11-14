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

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "empleado_feder")
@Getter
@Setter
public class EmpleadoFeder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "empleado_federid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "cargo", length = 100)
	private String cargo;

	@Column(name = "fecha_contratacion")
	private LocalDate fechaContratacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuarioid")
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "personaid")
	private Persona persona;
}

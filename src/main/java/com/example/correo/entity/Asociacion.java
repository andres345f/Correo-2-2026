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
@Table(name = "asociacion")
@Getter
@Setter
public class Asociacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "asociacionid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "correo", length = 255)
	private String correo;

	@Column(name = "direccion", length = 255)
	private String direccion;

	@Column(name = "fecha_registro")
	private LocalDateTime fechaRegistro;

	@Column(name = "nombre", length = 150)
	private String nombre;

	@Column(name = "telefono", length = 20)
	private String telefono;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "municipioid")
	private Municipio municipio;
}

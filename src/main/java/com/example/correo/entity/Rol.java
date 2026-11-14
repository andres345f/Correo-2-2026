package com.example.correo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rol")
@Getter
@Setter
public class Rol {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "rolid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "descripcion", columnDefinition = "text")
	private String descripcion;

	@Column(name = "nombre", length = 150)
	private String nombre;
}

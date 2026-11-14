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
@Table(name = "municipio")
@Getter
@Setter
public class Municipio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "municipioid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "departamento", length = 100)
	private String departamento;

	@Column(name = "nombre", length = 150)
	private String nombre;
}

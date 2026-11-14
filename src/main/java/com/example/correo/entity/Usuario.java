package com.example.correo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "usuarioid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "correo", length = 255)
	private String correo;

	@Column(name = "fecha_creacion")
	private LocalDateTime fechaCreacion;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(name = "ultimo_acceso")
	private LocalDateTime ultimoAcceso;
}

package com.example.correo.entity;

import com.example.correo.entity.enums.TipoInventario;

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
@Table(name = "inventario")
@Getter
@Setter
public class Inventario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "inventarioid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "codigo", length = 50)
	private String codigo;

	@Column(name = "es_principal")
	private Boolean esPrincipal;

	@Column(name = "fecha_creacion")
	private LocalDateTime fechaCreacion;

	@Column(name = "nombre", length = 150)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "tipo", columnDefinition = "tipo_inventario")
	private TipoInventario tipo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "asociacionid")
	private Asociacion asociacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productorid")
	private Productor productor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ubicacionid")
	private Ubicacion ubicacion;
}

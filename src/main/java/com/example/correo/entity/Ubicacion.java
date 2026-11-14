package com.example.correo.entity;

import com.example.correo.entity.enums.TipoUbicacion;

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

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ubicacion")
@Getter
@Setter
public class Ubicacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ubicacionid")
	private Integer id;

	@Column(name = "activo")
	private Boolean activo;

	@Column(name = "comunidad", length = 100)
	private String comunidad;

	@Column(name = "direccion", length = 255)
	private String direccion;

	@ColumnTransformer(read = "geom::text", write = "?::point")
	@Column(name = "geom", columnDefinition = "point")
	private String geom;

	@Column(name = "latitud", precision = 9, scale = 6)
	private BigDecimal latitud;

	@Column(name = "longitud", precision = 9, scale = 6)
	private BigDecimal longitud;

	@Column(name = "nombre", length = 150)
	private String nombre;

	@Column(name = "referencia", length = 255)
	private String referencia;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "tipo", columnDefinition = "tipo_ubicacion")
	private TipoUbicacion tipo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "municipioid")
	private Municipio municipio;
}

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

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cambio_bitacora")
@Getter
@Setter
public class CambioBitacora {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "cambio_bitacoraid")
	private Integer id;

	@Column(name = "ruta_campo", length = 255)
	private String rutaCampo;

	@Column(name = "valor_anterior", columnDefinition = "text")
	private String valorAnterior;

	@Column(name = "valor_nuevo", columnDefinition = "text")
	private String valorNuevo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bitacoraid")
	private Bitacora bitacora;
}

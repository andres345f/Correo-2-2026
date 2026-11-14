package com.example.correo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lote_feder")
@Getter
@Setter
public class LoteFeder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "lote_federid")
	private Integer id;

	@Column(name = "cantidad_actual", precision = 12, scale = 2)
	private BigDecimal cantidadActual;

	@Column(name = "cantidad_reservada", precision = 12, scale = 2)
	private BigDecimal cantidadReservada;

	@Column(name = "codigo_lote", length = 50)
	private String codigoLote;

	@Column(name = "fecha_ingreso")
	private LocalDate fechaIngreso;

	@Column(name = "fecha_vencimiento")
	private LocalDate fechaVencimiento;

	@Column(name = "precio_adquisicion", precision = 12, scale = 2)
	private BigDecimal precioAdquisicion;

	@Version
	@Column(name = "version")
	private Integer version;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "detalle_compraid")
	private DetalleCompra detalleCompra;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inventarioid")
	private Inventario inventario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productoid")
	private Producto producto;
}

package com.example.correo.entity;

import com.example.correo.entity.enums.TipoMovimientoInventario;

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
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "movimiento_inventario")
@Getter
@Setter
public class MovimientoInventario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "movimiento_inventarioid")
	private Integer id;

	@Column(name = "cantidad", precision = 12, scale = 2)
	private BigDecimal cantidad;

	@Column(name = "clave_operacion", length = 50)
	private String claveOperacion;

	@Column(name = "fecha")
	private LocalDateTime fecha;

	@Column(name = "observacion", columnDefinition = "text")
	private String observacion;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "tipo", columnDefinition = "tipo_movimiento_inventario")
	private TipoMovimientoInventario tipo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "detalle_compraid")
	private DetalleCompra detalleCompra;

	// Corrección: antes "sale de" y "llega a" compartían la columna inventarioid, lo que obligaba
	// a que origen y destino fueran el mismo inventario. Ahora cada extremo tiene su columna.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inventario_origenid")
	private Inventario inventarioOrigen;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inventario_destinoid")
	private Inventario inventarioDestino;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lote_asociacionid")
	private LoteAsociacion loteAsociacion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuarioid")
	private Usuario usuario;
}

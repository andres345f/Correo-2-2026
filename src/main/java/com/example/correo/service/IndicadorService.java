package com.example.correo.service;

import com.example.correo.dto.Resultado;
import com.example.correo.entity.Asociacion;
import com.example.correo.entity.Bitacora;
import com.example.correo.entity.CambioBitacora;
import com.example.correo.entity.Devolucion;
import com.example.correo.entity.Entrega;
import com.example.correo.entity.HistorialEstadoVenta;
import com.example.correo.entity.IndicadorFinanciero;
import com.example.correo.entity.LoteAsociacion;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.Municipio;
import com.example.correo.entity.Producto;
import com.example.correo.entity.Venta;
import com.example.correo.entity.enums.DimensionIndicador;
import com.example.correo.entity.enums.EstadoVenta;
import com.example.correo.entity.enums.MotivoMovimientoFeder;
import com.example.correo.entity.enums.TipoMovimientoFeder;
import com.example.correo.entity.enums.TipoVenta;
import com.example.correo.repository.IndicadorConsultasRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/** CU7: mediciones al corte. Cada calculo crea un corte nuevo y conserva los anteriores. */
@Service
public class IndicadorService {

	private final IndicadorConsultasRepository consultas;

	public enum Metrica {
		CAJA_NETA, INGRESO_RECONOCIDO, MARGEN, UNIDADES_VENDIDAS, INVENTARIO
	}

	/** Un hecho economico imputado al origen real de las unidades. */
	public record Imputacion(LocalDate fecha, Producto producto, Asociacion asociacion, Municipio municipio, BigDecimal unidades, BigDecimal ingreso,
			BigDecimal costo, BigDecimal envio, BigDecimal reparto, boolean repartoPendiente) {
		public BigDecimal margen() {
			return ingreso.subtract(costo).add(envio).subtract(reparto);
		}
	}

	private final Datos datos;

	public IndicadorService(Datos datos, IndicadorConsultasRepository consultas) {
		this.consultas = consultas;
		this.datos = datos;
	}

	private Imputacion imputar(LocalDate fecha, LoteFeder lote, BigDecimal unidades, BigDecimal ingreso, BigDecimal costo, BigDecimal envio, BigDecimal reparto, boolean pendiente) {
		LoteAsociacion origen = lote.getDetalleCompra().getLoteAsociacion();
		return new Imputacion(fecha, lote.getProducto(), origen.getAsociacion(), origen.getMunicipio(), unidades, ingreso, costo, envio, reparto, pendiente);
	}

	/** Ingreso y costo se reconocen al entregar de verdad; los ajustes, en su propia fecha. */
	public List<Imputacion> imputaciones(LocalDate desde, LocalDate hasta) {
		LocalDateTime inicio = desde.atStartOfDay();
		LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
		List<Imputacion> hechos = new ArrayList<>();
		consultas.historialEnPeriodo(EstadoVenta.ENTREGADO, inicio, fin)
				.forEach(h -> imputarVenta(h, hechos));
		imputarReembolsos(inicio, fin, hechos);
		consultas.movimientosEnPeriodo(List.of(MotivoMovimientoFeder.DEVOLUCION, MotivoMovimientoFeder.REPOSICION), inicio, fin)
				.forEach(m -> {
					BigDecimal valor = m.getCantidad().multiply(m.getLoteFeder().getPrecioAdquisicion());
					BigDecimal costo = m.getMotivo() == MotivoMovimientoFeder.DEVOLUCION ? valor.negate() : valor;
					hechos.add(imputar(m.getFecha().toLocalDate(), m.getLoteFeder(), BigDecimal.ZERO, BigDecimal.ZERO, costo, BigDecimal.ZERO, BigDecimal.ZERO, false));
				});
		return hechos;
	}

	private void imputarVenta(HistorialEstadoVenta paso, List<Imputacion> hechos) {
		Venta venta = paso.getVenta();
		Entrega entrega = consultas.entregasDeVenta(venta.getId()).stream().findFirst().orElse(null);
		// Una venta de mostrador no tiene reparto; una entrega sin costo registrado no acredita cero.
		boolean pendiente = venta.getTipo() != TipoVenta.INMEDIATA && (entrega == null || entrega.getCostoReal() == null);
		BigDecimal reparto = entrega == null ? BigDecimal.ZERO : Datos.cero(entrega.getCostoReal());
		for (MovimientoFeder salida : consultas.movimientosDeVenta(MotivoMovimientoFeder.VENTA, venta.getId())) {
			BigDecimal ingreso = salida.getCantidad().multiply(salida.getDetalleVenta().getPrecioUnitario());
			BigDecimal peso = venta.getSubtotal().signum() == 0 ? BigDecimal.ZERO : ingreso.divide(venta.getSubtotal(), 10, RoundingMode.HALF_UP);
			BigDecimal costo = salida.getCantidad().multiply(salida.getLoteFeder().getPrecioAdquisicion());
			hechos.add(imputar(paso.getFecha().toLocalDate(), salida.getLoteFeder(), salida.getCantidad(), ingreso, costo,
					Datos.cero(venta.getCostoEnvio()).multiply(peso), reparto.multiply(peso), pendiente));
		}
	}

	private void imputarReembolsos(LocalDateTime inicio, LocalDateTime fin, List<Imputacion> hechos) {
		for (Bitacora evento : consultas.eventosContablesEnPeriodo(DevolucionService.CONTABILIZA, inicio, fin)) {
			Devolucion caso = datos.buscar(Devolucion.class, evento.getEntidadId());
			CambioBitacora monto = consultas.cambioDeEvento(evento.getId(), DevolucionService.RUTA_MONTO);
			hechos.add(imputar(evento.getFecha().toLocalDate(), caso.getDespacho().getLoteFeder(), BigDecimal.ZERO, new BigDecimal(monto.getValorNuevo()).negate(),
					BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
		}
	}

	private BigDecimal sumar(List<Imputacion> hechos, Function<Imputacion, BigDecimal> valor) {
		return hechos.stream().map(valor).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/** Cobros conciliados menos reversiones y reembolsos, cada uno en la fecha de su liquidacion. */
	private BigDecimal cajaNeta(LocalDate desde, LocalDate hasta) {
		LocalDateTime inicio = desde.atStartOfDay();
		LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
		BigDecimal cobros = consultas.cobros(inicio, fin);
		BigDecimal reversos = consultas.reversos(inicio, fin);
		BigDecimal reembolsos = consultas.reembolsos(inicio, fin);
		return cobros.subtract(reversos).subtract(reembolsos);
	}

	/** Saldo fisico al final del periodo: suma firmada del libro comercial. */
	private BigDecimal inventarioAlCorte(LocalDate hasta, Predicate<LoteFeder> ambito) {
		return consultas.movimientosAntesDe(hasta.plusDays(1).atStartOfDay()).stream()
				.filter(m -> ambito.test(m.getLoteFeder()))
				.map(m -> m.getTipo() == TipoMovimientoFeder.SALIDA || m.getTipo() == TipoMovimientoFeder.BAJA ? m.getCantidad().negate() : m.getCantidad())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public Resultado calcular(Actor actor, Args p) {
		Metrica metrica = p.enumerado(0, Metrica.class);
		DimensionIndicador dimension = p.enumerado(1, DimensionIndicador.class);
		LocalDate desde = p.fecha(2);
		LocalDate hasta = p.fecha(3);
		if (desde.isAfter(hasta)) {
			throw Datos.regla("El periodo esta invertido.");
		}
		if (dimension == DimensionIndicador.GLOBAL && !p.vacio(4)) {
			throw Datos.regla("GLOBAL no lleva ambito.");
		}
		if (metrica == Metrica.CAJA_NETA && dimension != DimensionIndicador.GLOBAL) {
			throw Datos.regla("La caja neta solo se mide en GLOBAL: un cobro no pertenece a un origen.");
		}
		Integer ambito = dimension == DimensionIndicador.GLOBAL ? null : p.id(4);
		IndicadorFinanciero medicion = new IndicadorFinanciero();
		medicion.setDimension(dimension);
		switch (dimension) {
			case PRODUCTO -> medicion.setProducto(datos.buscar(Producto.class, ambito));
			case ASOCIACION -> medicion.setAsociacion(datos.buscar(Asociacion.class, ambito));
			case MUNICIPIO -> medicion.setMunicipio(datos.buscar(Municipio.class, ambito));
			default -> {
			}
		}
		medicion.setTipo(metrica.name());
		medicion.setPeriodoDesde(desde);
		medicion.setPeriodoHasta(hasta);
		medicion.setFechaCalculo(LocalDateTime.now());
		medicion.setUnidad(metrica == Metrica.UNIDADES_VENDIDAS || metrica == Metrica.INVENTARIO ? "unidades" : "Bs");
		medicion.setValor(valor(metrica, dimension, ambito, desde, hasta).setScale(2, RoundingMode.HALF_UP));
		datos.guardar(medicion);
		return Resultado.exito(String.format(Locale.ROOT, "%s (%s) del %s al %s: %s %s. Corte %d.", metrica, dimension, desde, hasta, medicion.getValor(), medicion.getUnidad(), medicion.getId()));
	}

	private BigDecimal valor(Metrica metrica, DimensionIndicador dimension, Integer ambito, LocalDate desde, LocalDate hasta) {
		if (metrica == Metrica.CAJA_NETA) {
			return cajaNeta(desde, hasta);
		}
		if (metrica == Metrica.INVENTARIO) {
			return inventarioAlCorte(hasta, lote -> dentroDe(dimension, ambito, lote.getProducto().getId(),
					lote.getDetalleCompra().getLoteAsociacion().getAsociacion().getId(), lote.getDetalleCompra().getLoteAsociacion().getMunicipio().getId()));
		}
		List<Imputacion> hechos = imputaciones(desde, hasta).stream()
				.filter(h -> dentroDe(dimension, ambito, h.producto().getId(), h.asociacion().getId(), h.municipio().getId())).toList();
		return switch (metrica) {
			case INGRESO_RECONOCIDO -> sumar(hechos, Imputacion::ingreso);
			case UNIDADES_VENDIDAS -> sumar(hechos, Imputacion::unidades);
			default -> {
				if (hechos.stream().anyMatch(Imputacion::repartoPendiente)) {
					throw Datos.regla("Hay entregas sin costo real registrado: registrelo con COSENT, incluso si fue cero.");
				}
				yield sumar(hechos, Imputacion::margen);
			}
		};
	}

	private boolean dentroDe(DimensionIndicador dimension, Integer ambito, Integer producto, Integer asociacion, Integer municipio) {
		return switch (dimension) {
			case GLOBAL -> true;
			case PRODUCTO -> ambito.equals(producto);
			case ASOCIACION -> ambito.equals(asociacion);
			case MUNICIPIO -> ambito.equals(municipio);
		};
	}

}

package com.example.correo.service;

import com.example.correo.dto.FormatoReporte;
import com.example.correo.dto.Informe;
import com.example.correo.dto.Resultado;
import com.example.correo.entity.LoteAsociacion;
import com.example.correo.entity.Producto;
import com.example.correo.repository.ReporteConsultasRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** CU8: reportes y estadisticas con la misma consulta en correo, PDF y Excel. */
@Service
public class ReporteService {

	private final ReporteConsultasRepository consultas;

	/** Acumula columnas numericas por grupo y agrega la fila de totales. */
	private static final class Agrupador {
		private final int columnas;
		private final Map<String, BigDecimal[]> grupos = new TreeMap<>();

		Agrupador(int columnas) {
			this.columnas = columnas;
		}

		void sumar(String grupo, BigDecimal... valores) {
			BigDecimal[] acumulado = grupos.computeIfAbsent(grupo, g -> {
				BigDecimal[] ceros = new BigDecimal[columnas];
				java.util.Arrays.fill(ceros, BigDecimal.ZERO);
				return ceros;
			});
			for (int i = 0; i < columnas; i++) {
				acumulado[i] = acumulado[i].add(valores[i]);
			}
		}

		List<List<String>> filas() {
			List<List<String>> filas = new ArrayList<>();
			BigDecimal[] total = new BigDecimal[columnas];
			java.util.Arrays.fill(total, BigDecimal.ZERO);
			grupos.forEach((grupo, valores) -> {
				List<String> fila = new ArrayList<>(List.of(grupo));
				for (int i = 0; i < columnas; i++) {
					fila.add(texto(valores[i]));
					total[i] = total[i].add(valores[i]);
				}
				filas.add(fila);
			});
			if (!filas.isEmpty()) {
				List<String> fila = new ArrayList<>(List.of("TOTAL"));
				for (BigDecimal valor : total) {
					fila.add(texto(valor));
				}
				filas.add(fila);
			}
			return filas;
		}

		private static String texto(BigDecimal valor) {
			return valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
		}
	}

	private final Datos datos;
	private final IndicadorService indicadores;

	public ReporteService(Datos datos, IndicadorService indicadores, ReporteConsultasRepository consultas) {
		this.consultas = consultas;
		this.datos = datos;
		this.indicadores = indicadores;
	}

	private LocalDate desde(Args p) {
		return p.fechaOpcional(0) == null ? LocalDate.of(2000, 1, 1) : p.fecha(0);
	}

	private LocalDate hasta(Args p) {
		return p.fechaOpcional(1) == null ? LocalDate.now() : p.fecha(1);
	}

	private String periodo(Args p) {
		return "Periodo: " + desde(p) + " a " + hasta(p);
	}

	private BigDecimal uno() {
		return BigDecimal.ONE;
	}

	private String grupoOrigen(String criterio, IndicadorService.Imputacion h) {
		return switch (criterio) {
			case "ASOCIACION" -> h.asociacion().getNombre();
			case "MUNICIPIO" -> h.municipio().getNombre();
			case "DIA" -> h.fecha().toString();
			case "PRODUCTO" -> h.producto().getNombre();
			default -> throw Datos.regla("Agrupe por PRODUCTO, ASOCIACION, MUNICIPIO o DIA.");
		};
	}

	public Resultado ventas(Actor actor, Args p) {
		String criterio = p.vacio(2) ? "PRODUCTO" : p.texto(2).toUpperCase();
		Agrupador grupos = new Agrupador(5);
		for (IndicadorService.Imputacion h : indicadores.imputaciones(desde(p), hasta(p))) {
			grupos.sumar(grupoOrigen(criterio, h), h.unidades(), h.ingreso(), h.costo(), h.envio().subtract(h.reparto()), h.margen());
		}
		return responder(p, 3, new Informe("Ventas entregadas por " + criterio, periodo(p) + " | Moneda: Bs",
				List.of(criterio, "Unidades", "Ingreso", "Costo", "Envio neto", "Margen"), grupos.filas()));
	}

	public Resultado inventario(Actor actor, Args p) {
		Agrupador grupos = new Agrupador(3);
		consultas.lotesFeder()
				.forEach(l -> grupos.sumar(l.getProducto().getNombre(), l.getCantidadActual(), l.getCantidadReservada(), l.getCantidadActual().subtract(l.getCantidadReservada())));
		List<List<String>> filas = new ArrayList<>(grupos.filas());
		Map<String, Producto> productos = new TreeMap<>();
		consultas.productos().forEach(c -> productos.put(c.getNombre(), c));
		filas.forEach(f -> f.add(productos.containsKey(f.get(0)) ? productos.get(f.get(0)).getStockMinimo().toPlainString() : ""));
		return responder(p, 3, new Informe("Inventario FEDER", "Saldos actuales | Unidades", List.of("Producto", "Fisico", "Reservado", "Libre", "Minimo"), filas));
	}

	public Resultado lotes(Actor actor, Args p) {
		String criterio = p.vacio(2) ? "ASOCIACION" : p.texto(2).toUpperCase();
		Function<LoteAsociacion, String> grupo = switch (criterio) {
			case "ASOCIACION" -> l -> l.getAsociacion().getNombre();
			case "MUNICIPIO" -> l -> l.getMunicipio().getNombre();
			case "PRODUCTO" -> l -> l.getProducto().getNombre();
			case "VALIDACION" -> l -> l.getEstadoValidacion().name();
			default -> throw Datos.regla("Agrupe por ASOCIACION, MUNICIPIO, PRODUCTO o VALIDACION.");
		};
		Agrupador grupos = new Agrupador(3);
		consultas.lotesRegistradosEnPeriodo(desde(p).atStartOfDay(), hasta(p).plusDays(1).atStartOfDay())
				.forEach(l -> grupos.sumar(grupo.apply(l), uno(), l.getCantidadInicial(), l.getCantidadDisponible()));
		return responder(p, 3, new Informe("Lotes de proveedores por " + criterio, periodo(p) + " | Unidades", List.of(criterio, "Lotes", "Inicial", "Saldo"), grupos.filas()));
	}

	public Resultado pagos(Actor actor, Args p) {
		Agrupador grupos = new Agrupador(3);
		consultas.pagosEnPeriodo(desde(p).atStartOfDay(), hasta(p).plusDays(1).atStartOfDay())
				.forEach(g -> grupos.sumar(g.getMetodoPago().name(), uno(), g.getMonto(), g.getFechaReversion() == null ? BigDecimal.ZERO : g.getMonto()));
		return responder(p, 3, new Informe("Cobros por metodo", periodo(p) + " | Moneda: Bs", List.of("Metodo", "Cobros", "Monto", "Revertido"), grupos.filas()));
	}

	public Resultado entregas(Actor actor, Args p) {
		Agrupador grupos = new Agrupador(3);
		consultas.entregasDeVentasEnPeriodo(desde(p).atStartOfDay(), hasta(p).plusDays(1).atStartOfDay())
				.forEach(e -> grupos.sumar(e.getModalidad() + " / " + e.getEstado(), uno(), Datos.cero(e.getVenta().getCostoEnvio()), Datos.cero(e.getCostoReal())));
		return responder(p, 3, new Informe("Entregas", periodo(p) + " | Moneda: Bs", List.of("Modalidad / estado", "Entregas", "Envio cobrado", "Costo real"), grupos.filas()));
	}

	public Resultado devoluciones(Actor actor, Args p) {
		Agrupador grupos = new Agrupador(3);
		consultas.devolucionesEnPeriodo(desde(p).atStartOfDay(), hasta(p).plusDays(1).atStartOfDay())
				.forEach(d -> grupos.sumar(d.getMotivo() + " / " + d.getEstado(), uno(), d.getCantidad(), Datos.cero(d.getMontoReembolso())));
		return responder(p, 3, new Informe("Devoluciones", periodo(p) + " | Moneda: Bs", List.of("Motivo / estado", "Casos", "Unidades", "Reembolsado"), grupos.filas()));
	}

	public Resultado indicadoresGuardados(Actor actor, Args p) {
		String tipo = p.opcional(2);
		List<List<String>> filas = consultas.indicadoresEnPeriodo(desde(p), hasta(p)).stream()
				.filter(i -> tipo == null || i.getTipo().equalsIgnoreCase(tipo))
				.map(i -> List.of(String.valueOf(i.getId()), i.getTipo(), i.getDimension().name(), String.valueOf(i.getPeriodoDesde()), String.valueOf(i.getPeriodoHasta()), i.getValor().toPlainString(), i.getUnidad(), String.valueOf(i.getFechaCalculo())))
				.toList();
		return responder(p, 3, new Informe("Indicadores financieros", periodo(p) + (tipo == null ? "" : " | Tipo: " + tipo), List.of("Corte", "Tipo", "Dimension", "Desde", "Hasta", "Valor", "Unidad", "Calculado"), filas));
	}

	private Resultado responder(Args p, int indiceFormato, Informe informe) {
		FormatoReporte formato = p.vacio(indiceFormato) ? FormatoReporte.TODOS : p.enumerado(indiceFormato, FormatoReporte.class);
		return Resultado.informe(informe, formato);
	}
}

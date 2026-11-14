package com.example.correo.service;

import com.example.correo.entity.AlertaStock;
import com.example.correo.entity.Asociacion;
import com.example.correo.entity.Bitacora;
import com.example.correo.entity.CambioBitacora;
import com.example.correo.entity.Categoria;
import com.example.correo.entity.Cliente;
import com.example.correo.entity.Compra;
import com.example.correo.entity.DetalleCompra;
import com.example.correo.entity.DetalleVenta;
import com.example.correo.entity.Devolucion;
import com.example.correo.entity.EmpleadoFeder;
import com.example.correo.entity.EncargadoAsociacion;
import com.example.correo.entity.Entrega;
import com.example.correo.entity.Factura;
import com.example.correo.entity.HistorialEstadoVenta;
import com.example.correo.entity.IndicadorFinanciero;
import com.example.correo.entity.Inventario;
import com.example.correo.entity.ItemCarrito;
import com.example.correo.entity.LoteAsociacion;
import com.example.correo.entity.LoteFeder;
import com.example.correo.entity.MovimientoFeder;
import com.example.correo.entity.MovimientoInventario;
import com.example.correo.entity.Municipio;
import com.example.correo.entity.Notificacion;
import com.example.correo.entity.OfertaAsociacion;
import com.example.correo.entity.Pago;
import com.example.correo.entity.Persona;
import com.example.correo.entity.Produccion;
import com.example.correo.entity.Producto;
import com.example.correo.entity.Productor;
import com.example.correo.entity.ReservaStock;
import com.example.correo.entity.Rol;
import com.example.correo.entity.Ubicacion;
import com.example.correo.entity.Usuario;
import com.example.correo.entity.UsuarioRol;
import com.example.correo.entity.Venta;
import java.util.Map;
import static java.util.Map.entry;

/** Entidades disponibles para los casos de uso genericos. */
public final class CatalogoTablas {
	private CatalogoTablas() {}
	public static final Map<String, Class<?>> TABLAS = Map.ofEntries(
			entry("ALE", AlertaStock.class),
			entry("ASO", Asociacion.class),
			entry("BIT", Bitacora.class),
			entry("CAB", CambioBitacora.class),
			entry("CAR", ItemCarrito.class),
			entry("CAT", Categoria.class),
			entry("CLI", Cliente.class),
			entry("COM", Compra.class),
			entry("DCO", DetalleCompra.class),
			entry("DVE", DetalleVenta.class),
			entry("DEV", Devolucion.class),
			entry("EMP", EmpleadoFeder.class),
			entry("ENC", EncargadoAsociacion.class),
			entry("ENT", Entrega.class),
			entry("FAC", Factura.class),
			entry("HIS", HistorialEstadoVenta.class),
			entry("IND", IndicadorFinanciero.class),
			entry("INV", Inventario.class),
			entry("LOA", LoteAsociacion.class),
			entry("LOF", LoteFeder.class),
			entry("MOF", MovimientoFeder.class),
			entry("MOI", MovimientoInventario.class),
			entry("MUN", Municipio.class),
			entry("NOT", Notificacion.class),
			entry("OFE", OfertaAsociacion.class),
			entry("PAG", Pago.class),
			entry("PER", Persona.class),
			entry("PRD", Produccion.class),
			entry("PRO", Producto.class),
			entry("PTR", Productor.class),
			entry("RES", ReservaStock.class),
			entry("ROL", Rol.class),
			entry("UBI", Ubicacion.class),
			entry("URO", UsuarioRol.class),
			entry("USU", Usuario.class),
			entry("VEN", Venta.class));

}

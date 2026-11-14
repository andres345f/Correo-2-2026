/* Reglas del modelo FEDER que db.sql no trae. Se ejecuta despues de db.sql.
   No borra datos. Es todo o nada: si algun dato existente incumple una regla,
   no se aplica ninguna y PostgreSQL indica cual fallo. */

BEGIN;

/* Datos obligatorios */

ALTER TABLE Usuario ALTER COLUMN Correo SET NOT NULL, ALTER COLUMN Password_hash SET NOT NULL;
ALTER TABLE Rol ALTER COLUMN Nombre SET NOT NULL;
ALTER TABLE Usuario_rol ALTER COLUMN UsuarioID SET NOT NULL, ALTER COLUMN RolID SET NOT NULL;
ALTER TABLE Persona ALTER COLUMN Ci SET NOT NULL, ALTER COLUMN Nombre SET NOT NULL, ALTER COLUMN Apellido SET NOT NULL;
ALTER TABLE Cliente ALTER COLUMN PersonaID SET NOT NULL, ALTER COLUMN UsuarioID SET NOT NULL;
ALTER TABLE Empleado_feder ALTER COLUMN PersonaID SET NOT NULL, ALTER COLUMN UsuarioID SET NOT NULL;
ALTER TABLE Productor ALTER COLUMN PersonaID SET NOT NULL, ALTER COLUMN AsociacionID SET NOT NULL;
ALTER TABLE Encargado_asociacion ALTER COLUMN PersonaID SET NOT NULL, ALTER COLUMN UsuarioID SET NOT NULL,
	ALTER COLUMN AsociacionID SET NOT NULL, ALTER COLUMN Desde SET NOT NULL;
ALTER TABLE Municipio ALTER COLUMN Nombre SET NOT NULL;
ALTER TABLE Asociacion ALTER COLUMN Nombre SET NOT NULL, ALTER COLUMN MunicipioID SET NOT NULL;
ALTER TABLE Ubicacion ALTER COLUMN Nombre SET NOT NULL, ALTER COLUMN Tipo SET NOT NULL, ALTER COLUMN MunicipioID SET NOT NULL;
ALTER TABLE Inventario ALTER COLUMN Codigo SET NOT NULL, ALTER COLUMN Tipo SET NOT NULL, ALTER COLUMN UbicacionID SET NOT NULL;
ALTER TABLE Categoria ALTER COLUMN Nombre SET NOT NULL;
ALTER TABLE Producto ALTER COLUMN Nombre SET NOT NULL, ALTER COLUMN Precio_venta SET NOT NULL,
	ALTER COLUMN Stock_minimo SET NOT NULL, ALTER COLUMN CategoriaID SET NOT NULL;
ALTER TABLE Produccion ALTER COLUMN Cantidad_estimada SET NOT NULL, ALTER COLUMN Estado SET NOT NULL,
	ALTER COLUMN Fecha_siembra SET NOT NULL, ALTER COLUMN Fecha_cosecha_estimada SET NOT NULL,
	ALTER COLUMN UsuarioID SET NOT NULL, ALTER COLUMN ProductorID SET NOT NULL, ALTER COLUMN ProductoID SET NOT NULL;
ALTER TABLE Oferta_asociacion ALTER COLUMN AsociacionID SET NOT NULL, ALTER COLUMN ProductoID SET NOT NULL,
	ALTER COLUMN Encargado_asociacionID SET NOT NULL;
ALTER TABLE Lote_asociacion ALTER COLUMN Codigo_lote SET NOT NULL, ALTER COLUMN Cantidad_inicial SET NOT NULL,
	ALTER COLUMN Cantidad_disponible SET NOT NULL, ALTER COLUMN Precio_ofrecido SET NOT NULL,
	ALTER COLUMN Estado_validacion SET NOT NULL, ALTER COLUMN AsociacionID SET NOT NULL,
	ALTER COLUMN MunicipioID SET NOT NULL, ALTER COLUMN UsuarioID SET NOT NULL;
ALTER TABLE Movimiento_inventario ALTER COLUMN Tipo SET NOT NULL, ALTER COLUMN Cantidad SET NOT NULL,
	ALTER COLUMN Clave_operacion SET NOT NULL, ALTER COLUMN Lote_asociacionID SET NOT NULL, ALTER COLUMN UsuarioID SET NOT NULL;
ALTER TABLE Compra ALTER COLUMN Fecha SET NOT NULL, ALTER COLUMN AsociacionID SET NOT NULL, ALTER COLUMN Empleado_federID SET NOT NULL;
ALTER TABLE Detalle_compra ALTER COLUMN Cantidad SET NOT NULL, ALTER COLUMN Precio_pagado SET NOT NULL,
	ALTER COLUMN CompraID SET NOT NULL, ALTER COLUMN Lote_asociacionID SET NOT NULL;
ALTER TABLE Lote_feder ALTER COLUMN Codigo_lote SET NOT NULL, ALTER COLUMN Cantidad_actual SET NOT NULL,
	ALTER COLUMN Cantidad_reservada SET NOT NULL, ALTER COLUMN Detalle_compraID SET NOT NULL, ALTER COLUMN InventarioID SET NOT NULL;
ALTER TABLE Movimiento_feder ALTER COLUMN Tipo SET NOT NULL, ALTER COLUMN Motivo SET NOT NULL, ALTER COLUMN Cantidad SET NOT NULL,
	ALTER COLUMN Clave_operacion SET NOT NULL, ALTER COLUMN Lote_federID SET NOT NULL, ALTER COLUMN Empleado_federID SET NOT NULL;
ALTER TABLE Alerta_stock ALTER COLUMN ProductoID SET NOT NULL;
ALTER TABLE Item_carrito ALTER COLUMN Cantidad SET NOT NULL, ALTER COLUMN ClienteID SET NOT NULL, ALTER COLUMN ProductoID SET NOT NULL;
ALTER TABLE Venta ALTER COLUMN Codigo SET NOT NULL, ALTER COLUMN Tipo SET NOT NULL, ALTER COLUMN Estado SET NOT NULL;
ALTER TABLE Detalle_venta ALTER COLUMN Cantidad SET NOT NULL, ALTER COLUMN Precio_unitario SET NOT NULL,
	ALTER COLUMN VentaID SET NOT NULL, ALTER COLUMN ProductoID SET NOT NULL;
ALTER TABLE Reserva_stock ALTER COLUMN Cantidad SET NOT NULL, ALTER COLUMN Estado SET NOT NULL, ALTER COLUMN Fecha_expiracion SET NOT NULL,
	ALTER COLUMN Detalle_ventaID SET NOT NULL, ALTER COLUMN Lote_federID SET NOT NULL;
ALTER TABLE Historial_estado_venta ALTER COLUMN Estado_nuevo SET NOT NULL, ALTER COLUMN Secuencia SET NOT NULL, ALTER COLUMN VentaID SET NOT NULL;
ALTER TABLE Pago ALTER COLUMN Metodo_pago SET NOT NULL, ALTER COLUMN Monto SET NOT NULL, ALTER COLUMN Estado_pago SET NOT NULL, ALTER COLUMN VentaID SET NOT NULL;
ALTER TABLE Entrega ALTER COLUMN Modalidad SET NOT NULL, ALTER COLUMN Estado SET NOT NULL, ALTER COLUMN InventarioID SET NOT NULL, ALTER COLUMN VentaID SET NOT NULL;
ALTER TABLE Factura ALTER COLUMN Numero SET NOT NULL, ALTER COLUMN Nit_ci SET NOT NULL, ALTER COLUMN Razon_social SET NOT NULL,
	ALTER COLUMN Monto_total SET NOT NULL, ALTER COLUMN VentaID SET NOT NULL;
ALTER TABLE Devolucion ALTER COLUMN Cantidad SET NOT NULL, ALTER COLUMN Motivo SET NOT NULL, ALTER COLUMN Estado SET NOT NULL,
	ALTER COLUMN Detalle_ventaID SET NOT NULL, ALTER COLUMN Empleado_federID SET NOT NULL;
ALTER TABLE Notificacion ALTER COLUMN Canal SET NOT NULL, ALTER COLUMN Mensaje SET NOT NULL, ALTER COLUMN Estado_envio SET NOT NULL, ALTER COLUMN ClienteID SET NOT NULL;
ALTER TABLE Bitacora ALTER COLUMN Accion SET NOT NULL, ALTER COLUMN Entidad SET NOT NULL, ALTER COLUMN Entidad_id SET NOT NULL, ALTER COLUMN UsuarioID SET NOT NULL;
ALTER TABLE Cambio_bitacora ALTER COLUMN Ruta_campo SET NOT NULL, ALTER COLUMN BitacoraID SET NOT NULL;
ALTER TABLE Indicador_financiero ALTER COLUMN Tipo SET NOT NULL, ALTER COLUMN Dimension SET NOT NULL, ALTER COLUMN Periodo_desde SET NOT NULL,
	ALTER COLUMN Periodo_hasta SET NOT NULL, ALTER COLUMN Valor SET NOT NULL, ALTER COLUMN Unidad SET NOT NULL;

/* Unicidad */

ALTER TABLE Usuario ADD CONSTRAINT UQ_Usuario_correo UNIQUE (Correo);
CREATE UNIQUE INDEX UQ_Usuario_correo_sin_mayusculas ON Usuario (lower(Correo));
ALTER TABLE Rol ADD CONSTRAINT UQ_Rol_nombre UNIQUE (Nombre);
ALTER TABLE Usuario_rol ADD CONSTRAINT UQ_Usuario_rol UNIQUE (UsuarioID, RolID);
ALTER TABLE Persona ADD CONSTRAINT UQ_Persona_ci UNIQUE (Ci);
ALTER TABLE Persona ADD CONSTRAINT UQ_Persona_usuario UNIQUE (UsuarioID);
ALTER TABLE Cliente ADD CONSTRAINT UQ_Cliente_persona UNIQUE (PersonaID);
ALTER TABLE Empleado_feder ADD CONSTRAINT UQ_Empleado_persona UNIQUE (PersonaID);
ALTER TABLE Productor ADD CONSTRAINT UQ_Productor_persona UNIQUE (PersonaID);
ALTER TABLE Encargado_asociacion ADD CONSTRAINT UQ_Encargado_asociacion_desde UNIQUE (AsociacionID, Desde);
ALTER TABLE Encargado_asociacion ADD CONSTRAINT UQ_Encargado_persona_desde UNIQUE (PersonaID, Desde);
CREATE UNIQUE INDEX UQ_Encargado_abierto_asociacion ON Encargado_asociacion (AsociacionID) WHERE Hasta IS NULL;
CREATE UNIQUE INDEX UQ_Encargado_abierto_persona ON Encargado_asociacion (PersonaID) WHERE Hasta IS NULL;
ALTER TABLE Municipio ADD CONSTRAINT UQ_Municipio_nombre UNIQUE (Nombre);
ALTER TABLE Categoria ADD CONSTRAINT UQ_Categoria_nombre UNIQUE (Nombre);
ALTER TABLE Inventario ADD CONSTRAINT UQ_Inventario_codigo UNIQUE (Codigo);
ALTER TABLE Inventario ADD CONSTRAINT UQ_Inventario_productor UNIQUE (ProductorID);
ALTER TABLE Inventario ADD CONSTRAINT UQ_Inventario_asociacion UNIQUE (AsociacionID);
CREATE UNIQUE INDEX UQ_Inventario_principal ON Inventario ((true)) WHERE Es_principal AND Activo;
ALTER TABLE Oferta_asociacion ADD CONSTRAINT UQ_Oferta UNIQUE (AsociacionID, ProductoID);
ALTER TABLE Movimiento_inventario ADD CONSTRAINT UQ_Movimiento_inventario_clave UNIQUE (Clave_operacion);
CREATE UNIQUE INDEX UQ_Registro_inicial_lote ON Movimiento_inventario (Lote_asociacionID) WHERE Tipo = 'REGISTRO';
CREATE UNIQUE INDEX UQ_Salida_por_detalle_compra ON Movimiento_inventario (Detalle_compraID) WHERE Detalle_compraID IS NOT NULL;
ALTER TABLE Detalle_compra ADD CONSTRAINT UQ_Detalle_compra UNIQUE (CompraID, Lote_asociacionID);
ALTER TABLE Lote_feder ADD CONSTRAINT UQ_Lote_feder_detalle UNIQUE (Detalle_compraID);
ALTER TABLE Movimiento_feder ADD CONSTRAINT UQ_Movimiento_feder_clave UNIQUE (Clave_operacion);
CREATE UNIQUE INDEX UQ_Entrada_compra_lote ON Movimiento_feder (Lote_federID) WHERE Motivo = 'COMPRA';
CREATE UNIQUE INDEX UQ_Alerta_abierta ON Alerta_stock (ProductoID) WHERE NOT Atendida;
ALTER TABLE Item_carrito ADD CONSTRAINT UQ_Item_carrito UNIQUE (ClienteID, ProductoID);
ALTER TABLE Venta ADD CONSTRAINT UQ_Venta_codigo UNIQUE (Codigo);
ALTER TABLE Detalle_venta ADD CONSTRAINT UQ_Detalle_venta UNIQUE (VentaID, ProductoID);
CREATE UNIQUE INDEX UQ_Reserva_activa ON Reserva_stock (Detalle_ventaID, Lote_federID) WHERE Estado = 'ACTIVA';
ALTER TABLE Historial_estado_venta ADD CONSTRAINT UQ_Historial_secuencia UNIQUE (VentaID, Secuencia);
CREATE UNIQUE INDEX UQ_Pago_unico_cobrado ON Pago (VentaID) WHERE Estado_pago = 'PAGADO';
CREATE UNIQUE INDEX UQ_Pago_referencia ON Pago (Metodo_pago, Referencia_pago) WHERE Referencia_pago IS NOT NULL;
ALTER TABLE Entrega ADD CONSTRAINT UQ_Entrega_venta UNIQUE (VentaID);
ALTER TABLE Factura ADD CONSTRAINT UQ_Factura_numero UNIQUE (Numero);
ALTER TABLE Factura ADD CONSTRAINT UQ_Factura_venta UNIQUE (VentaID);
ALTER TABLE Cambio_bitacora ADD CONSTRAINT UQ_Cambio_bitacora_ruta UNIQUE (BitacoraID, Ruta_campo);

/* Reglas de valores */

ALTER TABLE Encargado_asociacion ADD CONSTRAINT CK_Encargado_intervalo CHECK (Hasta IS NULL OR Desde < Hasta);

ALTER TABLE Ubicacion ADD CONSTRAINT CK_Ubicacion_coordenadas CHECK (
	(Latitud IS NULL) = (Longitud IS NULL)
	AND (Latitud IS NULL OR Latitud BETWEEN -90 AND 90)
	AND (Longitud IS NULL OR Longitud BETWEEN -180 AND 180));

ALTER TABLE Inventario ADD CONSTRAINT CK_Inventario_titular CHECK (
	(Tipo = 'PRODUCTOR' AND ProductorID IS NOT NULL AND AsociacionID IS NULL)
	OR (Tipo = 'ASOCIACION' AND AsociacionID IS NOT NULL AND ProductorID IS NULL)
	OR (Tipo = 'FEDER' AND ProductorID IS NULL AND AsociacionID IS NULL));
ALTER TABLE Inventario ADD CONSTRAINT CK_Inventario_principal CHECK (NOT Es_principal OR Tipo = 'FEDER');

ALTER TABLE Producto ADD CONSTRAINT CK_Producto_valores CHECK (Precio_venta >= 0 AND Stock_minimo >= 0);

ALTER TABLE Produccion ADD CONSTRAINT CK_Produccion_valores CHECK (Cantidad_estimada > 0 AND Fecha_cosecha_estimada >= Fecha_siembra);

ALTER TABLE Oferta_asociacion ADD CONSTRAINT CK_Oferta_valores CHECK (
	(Cantidad_prevista IS NULL) = (Disponible_desde IS NULL)
	AND (Cantidad_prevista IS NULL OR Cantidad_prevista > 0)
	AND (Precio_previsto IS NULL OR Precio_previsto >= 0));

ALTER TABLE Lote_asociacion ADD CONSTRAINT CK_Lote_asociacion_valores CHECK (
	Cantidad_inicial > 0 AND Cantidad_disponible >= 0 AND Precio_ofrecido >= 0);
ALTER TABLE Lote_asociacion ADD CONSTRAINT CK_Lote_asociacion_validacion CHECK (
	Estado_validacion = 'PENDIENTE' OR (Encargado_asociacionID IS NOT NULL AND Fecha_validacion IS NOT NULL));

ALTER TABLE Movimiento_inventario ADD CONSTRAINT CK_Movimiento_inventario CHECK (
	Cantidad > 0 AND (
		(Tipo = 'REGISTRO' AND Inventario_origenID IS NULL AND Inventario_destinoID IS NOT NULL)
		OR (Tipo = 'TRANSFERENCIA' AND Inventario_origenID IS NOT NULL AND Inventario_destinoID IS NOT NULL
			AND Inventario_origenID <> Inventario_destinoID AND Detalle_compraID IS NULL)
		OR (Tipo = 'TRANSFERENCIA' AND Inventario_origenID IS NOT NULL AND Inventario_destinoID IS NULL AND Detalle_compraID IS NOT NULL)
		OR (Tipo = 'MERMA' AND Inventario_origenID IS NOT NULL AND Inventario_destinoID IS NULL)
		OR (Tipo = 'AJUSTE' AND (Inventario_origenID IS NULL) <> (Inventario_destinoID IS NULL))));

ALTER TABLE Detalle_compra ADD CONSTRAINT CK_Detalle_compra_valores CHECK (Cantidad > 0 AND Precio_pagado >= 0);

ALTER TABLE Lote_feder ADD CONSTRAINT CK_Lote_feder_saldos CHECK (Cantidad_reservada >= 0 AND Cantidad_actual >= Cantidad_reservada);

ALTER TABLE Movimiento_feder ADD CONSTRAINT CK_Movimiento_feder CHECK (
	(Tipo = 'AJUSTE' AND Motivo = 'AJUSTE' AND Cantidad <> 0)
	OR (Cantidad > 0 AND (
		(Tipo = 'ENTRADA' AND Motivo IN ('COMPRA', 'DEVOLUCION'))
		OR (Tipo = 'SALIDA' AND Motivo IN ('VENTA', 'REPOSICION'))
		OR (Tipo = 'BAJA' AND Motivo IN ('VENCIDO', 'DANADO', 'DEFECTUOSO')))));

ALTER TABLE Alerta_stock ADD CONSTRAINT CK_Alerta_atencion CHECK (
	NOT Atendida OR (Fecha_atendida IS NOT NULL AND Empleado_federID IS NOT NULL));

ALTER TABLE Item_carrito ADD CONSTRAINT CK_Item_carrito_cantidad CHECK (Cantidad > 0);

ALTER TABLE Venta ADD CONSTRAINT CK_Venta_valores CHECK (Costo_envio IS NULL OR Costo_envio >= 0);
ALTER TABLE Venta ADD CONSTRAINT CK_Venta_cliente CHECK (Tipo = 'INMEDIATA' OR ClienteID IS NOT NULL);

ALTER TABLE Detalle_venta ADD CONSTRAINT CK_Detalle_venta_valores CHECK (Cantidad > 0 AND Precio_unitario >= 0);

ALTER TABLE Reserva_stock ADD CONSTRAINT CK_Reserva_valores CHECK (
	Cantidad > 0
	AND Fecha_expiracion > Fecha_reserva
	AND ((Estado = 'ACTIVA') = (Fecha_cierre IS NULL))
	AND (Fecha_cierre IS NULL OR Fecha_cierre >= Fecha_reserva));

ALTER TABLE Historial_estado_venta ADD CONSTRAINT CK_Historial_secuencia CHECK (
	Secuencia >= 1 AND ((Secuencia = 1) = (Estado_anterior IS NULL)));

ALTER TABLE Pago ADD CONSTRAINT CK_Pago_valores CHECK (
	Monto > 0
	AND (Estado_pago <> 'PAGADO' OR Fecha_pago IS NOT NULL)
	AND (Fecha_reversion IS NULL OR (Estado_pago = 'PAGADO' AND Fecha_reversion >= Fecha_pago))
	AND (Referencia_reversion IS NULL OR Fecha_reversion IS NOT NULL));

ALTER TABLE Entrega ADD CONSTRAINT CK_Entrega_coordenadas CHECK (
	(Latitud IS NULL) = (Longitud IS NULL)
	AND (Latitud IS NULL OR Latitud BETWEEN -90 AND 90)
	AND (Longitud IS NULL OR Longitud BETWEEN -180 AND 180));
ALTER TABLE Entrega ADD CONSTRAINT CK_Entrega_delivery CHECK (
	Modalidad = 'RECOJO' OR (Direccion IS NOT NULL AND Telefono_contacto IS NOT NULL));
ALTER TABLE Entrega ADD CONSTRAINT CK_Entrega_en_ruta CHECK (
	Estado <> 'EN_RUTA' OR (Empleado_federID IS NOT NULL AND Fecha_salida IS NOT NULL));
ALTER TABLE Entrega ADD CONSTRAINT CK_Entrega_efectiva CHECK ((Estado = 'ENTREGADA') = (Fecha_entrega IS NOT NULL));
ALTER TABLE Entrega ADD CONSTRAINT CK_Entrega_costo CHECK (Costo_real IS NULL OR Costo_real >= 0);

ALTER TABLE Devolucion ADD CONSTRAINT CK_Devolucion_valores CHECK (
	Cantidad > 0
	AND (Motivo <> 'OTRO_JUSTIFICADO' OR Descripcion IS NOT NULL)
	AND (Monto_reembolso IS NULL OR Monto_reembolso > 0)
	AND (Estado <> 'RESUELTA' OR (Resolucion IS NOT NULL AND Justificacion IS NOT NULL))
	AND (Fecha_reembolso IS NULL OR (Resolucion = 'REEMBOLSO' AND Monto_reembolso IS NOT NULL)));

ALTER TABLE Notificacion ADD CONSTRAINT CK_Notificacion_canal CHECK (
	(Canal = 'CORREO' OR Asunto IS NULL) AND (Canal = 'PLATAFORMA' OR Fecha_lectura IS NULL));

ALTER TABLE Indicador_financiero ADD CONSTRAINT CK_Indicador_valores CHECK (
	Periodo_desde <= Periodo_hasta
	AND (
		(Dimension = 'GLOBAL' AND ProductoID IS NULL AND AsociacionID IS NULL AND MunicipioID IS NULL)
		OR (Dimension = 'PRODUCTO' AND ProductoID IS NOT NULL AND AsociacionID IS NULL AND MunicipioID IS NULL)
		OR (Dimension = 'ASOCIACION' AND AsociacionID IS NOT NULL AND ProductoID IS NULL AND MunicipioID IS NULL)
		OR (Dimension = 'MUNICIPIO' AND MunicipioID IS NOT NULL AND ProductoID IS NULL AND AsociacionID IS NULL)));

/* Roles que usa el sistema para autorizar cada comando */

INSERT INTO Rol (Activo, Nombre, Descripcion) VALUES
	(true, 'ADMINISTRADOR', 'Gestiona cuentas, roles y todas las tablas'),
	(true, 'PERSONAL', 'Opera compras, stock, pedidos, pagos y entregas'),
	(true, 'ENCARGADO', 'Mantiene la oferta y valida los lotes de su asociacion'),
	(true, 'PRODUCTOR', 'Registra su produccion'),
	(true, 'CLIENTE', 'Administra su carrito y sus pedidos')
ON CONFLICT (Nombre) DO NOTHING;

COMMIT;

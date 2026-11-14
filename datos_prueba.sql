/* Datos de relleno para probar el sistema por correo.
   Ejecutar despues de db.sql y restricciones.sql, una sola vez.
   Es todo o nada: si algo falla, no se guarda nada.

   Todos los usuarios tienen la contrasena: Clave123
   Los correos @example.com son ficticios (no reciben correo). Para probar un rol
   desde su correo real, cambie la direccion con MODUSU["id","su@correo","Clave123"].

   Los saldos son coherentes con los movimientos, como si se hubieran hecho por comandos:
   lote registrado -> trasladado al acopio -> comprado -> recibido en FEDER -> vendido. */

BEGIN;

SET client_encoding = 'UTF8';

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM Usuario WHERE lower(Correo) IN ('almacen@example.com', 'luisandresmendozasuarez5@gmail.com')) THEN
		RAISE EXCEPTION 'Los datos de prueba ya estan cargados (o ya existe la cuenta del administrador).';
	END IF;
END $$;

/* Busquedas por clave natural: el script no depende de los ID que asigne la base. */
CREATE FUNCTION pg_temp.usu(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT UsuarioID FROM Usuario WHERE Correo = t $$;
CREATE FUNCTION pg_temp.per(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT PersonaID FROM Persona WHERE Ci = t $$;
CREATE FUNCTION pg_temp.rol(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT RolID FROM Rol WHERE Nombre = t $$;
CREATE FUNCTION pg_temp.mun(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT MunicipioID FROM Municipio WHERE Nombre = t $$;
CREATE FUNCTION pg_temp.ubi(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT UbicacionID FROM Ubicacion WHERE Nombre = t $$;
CREATE FUNCTION pg_temp.aso(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT AsociacionID FROM Asociacion WHERE Nombre = t $$;
CREATE FUNCTION pg_temp.cat(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT CategoriaID FROM Categoria WHERE Nombre = t $$;
CREATE FUNCTION pg_temp.pro(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT ProductoID FROM Producto WHERE Nombre = t $$;
CREATE FUNCTION pg_temp.inv(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT InventarioID FROM Inventario WHERE Codigo = t $$;
CREATE FUNCTION pg_temp.emp(ci TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT Empleado_federID FROM Empleado_feder WHERE PersonaID = pg_temp.per(ci) $$;
CREATE FUNCTION pg_temp.enc(ci TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT Encargado_asociacionID FROM Encargado_asociacion WHERE PersonaID = pg_temp.per(ci) $$;
CREATE FUNCTION pg_temp.ptr(ci TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT ProductorID FROM Productor WHERE PersonaID = pg_temp.per(ci) $$;
CREATE FUNCTION pg_temp.cli(ci TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT ClienteID FROM Cliente WHERE PersonaID = pg_temp.per(ci) $$;
CREATE FUNCTION pg_temp.loa(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT Lote_asociacionID FROM Lote_asociacion WHERE Codigo_lote = t $$;
CREATE FUNCTION pg_temp.dco(lote TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT Detalle_compraID FROM Detalle_compra WHERE Lote_asociacionID = pg_temp.loa(lote) $$;
CREATE FUNCTION pg_temp.lof(lote TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT Lote_federID FROM Lote_feder WHERE Detalle_compraID = pg_temp.dco(lote) $$;
CREATE FUNCTION pg_temp.ven(t TEXT) RETURNS INTEGER LANGUAGE sql AS $$ SELECT VentaID FROM Venta WHERE Codigo = t $$;
CREATE FUNCTION pg_temp.dve(venta TEXT, producto TEXT) RETURNS INTEGER LANGUAGE sql AS $$
	SELECT Detalle_ventaID FROM Detalle_venta WHERE VentaID = pg_temp.ven(venta) AND ProductoID = pg_temp.pro(producto) $$;

/* ---------- Usuarios, personas y roles ---------- */

INSERT INTO Usuario (Activo, Correo, Fecha_creacion, Password_hash) VALUES
	(true, 'luisandresmendozasuarez5@gmail.com', '2026-09-01 08:00', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'almacen@example.com',                '2026-09-01 08:10', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'encargado.progreso@example.com',     '2026-09-02 09:00', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'encargado.samaipata@example.com',    '2026-09-02 09:05', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'productor.pena@example.com',         '2026-09-03 10:00', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'productor.nunez@example.com',        '2026-09-03 10:05', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'productor.quispe@example.com',       '2026-09-03 10:10', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'cliente.ana@example.com',            '2026-09-10 15:00', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'cliente.jose@example.com',           '2026-09-11 16:30', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.'),
	(true, 'cliente.maria@example.com',          '2026-09-12 11:45', '$2a$10$LqR3u9JdKmyDv/KNJNRPxe90TkWkuM37JJsJoTtaJNKr5/3W9zSy.');

INSERT INTO Usuario_rol (Fecha_asignacion, UsuarioID, RolID) VALUES
	('2026-09-01 08:00', pg_temp.usu('luisandresmendozasuarez5@gmail.com'), pg_temp.rol('ADMINISTRADOR')),
	('2026-09-01 08:00', pg_temp.usu('luisandresmendozasuarez5@gmail.com'), pg_temp.rol('PERSONAL')),
	('2026-09-01 08:10', pg_temp.usu('almacen@example.com'), pg_temp.rol('PERSONAL')),
	('2026-09-02 09:00', pg_temp.usu('encargado.progreso@example.com'), pg_temp.rol('ENCARGADO')),
	('2026-09-02 09:05', pg_temp.usu('encargado.samaipata@example.com'), pg_temp.rol('ENCARGADO')),
	('2026-09-03 10:00', pg_temp.usu('productor.pena@example.com'), pg_temp.rol('PRODUCTOR')),
	('2026-09-03 10:05', pg_temp.usu('productor.nunez@example.com'), pg_temp.rol('PRODUCTOR')),
	('2026-09-03 10:10', pg_temp.usu('productor.quispe@example.com'), pg_temp.rol('PRODUCTOR')),
	('2026-09-10 15:00', pg_temp.usu('cliente.ana@example.com'), pg_temp.rol('CLIENTE')),
	('2026-09-11 16:30', pg_temp.usu('cliente.jose@example.com'), pg_temp.rol('CLIENTE')),
	('2026-09-12 11:45', pg_temp.usu('cliente.maria@example.com'), pg_temp.rol('CLIENTE'));

-- Nombres con ñ, tildes y dieresis a proposito: sirven para probar la codificacion de las respuestas.
INSERT INTO Persona (Apellido, Ci, Nombre, Telefono, UsuarioID) VALUES
	('del Sistema',     '1000001', 'Administrador', '+591 70000001', pg_temp.usu('luisandresmendozasuarez5@gmail.com')),
	('Suárez Peña',     '1000002', 'Carla',         '+591 70000002', pg_temp.usu('almacen@example.com')),
	('Áñez Vaca',       '1000003', 'Jorge',         '+591 70000003', pg_temp.usu('encargado.progreso@example.com')),
	('Gutiérrez Ortiz', '1000004', 'Lucía',         '+591 70000004', pg_temp.usu('encargado.samaipata@example.com')),
	('Peña Rojas',      '1000005', 'Martín',        '+591 70000005', pg_temp.usu('productor.pena@example.com')),
	('Núñez Choque',    '1000006', 'Rosa',          '+591 70000006', pg_temp.usu('productor.nunez@example.com')),
	('Quispe Mamani',   '1000007', 'Ramiro',        '+591 70000007', pg_temp.usu('productor.quispe@example.com')),
	('Ibáñez Güemes',   '1000008', 'Ana María',     '+591 70000008', pg_temp.usu('cliente.ana@example.com')),
	('Terceros Añez',   '1000009', 'José Luis',     '+591 70000009', pg_temp.usu('cliente.jose@example.com')),
	('Cuéllar Muñoz',   '1000010', 'María José',    '+591 70000010', pg_temp.usu('cliente.maria@example.com'));

INSERT INTO Empleado_feder (Activo, Cargo, Fecha_contratacion, UsuarioID, PersonaID) VALUES
	(true, 'Administrador del sistema', '2026-09-01', pg_temp.usu('luisandresmendozasuarez5@gmail.com'), pg_temp.per('1000001')),
	(true, 'Responsable de almacén',    '2026-09-01', pg_temp.usu('almacen@example.com'), pg_temp.per('1000002'));

INSERT INTO Cliente (Activo, Direccion_frecuente, Nit_ci, Razon_social, UsuarioID, PersonaID) VALUES
	(true, 'Calle Ñuflo de Chávez #45, Equipetrol', '1000008',    'Ibáñez Güemes',             pg_temp.usu('cliente.ana@example.com'), pg_temp.per('1000008')),
	(true, 'Av. Banzer km 7½, local 3',             '1023456027', 'Comercial El Ñandú S.R.L.', pg_temp.usu('cliente.jose@example.com'), pg_temp.per('1000009')),
	(true, 'Barrio Las Palmas, calle 4 #12',        '1000010',    'Cuéllar Muñoz',             pg_temp.usu('cliente.maria@example.com'), pg_temp.per('1000010'));

/* ---------- Territorio ---------- */

INSERT INTO Municipio (Activo, Departamento, Nombre) VALUES
	(true, 'Santa Cruz', 'Santa Cruz de la Sierra'),
	(true, 'Santa Cruz', 'Warnes'),
	(true, 'Santa Cruz', 'Montero'),
	(true, 'Santa Cruz', 'Samaipata'),
	(true, 'Santa Cruz', 'San Ignacio de Velasco');

-- Geom es (longitud, latitud), igual que lo calcula el sistema.
INSERT INTO Ubicacion (Activo, Comunidad, Direccion, Geom, Latitud, Longitud, Nombre, Referencia, Tipo, MunicipioID) VALUES
	(true, NULL, 'Av. Grigotá #1250, 3er anillo', point(-63.180200, -17.800500), -17.800500, -63.180200, 'Almacén Central FEDER', 'Galpón azul, puerta 2', 'ALMACEN_FEDER', pg_temp.mun('Santa Cruz de la Sierra')),
	(true, 'San Juan del Pozo', 'Carretera al norte km 30', point(-63.166700, -17.510000), -17.510000, -63.166700, 'Centro de Acopio El Progreso', 'Al lado de la escuela', 'CENTRO_ACOPIO', pg_temp.mun('Warnes')),
	(true, 'Cuevas', 'Ruta 7, km 120', point(-63.874400, -18.179400), -18.179400, -63.874400, 'Centro de Acopio Samaipata', 'Frente al mirador', 'CENTRO_ACOPIO', pg_temp.mun('Samaipata')),
	(true, 'San Juan del Pozo', 'Camino vecinal s/n', point(-63.150300, -17.495200), -17.495200, -63.150300, 'Parcela Peña', 'Entrada por el puente', 'PARCELA', pg_temp.mun('Warnes')),
	(true, 'Las Pozas', 'Camino a Las Pozas km 4', point(-63.140800, -17.530600), -17.530600, -63.140800, 'Parcela Núñez', 'Colmenas detrás del cañaveral', 'PARCELA', pg_temp.mun('Warnes')),
	(true, 'Cuevas', 'Comunidad Cuevas, lote 8', point(-63.860100, -18.190700), -18.190700, -63.860100, 'Parcela Quispe', 'Ladera sur', 'PARCELA', pg_temp.mun('Samaipata'));

INSERT INTO Asociacion (Activo, Correo, Direccion, Fecha_registro, Nombre, Telefono, MunicipioID) VALUES
	(true, 'progreso@example.com', 'Carretera al norte km 30', '2026-09-02 08:00', 'Asociación de Productores «El Progreso»', '+591 3 923-4567', pg_temp.mun('Warnes')),
	(true, 'samaipata@example.com', 'Ruta 7, km 120', '2026-09-02 08:30', 'Cooperativa Agroecológica Samaipata & Valles', '+591 3 944-6060', pg_temp.mun('Samaipata'));

INSERT INTO Encargado_asociacion (Activo, Desde, Hasta, AsociacionID, UsuarioID, PersonaID) VALUES
	(true, '2026-09-02 09:00', NULL, pg_temp.aso('Asociación de Productores «El Progreso»'), pg_temp.usu('encargado.progreso@example.com'), pg_temp.per('1000003')),
	(true, '2026-09-02 09:05', NULL, pg_temp.aso('Cooperativa Agroecológica Samaipata & Valles'), pg_temp.usu('encargado.samaipata@example.com'), pg_temp.per('1000004'));

INSERT INTO Productor (Activo, Comunidad, Fecha_registro, AsociacionID, PersonaID, UsuarioID) VALUES
	(true, 'San Juan del Pozo', '2026-09-03 10:00', pg_temp.aso('Asociación de Productores «El Progreso»'), pg_temp.per('1000005'), pg_temp.usu('productor.pena@example.com')),
	(true, 'Las Pozas',         '2026-09-03 10:05', pg_temp.aso('Asociación de Productores «El Progreso»'), pg_temp.per('1000006'), pg_temp.usu('productor.nunez@example.com')),
	(true, 'Cuevas',            '2026-09-03 10:10', pg_temp.aso('Cooperativa Agroecológica Samaipata & Valles'), pg_temp.per('1000007'), pg_temp.usu('productor.quispe@example.com'));

INSERT INTO Inventario (Activo, Codigo, Es_principal, Fecha_creacion, Nombre, Tipo, AsociacionID, ProductorID, UbicacionID) VALUES
	(true, 'INV-FEDER-01', true,  '2026-09-01 08:00', 'Almacén Central', 'FEDER', NULL, NULL, pg_temp.ubi('Almacén Central FEDER')),
	(true, 'INV-ASO-01',   false, '2026-09-02 08:00', 'Acopio El Progreso', 'ASOCIACION', pg_temp.aso('Asociación de Productores «El Progreso»'), NULL, pg_temp.ubi('Centro de Acopio El Progreso')),
	(true, 'INV-ASO-02',   false, '2026-09-02 08:30', 'Acopio Samaipata', 'ASOCIACION', pg_temp.aso('Cooperativa Agroecológica Samaipata & Valles'), NULL, pg_temp.ubi('Centro de Acopio Samaipata')),
	(true, 'INV-PTR-01',   false, '2026-09-03 10:00', 'Parcela de Martín Peña', 'PRODUCTOR', NULL, pg_temp.ptr('1000005'), pg_temp.ubi('Parcela Peña')),
	(true, 'INV-PTR-02',   false, '2026-09-03 10:05', 'Colmenar de Rosa Núñez', 'PRODUCTOR', NULL, pg_temp.ptr('1000006'), pg_temp.ubi('Parcela Núñez')),
	(true, 'INV-PTR-03',   false, '2026-09-03 10:10', 'Parcela de Ramiro Quispe', 'PRODUCTOR', NULL, pg_temp.ptr('1000007'), pg_temp.ubi('Parcela Quispe'));

/* ---------- Catalogo ---------- */

INSERT INTO Categoria (Activo, Descripcion, Nombre) VALUES
	(true, 'Frutas frescas de temporada', 'Frutas'),
	(true, 'Verduras, tubérculos y hortalizas', 'Verduras y hortalizas'),
	(true, 'Granos andinos y del oriente', 'Granos y cereales'),
	(true, 'Quesos y derivados de la leche', 'Lácteos'),
	(true, 'Miel, polen y propóleo', 'Miel & derivados');

INSERT INTO Producto (Activo, Descripcion, Fecha_creacion, Imagen_url, Maneja_vencimiento, Nombre, Precio_venta, Presentacion, Stock_minimo, Unidad_medida, CategoriaID) VALUES
	(true, 'Banano de Yapacaní, maduración natural', '2026-09-05 09:00', NULL, false, 'Banano', 8.50, 'Racimo', 50, 'kg', pg_temp.cat('Frutas')),
	(true, 'Piña dulce de exportación', '2026-09-05 09:05', NULL, false, 'Piña', 12.00, 'Unidad', 20, 'unidad', pg_temp.cat('Frutas')),
	(true, 'Tomate perita fresco', '2026-09-05 09:10', NULL, false, 'Tomate', 7.00, 'Caja 20 kg', 30, 'kg', pg_temp.cat('Verduras y hortalizas')),
	(true, 'Papa holandesa', '2026-09-05 09:15', NULL, false, 'Papa', 5.50, 'Arroba', 100, 'kg', pg_temp.cat('Verduras y hortalizas')),
	(true, 'Arroz grano de oro de Montero', '2026-09-05 09:20', NULL, false, 'Arroz', 9.00, 'Bolsa 1 kg', 80, 'kg', pg_temp.cat('Granos y cereales')),
	(true, 'Quinua real orgánica — certificada', '2026-09-05 09:25', NULL, false, 'Quinua real', 22.00, 'Bolsa 1 kg', 40, 'kg', pg_temp.cat('Granos y cereales')),
	(true, 'Queso fresco de leche de vaca (≈ 1 kg)', '2026-09-05 09:30', NULL, true, 'Queso criollo', 45.00, 'Pieza', 10, 'kg', pg_temp.cat('Lácteos')),
	(true, 'Miel 100% pura de abeja, 500 g', '2026-09-05 09:35', NULL, true, 'Miel de abeja pura', 40.00, 'Frasco 500 g', 20, 'frasco', pg_temp.cat('Miel & derivados'));

INSERT INTO Oferta_asociacion (Activo, Cantidad_prevista, Disponible_desde, Fecha_actualizacion, Precio_previsto, Tiempo_entrega_horas, AsociacionID, ProductoID, Encargado_asociacionID) VALUES
	(true, 400, '2026-11-15', '2026-11-01 09:00', 5.00, 48, pg_temp.aso('Asociación de Productores «El Progreso»'), pg_temp.pro('Banano'), pg_temp.enc('1000003')),
	(true, 50,  '2026-11-20', '2026-11-01 09:10', 25.00, 72, pg_temp.aso('Asociación de Productores «El Progreso»'), pg_temp.pro('Miel de abeja pura'), pg_temp.enc('1000003')),
	(true, 200, '2026-12-01', '2026-11-02 10:00', 15.00, 96, pg_temp.aso('Cooperativa Agroecológica Samaipata & Valles'), pg_temp.pro('Quinua real'), pg_temp.enc('1000004'));

/* ---------- Produccion y lotes de las asociaciones ---------- */

INSERT INTO Produccion (Cantidad_estimada, Estado, Fecha_cosecha_estimada, Fecha_registro, Fecha_siembra, Observacion, UsuarioID, ProductorID, ProductoID) VALUES
	(600, 'COSECHADA', '2026-09-15', '2026-03-01 08:00', '2026-03-01', 'Campaña de banano, riego por goteo', pg_temp.usu('productor.pena@example.com'), pg_temp.ptr('1000005'), pg_temp.pro('Banano')),
	(120, 'COSECHADA', '2026-10-01', '2026-05-01 08:00', '2026-05-01', 'Cosecha de miel de 15 colmenas', pg_temp.usu('productor.nunez@example.com'), pg_temp.ptr('1000006'), pg_temp.pro('Miel de abeja pura')),
	(350, 'COSECHADA', '2026-09-30', '2026-04-10 08:00', '2026-04-10', 'Quinua real, sin agroquímicos', pg_temp.usu('productor.quispe@example.com'), pg_temp.ptr('1000007'), pg_temp.pro('Quinua real')),
	(500, 'EN_CURSO',  '2026-12-15', '2026-08-01 08:00', '2026-08-01', 'Papa holandesa, primera siembra', pg_temp.usu('productor.quispe@example.com'), pg_temp.ptr('1000007'), pg_temp.pro('Papa'));

-- Cantidad_disponible ya descuenta lo comprado por FEDER (ver Detalle_compra).
INSERT INTO Lote_asociacion (Cantidad_disponible, Cantidad_inicial, Codigo_lote, Estado_validacion, Fecha_registro, Fecha_validacion, Fecha_vencimiento, Precio_ofrecido,
		AsociacionID, Encargado_asociacionID, InventarioID, UsuarioID, MunicipioID, ProductorID, ProductoID, ProduccionID) VALUES
	(300, 500, 'LA-2026-001', 'VALIDADO', '2026-09-20 08:30', '2026-09-21 10:00', NULL, 5.00,
		pg_temp.aso('Asociación de Productores «El Progreso»'), pg_temp.enc('1000003'), pg_temp.inv('INV-ASO-01'), pg_temp.usu('productor.pena@example.com'), pg_temp.mun('Warnes'),
		pg_temp.ptr('1000005'), pg_temp.pro('Banano'), (SELECT ProduccionID FROM Produccion WHERE ProductorID = pg_temp.ptr('1000005'))),
	(40, 100, 'LA-2026-002', 'VALIDADO', '2026-10-02 09:00', '2026-10-03 11:00', '2027-10-01', 25.00,
		pg_temp.aso('Asociación de Productores «El Progreso»'), pg_temp.enc('1000003'), pg_temp.inv('INV-ASO-01'), pg_temp.usu('productor.nunez@example.com'), pg_temp.mun('Warnes'),
		pg_temp.ptr('1000006'), pg_temp.pro('Miel de abeja pura'), (SELECT ProduccionID FROM Produccion WHERE ProductorID = pg_temp.ptr('1000006'))),
	(150, 300, 'LA-2026-003', 'VALIDADO', '2026-10-01 08:00', '2026-10-01 15:00', '2027-09-30', 15.00,
		pg_temp.aso('Cooperativa Agroecológica Samaipata & Valles'), pg_temp.enc('1000004'), pg_temp.inv('INV-ASO-02'), pg_temp.usu('productor.quispe@example.com'), pg_temp.mun('Samaipata'),
		pg_temp.ptr('1000007'), pg_temp.pro('Quinua real'), (SELECT ProduccionID FROM Produccion WHERE ProductorID = pg_temp.ptr('1000007') AND Estado = 'COSECHADA')),
	-- Pendiente de validar y todavia en la parcela: sirve para probar VALLOA y TRALOA.
	(80, 80, 'LA-2026-004', 'PENDIENTE', '2026-11-05 07:30', NULL, NULL, 7.00,
		pg_temp.aso('Asociación de Productores «El Progreso»'), NULL, pg_temp.inv('INV-PTR-01'), pg_temp.usu('productor.pena@example.com'), pg_temp.mun('Warnes'),
		pg_temp.ptr('1000005'), pg_temp.pro('Piña'), NULL);

/* ---------- Compras de FEDER a las asociaciones ---------- */

INSERT INTO Compra (Fecha, Observacion, Total, AsociacionID, Empleado_federID) VALUES
	('2026-10-04', 'Primera compra de la campaña: banano y miel', 2500.00, pg_temp.aso('Asociación de Productores «El Progreso»'), pg_temp.emp('1000002')),
	('2026-10-06', 'Quinua real (pago contra entrega)', 2250.00, pg_temp.aso('Cooperativa Agroecológica Samaipata & Valles'), pg_temp.emp('1000002'));

INSERT INTO Detalle_compra (Cantidad, Precio_pagado, Subtotal, CompraID, Lote_asociacionID) VALUES
	(200, 5.00, 1000.00, (SELECT CompraID FROM Compra WHERE Fecha = '2026-10-04'), pg_temp.loa('LA-2026-001')),
	(60, 25.00, 1500.00, (SELECT CompraID FROM Compra WHERE Fecha = '2026-10-04'), pg_temp.loa('LA-2026-002')),
	(150, 15.00, 2250.00, (SELECT CompraID FROM Compra WHERE Fecha = '2026-10-06'), pg_temp.loa('LA-2026-003'));

-- Registro en la parcela, traslado al acopio y salida por la compra (claves como las genera el sistema).
INSERT INTO Movimiento_inventario (Cantidad, Clave_operacion, Fecha, Observacion, Tipo, Detalle_compraID, Inventario_origenID, Inventario_destinoID, Lote_asociacionID, UsuarioID) VALUES
	(500, 'REG-LA-2026-001', '2026-09-20 08:30', NULL, 'REGISTRO', NULL, NULL, pg_temp.inv('INV-PTR-01'), pg_temp.loa('LA-2026-001'), pg_temp.usu('productor.pena@example.com')),
	(500, 'TRA-LA-2026-001', '2026-09-22 07:00', 'Traslado en camión de la asociación', 'TRANSFERENCIA', NULL, pg_temp.inv('INV-PTR-01'), pg_temp.inv('INV-ASO-01'), pg_temp.loa('LA-2026-001'), pg_temp.usu('productor.pena@example.com')),
	(100, 'REG-LA-2026-002', '2026-10-02 09:00', NULL, 'REGISTRO', NULL, NULL, pg_temp.inv('INV-PTR-02'), pg_temp.loa('LA-2026-002'), pg_temp.usu('productor.nunez@example.com')),
	(100, 'TRA-LA-2026-002', '2026-10-02 16:00', NULL, 'TRANSFERENCIA', NULL, pg_temp.inv('INV-PTR-02'), pg_temp.inv('INV-ASO-01'), pg_temp.loa('LA-2026-002'), pg_temp.usu('productor.nunez@example.com')),
	(300, 'REG-LA-2026-003', '2026-10-01 08:00', NULL, 'REGISTRO', NULL, NULL, pg_temp.inv('INV-PTR-03'), pg_temp.loa('LA-2026-003'), pg_temp.usu('productor.quispe@example.com')),
	(300, 'TRA-LA-2026-003', '2026-10-01 12:00', NULL, 'TRANSFERENCIA', NULL, pg_temp.inv('INV-PTR-03'), pg_temp.inv('INV-ASO-02'), pg_temp.loa('LA-2026-003'), pg_temp.usu('productor.quispe@example.com')),
	(80, 'REG-LA-2026-004', '2026-11-05 07:30', NULL, 'REGISTRO', NULL, NULL, pg_temp.inv('INV-PTR-01'), pg_temp.loa('LA-2026-004'), pg_temp.usu('productor.pena@example.com'));

INSERT INTO Movimiento_inventario (Cantidad, Clave_operacion, Fecha, Tipo, Detalle_compraID, Inventario_origenID, Lote_asociacionID, UsuarioID)
SELECT d.Cantidad, 'REC-D' || d.Detalle_compraID, c.Fecha + TIME '14:00', 'TRANSFERENCIA', d.Detalle_compraID, l.InventarioID, l.Lote_asociacionID, pg_temp.usu('almacen@example.com')
FROM Detalle_compra d JOIN Compra c ON c.CompraID = d.CompraID JOIN Lote_asociacion l ON l.Lote_asociacionID = d.Lote_asociacionID;

/* ---------- Lotes FEDER (lo recibido en el almacen) ---------- */

-- Cantidad_actual ya descuenta lo vendido: banano 200-10, miel 60-2, quinua 150-5.
INSERT INTO Lote_feder (Cantidad_actual, Cantidad_reservada, Codigo_lote, Fecha_ingreso, Fecha_vencimiento, Precio_adquisicion, Version, Detalle_compraID, InventarioID, ProductoID)
SELECT d.Cantidad - CASE l.Codigo_lote WHEN 'LA-2026-001' THEN 10 WHEN 'LA-2026-002' THEN 2 ELSE 5 END,
	0, 'F-' || d.Detalle_compraID, c.Fecha, l.Fecha_vencimiento, d.Precio_pagado, 0, d.Detalle_compraID, pg_temp.inv('INV-FEDER-01'), l.ProductoID
FROM Detalle_compra d JOIN Compra c ON c.CompraID = d.CompraID JOIN Lote_asociacion l ON l.Lote_asociacionID = d.Lote_asociacionID;

INSERT INTO Movimiento_feder (Cantidad, Clave_operacion, Fecha, Motivo, Observacion, Tipo, Empleado_federID, Lote_federID)
SELECT d.Cantidad, 'ENT-D' || d.Detalle_compraID, c.Fecha + TIME '14:00', 'COMPRA', NULL, 'ENTRADA', pg_temp.emp('1000002'), f.Lote_federID
FROM Detalle_compra d JOIN Compra c ON c.CompraID = d.CompraID JOIN Lote_feder f ON f.Detalle_compraID = d.Detalle_compraID;

-- Sin stock en FEDER y por debajo del minimo: alerta abierta, como la dejaria el sistema.
INSERT INTO Alerta_stock (Atendida, Fecha_atendida, Fecha_generada, Stock_actual, Stock_minimo, Empleado_federID, ProductoID)
SELECT false, NULL, '2026-10-06 14:00', 0, p.Stock_minimo, NULL, p.ProductoID
FROM Producto p WHERE p.Nombre IN ('Piña', 'Tomate', 'Papa', 'Arroz', 'Queso criollo');

/* ---------- Ventas ----------
   SEED-V1: pedido con delivery, entregado, pagado por QR y facturado.
   SEED-V2: venta inmediata en tienda, pagada en efectivo y facturada.
   SEED-V3: pedido pendiente de confirmar (sirve para CONVEN).
   SEED-V4: pedido cancelado, con la reserva liberada.
   Al final el codigo pasa a V-<id>, como lo hace el sistema. */

INSERT INTO Venta (Codigo, Costo_envio, Estado, Fecha, Fecha_actualizacion, Subtotal, Tipo, Total, ClienteID, Empleado_federID, Empleado_ultimo_cambioID) VALUES
	('SEED-V1', 15.00, 'ENTREGADO', '2026-10-08 09:15', '2026-10-09 16:40', 165.00, 'ENCARGO', 180.00, pg_temp.cli('1000008'), pg_temp.emp('1000002'), pg_temp.emp('1000002')),
	('SEED-V2', 0.00, 'ENTREGADO', '2026-10-12 11:00', '2026-10-12 11:20', 110.00, 'INMEDIATA', 110.00, pg_temp.cli('1000009'), pg_temp.emp('1000002'), pg_temp.emp('1000002')),
	('SEED-V3', 0.00, 'PENDIENTE', '2026-11-09 18:20', '2026-11-09 18:20', 236.00, 'ENCARGO', 236.00, pg_temp.cli('1000010'), NULL, NULL),
	('SEED-V4', 0.00, 'CANCELADO', '2026-10-20 10:00', '2026-10-21 09:00', 120.00, 'ENCARGO', 120.00, pg_temp.cli('1000008'), pg_temp.emp('1000001'), pg_temp.emp('1000001'));

INSERT INTO Detalle_venta (Cantidad, Precio_unitario, Subtotal, VentaID, ProductoID) VALUES
	(10, 8.50, 85.00, pg_temp.ven('SEED-V1'), pg_temp.pro('Banano')),
	(2, 40.00, 80.00, pg_temp.ven('SEED-V1'), pg_temp.pro('Miel de abeja pura')),
	(5, 22.00, 110.00, pg_temp.ven('SEED-V2'), pg_temp.pro('Quinua real')),
	(20, 8.50, 170.00, pg_temp.ven('SEED-V3'), pg_temp.pro('Banano')),
	(3, 22.00, 66.00, pg_temp.ven('SEED-V3'), pg_temp.pro('Quinua real')),
	(3, 40.00, 120.00, pg_temp.ven('SEED-V4'), pg_temp.pro('Miel de abeja pura'));

-- Reservas de 48 horas, como las aparta CONVEN; V3 aun no tiene (se reserva al confirmar).
INSERT INTO Reserva_stock (Cantidad, Estado, Fecha_cierre, Fecha_expiracion, Fecha_reserva, Detalle_ventaID, Lote_federID) VALUES
	(10, 'CONSUMIDA', '2026-10-09 08:00', '2026-10-10 10:00', '2026-10-08 10:00', pg_temp.dve('SEED-V1', 'Banano'), pg_temp.lof('LA-2026-001')),
	(2,  'CONSUMIDA', '2026-10-09 08:00', '2026-10-10 10:00', '2026-10-08 10:00', pg_temp.dve('SEED-V1', 'Miel de abeja pura'), pg_temp.lof('LA-2026-002')),
	(5,  'CONSUMIDA', '2026-10-12 11:10', '2026-10-14 11:05', '2026-10-12 11:05', pg_temp.dve('SEED-V2', 'Quinua real'), pg_temp.lof('LA-2026-003')),
	(3,  'LIBERADA',  '2026-10-21 09:00', '2026-10-22 10:30', '2026-10-20 10:30', pg_temp.dve('SEED-V4', 'Miel de abeja pura'), pg_temp.lof('LA-2026-002'));

-- Salidas por venta: clave DES-R<reserva>, igual que DESVEN.
INSERT INTO Movimiento_feder (Cantidad, Clave_operacion, Fecha, Motivo, Observacion, Tipo, Detalle_ventaID, Empleado_federID, Lote_federID)
SELECT r.Cantidad, 'DES-R' || r.Reserva_stockID, r.Fecha_cierre, 'VENTA', NULL, 'SALIDA', r.Detalle_ventaID, pg_temp.emp('1000002'), r.Lote_federID
FROM Reserva_stock r WHERE r.Estado = 'CONSUMIDA';

INSERT INTO Historial_estado_venta (Estado_anterior, Estado_nuevo, Fecha, Secuencia, Empleado_federID, VentaID) VALUES
	(NULL, 'PENDIENTE', '2026-10-08 09:15', 1, NULL, pg_temp.ven('SEED-V1')),
	('PENDIENTE', 'CONFIRMADO', '2026-10-08 10:00', 2, pg_temp.emp('1000002'), pg_temp.ven('SEED-V1')),
	('CONFIRMADO', 'EN_PREPARACION', '2026-10-08 15:00', 3, pg_temp.emp('1000002'), pg_temp.ven('SEED-V1')),
	('EN_PREPARACION', 'LISTO', '2026-10-09 08:00', 4, pg_temp.emp('1000002'), pg_temp.ven('SEED-V1')),
	('LISTO', 'EN_RUTA', '2026-10-09 14:05', 5, pg_temp.emp('1000002'), pg_temp.ven('SEED-V1')),
	('EN_RUTA', 'ENTREGADO', '2026-10-09 16:40', 6, pg_temp.emp('1000002'), pg_temp.ven('SEED-V1')),
	(NULL, 'PENDIENTE', '2026-10-12 11:00', 1, pg_temp.emp('1000002'), pg_temp.ven('SEED-V2')),
	('PENDIENTE', 'CONFIRMADO', '2026-10-12 11:05', 2, pg_temp.emp('1000002'), pg_temp.ven('SEED-V2')),
	('CONFIRMADO', 'ENTREGADO', '2026-10-12 11:20', 3, pg_temp.emp('1000002'), pg_temp.ven('SEED-V2')),
	(NULL, 'PENDIENTE', '2026-11-09 18:20', 1, NULL, pg_temp.ven('SEED-V3')),
	(NULL, 'PENDIENTE', '2026-10-20 10:00', 1, NULL, pg_temp.ven('SEED-V4')),
	('PENDIENTE', 'CONFIRMADO', '2026-10-20 10:30', 2, pg_temp.emp('1000001'), pg_temp.ven('SEED-V4')),
	('CONFIRMADO', 'CANCELADO', '2026-10-21 09:00', 3, pg_temp.emp('1000001'), pg_temp.ven('SEED-V4'));

INSERT INTO Entrega (Costo_real, Direccion, Distancia_km, Estado, Fecha_entrega, Fecha_programada, Fecha_salida, Geom, Latitud, Longitud, Modalidad, Observacion, Referencia, Telefono_contacto, Empleado_federID, InventarioID, VentaID) VALUES
	(12.00, 'Calle Ñuflo de Chávez #45, Equipetrol', 6.50, 'ENTREGADA', '2026-10-09 16:40', '2026-10-09 14:00', '2026-10-09 14:05', point(-63.196700, -17.765400), -17.765400, -63.196700,
		'DELIVERY_PROPIO', 'Recibió la señora Ana', 'Portón verde, frente a la plaza', '+591 70000008', pg_temp.emp('1000002'), pg_temp.inv('INV-FEDER-01'), pg_temp.ven('SEED-V1')),
	(NULL, NULL, NULL, 'PENDIENTE', NULL, NULL, NULL, NULL, NULL, NULL, 'RECOJO', NULL, NULL, NULL, NULL, pg_temp.inv('INV-FEDER-01'), pg_temp.ven('SEED-V3')),
	(NULL, NULL, NULL, 'PENDIENTE', NULL, NULL, NULL, NULL, NULL, NULL, 'RECOJO', NULL, NULL, NULL, NULL, pg_temp.inv('INV-FEDER-01'), pg_temp.ven('SEED-V4'));

INSERT INTO Pago (Estado_pago, Fecha_pago, Fecha_reversion, Metodo_pago, Monto, Referencia_pago, Referencia_reversion, VentaID) VALUES
	('PAGADO', '2026-10-08 11:00', NULL, 'QR', 180.00, 'PF-20261008-0001', NULL, pg_temp.ven('SEED-V1')),
	('PAGADO', '2026-10-12 11:15', NULL, 'EFECTIVO', 110.00, NULL, NULL, pg_temp.ven('SEED-V2'));

INSERT INTO Factura (Estado, Fecha_emision, Monto_total, Nit_ci, Numero, Razon_social, VentaID) VALUES
	('EMITIDA', '2026-10-09 17:00', 180.00, '1000008', 'SEED-F1', 'Ibáñez Güemes', pg_temp.ven('SEED-V1')),
	('EMITIDA', '2026-10-12 11:20', 110.00, '1023456027', 'SEED-F2', 'Comercial El Ñandú S.R.L.', pg_temp.ven('SEED-V2'));

UPDATE Venta SET Codigo = 'V-' || VentaID WHERE Codigo LIKE 'SEED-V%';
UPDATE Factura SET Numero = 'F-' || FacturaID WHERE Numero LIKE 'SEED-F%';

-- Avisos ya enviados (ENVIADA): el sistema no los vuelve a mandar.
INSERT INTO Notificacion (Asunto, Canal, Estado_envio, Fecha, Fecha_lectura, Leido, Mensaje, ClienteID, VentaID)
SELECT CASE a.canal WHEN 'CORREO' THEN 'Pedido ' || v.Codigo END, a.canal::canal_notificacion, 'ENVIADA', a.fecha::timestamp,
	a.lectura::timestamp, a.lectura IS NOT NULL, a.mensaje, v.ClienteID, v.VentaID
FROM (VALUES
	('2026-10-08 09:15', '2026-10-08 10:00', 'CORREO', NULL, 'Su pedido fue confirmado y sus productos estan reservados.'),
	('2026-10-08 09:15', '2026-10-09 14:05', 'CORREO', NULL, 'Su pedido salio a reparto.'),
	('2026-10-08 09:15', '2026-10-09 16:40', 'CORREO', NULL, 'Su pedido fue entregado. Gracias por su compra.'),
	('2026-10-08 09:15', '2026-10-09 16:45', 'PLATAFORMA', '2026-10-09 18:00', '¡Gracias por comprar en FEDER! Califique su pedido.'),
	('2026-10-20 10:00', '2026-10-21 09:00', 'CORREO', NULL, 'Su pedido fue cancelado.')
) AS a(venta_fecha, fecha, canal, lectura, mensaje)
JOIN Venta v ON v.Fecha = a.venta_fecha::timestamp;

/* ---------- Carritos ---------- */

INSERT INTO Item_carrito (Cantidad, Fecha_agregado, ClienteID, ProductoID) VALUES
	(1, '2026-11-09 19:00', pg_temp.cli('1000010'), pg_temp.pro('Miel de abeja pura')),
	(2, '2026-11-09 19:02', pg_temp.cli('1000010'), pg_temp.pro('Banano')),
	(3, '2026-11-10 08:30', pg_temp.cli('1000008'), pg_temp.pro('Quinua real'));

COMMIT;

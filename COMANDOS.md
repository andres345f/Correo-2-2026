# Comandos por correo

**Para empezar, envíe un correo con el asunto `HELP`.** La respuesta es una guía paso a paso con todas las tablas, sus datos y comandos listos para copiar. Las respuestas llegan en HTML, con texto de respaldo.

El comando va en el **Asunto**: `ACCIONTABLA["parametro","parametro"]`. Cada parámetro va entre comillas dobles y no puede contener comillas.

| Acción | Parámetros | Ejemplo |
|---|---|---|
| `LIS` listar | `"*"` para todos, o un ID | `LISPER["*"]` |
| `INS` insertar | los de la tabla de abajo, en ese orden | `INSCAT["Fruta fresca","Frutas"]` |
| `MOD` modificar | el ID y luego los de la tabla de abajo | `MODCAT["3","","Citricos"]` |
| `DEL` eliminar | el ID | `DELCAT["3"]` |

## Formato de los valores

- **Vacío `""`:** en `INS` guarda nulo; en `MOD` deja el valor como está.
- **Relaciones** (`usuario`, `producto`, `venta`...): el ID numérico del registro.
- **Números:** `12.50`. **Booleanos:** `true` o `false`.
- **Fecha:** `2026-11-08`. **Fecha y hora:** `2026-11-08 14:30`.
- **Enumerados:** uno de los valores definidos, por ejemplo `PARCELA` en `tipo` de `UBI`.
- En `USU`, el campo `passwordHash` recibe la contraseña y se guarda con hash; nunca se muestra en `LISUSU`.

## Permisos

Cada orden se autoriza por el remitente del correo, según los roles activos de la cuenta con esa dirección (`HELP` no requiere cuenta).

| Rol | Puede |
|---|---|
| `ADMINISTRADOR` | Todas las tablas |
| `PERSONAL` | Todas, menos `USU`, `ROL`, `URO`, `PER`, `EMP`, `ENC`, `BIT` y `CAB` |
| Cualquier otro rol | Solo `LIS` en `CAT` y `PRO`, más sus comandos de negocio |

La dirección de `APP_ADMIN_CORREO` (en `.env`) actúa como administrador aunque no tenga cuenta: sirve para crear las primeras. Las tablas con saldos, estados o historia (`ALE`, `BIT`, `CAB`, `CAR`, `COM`, `DCO`, `DEV`, `DVE`, `ENT`, `FAC`, `HIS`, `IND`, `LOA`, `LOF`, `MOF`, `MOI`, `NOT`, `OFE`, `PAG`, `PRD`, `RES`, `VEN`) solo admiten `LIS`: cambian con los comandos de negocio.

Antes de usar el sistema hay que ejecutar `db.sql` (solo en una base nueva, porque borra las tablas) y después `restricciones.sql`, que agrega las reglas y los roles sin borrar datos.

## Lo que hace el sistema solo

- Las fechas de registro, creación, actualización y similares, el ID, `activo` (nace en `true`) y `geom` (se calcula con latitud y longitud).
- `DEL` en una tabla con columna `activo` hace baja lógica (`activo=false`). En las demás borra la fila, y responde `Error` si otros registros dependen de ella.
- Cada `INS`, `MOD` y `DEL` y cada comando de negocio queda en `BIT` y `CAB` con su autor.
- Los avisos de `NOT` por correo se envían solos y las reservas vencidas se liberan solas.

## Parámetros de `INS` por tabla

| Código | Entidad | Parámetros, en orden |
|---|---|---|
| `ASO` | Asociacion | correo, direccion, nombre, telefono, municipio |
| `CAT` | Categoria | descripcion, nombre |
| `CLI` | Cliente | direccionFrecuente, nitCi, razonSocial, usuario, persona |
| `EMP` | EmpleadoFeder | cargo, fechaContratacion, usuario, persona |
| `ENC` | EncargadoAsociacion | desde, hasta, asociacion, usuario, persona |
| `INV` | Inventario | codigo, esPrincipal, nombre, tipo, asociacion, productor, ubicacion |
| `MUN` | Municipio | departamento, nombre |
| `PER` | Persona | apellido, ci, nombre, telefono, usuario |
| `PRO` | Producto | descripcion, imagenUrl, manejaVencimiento, nombre, precioVenta, presentacion, stockMinimo, unidadMedida, categoria |
| `PTR` | Productor | comunidad, asociacion, persona, usuario |
| `ROL` | Rol | descripcion, nombre |
| `UBI` | Ubicacion | comunidad, direccion, latitud, longitud, nombre, referencia, tipo, municipio |
| `URO` | UsuarioRol | usuario, rol |
| `USU` | Usuario | correo, passwordHash |

## Configuración en `.env`

| Clave | Para qué |
|---|---|
| `APP_ADMIN_CORREO` | Dirección que actúa como administrador inicial |
| `SMTP_PROVEEDOR` | `TECNOWEB` (por defecto) o `GOOGLE`, para enviar por Gmail |
| `SMTP_PUERTO` | Puerto de Tecnoweb (por defecto 25); sirve para usar un túnel SSH |
| `POP_PROVEEDOR` | `TECNOWEB` (por defecto) o `GOOGLE`, para leer los comandos de Gmail (`pop.gmail.com:995`) |
| `SMTP_GMAIL_USER`, `SMTP_GMAIL_APP_PASSWORD` | Cuenta y contraseña de aplicación de Gmail, si `SMTP_PROVEEDOR` o `POP_PROVEEDOR` es `GOOGLE` |

Para probar sin el servidor de correo de Tecnoweb, poner `SMTP_PROVEEDOR=GOOGLE` y `POP_PROVEEDOR=GOOGLE`, habilitar POP en la cuenta de Gmail (Configuración > Reenvío y correo POP/IMAP) y enviar los comandos a esa cuenta **desde otra dirección**: los correos de la propia cuenta se ignoran para no responderse a sí misma. Esa otra dirección es la que va en `APP_ADMIN_CORREO`.
| `PAGOFACIL_TOKEN_SERVICE`, `PAGOFACIL_TOKEN_SECRET` | Credenciales de PagoFácil para `GQRVEN` y `ACTPAG` |
| `PAGOFACIL_BASE_URL`, `PAGOFACIL_CALLBACK_URL` | Opcionales |

## Comandos de negocio

Tienen la misma forma: `CODIGO["dato","dato"]`. Cada uno corre en una sola transacción junto con su registro en `BIT`. El `ADMINISTRADOR` puede intentar cualquiera, pero los que quedan firmados exigen su perfil (por ejemplo, `EMP`).

`claveOperacion` es un texto que usted inventa (hasta 40 caracteres): reenviar el comando con la misma clave no repite el hecho.

En `CALIND`, `metrica` es `CAJA_NETA`, `INGRESO_RECONOCIDO`, `MARGEN`, `UNIDADES_VENDIDAS` o `INVENTARIO`; `dimension` es `GLOBAL`, `PRODUCTO`, `ASOCIACION` o `MUNICIPIO`, y `ambito` es el ID correspondiente (vacío en `GLOBAL`). En los `INF`, `formato` es `HTML`, `PDF`, `EXCEL` o `TODOS` (vacío = `TODOS`); `desde` y `hasta` pueden ir vacíos.

### CU1. Usuarios

| Comando | Qué hace | Parámetros | Roles |
|---|---|---|---|
| `RELENC` | Relevar al encargado de una asociacion | asociacion, persona | ADMINISTRADOR |

### CU3. Producción, lotes y trazabilidad

| Comando | Qué hace | Parámetros | Roles |
|---|---|---|---|
| `REGPRD` | Registrar una campana | productor, producto, cantidadEstimada, fechaSiembra, fechaCosecha, observacion | PERSONAL, PRODUCTOR, ENCARGADO |
| `ESTPRD` | Cambiar el estado de una campana | produccion, estado | PERSONAL, PRODUCTOR, ENCARGADO |
| `MISPRD` | Ver mis campanas | todo | PRODUCTOR, ENCARGADO |
| `REGLOA` | Registrar un lote fisico | codigo, cantidad, precioOfrecido, vencimiento, produccion, productor, producto, inventario, claveOperacion | PERSONAL, PRODUCTOR, ENCARGADO |
| `TRALOA` | Trasladar todo el remanente de un lote | lote, inventarioDestino, claveOperacion | PERSONAL, PRODUCTOR, ENCARGADO |
| `VALLOA` | Validar o rechazar un lote | lote, decision | ENCARGADO |
| `MERLOA` | Registrar merma de un lote | lote, cantidad, observacion, claveOperacion | PERSONAL, PRODUCTOR, ENCARGADO |
| `AJULOA` | Ajustar el saldo de un lote (+ o -) | lote, cantidad, observacion, claveOperacion | PERSONAL, PRODUCTOR, ENCARGADO |
| `MISLOA` | Ver mis lotes | todo | PRODUCTOR, ENCARGADO |
| `ORILOA` | Ver los movimientos de un lote | lote | PERSONAL, PRODUCTOR, ENCARGADO |
| `PUBOFE` | Publicar la oferta de su asociacion | producto, tiempoEntregaHoras, precioPrevisto, cantidadPrevista, disponibleDesde | ENCARGADO |
| `ORILOF` | Ver el origen de un lote FEDER | loteFeder | PERSONAL |
| `ORIDVE` | Ver el origen de una linea vendida | detalleVenta | PERSONAL, CLIENTE |

### CU4. Catálogo, inventarios y alertas

| Comando | Qué hace | Parámetros | Roles |
|---|---|---|---|
| `BUSPRO` | Buscar en el catalogo | nombre, categoria, presentacion, municipio | Cualquier rol |
| `STKPRO` | Ver stock por lote de un producto | producto | PERSONAL |
| `OPCPRO` | Ver de donde reponer un producto | producto | PERSONAL |
| `CRECOM` | Crear una compra | asociacion, observacion | PERSONAL |
| `AGRDCO` | Agregar una linea a la compra | compra, loteAsociacion, cantidad, precioPagado | PERSONAL |
| `QUIDCO` | Quitar una linea sin recibir | detalleCompra | PERSONAL |
| `RECDCO` | Recibir una linea de compra | detalleCompra, inventarioFeder | PERSONAL |
| `BAJLOF` | Dar de baja unidades de un lote | lote, cantidad, motivo, observacion, claveOperacion | PERSONAL |
| `AJULOF` | Ajustar el saldo fisico (+ o -) | lote, cantidad, observacion, claveOperacion | PERSONAL |
| `MOVLOF` | Consolidar un lote en otro almacen | lote, inventarioFeder | PERSONAL |
| `ATEALE` | Atender una alerta de stock | alerta | PERSONAL |

### CU5. Carrito y ventas

| Comando | Qué hace | Parámetros | Roles |
|---|---|---|---|
| `AGRCAR` | Agregar al carrito | producto, cantidad | CLIENTE |
| `CAMCAR` | Cambiar cantidad en el carrito | producto, cantidad | CLIENTE |
| `QUICAR` | Quitar del carrito | producto | CLIENTE |
| `VERCAR` | Ver mi carrito | todo | CLIENTE |
| `PEDCAR` | Crear el pedido desde el carrito | modalidad, direccion, referencia, telefono | CLIENTE |
| `CREVEN` | Crear una venta inmediata | cliente | PERSONAL |
| `AGRDVE` | Agregar una linea a la venta | venta, producto, cantidad | PERSONAL |
| `QUIDVE` | Quitar una linea de la venta | detalleVenta | PERSONAL |
| `MISVEN` | Ver mis pedidos | todo | CLIENTE |
| `VERVEN` | Ver el detalle de una venta | venta | PERSONAL, CLIENTE |

### CU6. Pedidos, pagos, entregas, avisos y devoluciones

| Comando | Qué hace | Parámetros | Roles |
|---|---|---|---|
| `CONVEN` | Confirmar: fija envio y reserva stock | venta, costoEnvio | PERSONAL |
| `RESVEN` | Volver a reservar lo vencido | venta | PERSONAL |
| `ESTVEN` | Pasar a EN_PREPARACION o LISTO | venta, estado | PERSONAL |
| `DESVEN` | Despachar lo reservado | venta | PERSONAL |
| `SALENT` | Salir a reparto | venta, repartidor | PERSONAL |
| `CIEENT` | Cerrar la entrega efectiva | venta, costoReal | PERSONAL |
| `FALENT` | Marcar un reparto fallido | venta, observacion | PERSONAL |
| `COSENT` | Registrar el costo real del reparto | venta, costoReal | PERSONAL |
| `CANVEN` | Cancelar antes de entregar | venta | PERSONAL, CLIENTE |
| `REIVEN` | Reingresar lo despachado de una venta cancelada | detalleVenta, loteFeder, cantidad, claveOperacion | PERSONAL |
| `PAGVEN` | Registrar un intento de cobro | venta, metodo, referencia, resultado | PERSONAL |
| `GQRVEN` | Generar el QR de pago de PagoFacil | venta | PERSONAL, CLIENTE |
| `ACTPAG` | Consultar en PagoFacil un intento QR | pago | PERSONAL, CLIENTE |
| `CONPAG` | Resolver un intento pendiente | pago, resultado | PERSONAL |
| `REVPAG` | Revertir el cobro de una venta cancelada | pago, referencia | PERSONAL |
| `FACVEN` | Emitir la factura | venta, nitCi, razonSocial | PERSONAL |
| `AVINOT` | Enviar un aviso a un cliente | cliente, venta, canal, asunto, mensaje | PERSONAL |
| `VERNOT` | Ver mis avisos | todo | CLIENTE |
| `LEENOT` | Marcar un aviso como leido | notificacion | CLIENTE |
| `REPDEV` | Reportar un reclamo | detalleVenta, cantidad, motivo, descripcion | PERSONAL |
| `REVDEV` | Pasar un reclamo a revision | devolucion | PERSONAL |
| `APRDEV` | Aprobar contra un despacho (MOF) | devolucion, movimientoDespacho, justificacion | PERSONAL |
| `RCHDEV` | Rechazar un reclamo | devolucion, justificacion | PERSONAL |
| `RECDEV` | Recibir la mercancia devuelta | devolucion, cantidad, aptoReventa, claveOperacion | PERSONAL |
| `RPSDEV` | Resolver con reposicion | devolucion, loteFeder, claveOperacion | PERSONAL |
| `CTBDEV` | Contabilizar un reembolso antes de pagarlo | devolucion, monto | PERSONAL |
| `RMBDEV` | Resolver con reembolso liquidado | devolucion, monto, justificacion | PERSONAL |

### CU7. Indicadores

| Comando | Qué hace | Parámetros | Roles |
|---|---|---|---|
| `CALIND` | Calcular un indicador al corte | metrica, dimension, desde, hasta, ambito | PERSONAL |

### CU8. Reportes

| Comando | Qué hace | Parámetros | Roles |
|---|---|---|---|
| `INFVEN` | Reporte de ventas entregadas | desde, hasta, agruparPor, formato | PERSONAL |
| `INFINV` | Reporte de inventario FEDER | desde, hasta, agruparPor, formato | PERSONAL |
| `INFLOA` | Reporte de lotes de proveedores | desde, hasta, agruparPor, formato | PERSONAL |
| `INFPAG` | Reporte de cobros | desde, hasta, agruparPor, formato | PERSONAL |
| `INFENT` | Reporte de entregas | desde, hasta, agruparPor, formato | PERSONAL |
| `INFDEV` | Reporte de devoluciones | desde, hasta, agruparPor, formato | PERSONAL |
| `INFIND` | Reporte de indicadores guardados | desde, hasta, tipo, formato | PERSONAL |

## Códigos de error

Son códigos estándar de correo: respuesta SMTP (RFC 5321) y código extendido (RFC 3463). Los `5xx` son errores permanentes, y hay que corregir el comando. Los `4xx` son temporales, y se puede reintentar. Varios casos comparten código; el tipo y el detalle indican cuál fue.

| Código | Tipo | Cuándo ocurre | Dónde se ve |
|---|---|---|---|
| `500 5.5.2` | FORMATO_INVALIDO | El asunto no tiene la forma `ACCIONTABLA[...]` | Correo y terminal |
| `500 5.5.1` | COMANDO_INEXISTENTE | La acción o la tabla no existen | Correo y terminal |
| `501 5.5.2` | PARAMETROS_MAL_ESCRITOS | Faltan comillas o comas, o los corchetes están vacíos | Correo y terminal |
| `501 5.5.4` | CANTIDAD_PARAMETROS | Sobran o faltan parámetros | Correo y terminal |
| `501 5.5.4` | VALOR_INVALIDO | Tipo, fecha, enumerado, longitud o decimales incorrectos, o `INS` sin ningún valor | Correo y terminal |
| `501 5.5.4` | ID_INVALIDO | El ID no es un número | Correo y terminal |
| `550 5.0.0` | REGISTRO_NO_ENCONTRADO | No existe el ID indicado o el de una relación | Correo y terminal |
| `550 5.7.1` | ACCESO_DENEGADO | El remitente no tiene una cuenta o rol que permita la orden, o la tabla es histórica | Correo y terminal |
| `554 5.7.0` | REGLA_NEGOCIO | El comando es válido, pero el negocio no lo permite (por ejemplo, stock insuficiente) | Correo y terminal |
| `554 5.0.0` | RESTRICCION_BD | La base rechaza la operación, por ejemplo al borrar un registro con dependencias | Correo y terminal |
| `451 4.3.0` | BD_NO_DISPONIBLE | No hay conexión con la base o se agotó el tiempo | Correo y terminal |
| `554 5.3.0` | ERROR_INTERNO | Fallo inesperado | Correo y terminal |
| `421 4.4.0` | BUZON_NO_DISPONIBLE | No se pudo entrar al buzón por POP3 | Terminal |
| `554 5.6.0` | ASUNTO_DANADO | El asunto llegó con caracteres de control o mal codificado (MIME); la orden no se ejecuta | Correo y terminal |
| `554 5.6.0` | CORREO_ILEGIBLE | No se pudo procesar un correo recibido | Terminal |
| `421 4.4.1` | RESPUESTA_NO_ENVIADA | No hubo conexión con el servidor SMTP. Si el servidor rechaza el envío, se muestra su propio código, como `550 5.7.1` | Terminal |

El asunto de la respuesta es `Error 501 5.5.4` y el cuerpo indica el código, el tipo y el detalle. En los errores de base de datos la terminal añade el `SQLSTATE` (por ejemplo `23503`, clave foránea).

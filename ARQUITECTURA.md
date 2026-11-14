# Arquitectura de tres capas

El sistema recibe órdenes por correo. Ese canal es su interfaz de usuario: no necesita un controlador HTTP para tener capa de presentación.

```text
Correo entrante
    |
    v
presentation: CorreoService -> ComandoService
    |
    v
service: permisos, CRUD y operaciones de negocio
    |
    v
repository: consultas JPA, persistencia y bloqueos
    |
    v
PostgreSQL

service -> dto.Resultado -> presentation -> correo de respuesta
```

## Responsabilidades

| Paquete | Responsabilidad | Ejemplos |
| --- | --- | --- |
| `presentation` | Leer el correo, interpretar la sintaxis del asunto, convertir resultados en texto/HTML, generar adjuntos y enviar mensajes. | `ComandoService`, `CorreoService`, `PresentadorRespuesta`, `PresentadorReporte` |
| `service` | Validar permisos y reglas, coordinar operaciones, calcular importes y mantener las transacciones. | `VentaService`, `CobroService`, `OperacionesService`, `CrudService` |
| `repository` | Ejecutar consultas y operaciones de persistencia. Encapsular `EntityManager`, Spring Data y bloqueos. | `VentaConsultasRepository`, `CrudRepository`, `PersistenciaRepository` |

Los paquetes de apoyo no son capas adicionales de negocio:

- `entity`: modelo persistente compartido y sus enumerados.
- `dto`: resultados independientes del correo, como `Resultado`, `Listado` e `Informe`.
- `service.puerto`: contratos que necesita el negocio para comunicarse con servicios externos.
- `infrastructure`: adaptadores externos, actualmente `PagoFacilGateway`, que implementa `PasarelaPago`.
- `config`: configuración y arranque de Spring.

## Límites que deben mantenerse

1. Presentación llama a servicios; nunca llama directamente a repositorios.
2. Los servicios llaman a repositorios y puertos; no importan presentación ni adaptadores concretos.
3. Los repositorios no llaman a los servicios y no construyen HTML ni respuestas por correo.
4. Los repositorios devuelven entidades, valores, listas u opcionales. No exponen `EntityManager`, consultas JPA ni streams asociados a una sesión.
5. Las reglas de negocio permanecen en servicios. Por ejemplo, un repositorio puede bloquear un lote, pero el servicio decide si se puede vender.
6. Los resultados de negocio se devuelven como DTO. `PresentadorRespuesta` construye HTML y el QR; `PresentadorReporte` exporta PDF y Excel.

## Transacciones y salidas externas

`OperacionesService.ejecutar()` conserva la transacción que incluye el caso de uso y su auditoría. Los métodos CRUD y las tareas de negocio mantienen sus propias transacciones. Los repositorios participan en esas transacciones; los bloqueos pesimistas se mantienen hasta su finalización.

La presentación de resultados y la exportación de informes se realizan después de que el caso de uso devuelve sus datos. El negocio de cobros depende de `PasarelaPago`, implementada por `PagoFacilGateway`. El negocio de notificaciones depende de `EmisorNotificaciones`, implementado por `EmisorNotificacionesCorreo`.

Esta reorganización conserva los comandos, el esquema SQL y las reglas funcionales existentes. No corrige los problemas funcionales identificados en la revisión previa: autenticidad del remitente, borrado anticipado del correo, validación del vencimiento al despachar, coherencia del productor y reintentos de notificaciones.

## Comprobación

Desde PowerShell, ejecutar las pruebas aisladas y las reglas de arquitectura:

```powershell
.\mvnw.cmd clean '-Dtest=!ConexionBaseDatosTest,!CorreoApplicationTests' test
```

`ArquitecturaCapasTest` revisa las dependencias prohibidas en el código fuente. Las pruebas existentes verifican comandos, permisos, correo MIME, pagos y flujos de negocio con PostgreSQL temporal. Se excluyen las dos pruebas que usan la conexión de base de datos configurada en el proyecto.

El respaldo anterior a la reorganización está en `.revision/antes-tres-capas.zip`; contiene código, pruebas, `pom.xml` y `COMANDOS.md`.

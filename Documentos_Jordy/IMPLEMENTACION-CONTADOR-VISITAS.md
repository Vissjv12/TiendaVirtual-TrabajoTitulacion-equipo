# Implementación del contador persistente de visitas

## 1. Objetivo

Se incorporó un contador de visitas para la página pública principal del sistema. La solución registra como máximo una visita por navegador cada 24 horas y presenta en el panel administrativo:

- total histórico de visitas;
- visitas correspondientes al día actual;
- fecha utilizada para el cálculo diario.

La funcionalidad se implementó en la rama `feature/contador-visitas`, sin modificar directamente `main`.

## 2. Decisión de persistencia

Se evaluó utilizar Valkey para almacenar el contador. Sin embargo, se decidió utilizar una tabla nueva en MySQL como fuente de verdad.

### Motivos de la decisión

1. MySQL ya es el almacenamiento persistente principal del sistema.
2. El contador forma parte de la información histórica y administrativa del negocio.
3. La tabla permite conservar los datos después de reinicios del servicio o de Valkey.
4. La consulta histórica y las copias de seguridad quedan integradas con la base de datos existente.
5. Valkey permanece disponible para su propósito actual de caché de catálogo, sin mezclar caché con información estadística definitiva.

En el servidor se verificó que Valkey tiene persistencia RDB activa, pero aun así no se utilizó para este dato porque el contador no debe depender de la disponibilidad, expiración o restauración de una caché.

## 3. Diseño de la tabla

La migración se encuentra en [`V12-visitas-diarias.sql`](../scripts/prod/migraciones/V12-visitas-diarias.sql).

Tabla: `productos.visitas_diarias`

| Campo | Tipo | Función |
|---|---|---|
| `fecha` | `DATE` | Identificador del día y clave primaria |
| `cantidad` | `BIGINT` | Número acumulado de visitas de ese día |
| `fecha_actualizacion` | `TIMESTAMP` | Fecha y hora de la última actualización |

La tabla no tiene relación con productos, empresas, usuarios ni otras entidades. Es un registro agregado independiente: cada fila representa un día y conserva el histórico completo, no solamente el día actual.

Ejemplo conceptual:

```text
2026-11-17 | 25
2026-11-18 | 8
```

El total histórico se obtiene sumando `cantidad` de todas las filas.

La operación de incremento utiliza una inserción idempotente por fecha:

```sql
INSERT INTO visitas_diarias (fecha, cantidad)
VALUES (:fecha, 1)
ON DUPLICATE KEY UPDATE cantidad = cantidad + 1;
```

Esto evita crear varias filas para el mismo día y permite incrementar el contador de forma segura ante solicitudes concurrentes.

## 4. Cambios realizados

### Backend: `msvc-producto`

Se agregaron:

- entidad [`VisitaDiariaEntity.java`](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/persistence/entity/VisitaDiariaEntity.java);
- repositorio [`VisitaDiariaJpaRepository.java`](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/persistence/repository/VisitaDiariaJpaRepository.java);
- servicio [`VisitasService.java`](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/VisitasService.java);
- DTO [`VisitasEstadisticasDto.java`](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/dto/VisitasEstadisticasDto.java);
- controlador [`VisitasController.java`](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/presentation/controller/VisitasController.java).

Endpoints:

| Método | Ruta | Acceso | Resultado |
|---|---|---|---|
| `POST` | `/api/visitas` | Público | Registra una visita y devuelve `204` |
| `GET` | `/api/visitas/estadisticas` | `ROLE_ADMIN` | Devuelve total, visitas del día y fecha |

El servicio utiliza la zona horaria `America/Guayaquil` para determinar el día de negocio.

### Gateway

Se agregó en [`gateway/nginx.conf`](../gateway/nginx.conf) la ruta `/api/visitas`, direccionada hacia `msvc-producto` en el puerto `8081`.

Esto permite que el frontend use el Gateway y no acceda directamente al puerto interno del microservicio.

### Frontend

Se agregó [`visitasService.ts`](../avalon-react-10.1.0/services/visitasService.ts).

La página pública registra la visita al cargarse y establece la cookie `feriadigital_visit_day` durante 24 horas. Si la cookie existe, no vuelve a contabilizar el mismo navegador durante ese período.

El panel administrativo consulta las estadísticas y muestra una tarjeta con el total acumulado y el total del día.

## 5. Validación realizada

Se verificó el Gateway local en el puerto `8090`:

```text
gateway-feria-digital: Up (healthy)
```

Registro de visita a través del Gateway:

```text
POST http://127.0.0.1:8090/api/visitas
HTTP/1.1 204 No Content
```

Verificación de la tabla:

```text
DESCRIBE productos.visitas_diarias
COUNT(*) = 1
```

El resultado confirma que se creó una fila para el día actual y que la visita quedó persistida en MySQL.

La consulta de estadísticas sin credenciales devolvió `403`, confirmando que el endpoint administrativo está protegido. La consulta correcta para un administrador es:

```text
GET /api/visitas/estadisticas
Authorization: Bearer <JWT_ADMIN>
```

Los tokens no deben incluirse en documentación, commits ni evidencias públicas.

## 6. Operación y despliegue

1. Aplicar la migración [`V12-visitas-diarias.sql`](../scripts/prod/migraciones/V12-visitas-diarias.sql) en la base `productos`.
2. Verificar que la tabla exista antes de iniciar `msvc-producto`, debido a que el servicio utiliza `ddl-auto=validate`.
3. Iniciar o actualizar `msvc-producto`.
4. Recargar el Gateway para que incluya la ruta `/api/visitas`.
5. Reiniciar el frontend para que cargue la configuración actual del Gateway.
6. Validar un `POST` público y un `GET` administrativo autenticado.

La recreación del contenedor del Gateway no elimina datos de MySQL. No se debe ejecutar `docker compose down -v` como parte de este despliegue, porque eliminaría el volumen de la base local.

## 7. Prueba automatizada

Se añadió [`VisitasServiceTest.java`](../Microservicios/msvc-producto/src/test/java/com/example/msvc_producto/VisitasServiceTest.java), que verifica:

- incremento de visitas por fecha;
- cálculo del total histórico;
- cálculo de las visitas del día actual;
- uso de una fecha controlada mediante un reloj fijo.

## 8. Commit y rama

La implementación fue publicada en:

```text
Rama: feature/contador-visitas
Commit: daffd59
```

La rama no altera `main` y puede revisarse o integrarse mediante un Pull Request cuando el equipo apruebe el cambio.


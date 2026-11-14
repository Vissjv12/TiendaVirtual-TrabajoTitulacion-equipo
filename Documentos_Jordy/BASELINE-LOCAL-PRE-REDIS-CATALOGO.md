# Línea base local del catálogo antes de Redis

- **Fecha:** 2026-11-13 (America/Guayaquil)
- **Rama local:** `feature/a17-piloto-redis-local`
- **Base:** `integration/estabilizacion-produccion` en `aca30ab648f968651d773d7bfb778f29c9002950`
- **Código de servicio:** Spring Boot 3.4.5, Java 17
- **Entorno:** MySQL 8.0.46 y Redis 7 Alpine en Docker de pruebas local
- **Datos:** semillas sintéticas del repositorio; ningún dato ni credencial del servidor institucional

## Qué se midió

Se hicieron 5 solicitudes de calentamiento y después 50 solicitudes secuenciales medidas por cada ruta:

1. `GET http://127.0.0.1:8081/api/productos/listado`, que lee productos en `productos` y solicita cantidades al servicio de Inventario en `8082`.
2. `POST http://127.0.0.1:8082/api/inventarios/cantidad/batch`, con los IDs 1 a 39, para medir la lectura batch de Inventario por separado.

El cliente midió tiempo total HTTP en loopback hasta recibir el cuerpo. `p50` y `p95` usan el rango más cercano de 50 muestras. En MySQL se comparó el contador de sentencias `SELECT` de `performance_schema.events_statements_summary_by_digest` antes y después de cada serie, agrupado por esquema.

## Resultado observado

| Ruta | Solicitudes medidas | p50 | p95 | Errores HTTP | Respuestas distintas (hash) |
|---|---:|---:|---:|---:|---:|
| Catálogo con Inventario | 50 | 22.86 ms | 27.36 ms | 0 | 1 |
| Inventario batch directo | 50 | 6.62 ms | 7.59 ms | 0 | 1 |

| Lectura MySQL durante las muestras | SELECTs |
|---|---:|
| Esquema `productos` durante el catálogo | 50 |
| Esquema `inventario` durante el catálogo | 50 |
| Esquema `inventario` durante Inventario batch directo | 50 |

El set de prueba tiene 39 productos, 4 empresas, 6 categorías y 39 registros de inventario. El listado devolvió 39 productos; el producto 1 tuvo stock 500 en ambas rutas. Por cada solicitud medida de catálogo se observó un `SELECT` en `productos` y uno en `inventario`.

## Configuración relevante

- Los puertos de contenedor son `127.0.0.1:3307` para MySQL y `127.0.0.1:6380` para Redis.
- `CACHE_REDIS_ENABLED=false`; durante la medición, el microservicio Producto se configuró hacia `127.0.0.1:6399`, donde no había Redis escuchando. El endpoint funcionó y devolvió stock desde Inventario.
- Se midió el microservicio directamente; no se usó NGINX Gateway.
- La suite completa de `msvc-producto` pasó: 16 pruebas, 0 fallos.

## Límites de esta evidencia

Es una sola corrida secuencial y local, con volumen pequeño, servicios calientes y red loopback. No representa concurrencia, latencia externa ni comportamiento del servidor. No mide una ruta de producto sin el enriquecimiento de Inventario, ni demuestra aislamiento de autorización entre empresas. Los hashes solo demuestran que el cuerpo no cambió dentro de estas 50 solicitudes sintéticas.

Los tiempos de Inventario y catálogo son mediciones de rutas completas por separado; su diferencia no atribuye causalmente el tiempo a un componente. Sí queda observado que la ruta actual ejecuta una lectura de producto y una lectura batch de inventario por solicitud.

## Decisiones aún necesarias

Esta línea base no aprueba el contenido de Redis ni habilita el caché en el endpoint. Siguen abiertos T07, T12/A23, T13 y H-15, incluyendo la autorización y la exposición de `empresaId` y precio. No se cacheó `ProductoListadoDto`, inventario, ni datos de empresa. Redis queda como infraestructura opcional pendiente del contrato público aprobado.

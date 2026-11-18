# Verificación Redis — 17 de noviembre de 2026

## Prueba

- Endpoint: `GET /api/productos/listado` (listado completo, que sí tiene caché en el código desplegado).
- Perfil k6: 1 req/s durante 10 s; 10 iteraciones, sin Docker.
- Flags durante la prueba: `CACHE_REDIS_ENABLED=true`, `CACHE_REDIS_PRODUCTOS_LISTADO_ENABLED=true`, `CACHE_REDIS_CATEGORIAS_ENABLED=true`.
- Resultado: 10/10 HTTP 200, 20/20 checks, cero fallos HTTP y cero iteraciones descartadas.

## Evidencia de caché

- Antes de la prueba solo estaba la clave de generación `feriadigital:v1:{catalogo}:epoch`.
- Después apareció `feriadigital:v1:{catalogo}:productos:listado:g50ac869e-ca9c-427a-b01f-2d8c2e9ce688`, con TTL de 243 s al consultarla.
- En `INFO stats`, entre las lecturas anterior y posterior, los contadores globales de Redis pasaron de 31.417 a 31.447 `keyspace_hits` y de 57 a 58 `keyspace_misses`. Son contadores de toda la instancia, así que puede haber actividad concurrente; la clave nueva del listado es la evidencia directa de que la ruta pobló Redis.
- El JAR mantuvo el SHA-256 `12cb2a840acc5089bcc723c12d3981ade8aca245cdba272c7a63c7e75ae49d72`; el PID se mantuvo en `576134`. El servicio siguió activo, health `UP` y Valkey respondió `PONG`.

## Alcance

Esto demuestra que la ruta escribe y reutiliza caché Redis con los flags activos. No demuestra una mejora de latencia: 10 solicitudes son una comprobación funcional, no una comparación ON/OFF ni una prueba de carga. El p95 de 186,93 ms de esta muestra pequeña no debe compararse con otras corridas.

## Archivos de evidencia

- `k6.log`
- `summary.json`
- `samples.json`

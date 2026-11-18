# Evidencia Redis y comparación ON/OFF en producción

- **Fecha de prueba:** 17 de noviembre de 2026
- **Alcance:** lectura pública del catálogo de productos; no se consultaron ventas ni clientes y no se escribieron datos de negocio.
- **Estado final observado:** flags Redis restaurados a `true`; `msvc-producto` activo, health `UP` y Valkey `PONG`.

## Conclusión ejecutiva

La evidencia confirma que Redis **sí está integrado y se usa** para servir el listado completo de productos: el smoke funcional creó una clave de catálogo con TTL, y en la carga ON los hits globales de Redis aumentaron de forma consistente con las solicitudes k6.

La comparación HTTP de producción **no demuestra una mejora perceptible de latencia**. A 10 solicitudes por segundo, el p95 promedio de las dos corridas ON fue 100,88 ms y el de las dos corridas OFF 101,82 ms: 0,94 ms, o aproximadamente 0,9 %, a favor de ON. Esa diferencia es menor que la variación entre repeticiones. Por tanto, la conclusión defendible es: **el caché funciona y evita volver a consultar el catálogo en cada petición; no se ha probado una mejora práctica del p95 de extremo a extremo en esta carga**.

## Dónde está implementado

| Ruta | Estado de caché confirmado en el código | Qué se conserva en Redis |
|---|---|---|
| `GET /api/productos/listado` | Cache-aside, gobernado por los flags global y de listado | Proyección pública de productos; no incluye cantidades de inventario |
| `GET /api/categorias` | Cache-aside, gobernado por los flags global y de categorías | Lista de categorías |
| `GET /api/categorias/{id}` | Cache-aside, gobernado por los flags global y de categorías | Detalle de una categoría |
| `GET /api/productos/listado/paginado` | **No tiene caché** en la versión revisada | Consulta `findListadoPaginado(...)` y luego obtiene el inventario por lote |

El controller del listado completo delega en `ProductoServiceImpl.obtenerProductosOptimizado()`. Su método `obtenerCatalogoBase()` consulta `ProductoCatalogoCacheService`: ante un miss, lee la proyección de MySQL y la publica en Redis; ante un hit, recupera el JSON de Redis. Después se consulta Inventario para cada petición. El DTO `ProductoCatalogoPublicoDto` excluye el stock deliberadamente, así que el caché no sirve cantidades viejas como si fueran actuales.

La clave del catálogo usa una generación compartida (`feriadigital:v1:{catalogo}:epoch`) y una clave versionada para el listado. Las escrituras de catálogo avanzan esa generación después del commit. Si Redis falla, el servicio usa la fuente MySQL; si el JSON cacheado está dañado, invalida la generación y vuelve a consultar MySQL.

El motivo de empezar por el listado completo es que es una lectura pública y repetida de metadatos relativamente estables, donde el mismo catálogo puede evitar consultas repetidas a MySQL. Categorías también son datos de lectura frecuente. El stock se mantiene en el servicio de Inventario, que sigue respondiendo en vivo. La ruta paginada no se incluye porque tiene combinaciones de filtros, orden y página que requieren claves y política de invalidación específicas; está documentada por separado como trabajo futuro.

Referencias de código:

- [Controller de productos](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/presentation/controller/ProductoController.java#L182) y [ruta paginada](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/presentation/controller/ProductoController.java#L236).
- [Flujo de caché e inventario](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoServiceImpl.java#L209), [coordinador Cache-Aside](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoCatalogoCacheService.java#L39) y [proyección pública](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/dto/ProductoCatalogoPublicoDto.java#L1).
- [Configuración de flags y TTL](../Microservicios/msvc-producto/src/main/resources/application.properties#L62), [lecturas de categorías](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/CategoriaCatalogoQueryService.java#L24) e [invalidación tras commit](../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoCatalogoInvalidacionService.java#L22).

Los defaults del repositorio son seguros: `CACHE_REDIS_ENABLED=false`, `CACHE_REDIS_PRODUCTOS_LISTADO_ENABLED=false` y `CACHE_REDIS_CATEGORIAS_ENABLED=false` cuando no se configuran. Los TTL por defecto son 5 minutos para productos y 15 minutos para categorías.

## Pruebas realizadas en el servidor

La comparación aisló el caché del listado: el modo **ON** usó global=true, productos=true y categorías=true; el modo **OFF** mantuvo global=true y categorías=true y cambió solo productos=false. El servicio se reinició al cambiar y restaurar ese flag. El SHA-256 del JAR se mantuvo igual en los cuatro tramos:

`12cb2a840acc5089bcc723c12d3981ade8aca245cdba272c7a63c7e75ae49d72`

No se verificó una atestación que vincule ese JAR con un commit concreto. El código inspeccionado estaba en `main`, commit `15b474a11e8ee26a51071edca1bee573ca871e88`, igual a `origin/main` durante la revisión.

### Carga k6 ON/OFF

- Herramienta: k6 v2.4.0; sin Docker.
- Petición: `GET https://tienda.gadmsigchos.gob.ec:444/api/productos/listado`.
- Carga: 10 req/s durante 5 minutos; dos repeticiones por modo, alrededor de 3.000 solicitudes por repetición.
- Checks: HTTP 200 y JSON con forma de lista.
- Se conservó el mismo JAR, ruta, tasa, duración y umbrales. Solo cambió el flag específico del caché de productos.

| Modo | Corrida | Solicitudes | Media | p50 | p95 | Fallos HTTP | Checks fallidos | Iteraciones descartadas |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ON | 1 | 3.001 | 99,87 ms | 99,14 ms | 101,80 ms | 0 | 0 | 0 |
| ON | 2 | 3.001 | 99,41 ms | 98,87 ms | 99,97 ms | 0 | 0 | 0 |
| OFF | 1 | 3.001 | 100,99 ms | 100,34 ms | 103,06 ms | 0 | 0 | 0 |
| OFF | 2 | 3.000 | 99,65 ms | 99,16 ms | 100,59 ms | 0 | 0 | 0 |

Las diferencias de los promedios de las dos corridas fueron:

- p95: ON 100,88 ms frente a OFF 101,82 ms; ON fue 0,94 ms (0,9 %) menor.
- p50: ON 99,00 ms frente a OFF 99,75 ms; ON fue 0,75 ms (0,75 %) menor.
- Media: ON 99,64 ms frente a OFF 100,32 ms; ON fue 0,68 ms (0,68 %) menor.

Estos valores son promedios de los percentiles de cada corrida, no percentiles recalculados sobre todas las muestras juntas. En las cuatro corridas k6 mostró los umbrales aprobados, 100 % de checks y código de salida 0.

### Comprobación directa de Redis

Antes del smoke funcional, Redis tenía la clave de generación, pero aún no una clave del listado. Después de 10 solicitudes a 1 req/s durante 10 segundos:

- Apareció una clave bajo `feriadigital:v1:{catalogo}:productos:listado:g<generación>` con TTL de 243 segundos al consultarla.
- Las 10 respuestas fueron HTTP 200 y pasaron los 20 checks; no hubo iteraciones descartadas.
- Los contadores globales de Redis avanzaron de 31.417 a 31.447 hits y de 57 a 58 misses. Son contadores de toda la instancia, no métricas aisladas de este endpoint; la clave versionada recién creada es la evidencia directa de que el listado pobló Redis.

En ON1, `INFO stats` pasó de 31.448 a 40.451 hits y de 58 a 59 misses, con 3.001 solicitudes k6. El cambio (+9.003 hits, +1 miss) es consistente con reutilización de caché, aunque los contadores de la instancia pueden incluir actividad concurrente.

### Evidencia automatizada existente

El repositorio contiene pruebas para hit sin consultar el origen, miss y publicación con TTL, fallos de Redis con fallback, JSON dañado, inventario fresco por petición, invalidación después de escrituras y generaciones concurrentes. Entre ellas están [ProductoCatalogoCacheServiceTest](../Microservicios/msvc-producto/src/test/java/com/example/msvc_producto/ProductoCatalogoCacheServiceTest.java#L34), [ProductoCatalogoHttpIT](../Microservicios/msvc-producto/src/test/java/com/example/msvc_producto/ProductoCatalogoHttpIT.java#L141) y [RedisGenerationIT](../Microservicios/msvc-producto/src/test/java/com/example/msvc_producto/RedisGenerationIT.java#L23). Esta comparación de producción no volvió a ejecutar Maven.

El documento [REDIS-RESULTADOS-A18.md](REDIS-RESULTADOS-A18.md) registra pruebas locales previas con MySQL sintético y un stub de Inventario: observó cerca de 99,8 % menos `SELECT` por solicitud en modo hit, pero el p95 mediano fue 54,69 ms con hit frente a 49,45 ms sin caché. Es evidencia local de menor lectura al origen, no una cifra de producción ni una garantía de menor latencia.

### Incidente anterior excluido de la comparación

Una corrida exploratoria previa de aproximadamente 35 minutos, con tasas de 1/2/5/10 req/s, terminó con 23 respuestas 502 durante la segunda repetición a 10 req/s. En ese intervalo el journal mostró que systemd detenía `msvc-producto` y Java terminaba con SIGTERM; después cambió el SHA del JAR. No se determinó quién inició la parada. Esa corrida se excluyó de esta comparación ON/OFF; las cuatro corridas de esta sección usaron el mismo SHA y el servicio permaneció saludable.

## Qué demuestra y qué no

**Sí demuestra:** Redis está conectado, la ruta crea y reutiliza la clave de catálogo, el flag permite comparar el camino cacheado con la consulta a origen y el endpoint siguió respondiendo correctamente a 10 req/s en los cuatro tramos.

**No demuestra:** una mejora significativa del tiempo de respuesta ni una reducción porcentual de consultas MySQL en producción. No se capturaron métricas SQL por endpoint ni latencia separada de Inventario. El cambio p95 de 0,9 % es menor que la variación entre las dos repeticiones de cada modo; además, el orden fue ON y luego OFF, no aleatorizado, y solo se hicieron dos repeticiones.

La salida `summary.json` de k6 codifica como `false` los estados de algunos umbrales aunque el log de consola los muestra aprobados y cada proceso terminó con código 0. Para el estado de umbrales se conservaron y contrastaron los logs de consola y los conteos numéricos; no se usaron esos booleanos del JSON para declarar fallos.

## Recomendaciones

1. Mantener la afirmación de Redis acotada: **cachea correctamente el listado y reduce lecturas repetidas de catálogo; esta prueba no demuestra una mejora apreciable de latencia p95**.
2. Instrumentar el origen de datos en producción: consultas `SELECT` atribuibles a esta ruta, CPU de MySQL, tiempos de `findAllOptimized()` y latencia de Inventario. Así se identifica qué parte domina los ~100 ms observados.
3. Separar métricas Redis por recurso. Hoy el tag de Micrometer usa `resource=catalogo`, que agrupa más de una lectura; distinguir productos, categorías y futuros endpoints facilitaría medir hits, misses, fallback y fallos.
4. Conservar el stock fuera de esta caché. Evaluar cualquier caché de inventario solo con una política de frescura e invalidación aprobada por el responsable del dato.
5. Antes de ampliar la caché paginada, implementar una clave que incluya filtros normalizados, página, tamaño y orden; cachear solo metadatos del catálogo, consultar stock en vivo y probar invalidación, filtros, caída de Redis y respuesta paginada. No afirmar que `GET /api/productos/listado/paginado` ya use Redis.
6. Para una conclusión más sólida de latencia, repetir al menos tres pares ON/OFF con orden alternado, misma versión y datos, calentamiento controlado y carga acordada. Definir antes un umbral de mejora p95 y guardar también métricas SQL e Inventario.

## Evidencia adjunta

Los resúmenes finales de consola (`k6-final-summary.txt`), los JSON de cada corrida y el script reproducible están en [EVIDENCIA-REDIS-COMPARACION-2026-11-17](EVIDENCIA-REDIS-COMPARACION-2026-11-17/): `C-on-rep1`, `C-on-rep2`, `B-off-rep1`, `B-off-rep2` y `smoke-funcional`. Los logs completos permanecen en el espacio local de ejecución. El script usa variables de entorno y no contiene credenciales.

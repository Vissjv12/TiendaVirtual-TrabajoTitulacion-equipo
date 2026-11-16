# Resultados de carga local A18 — piloto Redis

**Fecha:** 2026-11-14  
**Rama:** `integration/avance-hasta-redis`  
**Base de la ejecución:** `1f4aeceef6bcfdf90d1aeb430dfbde4b2fc9317d`  
**Alcance:** microservicio Producto, Redis local, MySQL sintético, Inventario simulado y Gateway NGINX aislado. No hubo acceso al servidor ni a la base institucional.

> **Todos los resultados de rendimiento y funcionamiento que aparecen aquí son de pruebas locales aisladas con datos sintéticos. No son resultados del servidor institucional: no se desplegó la aplicación ni se consultó o midió su base de datos.**

## Resumen

- Redis funciona con la ruta de catálogo configurada y permite observar miss, hit, fallback, fallos, salud y vencimiento. Las pruebas `clean test` pasaron **26/26**; el conjunto focalizado Redis/HTTP/autorización pasó **46/46** en Maven/JDK 17.
- La prueba de runtime atendió catálogo por el Gateway con Redis activo y también con Redis caído. Durante la caída la respuesta de Producto continuó con HTTP 200, liveness/readiness continuaron en 200 y la salud exclusiva de caché pasó a 503. Tras reiniciar Redis, salud y catálogo volvieron a 200.
- La primera comparación mixta (seis rutas) quedó diluida. Después se ejecutó un escenario dedicado de `GET /api/productos/listado`: tres corridas de 30 s por cada modo, con métricas MySQL. Redis hit redujo fuertemente las lecturas `SELECT` observadas, pero el p95 no mejoró en estas corridas.
- Mantener desactivadas las banderas en entornos compartidos. Las decisiones de campos públicos y la prueba institucional siguen pendientes.

## Entorno y reproducción

Se usó el JAR de este checkout, construido con JDK 17. Las rutas ocuparon puertos aislados para no confundirlas con procesos de otros checkouts:

| Componente | Dirección local | Datos/función |
|---|---|---|
| Producto de esta rama | `127.0.0.1:8281` | JAR actual, enlazado a MySQL sintético y Redis local |
| Gateway NGINX aislado | `127.0.0.1:8290` | Configuración derivada de `gateway/nginx.conf`, upstream de Producto hacia 8281 |
| Stub de Inventario | `127.0.0.1:8282` | Devuelve cantidad sintética 100 y cuenta llamadas al lote |
| MySQL de pruebas | `127.0.0.1:3307` | Contenedor `mysql-feria-digital-test`, base local `productos` |
| Redis de pruebas | `127.0.0.1:6380` | Contenedor `redis-feria-digital-test` |

La primera carga se ejecutó con `grafana/k6:latest`, el script `scripts/p6-baseline-readonly.js`, 5 VUs y 30 segundos por modo, por el mismo Gateway y con el mismo conjunto de seis rutas. Para la repetición dirigida se añadió `scripts/redis-a18-listado.js`, que envía todas las solicitudes a `GET /api/productos/listado` y verifica HTTP 200 y 39 productos:

```powershell
$scriptPath = (Resolve-Path 'scripts/redis-a18-listado.js').Path
docker run --rm --add-host host.docker.internal:host-gateway `
  --mount "type=bind,source=$scriptPath,target=/scripts/redis-a18-listado.js,readonly" `
  --env BASE_URL=http://host.docker.internal:8290 `
  --env EXPECTED_PRODUCTS=39 grafana/k6:latest `
  run --vus 5 --duration 30s /scripts/redis-a18-listado.js
```

No se guardaron en este documento variables con secretos, tokens ni valores de `.env`.

### Corrección necesaria al lanzar el JAR

`application-redis-test.properties` vive en `src/test/resources` y no se incluye en el JAR ejecutable. Activar `redis-test` al lanzar el JAR no basta para cargar esas propiedades. En la primera tentativa, Redis quedó en el puerto predeterminado y el indicador informó indisponibilidad; esa carga se descartó, no se contó como miss. Para la ejecución correcta del JAR se pasaron explícitamente las variables de conexión **locales** de MySQL (`127.0.0.1:3307`) y Redis (`127.0.0.1:6380`), más la URL del stub local. La revisión detectó esta diferencia de empaquetado; no se cambiaron los valores por defecto de la aplicación.

## Primera comparación: carga mixta de seis rutas

Este bloque conserva los resultados de la primera corrida mixta (una repetición por modo). Sus límites aplican a ese escenario; la segunda medición dedicada al listado, más abajo, es la comparación actual para evaluar el efecto de Redis en `GET /api/productos/listado`.

El modo miss usó un TTL de prueba de 1 ms para forzar expiraciones. La concurrencia produjo algunos hits antes del vencimiento; después de descontar una lectura de verificación previa a la carga, el período k6 tuvo 1.559 misses y 100 hits. El modo hit tuvo una lectura de precalentamiento antes de k6 y, durante la carga, 1.806 hits. Redis seguía con una clave de listado y TTL de 228 segundos después de la ejecución hit.

| Modo | Solicitudes k6 | p50 | p95 | Promedio HTTP | Solicitudes/s | Errores HTTP | Contador de Inventario | Memoria proceso Producto |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Redis desactivado | 9.879 | 12,68 ms | 31,94 ms | 14,96 ms | 327,46 | 3 (0,03 %) | 1.690 | 408 MiB |
| Redis activo, TTL 1 ms (miss) | 10.208 | 13,08 ms | 37,08 ms | 14,45 ms | 339,94 | 0 | 1.659 | 462 MiB |
| Redis activo, clave precalentada (hit) | 10.893 | 12,79 ms | 32,48 ms | 13,53 ms | 363,00 | 0 | 1.806 | 439 MiB |

En la línea base hubo 3 respuestas que no cumplieron el check HTTP 200 (0,03 %); el umbral de error de k6 configurado para la corrida siguió pasando. Miss y hit no registraron respuestas fallidas. Los tamaños de memoria son muestras de `WorkingSet64` del proceso al final de cada corrida, no promedios ni máximos. En hit, Redis reportó `used_memory=1.441.752` bytes.

La ruta del listado representa aproximadamente una de las seis rutas mezcladas. El número de llamadas al stub de Inventario quedó cercano a la frecuencia del listado en los tres modos. Esto confirma que el stock sigue consultándose en cada listado cacheado; Redis no almacena inventario. La mejora de rendimiento del listado puede quedar diluida por las otras rutas y por la llamada a Inventario.

### Interpretación de la primera corrida mixta (histórica)

- El p95 hit fue **32,48 ms**, aproximadamente **1,7 % mayor** que los **31,94 ms** de la línea base local; no alcanza la mejora propuesta de 20 %.
- El rendimiento observado subió aproximadamente 10,9 % en la mezcla hit, pero el número total de iteraciones difiere y solo hubo una corrida; esto no demuestra una mejora estable.
- Las métricas de aplicación y las pruebas confirman que un hit no invoca el proveedor de catálogo (`hitReturnsCachedJsonWithoutCallingSource`). No existe todavía una medida del número de sentencias/consultas SQL bajo k6; no se afirma una reducción porcentual de consultas.
- No se repitieron las corridas y no se aisló únicamente `GET /api/productos/listado`. Se requiere una siguiente medición con varias repeticiones, mismos datos, distribución fija y medición del origen SQL antes de decidir expansión.

**Lectura de esa corrida:** por sí sola no justifica expansión ni demuestra una mejora estable. La conclusión de A18 para el listado se basa en la medición repetida de la sección siguiente.

## Medición local repetida A18: solo listado de productos

Para aislar el efecto de Redis, se repitió **tres veces por modo** el mismo script k6 con 5 VUs durante 30 s, el mismo Gateway, fixture de 39 productos, MySQL local y stub de Inventario. El modo miss usó TTL de 1 ms y el hit usó el TTL normal de 5 min, con una petición de calentamiento fuera de cada medición. El stub confirmó una llamada de Inventario por cada petición en los tres modos.

`SELECT Δ` es el cambio del contador global `Com_select` de MySQL antes y después de cada ventana. El contenedor era el MySQL sintético de la prueba. El contador global puede incluir consultas de fondo; los contadores de Cache-Aside y la prueba `hitReturnsCachedJsonWithoutCallingSource` aportan la atribución al listado.

| Modo | Corrida | Solicitudes | p50 | p95 | Req/s | HTTP/check fallidos | `SELECT Δ` | Cache miss/hit | Inventario |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Desactivado | 1 | 3.640 | 39,66 ms | 56,34 ms | 121,26 | 0/0 | 3.651 | — | 3.640 |
| Desactivado | 2 | 4.073 | 34,51 ms | 49,45 ms | 135,62 | 0/0 | 4.084 | — | 4.073 |
| Desactivado | 3 | 4.257 | 33,08 ms | 47,63 ms | 141,80 | 0/0 | 4.264 | — | 4.257 |
| Miss, TTL 1 ms | 1 | 2.830 | 49,28 ms | 82,73 ms | 94,21 | 0/0 | 2.659 | 2.648/182 | 2.830 |
| Miss, TTL 1 ms | 2 | 3.332 | 43,46 ms | 60,37 ms | 110,99 | 0/0 | 3.171 | 3.160/172 | 3.332 |
| Miss, TTL 1 ms | 3 | 3.578 | 40,27 ms | 58,18 ms | 119,18 | 0/0 | 3.366 | 3.355/223 | 3.578 |
| Hit, TTL 5 min | 1 | 3.829 | 37,15 ms | 54,69 ms | 127,51 | 0/0 | 11 | 0/3.829 | 3.829 |
| Hit, TTL 5 min | 2 | 4.183 | 30,67 ms | 62,27 ms | 139,37 | 0/0 | 7 | 0/4.183 | 4.183 |
| Hit, TTL 5 min | 3 | 4.567 | 30,84 ms | 45,28 ms | 152,15 | 0/0 | 5 | 0/4.567 | 4.567 |

| Mediana de las tres corridas | p50 | p95 | Req/s |
|---|---:|---:|---:|
| Desactivado | 34,51 ms | 49,45 ms | 135,62 |
| Miss | 43,46 ms | 60,37 ms | 110,99 |
| Hit | 30,84 ms | 54,69 ms | 139,37 |

En las tres corridas desactivadas se observaron 11.999 `SELECT` frente a 11.970 solicitudes. En miss hubo 9.196 `SELECT`; los contadores atribuyen 9.163 misses y 577 hits concurrentes. En hit hubo 23 `SELECT` totales frente a 12.579 solicitudes y todas las peticiones de catálogo registraron hit. Normalizado por solicitud, el contador pasó de aproximadamente **1,002 `SELECT`** con Redis desactivado a **0,0018** en hit: cerca de **99,8 % menos `SELECT` observados por solicitud**. Es una señal local clara de reducción de carga MySQL; el residual de 5–11 sentencias por ventana no se atribuye al listado.

La mediana p50 mejoró alrededor de 10,6 % y el rendimiento mediano subió 2,8 %, pero el p95 mediano de hit fue **54,69 ms**, 10,6 % mayor que los 49,45 ms desactivados. Las corridas tienen variación visible y el stub de Inventario sigue consumiendo una llamada por petición; Redis no evita ese costo. Por eso no se afirma una mejora de latencia p95. Los nueve recorridos tuvieron 0 errores HTTP y 0 checks fallidos; todos verificaron 39 productos.

**Decisión de medición:** el criterio local alternativo de reducción de lecturas a origen supera el umbral propuesto de 20 % para este endpoint; el criterio p95 no se cumple. Mantener Redis como piloto únicamente para el listado, con flags desactivados por defecto fuera de local. No ampliar ni activar institucionalmente hasta aprobar los campos públicos, aceptar/resolver R15, validar la métrica bajo entorno controlado y completar los pendientes institucionales.

## Fallo y recuperación de Redis en runtime

La comprobación se hizo contra el contenedor local de pruebas y se restauró al finalizar:

| Escenario | Resultado observado |
|---|---|
| Redis apagado antes de iniciar el JAR con la caché habilitada | Aplicación iniciada; liveness 200; catálogo por Gateway 200 con 39 productos; `/actuator/health/cache` 503 |
| Redis detenido durante el servicio | Catálogo por Gateway 200 con 39 productos; liveness/readiness 200; salud de caché 503; métrica de requests `fallback` y de fallos de lectura aumentó en 1 |
| Redis reiniciado | `PING` respondió `PONG`; salud de caché 200 y catálogo por Gateway 200 |

La latencia deliberada para forzar un timeout superior a los `500ms` configurados no se midió. El caso R06 sigue pendiente; una conexión rechazada o Redis detenido no equivale a una prueba de timeout lento.

Las métricas `/actuator/metrics/**` requieren autoridad `ROLE_ADMIN`. Las pruebas HTTP verifican que usuario anónimo recibe 401, emprendedor recibe 403 y administrador puede consultar; health no devuelve credenciales ni detalles de conexión. El endpoint de salud de caché se separa de liveness/readiness.

## Matriz R01–R16

| ID | Estado en esta ejecución | Evidencia y límite |
|---|---|---|
| R01 | Verificado | Flags apagados por defecto; `RedisCacheConfigTest` demuestra que el modo desactivado no conecta a Redis; k6 ejecutado con Redis apagado en la configuración de caché. |
| R02 | Verificado | Miss carga catálogo y publica; carga correcta: 1.559 misses en el período k6 y 100 hits concurrentes con TTL de 1 ms. |
| R03 | Verificado | 1.806 hits en k6; prueba de servicio compara camino hit sin invocar origen y pruebas HTTP verifican contrato e Inventario fresco. |
| R04 | Verificado | Redis detenido antes de iniciar; aplicación inicia, catálogo 200 y salud de caché 503. |
| R05 | Verificado | Redis detenido en runtime; solicitud cae al origen con HTTP 200 y registra fallback. |
| R06 | Pendiente | Falta inyectar demora/timeout por encima de 500 ms y medir el tiempo máximo extremo a extremo. |
| R07 | Verificado | Prueba de integración usa TTL sintético corto, confirma expiración y lectura nueva; un hit no renueva TTL. |
| R08 | Verificado | Pruebas cubren crear, actualizar y desactivar (baja lógica) un producto, con invalidación tras commit; rollback no avanza generación. |
| R09 | Verificado | Pruebas cubren renombrar/desactivar categoría y renombrar/desactivar empresa e invalidación del catálogo. |
| R10 | Verificado | Dos empresas sintéticas y roles; el catálogo compartido es público por diseño, y los cambios de un emprendedor sobre producto ajeno se rechazan sin llamar al servicio. |
| R11 | Parcial | Proyección no incluye stock, `emprendedorId` ni datos de contacto; A16 mantiene pendientes `precio`, `empresaId` y campos de categoría hasta aprobación del responsable. Flags siguen apagados por defecto. |
| R12 | Parcial | Se cubren JSON corrupto, fallo de Inventario y fallos inyectados del servicio sin publicación; falta una prueba runtime de fallo real de MySQL a través de HTTP. |
| R13 | Verificado | Redis local se detuvo/reinició con la aplicación activa; PONG, salud y catálogo se recuperaron. |
| R14 | Parcial | GET públicos de la mezcla k6 y pruebas de contrato pasan por Gateway aislado; no se hizo la matriz completa de actores/autorización a través del Gateway en esta corrida. |
| R15 | Verificado con límite | Inyección después del commit mantiene la escritura y entrada vieja solo hasta TTL sintético; no se observó un TTL real de 5 min ni se aprobó esa ventana institucionalmente. |
| R16 | Verificado | Prueba determinista bloquea publicación de una lectura antigua después de cambiar la generación. |

## Decisiones pendientes y siguiente trabajo

1. Obtener del responsable la decisión de visibilidad pública para `precio`, `empresaId`, `activo` y fechas de categoría, y registrar quién la aprobó.
2. Resolver si la ventana R15 después de fallar la invalidación es aceptable; si no lo es, diseñar recuperación durable y evaluar migración/outbox antes de activarla fuera de local.
3. Completar R06 con latencia/timeout controlados y medir el costo separado del origen.
4. Añadir medición de cargas/consultas SQL o una métrica de lecturas al origen claramente diferenciada de sentencias SQL.
5. Repetir la línea base, miss y hit al menos tres veces con tráfico y distribución fijos; medir también el listado únicamente y contenido idéntico.
6. Hacer la comparación completa de activos/inactivos y la matriz de autorización por Gateway.
7. Obtener acceso y autorización institucional, identificar la versión desplegada y acordar una ventana/respaldo. Esta guía local no autoriza conexión, despliegue ni modificación del servidor.


## Revisión adicional en la rama estable (15 de noviembre de 2026)

Esta corrida local se hizo después del pull de integration/version-estable-deuna-pulido en bdb79ab799aaf82305bd1fd3a698410da9bf2636, con MySQL y Redis sintéticos. La suite completa pasó 33/33; la suite enfocada en Redis, HTTP y autorización pasó 49/49; Maven empaquetó el JAR. No se repitió k6 en esta revisión y las métricas históricas de este informe no se presentan como mediciones nuevas.

La decisión de campos que antes constaba pendiente fue aprobada después de la fecha de las corridas históricas. Las proyecciones actuales de producto y categoría coinciden con lo aprobado; producto omite inventarioCantidad. Esto actualiza el estado de diseño, pero no cierra R06, R12 ni R14: no hubo timeout Redis lento medido, fallo real de MySQL por HTTP ni matriz completa de autorización a través del Gateway. R15 mantiene el límite histórico: falta que el responsable acepte expresamente la ventana de datos obsoletos posterior a fallo de invalidación.

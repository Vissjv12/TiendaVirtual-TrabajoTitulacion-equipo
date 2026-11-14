# Verificación de la conexión Redis en local — A17/A18

**Fecha de verificación:** 14 de noviembre de 2026
**Rama local:** `feature/a17-piloto-redis-local`
**Base local:** `integration/estabilizacion-produccion` (`4a99df47b56ae935521875f2907d6ac636984619`)
**Entorno:** datos sintéticos en MySQL y Redis locales; no se accedió al servidor institucional ni a su base de datos.
**Estado:** validación local; no se desplegó en el servidor institucional.

## Resultado

Redis ya participa en tres rutas reales del catálogo: `GET /api/productos/listado`, `GET /api/categorias` y `GET /api/categorias/{id}`. Las tres usan cache-aside y flags propios apagados por defecto; el perfil `redis-test` las activa solo para pruebas locales.

“Cubrir todos los endpoints” se interpretó como revisar y clasificar las rutas del sistema, decidir dónde corresponde caché y probar que las lecturas del catálogo que no están aprobadas no tocan Redis. No se conectaron indiscriminadamente las rutas privadas, comerciales o transaccionales a una caché compartida.

## Comportamiento implementado

1. Las tres rutas consultan la generación vigente y sus claves versionadas en Redis.
2. El miss de productos consulta MySQL y el lote de Inventario; solo publica después de completar ambas lecturas, con TTL de cinco minutos.
3. En un hit de productos omite la lectura del catálogo a MySQL y vuelve a consultar Inventario. El stock no se guarda en Redis.
4. La lista y el detalle de categorías usan TTL de quince minutos y conservan el DTO HTTP actual.
5. Si Redis no está disponible, se consulta el origen. Si falla Inventario para productos, no se publica una respuesta parcial.
6. JSON de caché ilegible rota la generación y cae a MySQL. Los cambios confirmados de producto, categoría o empresa avanzan esa generación después del commit; el rollback no la avanza.

El objeto de producto es `ProductoCatalogoPublicoDto`; guarda `id`, `nombre`, `descripcion`, `precio`, `imagen`, `categoriaNombre`, `empresaNombre` y `empresaId`. No guarda stock, identificador de emprendedor ni datos de contacto de la empresa. El valor de categoría conserva su DTO actual: `id`, `nombre`, `descripcion`, `activo`, `fechaCreacion` y `fechaActualizacion`. El listado HTTP sigue incluyendo stock actualizado por Inventario.

La ruta de listado es anónima en el código revisado y la proyección conserva precio e identificador de empresa. Esta prueba local **no** constituye aprobación institucional de esos campos ni de su exposición. Deben confirmarse con el responsable del requisito antes de activar Redis en otro entorno.

## Inventario de rutas y decisión de caché

La clasificación se obtuvo de los controladores de los microservicios y del Gateway. El alcance de Redis aplicado es:

| Grupo de rutas | Decisión actual | Evidencia / razón |
|---|---|---|
| `GET /api/productos/listado` | Usa Redis si `CACHE_REDIS_ENABLED` y `CACHE_REDIS_PRODUCTOS_LISTADO_ENABLED` están activos. | Pruebas HTTP de miss/hit, respuesta igual, lectura de inventario por petición, claves y TTL. |
| `GET /api/productos`, `GET /api/productos/{id}`, `GET /api/productos/empresa/{empresaId}`, `GET /api/productos/categoria/{categoriaId}` | Conservan el camino actual desde el origen. | Las cuatro rutas se ejercitan en la prueba de exclusión y se comprueba que no llaman a Redis. |
| `GET /api/categorias`, `GET /api/categorias/{id}` | Usan Redis con `CACHE_REDIS_CATEGORIAS_ENABLED`; TTL local 15 minutos. | Pruebas de hit/miss, JSON corrupto, equivalencia HTTP e invalidación tras renombrar categoría. |
| `GET /api/empresas`, `GET /api/empresas/{id}` | Conservan el camino actual desde el origen. | Se ejercitan por Gateway; `EmpresaResponseDto` incluye RUC, dirección, teléfono y email, por eso no se guarda completo en Redis. |
| Escrituras de producto, categoría y empresa | Escriben en MySQL; después del commit invalidan la generación compartida del catálogo. | Se verificaron cambios confirmados y rollback. La recuperación durable ante caída de Redis sigue pendiente. |
| `/api/inventarios/**` y `/api/movimientos/**` | Sin caché Redis. | Stock y movimientos permanecen en su servicio y origen; el listado público consulta cantidades en vivo. |
| Autenticación, usuarios, configuración privada, sesiones y permisos | Sin caché Redis. | Son datos o decisiones dependientes de identidad y autorización. |
| Ventas, clientes, pagos, logística y procesos BPM | Sin caché Redis. | Son datos comerciales, sensibles o transaccionales; las validaciones permanecen en su servicio de origen. |
| Gateway, descubrimiento, salud y rutas internas | Sin caché de respuestas. | El Gateway enruta; no se añadió `proxy_cache`. La salud de Redis no reemplaza liveness/readiness. |

Se ejercitaron por Gateway las nueve lecturas de Producto/Categoría/Empresa: las tres conectadas a Redis y las seis que siguen en el origen. Las pruebas HTTP del microservicio verifican además que las lecturas excluidas no creen claves. Para los demás grupos la decisión se confirmó inspeccionando controladores y configuración; no se probó cada endpoint de cada microservicio por Gateway ni se conectó Redis a ellos.

## Configuración local

- `CACHE_REDIS_ENABLED=false` y `CACHE_REDIS_PRODUCTOS_LISTADO_ENABLED=false` por defecto.
- `CACHE_REDIS_TTL_PRODUCTOS=PT5M` como TTL del listado.
- `CACHE_REDIS_CATEGORIAS_ENABLED=false` y `CACHE_REDIS_TTL_CATEGORIAS=PT15M` por defecto.
- El perfil `redis-test` activa las tres familias y apunta únicamente a MySQL sintético `127.0.0.1:3307` y Redis local `127.0.0.1:6380`.
- La generación es UUID, inicializada atómicamente. La lectura y publicación comparan la generación vigente; no se reutiliza una generación por defecto si desaparece la clave.
- Las métricas locales registran resultado, duración y fallos sin etiquetar usuarios o empresas. No se agregó un endpoint de Actuator para exponerlas.

## Evidencia ejecutada

MySQL y Redis locales estaban disponibles. Para probar este checkout sin detener los servicios ajenos que ya ocupaban 8081/8082, se levantó Producto en `8181`, Inventario sintético en `8182` y un Gateway NGINX aislado en `8189`, derivado de la configuración del proyecto con upstream de Producto apuntando a `8181`. Gateway, MySQL y Redis respondieron correctamente.

| Verificación | Resultado |
|---|---:|
| Suite completa de `msvc-producto` | 23/23 pasaron en Maven/JDK 17 sobre la base rebased |
| Suite focalizada de Redis (generación, almacén, configuración, fallback HTTP, catálogo HTTP) | 29/29 pasaron |
| `ProductoCatalogoHttpIT` | 12/12 pasaron; incluye las tres rutas cacheadas, equivalencia hit/miss, invalidación y rutas excluidas |

La compilación limpia y la suite completa se ejecutaron en `maven:3.9-eclipse-temurin-17-alpine`, conectada a la red Docker de pruebas, con MySQL y Redis sintéticos. Las variables `TEST_*` se cargaron desde `.env` sin imprimir sus valores. El comando fue `mvn -B -Dstyle.color=never clean test`.

La suite focalizada se ejecutó después sobre el host Windows, donde MySQL/Redis están publicados en `3307`/`6380`. Las clases ya estaban compiladas con JDK 17; como el host usa JDK 25, se configuró `JAVA_TOOL_OPTIONS=-Dnet.bytebuddy.experimental=true`. Para evitar recompilar con JDK 25 se invocó el objetivo Surefire directamente: `& .\mvnw.cmd -B '-Dtest=RedisGenerationIT,RedisProductoCatalogoStoreIT,ProductoCatalogoHttpIT,ProductoCatalogoFallbackIT,ProductoCatalogoCacheServiceTest,RedisCacheConfigTest' surefire:test`. Las pruebas de integración Redis usan `127.0.0.1:6380`, por lo que esta suite se ejecutó en el host, no dentro del contenedor Maven.

La suite focalizada comprueba: hit/miss, stock fresco, proyección sin stock ni campos privados, no publicación ante fallo de Inventario, JSON corrupto, invalidación tras commit, rollback, exclusión de otras lecturas y fallback con Redis inaccesible. Incluye lista y detalle de categoría, invalidación tras cambio y fallback ante JSON corrupto.

**Prueba por Gateway y evidencia Redis:** se consultó `http://127.0.0.1:8189`, un NGINX aislado que dirige Producto a esta rama, con MySQL sintético `3307`, Redis `6380` e Inventario local/sintético `8182`. Las rutas cacheadas se pidieron dos veces; las respuestas tuvieron el mismo cuerpo. Luego se inspeccionaron los nombres y TTL de Redis, sin leer ni imprimir valores.

| Ruta | Respuesta Gateway | Evidencia de uso Redis |
|---|---:|---|
| `GET /api/productos/listado` | 200; 39 productos; repetición idéntica | `feriadigital:v1:{catalogo}:productos:listado:g<generación>`, TTL 299 s |
| `GET /api/categorias` | 200; 6 categorías; repetición idéntica | `feriadigital:v1:{catalogo}:categorias:listado:g<generación>`, TTL 899 s |
| `GET /api/categorias/1` | 200; repetición idéntica | `feriadigital:v1:{catalogo}:categorias:detalle:1:g<generación>`, TTL 899 s |
| `GET /api/productos` | 200; 39 productos | Sin clave de respuesta propia; conserva el origen |
| `GET /api/productos/1` | 200 | Sin clave de respuesta propia; conserva el origen |
| `GET /api/productos/empresa/1` | 200; 13 productos | Sin clave de respuesta propia; conserva el origen |
| `GET /api/productos/categoria/1` | 200; 21 productos | Sin clave de respuesta propia; conserva el origen |
| `GET /api/empresas` | 200; 4 empresas | Sin clave de respuesta propia; conserva el origen |
| `GET /api/empresas/1` | 200 | Sin clave de respuesta propia; conserva el origen |

`/health` respondió 200 y `PUT /api/productos/1/sincronizar-stock?stock=7` recibió 403 del Gateway, como exige la regla perimetral. El escaneo de Redis encontró cuatro claves en total: una clave de generación y las tres claves de respuesta indicadas arriba. Los cuerpos no se imprimieron. Las pruebas del microservicio verifican además que las seis lecturas excluidas no creen claves ni llamen al almacén Redis.

El Gateway principal en `8080` también devolvió 200, pero su backend de Producto pertenece a otro checkout y ese resultado no se usó para validar esta rama. Las escrituras de negocio no se invocaron por Gateway en esta pasada; la invalidación tras commit y rollback sí está cubierta por pruebas de integración. Esta prueba cubre las nueve lecturas de catálogo del microservicio Producto y una ruta interna bloqueada, no todos los endpoints de los microservicios Auth, Inventario y Ventas. Tampoco se midió una nueva comparación de rendimiento hit/miss contra la línea base; no se afirma todavía una mejora porcentual.

## Pendientes antes de ampliar o activar fuera de local

1. Obtener la decisión del responsable sobre visibilidad anónima, `precio` y `empresaId` para el listado.
2. La invalidación actual es posterior al commit y de mejor esfuerzo. Si Redis falla después de confirmar una escritura, una entrada anterior podría permanecer hasta cinco minutos. Diseñar y aprobar una recuperación durable (por ejemplo, outbox) o mantener la bandera apagada en entornos compartidos.
3. Resolver autorización de las escrituras de categorías y empresas, y confirmar los campos públicos de categorías, antes de habilitar esas banderas en un entorno compartido.
4. Medir el escenario desactivado, miss y hit con carga comparable; reportar p50/p95, lecturas a MySQL, llamadas a Inventario, respuestas y errores.
5. Revisar los seis endpoints de lectura aún excluidos antes de considerar nuevas proyecciones; los DTOs actuales de producto/empresa incluyen datos que no deben cachearse completos.
6. Añadir otra familia solo después de aprobar su contrato, clave, TTL, seguridad, invalidación y prueba de exclusión/fallback.
7. Mantener la validación del servidor para una etapa posterior con autorización institucional; no se hizo despliegue ni consulta remota en este trabajo.

## Archivos principales de implementación y prueba

- `Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoServiceImpl.java`
- `Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoCatalogoCacheService.java`
- `Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoCatalogoInvalidacionService.java`
- `Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/CategoriaCatalogoQueryService.java`
- `Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/dto/ProductoCatalogoPublicoDto.java`
- `Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/cache/RedisProductoCatalogoStore.java`
- `Microservicios/msvc-producto/src/test/java/com/example/msvc_producto/ProductoCatalogoHttpIT.java`
- `Microservicios/msvc-producto/src/test/java/com/example/msvc_producto/ProductoCatalogoFallbackIT.java`

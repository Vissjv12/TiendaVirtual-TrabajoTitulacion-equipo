# Prueba 7: Registro Formal de Alcance y Partes de Redis Fuera del Alcance

- **Objetivo:** Dejar documentado con base en el código fuente desplegado (`main` `b60b307`) y la configuración real del VPS qué partes del sistema utilizan Redis/Valkey actualmente y qué capacidades **permanecen formalmente fuera del alcance**.
- **Estado:** **COMPLETADA** (verificado en código fuente y contra Valkey en producción).

---

## 1. Lo que SÍ está dentro del alcance de Redis/Valkey hoy

Implementado exclusivamente en **`Microservicios/msvc-producto`** mediante [`RedisCacheConfig.java`](../../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/config/RedisCacheConfig.java), [`RedisProductoCatalogoStore.java`](../../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/cache/RedisProductoCatalogoStore.java), [`ProductoCatalogoCacheService.java`](../../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoCatalogoCacheService.java) y [`CategoriaCatalogoQueryService.java`](../../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/CategoriaCatalogoQueryService.java):

| Recurso / Endpoint | Clave Valkey / Estrategia | TTL | Datos guardados |
|---|---|---|---|
| `GET /api/productos/listado` | `feriadigital:v1:{catalogo}:productos:listado:g{epoch}` | `PT5M` (`300s`) | Proyección pública `ProductoCatalogoPublicoDto` (**sin** stock ni `emprendedorId`; el stock se consulta en vivo a `msvc-inventario`) |
| `GET /api/categorias` | `feriadigital:v1:{catalogo}:categorias:listado:g{epoch}` | `PT15M` (`900s`) | Lista pública de categorías (`CategoriaResponseDto`) |
| `GET /api/categorias/{id}` | `feriadigital:v1:{catalogo}:categorias:detalle:{id}:g{epoch}` | `PT15M` (`900s`) | Detalle de categoría (`CategoriaResponseDto`) |
| Control de generación | `feriadigital:v1:{catalogo}:epoch` | Sin expiración (`TTL=-1s`) | UUID de generación rotado vía scripts Lua atómicos tras `afterCommit()` al mutar productos, categorías o empresas |

- **Fallback transparente:** `REDIS_CONNECT_TIMEOUT=500ms` y `REDIS_TIMEOUT=500ms`; si Valkey falla o se detiene, `msvc-producto` cae automáticamente a MySQL sin interrumpir el catálogo.

---

## 2. Partes que permanecen FORMALMENTE FUERA DEL ALCANCE de Redis

Confirmado por inspección de `application.properties` y clases Java de los 5 microservicios (`eureka-server`, `demo`, `msvc-producto`, `msvc-inventario`, `msvc-ventas`) y de `/etc/valkey/valkey.conf`:

1. **Stock e inventario en tiempo real (`msvc-inventario` y `msvc-producto`):**
   - El stock nunca se almacena ni se lee desde Redis. Incluso en el endpoint cacheado `GET /api/productos/listado`, [`ProductoServiceImpl.java`](../../Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoServiceImpl.java) consulta las cantidades actuales a `msvc-inventario` en cada petición.
2. **Resto de endpoints de `msvc-producto`:**
   - **No usan Redis:** `GET /api/productos`, `GET /api/productos/{id}`, `GET /api/productos/paginado`, `GET /api/productos/listado/paginado`, `GET /api/productos/empresa/{id}`, `GET /api/productos/empresa/{id}/paginado`, `GET /api/productos/empresa/{id}/ids`, `GET /api/productos/categoria/{id}`, `GET /api/productos/categoria/{id}/paginado`, `GET /api/categorias/paginado` y todos los endpoints de `/api/empresas` (`GET /api/empresas`, `/paginado`, `/{id}`).
3. **Microservicio de Autenticación y Usuarios (`auth-service` / `demo`):**
   - No tiene dependencia ni configuración de Redis: usuarios, roles, JWT stateless, recuperación de contraseña (`password_reset_codes`), revocación/blacklist de tokens, vinculación T11 (`emprendedores`) y configuración PayPhone/DeUna (`configuracion_metodos_pago`) van directo a MySQL (`auth_service`).
4. **Microservicio de Ventas, Pagos y BPM (`msvc-ventas`):**
   - No usa Redis: ventas, detalles, clientes, logística, generación de facturas PDF y procesos Camunda (`v7.20.0`) operan directo sobre MySQL (`ventas`) y disco local (`facturas/`).
5. **Rate Limiting y protección HTTP (`limit_req`):**
   - El limitador de tráfico (`prod_api_general`, `prod_api_auth`, `prod_api_auth_sensitive`) vive en **Nginx en memoria local**, no distribuido en Redis.
6. **Sesiones HTTP / Carrito en servidor / Locks distribuidos / Pub-Sub:**
   - El sistema no usa `spring-session-data-redis`, ni carrito persistido en Redis, ni locks distribuidos (como Redisson/ShedLock), ni colas/streams en Valkey.
7. **Alta disponibilidad, clustering y políticas de memoria en Valkey (`/etc/valkey/valkey.conf`):**
   - Valkey corre como **instancia única local** (`bind 127.0.0.1 -::1`, puerto `6379`), **sin Sentinel, sin Cluster, sin contraseña** (`REDIS_PASSWORD` vacío, protegido por escucha loopback) y actualmente con valores por defecto `maxmemory = 0` y `maxmemory-policy = noeviction` (queda como ajuste recomendado fijar `maxmemory 128mb` y `maxmemory-policy allkeys-lru`).
8. **Observabilidad externa de métricas Redis:**
   - Aunque `ProductoCatalogoCacheService` registra métricas Micrometer (`feriadigital.redis.cache.requests`, `duration`, `failures`) y `management.endpoint.health.group.cache.include=redisCache` responde `UP` en `127.0.0.1:8081/actuator/health/cache`, **Nginx bloquea `/actuator` (`403`) hacia Internet**, por lo que la inspección se realiza únicamente por SSH o consola local del VPS.

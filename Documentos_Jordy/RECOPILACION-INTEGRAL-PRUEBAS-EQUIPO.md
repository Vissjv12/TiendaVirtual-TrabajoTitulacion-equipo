# Recopilación Integral de Pruebas del Sistema — Tienda Virtual Cantón Sigchos

**Proyecto:** Sistema de Comercio Electrónico para los Emprendimientos del Cantón Sigchos (*Feria Digital Sigchos*)  
**Equipo de trabajo:**  
- **Christian Mateo Bonifaz Vásquez** (`cmbonifaz`)  
- **Jordy Viscaíno Anthony Tapia** (`jordy`)  
- **Alfonso Alejandro Arroyo Paredes** (`alfonso1031`)  

**Periodo consolidado de pruebas:** 07 al 18 de noviembre de 2026  
**Propósito del documento:** Fuente técnica maestra para documentar cronológica y exhaustivamente **todas las pruebas ejecutadas por el equipo** (locales, unitarias, `@WebMvcTest`, integración H2/MySQL/Redis, seguridad API Gateway, carga con `k6`, recuperación de respaldos y validación en producción), lista para convertirse en libro/documento formal con **Quarto**.

> [!NOTE]
> **Control de alcance, autoría y confidencialidad:**
> - **Solo pruebas del equipo actual:** se excluyen explícitamente entregas y documentos de cohortes anteriores presentes en el repositorio como referencia histórica (`Documentos_Jordy/FOR-TRABAJO-DE-TITULACION_CAJAS_CHAMORRO.pdf`, `Documentos_Jordy/TrabajoTitulación_L00400187_Patiño Abad Brayan Daniel.pdf` y las guías base de junio/julio de 2026 en `documentos-trabajo/docs_produ/`).
> - **Sin credenciales ni secretos:** este documento no contiene contraseñas, tokens JWT, tokens de PayPhone, claves AES ni variables privadas de `.env`.

---

## 1. Mapa de Fuentes Consolidadas

Toda la evidencia recopilada proviene directamente de los registros, scripts y suites de pruebas producidos por el equipo en el repositorio:

| Fuente principal | Cobertura técnica |
|---|---|
| `Documentos_Jordy/REGISTRO-ETAPA-1-A01-A06.md` | Inventario, ambiente local y pruebas de aislamiento horizontal (`A-05`). |
| `Documentos_Jordy/REGISTRO-SEGURIDAD-A08-A10.md` y `pruebas-seguridad-a07.ps1` | Validación directa contra API de Productos/Inventario (`A-07`) y arranque de `msvc-ventas` + Camunda. |
| `Documentos_Jordy/REGISTRO-ETAPA-4-GATEWAY-A11-A14.md` y `pruebas-gateway-a14.ps1` | Validación automatizada del API Gateway NGINX local (`43/43 OK`). |
| `Documentos_Jordy/P4-AUDITORIA-AUTORIDAD-COMPRA.md`, `P5-AUDITORIA-ENDURECIMIENTO.md`, `P6-LINEA-BASE-SIN-REDIS.md` y `CIERRE-ETAPA-P4-P7-Y-ENTREGA-BASE.md` | Autoridad de compra en servidor (`P4`), saneamiento de logs/errores (`P5`), línea base de latencia y carga `k6` sin Redis (`P6`). |
| `Documentos_Jordy/BASELINE-LOCAL-PRE-REDIS-CATALOGO.md`, `REDIS-VERIFICACION-CACHE-LOCAL-A17-A18.md`, `REDIS-RESULTADOS-A18.md`, `PLAN-CIERRE-REDIS-A17-A18.md` | Implementación, pruebas funcionales/fallback y comparativa repetida `k6` del piloto Redis (`A-15` a `A-18`). |
| `Documentos_Jordy/REGISTRO-DEUNA-CHECKOUT-Y-PERMISOS-EMPRENDEDOR.md`, `A22-RESULTADOS-PAGINACION.md`, `A24-A25-SEGURIDAD-MSVC-VENTAS-Y-REGRESION.md`, `REGISTRO-PULIDO-PRE-INTEGRACION.md` | DeUna/checkout, paginación server-side (`A-22`), seguridad JWT en `msvc-ventas` (`A-24`/`A-25`) y pulido pre-integración. |
| `Documentos_Jordy/REVISION-CODIGO-INTEGRACION-A15-A25-Y-PULIDO.md` | Cierre de hallazgos `C-01`, `C-02`, `A-01`–`A-05`, Stripe deshabilitado, facturas tras commit y `pruebas-a01-a02-gateway.ps1`. |
| `Documentos_Jordy/REGISTRO-T07-OPTIMIZACION-Y-T01-RESPALDOS.md`, `REGISTRO-HARDENING-T07-T08-T09-GATEWAY.md`, `REGISTRO-CORRECCIONES-T07-T08-T09-REVISION.md`, `REGISTRO-T11-VINCULACION-EMPRENDEDOR-EMPRESA.md` | Autorización por lote `T07`, respaldo `T01` (`simular-respaldo.sh`), hardening `T08`/`T09` y vinculación `T11`. |
| `Documentos_Jordy/PLAN-VERIFICACION-Y-RESTAURACION-RESPALDOS-LOCALES.md` | Verificación SHA-256 y ensayo aislado de restauración completa de producción en MySQL 8.4.9. |
| `documentos-trabajo/VERIFICACION-MYSQL-PRODUCCION.md`, `REPORTE-DESPLIEGUE-PRODUCCION-2026-11-16.md`, `pruebas-produccion-2026-11-17/00..07` | Auditoría MySQL, despliegue a producción (`tienda.gadmsigchos.gob.ec:444`), 7 pruebas de producción y correcciones en vivo (`eac5ec7`, `f8a2006`). |
| `Documentos_Jordy/REGISTRO-VALIDACION-GATEWAY-PRODUCCION.md`, `EVIDENCIA-PRUEBAS-AUTENTICADAS-PRODUCCION.md`, `EVIDENCIA-T11-PRUEBAS-PRODUCCION.md`, `INFORME-CONSOLIDADO-PRUEBAS-PRODUCCION.md` | Pruebas autenticadas en producción, verificación T11 en vivo y consolidado de pruebas del equipo. |
| `Documentos_Jordy/REDIS-EVIDENCIA-PRODUCCION-ON-OFF-2026-11-17.md` y `EVIDENCIA-REDIS-COMPARACION-2026-11-17/` | Prueba sostenida de producción ON/OFF con `k6` v2.4.0 (`10 req/s`, `4 x 5 min`). |
| `Documentos_Jordy/IMPLEMENTACION-CONTADOR-VISITAS.md` y `documentos-trabajo/GUIA-DESPLIEGUE-CONTADOR-VISITAS.md` | Pruebas unitarias, despliegue (`8cf1066`) y verificación en navegador + MySQL del contador de visitas (`V12`). |
| `Microservicios/*/src/test/**`, `avalon-react-10.1.0/tests/*.test.mjs`, `scripts/prod/pruebas/simular-respaldo.sh` | Suites automatizadas ejecutables del proyecto. |

---

## 2. Fase 1 — Seguridad Base, Aislamiento Multi-Empresa y Roles (Etapas 0 a 3: `A-01` a `A-10`)

### 2.1 Respaldo y Ambiente de Pruebas (`A-01`, `A-02`)
- Se inventariaron servicios, puertos, bases de datos (`auth_service`, `productos`, `inventario`, `ventas`) y matriz inicial de permisos.
- Se crearon y probaron los scripts locales de respaldo/restauración (`scripts/backup-db.ps1`, `scripts/restore-db.ps1`, `scripts/test-environment.ps1`) y se validaron las semillas de prueba (`ultimo_inca` -> empresa `1`, `sigcholac` -> empresa `2`, `perla_andina` -> empresa `3`).

### 2.2 Aislamiento Horizontal entre Emprendedores (`A-03` y `A-05`)
- **Suite ejecutada:** `ProductoAuthorizationServiceTest` (`7/7` casos aprobados, `0` fallos, `0` errores — `BUILD SUCCESS`).
- **Escenarios cubiertos:**
  1. `ROLE_ADMIN` conserva alcance global sobre cualquier empresa/producto.
  2. `ROLE_EMP` accede únicamente a productos de su propio `empresaId` (extraído del claim JWT).
  3. Rechazo de operación sobre empresa ajena.
  4. Rechazo de operación sobre producto perteneciente a otra empresa.
  5. Rechazo por ausencia de rol de gestión (`ROLE_USER` / `ROLE_CLIENTE`).
  6. Rechazo ante autenticación no JWT.
  7. Rechazo para `ROLE_EMP` sin `empresaId` asociado y aislamiento cruzado entre dos empresas distintas.

### 2.3 Protección Directa de APIs (`A-06` y `A-07`, subpasos 1 a 4)
- **Pruebas `@WebMvcTest` iniciales:**
  - `ProductoControllerApiAuthorizationTest`: `4/4 PASS` iniciales (`401` sin token en `POST`, `403` con rol insuficiente o empresa ajena, `200` en `GET /api/productos` público). Posteriormente ampliada hasta **`15/15 PASS`**.
  - `InventarioControllerApiAuthorizationTest`: inicialmente `5/5` en toda la suite de inventario (`4` casos HTTP nuevos), ampliada en fases posteriores hasta **`18/18`**.
- **Propagación de autorización interna e infraestructura de pruebas:**
  - Se incorporó validación JWT (`JWT_SECRET` compartido) en `msvc-inventario`, interceptor Feign en `msvc-producto`, `msvc-ventas` y `msvc-inventario`, alineación de contrato batch (`/api/inventarios/cantidad/batch`) y perfil de pruebas H2 para evitar dependencia obligatoria de MySQL en tests unitarios/WebMvc.
- **Script de pruebas directas sin mutar datos (`Documentos_Jordy/pruebas-seguridad-a07.ps1`):**
  - Verifica disponibilidad (`Eureka 8761`, `/api-docs` en `8081`/`8083`/`8084`, `/api/inventarios`), consistencia de stock listado vs inventario (`0` diferencias), `3` lecturas públicas (`200`), `9` escrituras anónimas/token falso (`401`) y matriz con `admin`, `sigcholac` y `cliente1` usando `categoriaId=999999` / IDs inexistentes para no persistir datos en caso de fallo.

### 2.4 Verificación de Arranque de `msvc-ventas` y Motor Camunda (`A-07` / `A-08`)
- Validación en entorno local (2026-11-07) con `MYSQL_USER`/`MYSQL_PASSWORD` de `.env`:
  - Arranque completo (`Started MsvcVentasApplication in 15.959 seconds`, Tomcat HTTP en puerto `8083`).
  - Hibernate `spring.jpa.hibernate.ddl-auto=validate` completado sin inconsistencias de esquema.
  - Motor **Camunda 7.20.0** `default` y `JobExecutor` activos.
  - `GET /api-docs` y `GET /api/ventas` respondieron `200 OK`.

---

## 3. Fase 2 — Pruebas del API Gateway NGINX Centralizado (Etapa 4: `A-11` a `A-14`)

Se centralizaron todas las llamadas del frontend a través de un punto único de entrada (`apiConfig.ts` en Next.js hacia NGINX Gateway en `:8080`), ocultando cabeceras CORS duplicadas (`O-13`) y normalizando la ruta DeUna QR (`O-06`).

### Ejecución automatizada del script `Documentos_Jordy/pruebas-gateway-a14.ps1`
- **Resultado global:** **`43` pruebas exitosas / `0` fallos (`43/43 OK`)**.

| Sección probada | Casos ejecutados | Resultado |
|---|---:|---|
| 1. Salud y disponibilidad (`GET /health`, `GET /api/health`) | 3 | `200`, payload JSON `{"status":"UP","engine":"nginx"}` |
| 2. CORS centralizado y preflight (`OPTIONS /api/productos`, `GET /api/categorias`) | 4 | `204` / `200`, cabecera `Access-Control-Allow-Origin` única (`O-13`) |
| 3. Bloqueo perimetral e errores JSON (`PUT .../sincronizar-stock`, `/actuator/health`, ruta inexistente) | 3 | `403`, `403` y `404` JSON estructurado |
| 4. Enrutamiento Norte-Sur público hacia los 4 microservicios | 9 | `200` en productos, categorías, empresas, inventarios, batch, ventas y `costo-envio` (`404` controlado en QR no cargado) |
| 5. Consistencia de stock vía Gateway | 1 | `productos=78`, `inventarios=39`, `diferencias=0` |
| 6. Rechazo de escrituras y rutas protegidas sin token o con token inválido | 11 | `401 Unauthorized` en los 11 casos |
| 7. Matriz de autorización JWT por rol (`ROLE_CLIENTE`, `sigcholac` sobre empresa `1`, `ROLE_ADMIN`) | 12 | `403` para cliente (6), `403` cruzado para emprendedor (3), `200`/`404` para admin (3) |

- **Pruebas adicionales de Etapa 4:**
  - Con los microservicios detenidos, el Gateway respondió **`502 Bad Gateway` en formato JSON uniforme** (con cabeceras CORS activas, sin páginas HTML de NGINX).
  - Durante las pruebas A-14 se detectó y corrigió un bug en `Microservicios/demo/.../GlobalExceptionHandler.java`, donde `AccessDeniedException` caía en el manejador genérico (`500`) en lugar de devolver **`403 Forbidden`**.

---

## 4. Fase 3 — Auditorías Pre-Redis: Autoridad, Endurecimiento y Línea Base (`P4`, `P5`, `P6`)

### 4.1 `P4` — Autoridad del Servidor en Flujo de Compra (Commit `137bccc`, 2026-11-13)
- Se eliminó la confianza del backend en precios, empresas y costos de envío enviados por el navegador: `msvc-ventas` consulta `ProductoClient` (`msvc-producto`) y `ConfiguracionPagosClient` (`auth-service`).
- **Validación:** suite de integración `VentaIntegrationTest` aprobada en su totalidad; en particular `test14_ignoraImportesManipuladosPorCliente` envió precio manipulado `$0.01`, envío `$0` y `empresa=9999`, verificando que la venta persistida conservó el precio oficial del producto, la tarifa `$5.00`, la empresa real y el IVA del `15%`.

### 4.2 `P5` — Endurecimiento de Errores y Logs (Commit `f1ac8eb`)
- Se desactivó `spring.jpa.show-sql` en configuraciones principales (`demo`, `msvc-producto`, `msvc-inventario`).
- Se sanearon las respuestas de `GlobalExceptionHandler` para ocultar URLs internas, puertos, mensajes JDBC y trazas Feign.
- **Validación manual y de contexto:**
  - `GET /api/usuarios` sin token -> `401` con cuerpo vacío (sin filtración de infraestructura).
  - Token malformado literal -> `401 Malformed token` sin URLs internas.
  - Pruebas de contexto tras credenciales locales corregidas: `LoginApplicationTests` (`1/1 PASS`) y `MsvcProductoApplicationTests` (`1/1 PASS`).

### 4.3 `P6` — Línea Base de Rendimiento Local sin Redis (Commit `d3baf2b`, 2026-11-13)
Pruebas de solo lectura sobre el Gateway local (`http://127.0.0.1:8090`, `39` productos, `39` inventarios, `0` diferencias):

**A. Medición secuencial vía Gateway (`30` solicitudes por endpoint, `0` fallos):**

| Endpoint | Solicitudes | Exitosas | Mín (ms) | Media (ms) | p50 (ms) | p95 (ms) | Máx (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|
| `/api/productos/listado` | 30 | 30 | 57.69 | 73.70 | 68.05 | 98.44 | 120.65 |
| `/api/productos/1` | 30 | 30 | 39.18 | 47.59 | 43.56 | 56.59 | 136.63 |
| `/api/categorias` | 30 | 30 | 35.72 | 41.12 | 40.42 | 45.93 | 50.09 |
| `/api/empresas` | 30 | 30 | 36.42 | 42.84 | 40.23 | 57.53 | 68.23 |
| `/api/inventarios/producto/1` | 30 | 30 | 47.06 | 57.53 | 55.92 | 69.24 | 72.10 |

**B. Medición directa con conteo de `SELECT` en MySQL (`50` solicitudes secuenciales, `BASELINE-LOCAL-PRE-REDIS-CATALOGO.md`):**
- `GET http://127.0.0.1:8081/api/productos/listado`: `50/50 OK`, `p50 = 22.86 ms`, `p95 = 27.36 ms`, `50 SELECT` en esquema `productos` + `50 SELECT` en esquema `inventario`.
- `POST http://127.0.0.1:8082/api/inventarios/cantidad/batch`: `50/50 OK`, `p50 = 6.62 ms`, `p95 = 7.59 ms`, `50 SELECT` en `inventario`.
- Suite `msvc-producto` aprobada: `16/16` (`0` fallos).

**C. Carga concurrente inicial con `k6` (`scripts/p6-baseline-readonly.js`, `5 VUs`, `30 s`):**
- Solicitudes totales: **`2,919`** (`2,919 / 2,919` checks exitosos).
- Tasa de error: **`0.00%`** | Throughput: **`97.22 req/s`**.
- Latencias: media **`50.61 ms`**, `p50` **`38.50 ms`**, `p95` **`135.54 ms`**, máxima **`469.76 ms`**.
- Consistencia post-prueba verificada: `39` productos, `39` inventarios, `0` diferencias.

---

## 5. Fase 4 — Piloto Redis Cache-Aside y Pruebas Locales (Etapa 5: `A-15` a `A-18`)

Implementado y medido localmente entre el **14 y 15 de noviembre de 2026** sobre MySQL `8.0.46` sintético (`3307`), Redis `7` (`6380`), stub de Inventario (`8182`/`8282`) y Gateway NGINX aislado (`8189`/`8290`), siguiendo estrictamente la decisión arquitectónica `A-15`: **solo Cache-Aside de lectura pública en catálogo sin almacenar stock, carritos ni datos privados**.

### 5.1 Evolución de Suites Automatizadas de Redis en `msvc-producto`
| Corte / Rama | Suite completa Maven (`JDK 17`) | Suite focalizada Redis/HTTP/Auth |
|---|---:|---:|
| `feature/a17-piloto-redis-local` | **23 / 23 PASS** | **29 / 29 PASS** (`ProductoCatalogoHttpIT` `12/12`) |
| `integration/avance-hasta-redis` (2026-11-14) | **26 / 26 PASS** | **46 / 46 PASS** |
| `integration/version-estable-deuna-pulido` (2026-11-15) | **33 / 33 PASS** | **49 / 49 PASS** |

Clases verificadas: `RedisGenerationIT`, `RedisProductoCatalogoStoreIT`, `ProductoCatalogoHttpIT`, `ProductoCatalogoFallbackIT`, `ProductoCatalogoCacheServiceTest` y `RedisCacheConfigTest` (cubriendo hit sin tocar origen, miss + publicación atómica con Lua, JSON corrupto, TTL no deslizante, invalidación `afterCommit`, rollback sin avanzar generación y fallback sin caída HTTP).

### 5.2 Verificación por Gateway Aislado de las 9 Rutas de Catálogo
| Ruta probada por Gateway (`:8189`) | Respuesta | Evidencia en Redis |
|---|---:|---|
| `GET /api/productos/listado` | `200` (39 productos, repetición idéntica) | Clave `feriadigital:v1:{catalogo}:productos:listado:g<gen>`, TTL observado `299 s` |
| `GET /api/categorias` | `200` (6 categorías) | Clave `...categorias:listado:g<gen>`, TTL `899 s` |
| `GET /api/categorias/1` | `200` | Clave `...categorias:detalle:1:g<gen>`, TTL `899 s` |
| `GET /api/productos`, `/api/productos/1`, `/empresa/1`, `/categoria/1`, `/api/empresas`, `/api/empresas/1` | `200` | **Sin clave propia** (excluidas de caché; lectura directa a origen verificada) |
| `PUT /api/productos/1/sincronizar-stock?stock=7` | `403` | Bloqueado correctamente en perímetro |

### 5.3 Pruebas de Resiliencia y Recuperación ante Caída de Redis
| Escenario probado en runtime | Comportamiento observado |
|---|---|
| Redis detenido **antes** de iniciar el JAR (`CACHE_REDIS_ENABLED=true`) | La aplicación arranca normalmente; `GET /api/productos/listado` responde `200` (39 productos desde MySQL); liveness/readiness `200`; `/actuator/health/cache` `503`. |
| Redis detenido **en ejecución** (caída en caliente) | Sin interrupción de servicio; catálogo sigue en `200` (fallback MySQL); métrica de `fallback` y fallos de lectura incrementa en `+1`; `/actuator/health/cache` `503`. |
| Redis **reiniciado** | `PING` -> `PONG`; `/actuator/health/cache` vuelve a `200`; catálogo por Gateway `200`. |

Además, se validó el acceso restringido a métricas de caché (`/actuator/metrics/**`): `401` anónimo, `403` emprendedor y `200` administrador.

### 5.4 Comparativa de Carga Local con `k6` (`scripts/redis-a18-listado.js`, 2026-11-14)
1. **Primera comparación exploratoria mixta (6 rutas, `5 VUs`, `30 s`):**
   - Desactivado: `9,879` req, `p50=12.68 ms`, `p95=31.94 ms`, `327.46 req/s`, `3` errores (0.03%).
   - Miss (TTL 1 ms): `10,208` req, `p50=13.08 ms`, `p95=37.08 ms`, `339.94 req/s`, `0` errores (`1,559` misses / `100` hits).
   - Hit (clave precalentada): `10,893` req, `p50=12.79 ms`, `p95=32.48 ms`, `363.00 req/s`, `0` errores (`1,806` hits, `used_memory=1,441,752` bytes).

2. **Medición dedicada repetida sobre `GET /api/productos/listado` (3 corridas por modo, `5 VUs`, `30 s`, con contador `Com_select` de MySQL y contador de llamadas al stub de Inventario):**

| Modo | Corrida | Solicitudes | p50 (ms) | p95 (ms) | Req/s | Fallos / Checks | `SELECT Δ` (MySQL) | Cache Miss / Hit | Llamadas Inventario |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **Desactivado** | 1 | 3,640 | 39.66 | 56.34 | 121.26 | `0 / 0` | 3,651 | — | 3,640 |
| Desactivado | 2 | 4,073 | 34.51 | 49.45 | 135.62 | `0 / 0` | 4,084 | — | 4,073 |
| Desactivado | 3 | 4,257 | 33.08 | 47.63 | 141.80 | `0 / 0` | 4,264 | — | 4,257 |
| **Miss (TTL 1 ms)** | 1 | 2,830 | 49.28 | 82.73 | 94.21 | `0 / 0` | 2,659 | `2,648 / 182` | 2,830 |
| Miss (TTL 1 ms) | 2 | 3,332 | 43.46 | 60.37 | 110.99 | `0 / 0` | 3,171 | `3,160 / 172` | 3,332 |
| Miss (TTL 1 ms) | 3 | 3,578 | 40.27 | 58.18 | 119.18 | `0 / 0` | 3,366 | `3,355 / 223` | 3,578 |
| **Hit (TTL 5 min)** | 1 | 3,829 | 37.15 | 54.69 | 127.51 | `0 / 0` | **11** | `0 / 3,829` | 3,829 |
| Hit (TTL 5 min) | 2 | 4,183 | 30.67 | 62.27 | 139.37 | `0 / 0` | **7** | `0 / 4,183` | 4,183 |
| Hit (TTL 5 min) | 3 | 4,567 | 30.84 | 45.28 | 152.15 | `0 / 0` | **5** | `0 / 4,567` | 4,567 |

- **Medianas resultantes y hallazgo clave:**
  - `Com_select` en MySQL cayó de `11,999` (`1.002 SELECT/req`) en modo desactivado a **`23` totales** (`0.0018 SELECT/req`) en modo Hit: **~99.8% menos consultas `SELECT` observadas** en MySQL (`p50` mejoró `-10.6%`, de `34.51 ms` a `30.84 ms`; el `p95` mediano fue `54.69 ms` vs `49.45 ms` porque Inventario sigue siendo invocado en vivo en cada petición).

### 5.5 Resumen Matriz `R01`–`R16` (A17/A18 Local)
- **Verificados (`12`):** `R01` (flags apagados por defecto), `R02` (miss), `R03` (hit + stock vivo), `R04` (arranque sin Redis), `R05` (caída en runtime), `R07` (expiración TTL no deslizante), `R08` (invalidación productos tras commit), `R09` (invalidación categorías/empresas), `R10` (aislamiento de mutaciones), `R13` (recuperación tras reinicio Redis), `R15` (ventana tras fallo post-commit con límite documentado), `R16` (bloqueo de publicación obsoleta concurrente).
- **Parciales / Pendientes en esa fecha (`4`):** `R06` (inyección artificial de timeout > 500 ms), `R11` (campos públicos aprobados posteriormente el `2026-11-15`), `R12` (fallo HTTP ante caída real de MySQL) y `R14` (completado luego con matriz en producción).

---

## 6. Fase 5 — DeUna/Checkout, Paginación (`A-19`–`A-22`), Seguridad Ventas (`A-24`/`A-25`) y Pulido

### 6.1 Paginación Server-Side en los 4 Microservicios y 17 Vistas (`A-19` a `A-22`)
Se implementaron `18` endpoints `/paginado` (`PageResponseDto<T>` de 8 campos, `size=12` vitrina / `size=10` gestión, límite estricto `MAX_SIZE=50`, normalización de `page < 0` y whitelist de ordenamiento `sort`):

| Microservicio | Clases de prueba ejecutadas (`A-22`) | Casos | Fallos / Errores | Resultado |
|---|---|---:|---:|---|
| `msvc-producto` | `ProductoPaginationTest`, `ProductoAuthorizationServiceTest`, `ProductoControllerApiAuthorizationTest`, `ProductoCatalogoCacheServiceTest`, `ProductoServiceInventoryBatchTest`, `RedisCacheConfigTest` | **32** | 0 / 0 | `BUILD SUCCESS` |
| `msvc-inventario` | `InventarioPaginationTest`, `InventarioControllerApiAuthorizationTest` | **10** | 0 / 0 | `BUILD SUCCESS` |
| `msvc-ventas` | `VentaPaginationTest`, `VentaServiceTest` | **14** | 0 / 0 | `BUILD SUCCESS` |
| `demo` (`auth-service`) | `UsuarioPaginationTest` | **2** | 0 / 0 | `BUILD SUCCESS` |
| **Total backend Etapa 6** | — | **58** | **0 / 0** | **58 / 58 PASS** |

- **Frontend (`avalon-react-10.1.0`):** `tsc --noEmit` (`0` errores) y `next build --no-lint` (`105` páginas estáticas generadas; los errores previos de `react/no-unescaped-entities` que impedían `next build` estándar se corrigieron posteriormente en el commit `b60b307`).

### 6.2 Seguridad JWT en `msvc-ventas` y Regresión (`A-24` y `A-25`)
Se convirtió `msvc-ventas` en Resource Server HS256 con `VentaAuthorizationService` (`canManageCompany`, `canAccessCliente`, `canViewVenta`, `canManageVenta`, `canViewPago`, `canManagePago`, `canUploadComprobantePago`) y protección granular de Camunda/BPM solo para `ROLE_ADMIN`:

| Módulo | Cobertura principal | Resultado |
|---|---|---:|
| `Microservicios/msvc-ventas` | `VentaAuthorizationServiceTest` (`4`), `VentaControllerApiAuthorizationTest` (`6`), `VentaIntegrationTest` con JWT H2 (`14`), `VentaServiceTest` (`12`), `VentaPaginationTest` (`2`), `MsvcVentasApplicationTests` (`1`) | **39 / 39 PASS** |
| `Microservicios/msvc-inventario` | `InventarioControllerApiAuthorizationTest` (`15`, +7 casos de `/salida-lote` y `/stock`), `InventarioPaginationTest` (`2`), `MsvcInventarioApplicationTests` (`1`) | **18 / 18 PASS** |
| `Microservicios/msvc-producto` | Suite unitaria/WebMvc paginación, autorización y caché | **32 / 32 PASS** |
| `Microservicios/demo` | `UsuarioPaginationTest` | **2 / 2 PASS** |

### 6.3 Pulido Pre-Integración (`feature/pulido-export-validaciones-404`)
- **Backend `demo` (`msvc-auth`):** `UsuarioServiceImplTest` (`9`), `AuthServiceDisabledUserTest` (`1`, bloqueo `403` a usuarios desactivados), `UsuarioPaginationTest` (`2`) -> **`12 / 12 PASS`** (incluyendo protección del último `ROLE_ADMIN` activo).
- **Backend `msvc-producto`:** `19 / 19 PASS` (`ProductoAuthorizationServiceTest`, `ProductoControllerApiAuthorizationTest`, `ProductoPaginationTest`).
- **Helpers frontend (`verify_helpers.js`):** **`22 assertions PASS`** para `csvExport.ts` (RFC 4180, BOM UTF-8 `\uFEFF`, neutralización de fórmulas Excel `=`, `+`, `-`, `@`) y `formValidations.ts`.
- **Navegador headless Chrome (`repro404.js` sobre `next start -p 3099`):** validación de ruta inexistente `/checkout/fse` y navegación `"Ir al Inicio"` sin error de DOM (`removeChild`).

---

## 7. Fase 6 — Pruebas de Cierre de Auditoría de Código (`C-01`, `C-02`, `A-01`–`A-05`, `M-06`, `T01`, `T07`–`T11`) y Respaldos

Tras la auditoría estática integral (`REVISION-CODIGO-INTEGRACION-A15-A25-Y-PULIDO.md`, 2026-11-15), el equipo cerró todos los hallazgos críticos y altos junto con las tareas de endurecimiento previas al despliegue, respaldándolos con suites automatizadas:

### 7.1 Pruebas Unitarias y de Integración por Hallazgo / Tarea
| Hallazgo / Tarea | Qué se validó en pruebas | Suite y Resultado |
|---|---|---|
| **`C-01` — Aislamiento QR DeUna y datos bancarios** | Sin fallback global (`findAll`) ni cruce entre `empresaId` y `emprendedorId`; fallo cerrado con `404` y deshabilitación en carritos sin QR o multi-empresa. | `ConfiguracionPagosServiceEmpresaTest` (**10/10**), `ConfiguracionPagosControllerEmpresaTest` (**4/4**), frontend `tests/checkoutEmpresa.test.mjs` (**20/20**) |
| **`C-02`, `A-03`, `A-04`, `M-06` y AES-256-GCM — PayPhone seguro** | Token jamás devuelto por API ni expuesto en frontend/logs; orden `PENDIENTE` creada en backend con precios autoritativos; Prepare/Confirm verificando `statusCode=3` y monto exacto; credencial efímera de servicio `ROLE_SERVICIO_PAGOS` (`2 min`); webhook idempotente que reconsulta a PayPhone; cifrado en reposo `enc:v1:`; bloqueo `SELECT ... FOR UPDATE`. | `PayphoneTokenNoExpuestoTest` (**7/7**), `PayphonePagoServiceTest` (**19/19**), `PayphoneTokenCifradoTest` (**8/8**), `VentasInternoClientTest` (**3/3**), `ConfiguracionPagosControllerPayphoneTest` (**10/10**), `PagoPasarelaServiceTest` (**10/10**), `InventarioServicioPagosAuthorizationTest` (**5/5** tras fix `eac5ec7`), frontend `tests/payphone.test.mjs` (**18/18** barrido + lógica) |
| **`A-01` y `A-02` — Propiedad de inventario y RUC/activo de empresa** | Todas las rutas `POST`/`PUT`/`DELETE` de inventario y movimientos validan empresa del JWT (`canManageProduct`, delta firmado y usuario real); `PUT /api/empresas/{id}` solo `ROLE_ADMIN`; `PUT .../contacto` con `@JsonAnySetter` rechaza `ruc`/`activo` con `400` y unicidad de RUC. | `InventarioPropiedadEscriturasTest` (**12/12**), `MovimientoPropiedadTest` (**3/3**), `InventarioServiceMovimientosTest` (**13/13**), `EmpresaListaBlancaTest` (**6/6**), `ProductoControllerApiAuthorizationTest` (**15/15**), script `Documentos_Jordy/pruebas-a01-a02-gateway.ps1` |
| **Stripe inactivo y Facturas tras commit (`A-05`)** | Con `STRIPE_ENABLED=false`, cero beans cargados y `/api/stripe/**` responde `401`/`404`; facturas generadas vía `GeneradorFacturaTrasCommit` (ejecutor acotado tras commit) sin `new Thread`/`sleep` y con sesión `@Transactional(readOnly = true)` (`ad70711`); pago pendiente creado en misma transacción. | `StripeDeshabilitadoTest` (**5/5**), `GeneradorFacturaTrasCommitTest` (**4/4**), `PagoServiceAprobarPagoTest` (**5/5**), `VentaApplicationServicePagoSincronoTest` (**3/3**), `FacturaServiceTransaccionalTest` (**1/1**), frontend `tests/stripeInactivo.test.mjs` (**6/6**) |
| **`T07` — Autorización de inventario en lote y `T01` — Respaldos** | Reducción de `N` llamadas a **1 consulta SQL `IN` + 1 llamada** a `/api/productos/empresa/{id}/ids` (`fail-closed`); simulación completa de backup/restore con SHA-256, `gzip -t`, rechazo de permisos `644`, dumps truncados y bases `restore_test_*`. | `InventarioAutorizacionLoteTest` (**8/8**), `scripts/prod/pruebas/simular-respaldo.sh` (**25/25 PASS**, sintaxis `bash -n` OK) |
| **`T08` y `T09` — Hardening API Gateway y Swagger** | Swagger apagado por defecto y restringido a `ROLE_ADMIN`, eliminación de `@CrossOrigin(*)` en 9 controladores, `server.forward-headers-strategy=native`, rate limiting `limit_req`, cabeceras de seguridad y bloqueos `403`/`404` en `tienda.prod.conf` y `nginx.conf`. | Validación `nginx -t` en contenedor `nginx:alpine` OK + suites WebMvc completas |
| **`T11` — Vinculación emprendedor-empresa** | 1 emprendedor = 1 empresa única (`uk_emprendedor_empresa`, respuesta `409` en duplicado/concurrencia, `503` si producto no responde, `empresaId` propagado en login y listado admin). | `EmprendedorEmpresaServiceTest` (**14/14**), `EmprendedorEmpresaControllerTest` (**5/5**), `EmpresaProductoClientTest` (**4/4**), `AuthServiceEmpresaJwtTest` (**3/3**), frontend `tests/empresaVinculacion.test.mjs` (**8/8**) |
| **`V12` — Contador persistente de visitas** | Registro en fecha zona `America/Guayaquil` con `INSERT ... ON DUPLICATE KEY UPDATE` y suma histórica. | `VisitasServiceTest` (**2/2 PASS**) |

### 7.2 Verificación de Respaldos y Ensayo Aislado de Restauración de Producción (2026-11-15)
Ejecutado localmente por Alfonso sobre las copias obtenidas del servidor antes del despliegue:
- **Verificación de integridad:** `27` archivos (`1,328,027,567` bytes), huella SHA-256 verificada del dump de las 4 bases (`c5c937b6529d3176a04a077f71f7fdc025ebb16bf50956698f88cd1032613de1`), comprobación `gzip -t`, `tar.gz`, `zip` y `.jar`.
- **Restauración aislada en contenedor MySQL `8.4.9` (`restaurar_ensayo_manual_2026_11_15.ps1`):** importadas con éxito **64 tablas** (`auth_service` = `34` tablas/registros verificados, `inventario` = `93`, `productos` = `49`, `ventas` = `144,966`), `CHECK TABLE` ejecutado y limpieza limpia del contenedor de ensayo.

### 7.3 Consolidado Total de Suites Automatizadas Vigentes
| Módulo / Suite | Último resultado observado | Notas exactas de alcance |
|---|---:|---|
| **`Microservicios/demo` (`auth-service`)** | **99 / 99 PASS** *(unitarias/WebMvc)* | `LoginApplicationTests` excluido cuando no hay MySQL local activo (`100/100` con MySQL). |
| **`Microservicios/msvc-producto`** | **47 / 47 PASS** *(unitarias tras `V12`)* | Con MySQL local falla únicamente `MsvcProductoApplicationTests` (`Communications link failure` si no hay BD local); suites `*IT` de Redis pasan `49/49` con MySQL+Redis sintéticos. |
| **`Microservicios/msvc-inventario`** | **59 / 59 PASS** | Ejecución completa tras fix `9883b96` (`ROLE_SERVICIO_PAGOS` en `/salida-lote`). |
| **`Microservicios/msvc-ventas`** | **72 / 72 PASS** | Ejecución completa tras fix `ad70711` (`FacturaServiceTransaccionalTest`). |
| **`avalon-react-10.1.0` (`npm test`)** | **49 / 49 PASS** *(11 suites)* | `checkoutEmpresa` + `empresaVinculacion` + `payphone` + `stripeInactivo` (ejecutado tras despliegue `8cf1066`). |
| **`avalon-react-10.1.0` (`npm run lint` + `build`)** | **0 errores** | `next lint` (`0` errores) y `next build` produciendo `105` páginas. |
| **`scripts/prod/pruebas/simular-respaldo.sh`** | **25 / 25 PASS** | Validación completa de respaldo/restauración en entorno simulado. |

---

## 8. Fase 7 — Pruebas en Producción Real (`https://tienda.gadmsigchos.gob.ec:444`, 16–17 Nov 2026)

### 8.1 Auditoría MySQL Pre-Despliegue y Despliegue Controlado (2026-11-16)
- **Auditoría MySQL `8.4.9` de solo lectura (`VERIFICACION-MYSQL-PRODUCCION.md`):**
  - Verificada compatibilidad de las **14 tablas de negocio** + `49` tablas Camunda para `ddl-auto=validate`.
  - Confirmados `13/13` usuarios activos con hashes BCrypt válidos de 60 caracteres, `39/39` productos e inventarios sin huérfanos, `1` token PayPhone heredado en texto plano (previo a migración AES) y `max_connections` ajustado a `250`.
- **Despliegue de `main` (`b60b307`) y resolución de SELinux (`REPORTE-DESPLIEGUE-PRODUCCION-2026-11-16.md`):**
  - Se resolvió el bloqueo de SELinux (`Enforcing`) reubicando `.env` a `/etc/tienda-virtual/backend.env` (`0600 root:root`, contexto `etc_t`).
  - Arrancaron los `9/9` servicios `systemd` en `active` (`mysqld`, `valkey`, `nginx`, `eureka-server`, `auth-service`, `msvc-producto`, `msvc-inventario`, `msvc-ventas`, `tienda-frontend`) y se comprobaron `17` rutas HTTP/HTTPS locales e Internet.
  - Se aplicó el índice único `T11` (`uk_emprendedor_empresa`, `NON_UNIQUE = 0`) y se confirmó la migración automática del token PayPhone en BD al prefijo `enc:v1:`.

### 8.2 Prueba 1 — Validación del API Gateway Real, TLS, Seguridad y Navegador (2026-11-17)
- **Infraestructura y TLS:** certificado Let's Encrypt válido sobre **TLS 1.3**, `nginx -t` correcto, redirección HTTP `:80 -> HTTPS :444` (`301`).
- **Exposición perimetral (desde Internet):** solo `80/tcp` y `444/tcp` abiertos en el VPS (`8080`, `8081`, `8082`, `8083`, `8761`, `3000`, `3306`, `6379` cerrados/filtrados; `8084` de la IP pública compartida responde HTTP 400 desde otro Nginx institucional externo, no de `auth-service`).
- **Bloqueos e errores perimetrales verificados en vivo:**
  - `GET /health`, `/api/health`, `/landing`, `/api/productos`, `/api/productos/listado`, `/api/categorias`, `/api/empresas`, `/uploads/qr-deuna/...` -> **`200`**.
  - `/actuator/health`, `/swagger-ui.html`, `/api-docs`, `/v3/api-docs`, `/camunda`, `/engine-rest`, `/api/productos/1/sincronizar-stock` -> **`403` JSON**.
  - `/api/ventas/internal/test` -> **`404` JSON**.
  - Origen no autorizado (`https://evil.example` / `https://origen-no-autorizado.example`) -> **`403 Invalid CORS request`**.
  - Ráfagas repetidas en login y catálogo -> **`429 Too Many Requests`** (`limit_req` activo).
- **Auditoría en Chrome real:** navegación completa por `/landing`, `/products`, `/checkout` y `/auth/login2` (incluyendo **Google OAuth `200`**). Hallazgos no bloqueantes anotados: 2 fuentes locales `roboto-*-700` con `404` y 2 imágenes heredadas con `i.imgur.com/default.png`.

### 8.3 Pruebas 2 y 4 — Flujo Completo de Compra E2E con PayPhone Sandbox y Correcciones en Vivo (2026-11-17)
Durante la primera prueba E2E real con tarjeta de prueba PayPhone se detectaron y corrigieron **dos bugs reales de producción**, que fueron parchados en `main` y validados en vivo:

1. **Bug 1 (`ventaId = 7`, corregido en `9883b96` / `eac5ec7`):**
   - **Causa:** `POST /api/inventarios/salida-lote` devolvió `403 Forbidden` al token de servicio `ROLE_SERVICIO_PAGOS` porque `SecurityConfig` de `msvc-inventario` exigía solo `ROLE_ADMIN`/`ROLE_EMP`.
   - **Fix y validación:** se habilitó `ROLE_SERVICIO_PAGOS` en `SecurityConfig`, se re-ejecutaron `59/59` pruebas en `msvc-inventario`, se desplegó el JAR y se reconcilió `ventaId = 7` vía webhook `/api/payphone/confirm` (`200`), quedando `COMPLETADA` con descuento único `297 -> 296`. Además, una segunda llamada confirmó **idempotencia** (`YA_PROCESADA`, stock intacto).
2. **Bug 2 (corregido en `ad70711` / `f8a2006`):**
   - **Causa:** al generarse la factura asíncrona en el hilo `[factura-1]` (`GeneradorFacturaTrasCommit`), `FacturaServiceImpl` lanzó `LazyInitializationException` fuera de sesión Hibernate.
   - **Fix y validación:** se añadió `@Transactional(readOnly = true)` en `FacturaServiceImpl.java` + `FacturaServiceTransaccionalTest` (`72/72` PASS en `msvc-ventas`) y se desplegó el JAR.

**Validación E2E definitiva desde cero en 1 solo intento (`ventaId = 9`, commit `f8a2006`):**
- Producto de prueba (`$2.25` + envío + IVA = **`$7.76`**), factura **`FACT-1794933559721`**, referencia PayPhone Sandbox **`93755596`**:
  1. `POST /api/ventas` -> **`201 Created`** (`PENDIENTE`, importes calculados en servidor).
  2. `POST .../payphone/ventas/9/preparar` -> **`200 OK`** (token descifrado en servidor con `PAYPHONE_TOKEN_ENC_KEY` AES-256 `enc:v1:` sin exponerse al navegador ni a `ROLE_EMP`).
  3. Pago aprobado en pasarela sandbox PayPhone (`Status: Approved`, `$7.76`).
  4. Retorno automático a `/checkout/payphone-confirmacion` -> **`200 OK`**, **`resultadoVenta = "COMPLETADA"` al primer intento**.
  5. Stock descontado exactamente 1 vez (`296 -> 295`) con registro de movimiento `SALIDA`.
  6. Factura PDF `factura_FACT-1794933559721.pdf` pre-generada automáticamente en segundo plano (`FACTURAS_TOTAL = 20`, `0` errores en logs) y descargada con HTTP **`200`** en Chrome desde `/profile/historial-compras`.
  7. Confirmación adicional: Stripe deshabilitado (`STRIPE_ENABLED=false`) en API y UI.

### 8.4 Pruebas 3 y 5 — Verificación de Vinculación `T11` y Regresión por Roles en Producción (2026-11-17)
- **Integridad T11 en MySQL:** índice `uk_emprendedor_empresa` activo (`Non_unique=0`), `0` duplicados, `MSVC_PRODUCTO_URL=http://127.0.0.1:8081` vivo en `auth-service`; usuarios `2, 3, 4, 5` (`ROLE_EMP`) mapeados respectivamente a empresas `1, 2, 3, 4`; usuario `1` (`ROLE_ADMIN`).
- **10 / 10 casos HTTP de T11 aprobados** (`400` al vincular `ROLE_ADMIN`, IDs `0` o `-1`; `409` al intentar tomar empresa `2` ocupada; `200 SIN_CAMBIOS` idempotente; `404` empresa `999999`; `401` sin JWT; `403` como `ROLE_EMP` en consulta y `PUT`).
- **11 / 11 pruebas HTTP autenticadas de control de acceso por rol aprobadas (`EVIDENCIA-PRUEBAS-AUTENTICADAS-PRODUCCION.md`):**
  - Logins `ADMIN`, `EMP` (`empresaId=2`), `CLIENTE` -> `200`.
  - Sin JWT -> `401` en 5 rutas privadas, `200` en 2 rutas públicas.
  - `CLIENTE` -> `403` en `/api/usuarios/paginado` y `/api/ventas/paginado`.
  - `ROLE_EMP` (`sigcholac`, `empresaId=2`) -> `200` en sus ventas (`/emprendedor/2/paginado`) y sus movimientos; **`403`** en `/api/usuarios/paginado`, `/api/movimientos`, ventas de empresa `1` y **`DELETE` / `PUT` sobre producto ajeno `id=1`** (verificando luego `GET /api/productos/1` -> `200`, producto intacto).
  - `ROLE_ADMIN` -> `200` global en usuarios, estadísticas, ventas e inventarios.
- **Verificación en navegador Chrome (4 perfiles):** guardas de UI en rutas restringidas, aislamiento de `ROLE_EMP` en sus módulos (`1` empresa, `10` productos) vs visibilidad global de `ROLE_ADMIN` (`14` usuarios, `4` empresas, `6` categorías, `39` productos) incluyendo tarjeta `"Empresa vinculada"` (`T11`) en `/administrador/usuarios/5/editar`.

### 8.5 Pruebas 6 y 7 — Medición Comparativa de Rendimiento Redis/Valkey y Alcance en Producción (2026-11-17)

#### A. Micro-benchmark en producción con conteo real de `MySQL Com_select` (`06-medicion-comparativa-rendimiento.md`)
Ejecutado sobre `https://tienda.gadmsigchos.gob.ec:444` (`2` calentamiento + `30` secuenciales + `20` concurrencia `5`, comparando Redis OFF vs Valkey ON, con `39` productos reales):

| Endpoint | Métrica | Sin Redis (OFF) | Con Valkey (ON) | Variación |
|---|---|---:|---:|---|
| **`GET /api/productos/listado`** *(caché catálogo + stock vivo)* | Media (`avg`) | `28.57 ms` | `24.35 ms` | **`-14.8%`** |
| | Mediana (`p50`) | `27.41 ms` | `24.43 ms` | **`-10.9%`** |
| | Percentil 95 (`p95`) | `35.20 ms` | `26.15 ms` | **`-25.7%`** |
| | Máxima (`max`) | `42.34 ms` | `26.58 ms` | **`-37.2%`** |
| | **Delta `MySQL Com_select`** | **`61`** | **`31`** | **`-49.2%` menos SELECTs** |
| | **Valkey `hits / misses`** | `0 / 0` | **`90 / 0`** | `100%` hit ratio |
| **`GET /api/categorias`** *(caché completo)* | Media (`avg`) | `19.45 ms` | `15.45 ms` | **`-20.6%`** |
| | Mediana (`p50`) | `19.26 ms` | `15.33 ms` | **`-20.4%`** |
| | Percentil 95 (`p95`) | `21.64 ms` | `16.04 ms` | **`-25.9%`** |
| | **Delta `MySQL Com_select`** | **`31`** | **`1`** | **`-96.8%` menos SELECTs** |
| | **Valkey `hits / misses`** | `0 / 0` | **`75 / 0`** | `100%` hit ratio |
| **`GET /api/productos`** *(control sin caché)* | Uso de Valkey | `0` hits | `0` hits | Confirmado fuera de caché |

Memoria Valkey tras pruebas: `used_memory_human = 1.17M`, `DBSIZE = 3` (`epoch` + `productos:listado` `TTL 5m` + `categorias:listado` `TTL 15m`), Actuator cache health `UP`, respuesta `PONG`.

#### B. Prueba de carga sostenida externa ON/OFF con `k6` v2.4.0 en producción (`REDIS-EVIDENCIA-PRODUCCION-ON-OFF-2026-11-17.md`)
Ejecutada por Alfonso contra `https://tienda.gadmsigchos.gob.ec:444/api/productos/listado`:
- **Smoke funcional previo (`1 req/s`, `10 s`):** `10/10` HTTP `200`, `20/20` checks; creó la clave `feriadigital:v1:{catalogo}:productos:listado:g50ac869e-ca9c-427a-b01f-2d8c2e9ce688` (TTL restante observado `243 s`).
- **4 corridas sostenidas (`10 req/s` durante `5 minutos` cada una, mismo SHA-256 del JAR, flags `productos=true` vs `productos=false`):**

| Modo | Corrida | Solicitudes | Media (ms) | p50 (ms) | p95 (ms) | Fallos HTTP / Checks | Iteraciones descartadas |
|---|---:|---:|---:|---:|---:|---:|---:|
| **ON** | 1 | 3,001 | 99.87 | 99.14 | 101.80 | `0 / 0` | 0 |
| **ON** | 2 | 3,001 | 99.41 | 98.87 | 99.97 | `0 / 0` | 0 |
| **OFF** | 1 | 3,001 | 100.99 | 100.34 | 103.06 | `0 / 0` | 0 |
| **OFF** | 2 | 3,000 | 99.65 | 99.16 | 100.59 | `0 / 0` | 0 |

- **Lectura técnica del equipo:** `12,003` solicitudes sostenidas con **`0` errores y `100%` checks**; durante `ON1` `INFO stats` de Valkey avanzó **`+9,003` hits y `+1` miss**; en red externa a `10 req/s` los promedios `p95` fueron prácticamente equivalentes (`100.88 ms` ON vs `101.82 ms` OFF), demostrando que **el beneficio probado y defendible de Valkey en producción es evitar consultas repetidas del catálogo en MySQL (conservando estabilidad `p95` bajo carga y stock en vivo)**, quedando la ruta paginada `/listado/paginado`, stock, ventas y sesiones documentadas formalmente como **fuera de alcance**.

### 8.6 Prueba 8 — Despliegue y Validación en Producción del Contador Persistente de Visitas (`V12`, `8cf1066`, 2026-11-17)
- **Unitarias y frontend:** `VisitasServiceTest` (`2/2 PASS`), `msvc-producto` `47/47` unitarias, `avalon-react-10.1.0` `49/49` `npm test`, `0` errores en `next lint` y `next build` verificado sin referencias a `localhost:8080`.
- **Corrección operativa descubierta en despliegue:** se añadió `|visitas` en las rutas de `prod_producto_service` en `/etc/nginx/conf.d/tienda.conf` (bloques `:444` e interno `127.0.0.1:8080`) y `gateway/tienda.prod.conf` (`8cf1066`).
- **Verificación en vivo (`https://tienda.gadmsigchos.gob.ec:444`):**
  - `GET /api/visitas/estadisticas` y `GET /api/proxy/productos/visitas/estadisticas` sin token -> **`403 FORBIDDEN`**.
  - **Navegador Chrome en contexto limpio (`/landing`):** primera visita (`200`) disparó automáticamente `POST /api/proxy/productos/visitas` -> **`204 No Content`** y fijó la cookie `feriadigital_visit_day=1`; recarga (`304`) envió la cookie y **no** volvió a contabilizar.
  - **MySQL en producción (`productos.visitas_diarias`):** fila verificada `fecha = 2026-11-17`, `cantidad = 1`.
  - **Pendiente menor manual:** validar visualmente la tarjeta *Visitas del sitio* iniciando sesión como `ROLE_ADMIN` en `/administrador`.

---

## 9. Hallazgos Menores No Bloqueantes y Alcance Omitido Documentado

Para total transparencia técnica del equipo al redactar conclusiones o limitaciones del documento final:

1. **Alcance no ejecutado en producción para proteger datos reales (`Opción A`):**
   - Mutaciones destructivas de `T11` (`DELETE /api/usuarios/{id}/empresa` y reasignaciones entre las `4/4` empresas productivas ya asignadas).
   - Creación de órdenes reales de DeUna/Transferencia con subida de comprobante sobre datos productivos.
2. **Detalles menores de UI detectados en Chrome (no bloqueantes para seguridad/pagos):**
   - `/landing`: faltan 2 archivos locales de fuente `roboto-*-700` (`404`).
   - `/products` y `/emprendedor/productos`: 2 productos heredados (`Queso tipo finca` IDs `17` y `18`) apuntan a `https://i.imgur.com/default.png`.
   - `/emprendedor`: tarjetas iniciales de resumen muestran datos de plantilla, mientras sus submódulos (`/productos`, `/inventario`, `/ventas`) consultan datos reales.
   - `/emprendedor/inventario` y `/administrador/inventario`: columna *"Ubicación/Estado"* muestra etiqueta `"Sin stock"` aunque *"Stock Actual"* muestra existencias reales.
   - `/administrador/usuarios/[id]/editar`: botón `"Cancelar"` dispara envío `PUT` por faltarle `type="button"`.
3. **Observaciones de infraestructura pendientes de seguimiento:**
   - Topología con firewall perimetral `pfSense` (`69.10.49.106`), por la cual la tienda opera por ahora en el puerto asignado `:444` (y Turismo en `:448`), documentada en `documentos-trabajo/GUIA-MIGRACION-PUERTO-444-A-443.md`.
   - Cierre externo de puertos internos desde Internet (revisar listener externo `:8084` de la IP pública compartida) e instalación en el servidor de los timers de respaldo automático `T01` (`scripts/prod/INSTALACION-RESPALDOS.md`) y pruebas de timeout lento `R06` (`> 500 ms`).

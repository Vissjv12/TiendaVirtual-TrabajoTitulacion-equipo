# Revisión de código: integración A-15 a A-25, Deuna y pulido

| Dato | Valor |
|---|---|
| Fecha de revisión | 2026-11-15 |
| Rama revisada | `origin/integration/version-estable-deuna-pulido` (commit `432f92b`, idéntica a `origin/feature/pulido-export-validaciones-404`) |
| Rama base de comparación | `integration/base-pre-redis` (commit `1b13ba6`, cierre P4–P7) |
| Diferencia | 25 commits, 200 archivos, +12 641 / −2 828 líneas |
| Tipo de revisión | Estática (lectura de código y documentación). No se levantó el sistema ni Docker. |
| Fuentes de lo "ideado" | `Plan_de_trabajo_etapas_y_ramas.docx`, `PLAN-IMPLEMENTACION-SISTEMA-V2 (1).docx`, `A15-EVALUACION-Y-DECISION.md`, `A16-MODELO-DE-CACHE.md`, `A19-CONTRATO-PAGINACION.md`, `PLAN-CIERRE-REDIS-A17-A18.md` |
| Fuentes de lo "declarado como hecho" | `REDIS-IMPLEMENTACION-RESULTADOS-A17-A18.md`, `A22-RESULTADOS-PAGINACION.md`, `A24-A25-SEGURIDAD-MSVC-VENTAS-Y-REGRESION.md`, `REGISTRO-DEUNA-CHECKOUT-Y-PERMISOS-EMPRENDEDOR.md`, `REGISTRO-PULIDO-PRE-INTEGRACION.md` |

> Las rutas de código citadas como `archivo:línea` corresponden al commit `432f92b`. Mientras la rama no se integre a `integration/base-pre-redis`, esas líneas no existirán en la copia de trabajo actual.

---

## 1. Resumen ejecutivo

1. **La integración es limpia.** La historia es lineal: cada rama contiene a la anterior y la más reciente puede integrarse por *fast-forward* sobre `integration/base-pre-redis`, sin conflictos.
2. **Lo planificado para las etapas 5, 6 y 7 está implementado en su mayor parte y coincide con su diseño.** Redis respeta A15/A16, la paginación cumple el contrato A19 (18 de 18 endpoints) y la seguridad de Ventas coincide con la matriz documentada en A-24.
3. **P4–P7 se conservan.** Los precios y la tarifa siguen siendo autoritativos en Ventas, los errores son genéricos, `show-sql=false` y no hay secretos nuevos en el repositorio.
4. **Hay hallazgos que deben resolverse antes de un despliegue:**
   - **2 críticos:** el QR de Deuna o los datos bancarios pueden ser de **otro comercio**, y el token de Payphone está expuesto públicamente.
   - **5 altos:** un emprendedor puede alterar el inventario de otra empresa; un emprendedor puede reactivar o cambiar el RUC de su empresa; Stripe acepta un PaymentIntent sin validar monto ni venta; el pago con tarjeta no descuenta stock; y la factura se genera en un hilo sin control.
5. **Desvíos respecto a lo ideado:** Redis acelera `/api/productos/listado`, pero la vitrina, ya paginada, consume `/listado/paginado`, que **no pasa por Redis**. Los códigos A-19 y A-20 se reutilizaron en nombres de ramas para trabajos que no corresponden a esas actividades.
6. **Trabajo adicional fuera del plan:** Deuna con verificación manual, cuentas bancarias reales en el checkout, exportación CSV, validaciones de formularios, página 404, borrado lógico de usuarios con protección del último admin, y permisos de emprendedor sobre categorías y empresa.

---

## 2. Estructura de ramas revisada

```
integration/base-pre-redis (1b13ba6)                                 P4–P7 (Jordy)
 └─ integration/avance-hasta-redis              +14  A17–A18 Redis (alfonso1031 / Alfonso Arroyo)
     └─ feature/a19-pagos-deuna-checkout         +3  Deuna, checkout, sincronización venta-pago (cmbonifaz)
         └─ feature/a20-permisos-emprendedor-inventario +2  Permisos EMP, inventario inicial, UI
             └─ feature/a19-a22-paginacion       +1  A19–A22 paginación
                 └─ feature/a24-a25-seguridad-ventas +1  A24–A25
                     └─ integration/version-estable-deuna-pulido +4  Pulido (CSV, validaciones, 404, usuarios, bancos)
```

| Rama fuera de la línea principal | Estado |
|---|---|
| `origin/feature/a15-a18-redis-gateway` | 3 commits que no están en la rama revisada. Solo son documentos A15–A16 portados a Gateway; no hay código pendiente de integrar. |
| `main`, `develop`, `etapa/0..2` | Sin cambios desde julio. |
| `release/corte-*` | **No existen.** El plan define `release/corte-1-seguridad`, `release/corte-2-arquitectura-rendimiento` y `release/corte-3-liberacion`. |

**Desvío del flujo planificado.** El plan establece `feature/* → etapa/* → develop → release/*` con un Pull Request y revisión cruzada por tarea. Lo observado es una cadena lineal de ramas `feature` e `integration`, sin `etapa/5..7`, sin `develop` actualizado y sin `release`. Tampoco hay evidencia en el repositorio de Pull Requests revisados.

---

## 3. Comparación plan ↔ código ↔ documentación por actividad

Leyenda de estado:
- **Cumple:** coincide con lo ideado.
- **Cumple con desvío:** funciona, pero difiere en algo relevante.
- **Parcial:** falta una parte de lo ideado.
- **No implementado.**
- **Adicional:** no estaba en el plan.

### 3.1 Etapa 5: Redis (A-15 a A-18)

| Actividad | Lo ideado | Lo implementado (código) | Lo documentado | Estado |
|---|---|---|---|---|
| A-15 Evaluar alternativas | Decidir alcance. El plan V2 proponía además Redis para **stock (DECR/Lua)**, **carritos (`cart:{clienteId}`)** y **rate limiting** en gateway. | Solo documento. | A15 restringe a un piloto Cache-Aside de lectura pública; excluye stock, carrito, checkout y pagos. | **Cumple.** La decisión A15 reemplaza conscientemente la propuesta amplia del plan V2. |
| A-16 Modelo | Claves versionadas `feriadigital:v1:...:g{generacion}:q:{hash}`, TTL de 5 min (productos) y 15 min (categorías/empresas), invalidación tras commit, proyección pública sin datos privados. | `feriadigital:v1:{catalogo}:productos:listado:g{N}`, `...categorias:listado` y `...categorias:detalle:{id}`; **una sola generación global** (`{catalogo}:epoch`); TTL `PT5M`/`PT15M` configurables; invalidación `afterCommit` (`ProductoCatalogoInvalidacionService`). Proyección `ProductoCatalogoPublicoDto` sin stock ni `emprendedorId`. | A16 + `REDIS-IMPLEMENTACION-RESULTADOS-A17-A18.md`. | **Cumple con desvío.** No hay hash de consulta porque solo se cachea un listado sin filtros. Hay una generación global en vez de una por recurso. Empresas no se cachean. |
| A-17 Piloto | Cache-Aside del catálogo con respaldo en MySQL. | `ProductoCatalogoCacheService` (hit, miss y fallback con métricas Micrometer), escritura condicionada por generación con script Lua, health `redisCache` independiente de liveness/readiness, actuator protegido, **desactivado por defecto** (`CACHE_REDIS_ENABLED=false`). El stock se consulta en vivo a inventario, nunca desde caché. | Resultados A17–A18. | **Cumple.** La implementación es sólida. |
| A-18 Evaluar migración | Recomendación basada en evidencia (el plan V2 menciona JMeter antes/después). | Escenario k6 `scripts/redis-a18-listado.js`. Mediciones solo locales. | Indica caída de `Com_select` de ~99,8 % en *hit*, pero un p95 **sin mejora** (54,69 ms frente a 49,45 ms). Prueba institucional pendiente. | **Parcial.** Falta la prueba institucional y la aprobación de campos públicos (`empresaId`, categoría); se usó k6 en vez de JMeter. |
| Coherencia con Etapa 6 | La vitrina debería beneficiarse de Redis. | La vitrina (`app/(main)/products/page.tsx:139`) usa `/api/productos/listado/paginado`, que consulta MySQL directamente (`ProductoServiceImpl.java:312`). Redis solo se usa en `/api/productos/listado` (`ProductoServiceImpl.java:219`), que ahora consume únicamente `FeaturedProducts` en la portada. | Ningún documento menciona este cruce. | **Desvío.** Hallazgo M-01. |

### 3.2 Etapa 6: Paginación (A-19 a A-22)

| Actividad | Lo ideado | Lo implementado | Lo documentado | Estado |
|---|---|---|---|---|
| A-19 Contrato | `content`, `pageNumber`, `pageSize`, `totalElements`, `totalPages`, `first`, `last` (más `empty` en V2); size 12 en vitrina y 10 en gestión, máximo 50. | `PageResponseDto` idéntico en los 4 servicios (8 campos). `PaginationUtils`: tamaño por defecto 12/10, `Math.min(size, 50)`, página negativa → 0, lista blanca de `sort`. | `A19-CONTRATO-PAGINACION.md`. | **Cumple.** |
| A-20 Backend | Listados con `Pageable` y límites. | **18 endpoints `/paginado`**, que coinciden 1 a 1 con el catálogo de A19 (verificado, sin faltantes ni sobrantes). Consultas JPA paginadas en base de datos. | A19 y A22. | **Cumple.** Nota: el código "A20" se usó también en `feature/a20-permisos-emprendedor-inventario`, que no es paginación (hallazgo B-04). |
| A-21 Frontend | DataTable `lazy` + Paginator, pidiendo solo la página necesaria. | Métodos paginados en 7 servicios del frontend y `lazy` en 17 vistas. La vitrina usa `obtenerProductosListadoPaginado`. | A22. | **Cumple.** |
| A-22 Rendimiento | Validar consultas grandes controladas. "Ningún servicio REST retorna colecciones infinitas" (V2). | Pruebas unitarias `*PaginationTest` en los 4 servicios. Los endpoints antiguos sin paginar (`GET /api/productos`, `/listado`, `/empresa/{id}`, `/categoria/{id}`, `/api/inventarios`, `/api/movimientos`, etc.) **se mantienen**. | A19 declara que se conservan por compatibilidad. A22 deja fuera las pruebas contra MySQL, Redis y gateway (sandbox). | **Parcial.** Hay colecciones sin límite aún expuestas públicamente y no hay medición E2E. |

### 3.3 Etapa 7: Catálogo público y compra autenticada (A-23 a A-25)

| Actividad | Lo ideado | Lo implementado | Lo documentado | Estado |
|---|---|---|---|---|
| A-23 Precios públicos | Catálogo y precios sin sesión. | Ya integrado en la base (`3d88e7e`). Lecturas de `msvc-producto` con `anyRequest().permitAll()`. | — | **Cumple.** Pero las lecturas públicas de empresas exponen RUC, email, teléfono y dirección (M-03), lo que A16 había advertido. |
| A-24 Compra autenticada | Checkout y ventas requieren JWT con rol `ROLE_CLIENTE` o superior; `usuarioId`/`empresaId` desde los claims. | `msvc-ventas` pasa a Resource Server HS256 con `anyRequest().authenticated()` y `@PreAuthorize` en cada endpoint (`VentaAuthorizationService`: empresa por claim `empresaId`, cliente por claim `email`). El frontend envía `Authorization` en ventas, pagos, clientes, Stripe y logística. | `A24-A25-...md` §2 (matriz). | **Cumple.** La matriz documentada coincide con los `@PreAuthorize` del código. |
| A-25 Separación público/privado | Pruebas del integrante 3 **directamente contra la API**: catálogo público, compra sin sesión y tokens inválidos. | `VentaControllerApiAuthorizationTest` (6), `VentaAuthorizationServiceTest` (4), `InventarioControllerApiAuthorizationTest` (+7): todas `@WebMvcTest` con mocks. | 39/39 (ventas) y 18/18 (inventario); en esta revisión se obtuvieron los mismos totales. | **Parcial.** No hay validación contra la API real ni por gateway, y el script A-14 quedó desactualizado (A-03). |

### 3.4 Trabajo adicional (no planificado como actividad A-XX)

| Bloque | Implementado | Observación |
|---|---|---|
| Deuna con verificación manual | `DeunaTransferenciaForm.tsx` (main y landing); QR validado por firma mágica + `ImageIO`, nombre UUID, máximo 5 MB, rutas normalizadas; `/uploads/qr-deuna/**` público. | La validación del archivo es correcta. La **resolución del QR tiene un fallo crítico** (C-01). |
| Sincronización venta ↔ pago ↔ stock | Completar o aprobar descuenta stock (`salida-lote`) y sincroniza `estadoPago`; cancelar rechaza el pago pendiente; se inicializa logística y la factura. | No hay doble descuento (ambos caminos exigen venta `PENDIENTE`). Hallazgos A-04, A-05 y M-02. |
| Permisos de emprendedor (rama "a20") | EMP crea categorías y edita su propia empresa; registro inicial de inventario como *upsert*. | Hallazgos A-01 y A-02; las categorías son globales (B-02). |
| Pulido | CSV con protección contra inyección de fórmulas, `formValidations.ts`, 404 sin `LayoutContext`, submenú dinámico de categorías, botón "Gestionar" por empresa, rutas `/administrador/...`, cuentas bancarias reales en transferencia. | Las validaciones son solo del lado del cliente (B-03). |
| Usuarios | Borrado lógico, bloqueo de login a inactivos (403), protección del último admin. | La protección del último admin **sí se aplica en el backend** (`UsuarioServiceImpl.validarProteccionUltimoAdmin`, en actualizar, cambiar estado y eliminar), no solo en la interfaz como sugiere el documento. Limitación: M-04. |

### 3.5 Lo que el plan contempla y no se encuentra en el código

| Ítem del plan | Situación |
|---|---|
| Redis para reservas de stock (DECR/Lua) y carritos con TTL (plan V2 §3) | Descartado por A15. Debe quedar explícito en el documento de cierre de etapa que se reemplazó la propuesta V2. |
| Rate limiting en gateway con Redis / Spring Cloud Gateway (plan V2) | No implementado; el gateway sigue siendo NGINX. |
| Ramas `etapa/5-redis`, `etapa/6-paginacion`, `etapa/7-catalogo-compra` y `release/corte-*` | No existen. |
| Pull Request con revisión cruzada y evidencia por tarea (plan §12–13) | No hay evidencia en el repositorio. |
| Medición JMeter pre/post (plan V2 §6) | Reemplazada por k6, solo local. |
| Validación de stock al crear la venta (evitar sobreventa) | La venta transferencia/Deuna queda `PENDIENTE` sin reservar stock; el descuento ocurre al aprobar. Si el stock se agota antes, la aprobación falla. Riesgo de negocio por documentar. |

---

## 4. Hallazgos de la revisión de código

Clasificación:
- **Origen:** *Nuevo* = introducido en los 25 commits; *Preexistente* = ya estaba en la base y sigue abierto.
- **Severidad:** Crítica, Alta, Media, Baja.

### Críticos

**C-01. El checkout puede mostrar el QR de Deuna o los datos bancarios de otro comercio.** *Nuevo.*
- `Microservicios/demo/src/main/java/com/example/demo/domain/service/ConfiguracionPagosService.java:184-212`, método `obtenerQrDeuna`:
  - **Nivel 3:** si la empresa no tiene QR, devuelve el QR válido **más reciente de cualquier emprendedor** (`configuracionRepository.findAll()...findFirst()`). El comentario dice "ambiente local", pero el código no comprueba ningún perfil: se ejecuta siempre.
  - **Nivel 2:** si no hay emprendedor con ese `empresa_id`, reinterpreta el mismo número como `emprendedor_id`, que puede pertenecer a otra empresa.
- `obtenerDatosBancarios` (mismo archivo, aprox. líneas 219-232) repite el **Nivel 2** con los datos bancarios.
- **Impacto:** el cliente paga a un tercero. Es dinero mal dirigido y difícil de revertir.
- **Recomendación:**
  - Eliminar el Nivel 3.
  - Resolver solo por `empresa_id`, con un endpoint explícito y sin reinterpretar el id.
  - Si no hay QR o cuenta, responder 404 y deshabilitar el método de pago en el checkout.

**C-02. El token de Payphone del comercio se expone sin autenticación.** *Preexistente.*
- `ConfiguracionPagosController.java:138-151`: `GET /api/emprendedor/configuracion-pagos/payphone/{empresaId}` es `permitAll` (`SecurityConfig.java:48`) y devuelve `PayphoneDto` con `payphoneToken` (`PayphoneDto.java:12`).
- **Impacto:** cualquiera puede obtener la credencial del comercio con solo enumerar `empresaId`.
- **Recomendación:**
  - Confirmar con la documentación de Payphone si ese token debe ser privado.
  - Si lo es, mover la creación del pago al backend y devolver al público solo lo imprescindible.

### Altos

**A-01. Un emprendedor puede modificar el inventario de productos de otra empresa.** *Preexistente; el commit "a20" lo agrava.*
- `msvc-inventario/.../InventarioController.java`:
  - `POST /api/inventarios` (l. 47), `POST /producto/{productoId}` (l. 68), `PUT /{id}` (l. 82) y `DELETE /{id}` (l. 222) solo exigen `ROLE_ADMIN`/`ROLE_EMP` en `SecurityConfig`, **sin validar la propiedad** del producto.
  - Lo mismo ocurre con `POST /api/movimientos` (`MovimientoInventarioController.java:43`).
  - Solo `salida-lote` y `PUT /producto/{id}/stock` usan `InventarioAuthorizationService`.
- El cambio de `1dcb7e8` (`InventarioServiceImpl.java:55-77`) convierte el `POST` en *upsert*: si ya existe inventario, **sobrescribe la cantidad**. Antes, ese caso devolvía un error.
- **Impacto:** un emprendedor puede poner en 0 el stock de un competidor.
- **Otros defectos del upsert:**
  - El movimiento registra la cantidad total, no la diferencia.
  - El `usuarioId` está fijo en `1L` (l. 73 y l. 163).
- **Recomendación:** aplicar `canManageProduct` a todas las escrituras de inventario y movimientos, y registrar la diferencia y el usuario real del token.

**A-02. Un emprendedor puede reactivar su empresa o cambiar su RUC.** *Nuevo.*
- `msvc-producto/.../EmpresaController.java:55-68` permite `PUT /api/empresas/{id}` a `ROLE_EMP` propietario.
- `EmpresaServiceImpl.java:44-50` copia todos los campos, incluidos `activo` y `ruc`.
- Si el cliente no envía `activo`, `EmpresaMapper.java:20` lo fija en `true`. Por eso, **cualquier edición normal del perfil** por parte del emprendedor reactiva una empresa que el admin había desactivado, aunque el emprendedor no lo pretenda.
- **Impacto:** se anula una desactivación administrativa y se puede alterar un dato tributario.
- **Recomendación:** que el emprendedor solo pueda editar los campos de perfil (nombre, dirección, teléfono, email) y que `activo`/`ruc` sean exclusivos del admin.

**A-03. Stripe confirma pagos sin validar monto ni venta.** *Preexistente.*
- `msvc-ventas/.../StripeController.java:70-101` solo comprueba `status == succeeded`.
- No compara `paymentIntent.amount` con `venta.total` ni `metadata.venta_id` con la venta recibida, y no impide reutilizar el mismo `paymentIntentId`.
- **Impacto:** un PaymentIntent exitoso de bajo valor podría marcar como pagada otra venta.
- **Recomendación:** validar monto, moneda y `metadata.venta_id`, y guardar `paymentIntentId` con restricción de unicidad.

**A-04. El pago con tarjeta no descuenta stock.** *Nuevo, por inconsistencia.*
- `PagoService.procesarPagoTarjeta` (`PagoService.java:292-331`) marca la venta como `COMPLETADA` sin llamar a `salida-lote`, mientras que `aprobarPago` y `completarVenta` sí descuentan.
- Además, el token del cliente no está autorizado para `salida-lote` (solo ADMIN/EMP), así que no podría descontarse con ese token.
- **Impacto:** el inventario queda desalineado en las compras con tarjeta.
- **Recomendación:** descontar con una credencial de servicio (no con el token del cliente) y unificar los tres caminos en un único método de dominio.

**A-05. La factura y la confirmación usan un `new Thread` con `sleep(100)` dentro de la transacción.** *Nuevo en `completarVenta`; preexistente en `PagoService`.*
- `VentaServiceImpl.java:274`, `PagoService.java:165` y `PagoService.java:331`.
- El hilo puede ejecutarse antes del commit: lee datos no confirmados o la venta aún no existe para esa sesión. Si falla, la factura se pierde sin reintento.
- **Recomendación:** generar la factura con `TransactionSynchronization.afterCommit` o `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`, con registro de fallos.

### Medios

**M-01. Redis no acelera la vitrina.** *Nuevo, por combinación de etapas.*
- Ver §3.1. Las mediciones A18 corresponden a `/listado`, que ya no es la ruta principal del catálogo.
- **Decisión pendiente:** cachear `/listado/paginado` por página y filtros (como en el diseño A16, con `hashConsulta`), o documentar que el piloto solo cubre los destacados.

**M-02. El descuento de stock no tiene compensación.** *Nuevo.*
- `aprobarPago` y `completarVenta` llaman a `salida-lote` dentro de `@Transactional`.
- Si inventario confirma y luego falla el guardado en Ventas, el stock queda descontado sin venta completada. No hay idempotencia por `ventaId`.
- **Recomendación:** que inventario sea idempotente por referencia de venta, más una compensación (entrada) ante fallo.

**M-03. Las lecturas públicas exponen datos de contacto y RUC de las empresas.** *Preexistente; A19 lo amplía.*
- `GET /api/empresas`, `/api/empresas/{id}`, `/api/empresas/paginado` (ordenable por `ruc`, `EmpresaController.java:80`) y `GET /api/productos/{id}` devuelven `EmpresaResponseDto` con `ruc`, `direccion`, `telefono` y `email`.
- A16 pedía una proyección pública con, como máximo, `id` y `nombre`.

**M-04. Desactivar un usuario no invalida sus tokens.** *Nuevo, como limitación.*
- `AuthService.java:59-67` bloquea el login, pero los JWT ya emitidos siguen siendo válidos en los 4 servicios hasta que expiran (arquitectura sin estado).
- Hay que documentar el tiempo de expiración o validar `activo` en operaciones sensibles.

**M-05. El historial de movimientos de inventario es público.** *Preexistente; A19 añade versiones paginadas.*
- `SecurityConfig.java:45` (inventario) permite `GET /api/movimientos/**`, que incluye motivos tipo "Venta #FACT-…" y `usuarioId`. Revela el volumen de ventas de cada empresa.

**M-06. El webhook de Stripe está vacío.** *Preexistente.*
- `StripeController.java:106-121` verifica la firma, pero no procesa nada. Si el cliente cierra el navegador antes de llamar a `confirm-payment`, el pago cobrado no se registra.

### Bajos y de trazabilidad

**B-01.** `Documentos_Jordy/pruebas-gateway-a14.ps1:155` espera `GET /api/ventas` sin token → 200. Desde A-24 la respuesta es 401, así que el script fallará y debe actualizarse.

**B-02.** Las categorías son globales (`Categoria` no tiene empresa). Un emprendedor puede crearlas (`CategoriaController.java:40`), pero solo el admin puede editarlas o eliminarlas. Hay riesgo de duplicados o nombres inapropiados visibles para todos. Debe ser una decisión de negocio explícita.

**B-03.** Las validaciones de `formValidations.ts` son solo del lado del cliente. Hay que verificar que el backend aplique reglas equivalentes, por ejemplo en `DatosBancariosDto` y en el cliente del checkout.

**B-04.** Hay reutilización de códigos: `feature/a19-pagos-deuna-checkout` y `feature/a20-permisos-emprendedor-inventario` usan A-19 y A-20, que en el plan son "Contrato de paginación" y "Backend de paginación". Esto rompe la trazabilidad exigida en el plan §12.

**B-05.** Los documentos nuevos enlazan rutas absolutas de otra máquina (`file:///c:/Users/Administrador/Desktop/...`), así que esos enlaces no funcionan en el repositorio.

**B-06.** Las pruebas de integración de Redis (`ProductoCatalogoFallbackIT`, `ProductoCatalogoHttpIT`, `RedisGenerationIT`, `RedisProductoCatalogoStoreIT`) no se ejecutan con `mvn test`: necesitan failsafe y `docker-compose.test.yml`. Falta documentar el comando.

**B-07.** Varias lecturas de A16 quedaron como "aprobación pendiente del propietario" (`PLAN-CIERRE-REDIS-A17-A18.md:39-40, 62, 74, 96`). Siguen abiertas.

### Verificado sin hallazgos

- **P4:** `VentaApplicationService` no cambió. Precio, empresa y costo de envío siguen viniendo de `msvc-producto` y `auth-service`.
- **P5:** los manejadores de error solo ganaron 401/403 genéricos; no se introdujo ningún `ex.getMessage()` nuevo en respuestas 5xx.
- **Configuración:** `.env.example` solo contiene marcadores. `docker-compose.test.yml` ahora publica MySQL y Redis de prueba solo en `127.0.0.1`. El `jwt.secret` de `application-test.properties` (Ventas) es un valor ficticio de pruebas.
- **Subida de QR:** lista blanca de MIME y extensión, firma mágica, decodificación `ImageIO`, nombre UUID y ruta normalizada.
- **CSV:** protección contra inyección de fórmulas (`=`, `+`, `-`, `@`, tabulador) y BOM UTF-8.
- **No hay doble descuento de stock** entre `aprobarPago` y `completarVenta`: ambos exigen venta `PENDIENTE`.
- **`FeignConfig` de Ventas:** no reenvía el token en GET internos (lecturas públicas) y descarta tokens mal formados.

### Pruebas automáticas (referencia, 2026-11-15, con Docker apagado)

| Servicio | Resultado | Nota |
|---|---|---|
| msvc-ventas | 39/39 | Coincide con A24-A25. |
| msvc-inventario | 18/18 | Coincide con A24-A25. |
| msvc-producto | 32/33 | Falla solo `contextLoads`, porque necesita MySQL. |
| demo (auth) | 12/13 | Falla solo `contextLoads`, porque necesita MySQL. |

---

## 5. Guía de pruebas manuales

### 5.0 Resultado de validación local del hallazgo C-01 (2026-11-15)

La validación se ejecutó mediante el API Gateway local (`127.0.0.1:8090`) y el servicio de autenticación directo (`127.0.0.1:8084`). No se realizó una compra ni se modificaron datos.

| Comprobación | Resultado |
|---|---|
| `GET /health` del Gateway | `200 OK`; Gateway operativo (`status=UP`, `service=api-gateway`, `engine=nginx`) |
| Producto local consultado | `GET /api/productos/1` devuelve `empresa.id=1` y `emprendedorId=1` |
| QR de empresa 1 mediante Gateway | `404`; no existe QR configurado |
| Datos bancarios de empresa 1 mediante Gateway | `404`; no existen datos configurados |
| QR de ID inexistente `999999` mediante Gateway | `404` |
| Datos bancarios de ID inexistente `999999` mediante Gateway | `404` |
| Mismas consultas directas contra auth | `404` en QR y datos bancarios |

**Interpretación.** La prueba no reproduce una filtración porque la base local no tiene ningún QR ni dato bancario configurado. Tampoco existen dos empresas sintéticas para comprobar el cruce entre comercios sin alterar la base. Sin embargo, el hallazgo C-01 permanece confirmado por la revisión del código: el backend contiene un fallback global para el QR y reinterpreta el mismo identificador como `empresaId` o `emprendedorId`. El resultado `404` actual solo demuestra que, en este estado de datos, no hay configuración que pueda filtrarse.

### 5.0.1 Plan de corrección C-01

1. Definir el contrato público como `empresaId`, sin aceptar ambigüedad entre empresa y emprendedor.
2. Resolver la configuración exclusivamente mediante la relación empresa → emprendedor → configuración.
3. Eliminar el fallback global `configuracionRepository.findAll()` del QR.
4. Eliminar el fallback que interpreta un `empresaId` como `emprendedorId`.
5. Responder `404` cuando la empresa no tenga configuración propia.
6. En el frontend, utilizar exclusivamente `producto.empresa.id` o un `empresaId` explícito; eliminar los fallback a `emprendedorId` y al valor fijo `1`.
7. Deshabilitar el método de pago en checkout cuando la respuesta sea `404`; nunca mostrar datos de otra empresa.
8. Agregar pruebas automatizadas para dos empresas, empresa sin configuración e identificador inexistente.

La implementación se realizará después de revisar los contratos existentes y sin crear datos de prueba en producción.

### 5.0.2 Corrección aplicada C-01 (2026-11-15, sin commit)

**Estado:** corregido en código y cubierto por pruebas unitarias. Falta la verificación manual con el sistema levantado (MP-13 y MP-14, más MP-13b).

| Paso del plan | Qué se hizo | Archivo |
|---|---|---|
| 1–2 | Contrato público = `empresaId`. Nuevos métodos `obtenerQrDeunaPorEmpresa` y `obtenerDatosBancariosPorEmpresa`, que resuelven solo empresa → emprendedor → configuración. Un id nulo o ≤ 0 devuelve vacío. | `Microservicios/demo/.../domain/service/ConfiguracionPagosService.java` |
| 3 | Eliminado el "Nivel 3" (`findAll()` y el QR más reciente de cualquier comercio). | ídem |
| 4 | Eliminado el "Nivel 2" (`findByEmprendedorId(id)` con el mismo id) en QR y en datos bancarios. | ídem |
| 5 | Los endpoints `GET /deuna-qr/{empresaId}` y `GET /bancarios/{empresaId}` responden `404` si la empresa no tiene configuración propia. Los datos bancarios también exigen `numeroCuenta`. | `.../presentation/controller/ConfiguracionPagosController.java` |
| 5 (gateway) | Eliminado el alias `location = /api/emprendedor/deuna-qr`, que reescribía a `deuna-qr/1` y entregaba el QR de la empresa 1 a cualquiera. Ahora esa ruta cae en el 404 JSON del gateway. El alias con id numérico se conserva. | `gateway/nginx.conf` |
| 6 | Nuevas funciones en `pagoService.ts`: `obtenerEmpresaIdProducto` (solo `empresa.id` / `empresaId`), `obtenerEmpresasCarrito`, `obtenerQrDeunaEmpresa` y `verificarDisponibilidadDeuna`. Se quitaron los fallback `emprendedorId` y `\|\| 1` en `DeunaTransferenciaForm.tsx` y `DeunaForm.tsx` (main y landing) y en `obtenerDatosBancariosCheckout`. | `avalon-react-10.1.0/services/pagoService.ts` y los componentes indicados |
| 7 | `PaymentMethodSelector` acepta `disabledMethods` (método → motivo). El checkout (main y landing) consulta la disponibilidad al cargar el carrito. Deuna queda deshabilitado, con el motivo visible, si la empresa no tiene QR o si el carrito mezcla **varias empresas**: un QR pertenece a un único comercio. Si Deuna estaba seleccionado, vuelve a Transferencia. | `PaymentMethodSelector.tsx`, `checkout/page.tsx` (main y landing) |
| 8 | 14 pruebas nuevas (detalle abajo). | `src/test/.../ConfiguracionPagosServiceEmpresaTest.java`, `ConfiguracionPagosControllerEmpresaTest.java` |

**Pruebas nuevas:**
- `ConfiguracionPagosServiceEmpresaTest` (10). Cubre dos empresas con QR y cuenta distintos, sin cruce; empresa con fila de configuración pero sin QR ni cuenta; empresa sin fila; empresa inexistente; id que solo coincide con un `emprendedorId` (verifica que no se reinterpreta); QR registrado pero sin archivo en disco; ids nulo, 0 y negativo. Además verifica que nunca se invoca `findAll()`.
- `ConfiguracionPagosControllerEmpresaTest` (4). Cubre 200 y 404 en `deuna-qr` y en `bancarios`.

**Resultados:**
- `mvnw test` del servicio `demo`, excluyendo `LoginApplicationTests` (necesita BD): **26/26 OK**.
- `tsc --noEmit` del frontend: sin errores.

**Decisión añadida al plan.** Deuna se deshabilita también con carritos de varias empresas. Antes se mostraba el QR del primer producto, de modo que el comprador pagaba todo a un solo comercio.

**Fuera del alcance de esta corrección:**
- C-02, el token de PayPhone público (`/payphone/{empresaId}`), sigue pendiente por decisión del equipo.
- El costo de envío conserva su valor por defecto de $5, que es un valor de negocio, no un dato de otro comercio.

**Prueba manual adicional MP-13b.** Carrito con productos de dos empresas, ambas con QR → la opción Deuna aparece deshabilitada con el texto "Disponible solo cuando todos los productos son del mismo emprendedor".

### 5.0.3 Revisión de la corrección C-01 (2026-11-15)

La revisión del diff confirma que la corrección elimina el riesgo principal en las tres capas:

- **Backend:** `ConfiguracionPagosService` ya resuelve exclusivamente `empresaId → emprendedor → configuración`; no usa `findAll()` ni reinterpreta el identificador como `emprendedorId`.
- **API:** los endpoints públicos devuelven `404` si la empresa no tiene su propia configuración.
- **Gateway:** se eliminó el alias sin identificador que redirigía implícitamente a la empresa `1`.
- **Frontend:** se eliminaron los fallback a `emprendedorId` y al valor fijo `1`; Deuna se deshabilita si el carrito mezcla empresas o no tiene QR.
- **Pruebas:** las pruebas unitarias nuevas cubren dos empresas, ausencia de configuración, identificador inexistente, colisión de IDs y respuestas HTTP `200/404`.

#### Pendientes detectados antes del cierre

1. `obtenerEmpresasCarrito` ignora los productos cuyo `empresa.id`/`empresaId` no sea válido. Si un carrito contiene un producto válido y otro sin empresa, todavía podría consultar los datos de la empresa válida. La política correcta debe ser **fallar cerrado**: si cualquier producto carece de empresa válida, no consultar QR ni datos bancarios.
2. `obtenerDatosBancariosCheckout` consulta una cuenta por cada empresa encontrada. Aunque la venta debería ser de una sola empresa, una cesta inconsistente podría mostrar varias cuentas. Debe rechazarse el carrito o deshabilitar la transferencia cuando haya más de una empresa, igual que Deuna.
3. Falta ejecutar la validación manual MP-13, MP-14 y MP-13b con el sistema levantado. La base actual solo tiene una empresa y no tiene QR ni datos bancarios, por lo que esas pruebas no pueden reproducir el cruce entre comercios.
4. Las modificaciones funcionales y las dos clases de prueba siguen sin commit; no deben mezclarse con los documentos, PDFs, `.claude/` ni otros cambios locales no relacionados.

#### Siguiente orden de trabajo

1. Corregir el comportamiento de carrito incompleto o multiempresa para QR y cuentas bancarias.
2. Agregar pruebas frontend para producto sin empresa, carrito mixto y carrito de una sola empresa.
3. Ejecutar `tsc --noEmit` y las pruebas focalizadas de `demo`.
4. Levantar el Gateway y comprobar que las consultas usan el `empresaId` correcto y que las respuestas sin configuración son `404`.
5. Revisar el diff y preparar un commit aislado de C-01.
6. Después de cerrar C-01, continuar con C-02 (Payphone) o con el siguiente hallazgo crítico priorizado por el equipo.

### 5.0.4 Cierre de los pendientes 1 y 2 (2026-11-15, sin commit)

Los pendientes 1 y 2 de la sección 5.0.3 están corregidos, y los pasos 1 a 3 de su orden de trabajo están completos.

**Regla única que falla cerrado.** Está en el nuevo módulo puro `avalon-react-10.1.0/services/checkoutEmpresa.ts`, que no tiene dependencias.

`obtenerEmpresaIdProducto` acepta solo `empresa.id` o `empresaId`. Devuelve `null` en estos casos:
- falta el id;
- el id es inválido (`0`, negativo, decimal o no numérico);
- el producto solo trae `emprendedorId`;
- `empresa.id` y `empresaId` se contradicen.

`resolverEmpresaUnicaCarrito` devuelve la empresa solo si **todos** los productos tienen empresa válida y es la **misma**. Si no, responde con el código y el motivo:
- `CARRITO_VACIO`;
- `PRODUCTO_SIN_EMPRESA`, cuando cualquier producto, en cualquier posición, carece de empresa válida;
- `MULTIPLES_EMPRESAS`.

`verificarDisponibilidadMetodo(carrito, consultar, motivo)` invoca `consultar` **solo** si el carrito resuelve una única empresa. Así, un carrito incompleto o mezclado no genera ninguna petición de QR ni de cuenta bancaria.

**Integración:**

| Archivo | Cambio |
|---|---|
| `services/pagoService.ts` | Se eliminaron `obtenerEmpresaIdProducto`, `obtenerEmpresasCarrito` y `obtenerDatosBancariosCheckout`, que consultaba una cuenta por empresa. Se añadieron `obtenerDatosBancariosEmpresa(empresaId)` y `verificarDisponibilidadTransferencia`. `verificarDisponibilidadDeuna` ahora usa la regla común. Ambas devuelven `{ disponible, motivo, empresaId, datos }`. |
| `checkout/components/TransferenciaBancariaForm.tsx` (landing lo reexporta) | Muestra como máximo **una** cuenta: la de la única empresa del carrito. Si el carrito está bloqueado, muestra el motivo. El formulario queda **inválido** mientras carga, ante un error o si no hay exactamente una cuenta. Antes podía enviarse sin ninguna cuenta destino. |
| `checkout/page.tsx` (main y landing) | Verifica a la vez la transferencia y Deuna, y pasa `metodosNoDisponibles` al selector. Si el método elegido queda bloqueado, cambia al primero disponible: Transferencia → PayPhone → Deuna en main, y Transferencia → Deuna en landing. Si ninguno está disponible, el botón de pago queda deshabilitado porque el formulario es inválido. |
| Formularios Deuna (main y landing) | Usan `disponibilidad.datos`. |

**Pruebas del frontend.** Se agregó `avalon-react-10.1.0/tests/checkoutEmpresa.test.mjs`, que corre con `node:test` sin dependencias nuevas, y el script `npm test`. Son 20 pruebas:
- **Producto:** solo `emprendedorId`; empresa ausente; ids inválidos; ids contradictorios; id válido.
- **Carrito:** una sola empresa; carrito vacío; producto válido más producto sin empresa, en ambas posiciones; carrito mezclado.
- **Consulta:** se invoca una vez y con la empresa correcta. No se invoca nunca con un producto sin empresa, con un carrito mezclado ni con un carrito vacío. Una respuesta 404 se trata como "sin configurar" y un error de red se propaga.

**Resultados:**

| Verificación | Resultado |
|---|---|
| `npm test` (frontend) | 20/20 OK |
| `npx tsc --noEmit` (frontend) | sin errores |
| `mvnw test -Dtest=ConfiguracionPagos*EmpresaTest` (demo) | 14/14 OK |

**Pruebas manuales adicionales:**
- **MP-13c.** Carrito con un producto de la empresa A (con QR y cuenta) y un producto cuyo `empresa.id` falta en el `localStorage` del carrito. Resultado esperado: Transferencia y Deuna deshabilitadas con "No se pudo identificar al emprendedor de uno o más productos", y en la pestaña Red no aparece ninguna petición a `deuna-qr/` ni a `bancarios/`.
- **MP-14b.** Carrito con productos de dos empresas, ambas con cuenta. Resultado esperado: Transferencia deshabilitada con "Disponible solo cuando todos los productos son del mismo emprendedor" y sin peticiones a `bancarios/`.

**Siguen fuera del alcance:**
- `PayphoneForm.tsx` todavía toma la empresa del primer producto. Corresponde a C-02 y no se tocó por decisión del equipo.
- Los pasos 4 a 6 de la sección 5.0.3 siguen pendientes: validación en el Gateway, commit aislado y siguiente hallazgo.

### 5.0.5 Diagnóstico C-02: token de Payphone expuesto públicamente (2026-11-15)

La revisión estática confirma el hallazgo crítico C-02. Todavía no se ejecutó una transacción Payphone ni se modificó configuración.

**Flujo actual observado:**

1. `GET /api/emprendedor/configuracion-pagos/payphone/{empresaId}` está declarado como público en `SecurityConfig`.
2. El controlador resuelve la empresa y devuelve directamente `PayphoneDto`.
3. `PayphoneDto` contiene `payphoneAppId` y `payphoneToken`.
4. `PayphoneForm.tsx` consulta el endpoint desde el navegador y guarda el token en estado React.
5. El token se entrega al SDK de Payphone mediante `window.payphone.Button({ token })`.

Por lo tanto, cualquier visitante puede consultar el endpoint, inspeccionar la respuesta en DevTools o interceptar la petición y obtener el token del comercio. El frontend requiere el App ID público para cargar el SDK, pero no debe recibir el token secreto.

**Evidencia principal:**

- `Microservicios/demo/.../infrastructure/security/SecurityConfig.java`: `payphone/*` está en `permitAll`.
- `Microservicios/demo/.../presentation/controller/ConfiguracionPagosController.java`: el endpoint devuelve `PayphoneDto` completo.
- `Microservicios/demo/.../application/dto/PayphoneDto.java`: el DTO contiene el campo sensible `payphoneToken`.
- `avalon-react-10.1.0/app/(main)/checkout/components/PayphoneForm.tsx`: el navegador obtiene y utiliza el token.
- `avalon-react-10.1.0/app/api/payphone/confirm/route.ts`: también puede obtener el token en servidor, pero actualmente conserva el fallback a `NEXT_PUBLIC_PAYPHONE_TOKEN` y expone demasiados detalles en logs/respuestas.

**Pruebas de diagnóstico sin realizar pagos:**

```powershell
$Gateway = 'http://127.0.0.1:8090'
$empresaId = 1

curl.exe -sS -i `
  "$Gateway/api/emprendedor/configuracion-pagos/payphone/$empresaId"
```

El riesgo queda confirmado si una respuesta `200` incluye `payphoneToken` sin encabezado `Authorization`. Si actualmente no hay configuración Payphone, puede responder `404`; eso no elimina el problema del contrato público ni de la exposición estática del token.

En el navegador, con la pestaña Network abierta, también debe revisarse que la respuesta de `payphone/{empresaId}` no contenga `payphoneToken`. No se debe copiar ni guardar el valor real.

**Plan de corrección propuesto:**

1. Mantener público, si Payphone lo requiere, solo el `payphoneAppId` o un DTO público sin token.
2. No devolver `payphoneToken` desde ningún endpoint público ni enviarlo al navegador.
3. Mover la confirmación Payphone al backend usando un token privado del servidor o una credencial de servicio.
4. El backend debe resolver la configuración por `empresaId` validado y asociar la confirmación con la venta/orden correspondiente.
5. Eliminar `NEXT_PUBLIC_PAYPHONE_TOKEN`; una variable con prefijo `NEXT_PUBLIC_` puede terminar en el bundle del navegador.
6. No registrar tokens, cuerpos completos de configuración ni respuestas sensibles en consola.
7. Responder errores genéricos al cliente y conservar detalles solo en logs protegidos, sin incluir credenciales.
8. Agregar pruebas que verifiquen que el endpoint público nunca contiene `payphoneToken` y que la confirmación del servidor funciona sin exponerlo.

**Decisión pendiente:** confirmar con el administrador si Payphone exige que el App ID sea público y qué operación exacta requiere el token. No se debe rotar ni reemplazar la credencial hasta tener la nueva proporcionada por el responsable.

### 5.0.6 Corrección aplicada C-02 (2026-11-15, sin commit)

**Estado:** corregido en código y cubierto por pruebas. No se tiene todavía el token real de Payphone, así que falta validar el contrato con el entorno de pruebas de Payphone (ver "Pendiente de validar").

**Respuesta a la decisión pendiente.** El SDK anterior, `window.payphone.Button({ token })`, exige el token en el navegador, así que ocultar el token rompía el pago. Por eso se cambió al flujo **"Botón de Pago" por API (redirección)**:

1. El backend prepara el cobro con el token.
2. El comprador paga en la página de Payphone.
3. Payphone redirige a `/checkout/payphone-confirmacion?id=…&clientTransactionId=…`.
4. El backend confirma con el token.

En este flujo **el App ID no es necesario en el navegador**, así que tampoco se publica.

**Cambios por punto del plan:**

| Plan | Qué se hizo | Archivo |
|---|---|---|
| 1 | El endpoint público `GET /payphone/{empresaId}` devuelve solo `{ empresaId, disponible }`, sin token ni App ID. | `PayphoneDisponibilidadDto.java`, `ConfiguracionPagosController.java` |
| 2 | `PayphoneDto` (entrada) marca el token como `@JsonProperty(WRITE_ONLY)` y `@ToString.Exclude`, así que no puede serializarse en ninguna respuesta. La configuración del propio emprendedor (`GET /configuracion-pagos`) devuelve `PayphoneEstadoDto { payphoneAppId, tokenConfigurado }`. La entidad excluye el token de `toString()`. | `PayphoneDto.java`, `PayphoneEstadoDto.java`, `ConfiguracionPagosDto.java`, `ConfiguracionMetodosPago.java`, `ConfiguracionPagosService.java` |
| 3–4 | Nuevos endpoints **autenticados** `POST /payphone/{empresaId}/preparar` y `POST /payphone/{empresaId}/confirmar` (detalle debajo de la tabla). | `PayphonePagoService.java`, `infrastructure/payphone/PayphoneApiClient.java` |
| 5 | No existía `NEXT_PUBLIC_PAYPHONE_TOKEN` en ningún `.env`; solo había un fallback en `route.ts`, que se eliminó. `.env.example` documenta que no debe crearse y añade `PAYPHONE_API_BASE_URL`, `PAYPHONE_RESPONSE_URL` y `PAYPHONE_CANCELLATION_URL`, también declaradas en `application.properties`. | `.env.example`, `application.properties` |
| 6–7 | `app/api/payphone/confirm/route.ts` ya no confirma ni maneja el token. **Se conserva** porque está registrada en Payphone Developer como "URL de Notificación" (`docs/despliegue-exitoso-sesion-2026-07-04.md`, fase 9.5); responde `200 { received: true }`, sin registrar nada. Se quitaron los `console.log` de token, configuración y cuerpos crudos. Los errores hacia el cliente son genéricos: `PayphoneIntegracionException` → 502 con un mensaje saneado; si la empresa no tiene configuración → 404. | `route.ts`, `payphoneService.ts`, `GlobalExceptionHandler.java` |
| 8 | Pruebas (ver más abajo). | |

**Detalle de los endpoints nuevos (3–4):**
- Resuelven el token por `empresaId` (regla C-01).
- Validan que `amount` = `amountWithoutTax` + `amountWithTax` + `tax` + `service` + `tip`.
- Generan `clientTransactionId = FD-{empresaId}-{aleatorio}`, de 50 caracteres como máximo.
- Fijan `responseUrl` y `cancellationUrl` en el servidor, no en el navegador.
- Solo aceptan enlaces `https://*.payphonetodoesposible.com`.
- La confirmación devuelve una **lista blanca** de campos: `statusCode`, `transactionStatus`, `transactionId`, `clientTransactionId`, `amount`, `currency`, `cardType` y `authorizationCode`.
- El cliente HTTP solo registra el código de estado; nunca el token ni el cuerpo.

**Frontend:**
- `PayphoneForm.tsx` ya no usa el SDK. Verifica la disponibilidad con la regla de empresa única de C-01, ofrece "Pagar con app Payphone" y "Pagar con tarjeta", llama a `preparar`, guarda `{ empresaId, clientTransactionId }` en `sessionStorage` y redirige.
- La página de confirmación toma la empresa del `clientTransactionId` generado por el servidor (debe coincidir con el pago pendiente de la sesión) y confirma por el backend.
- **Antes se marcaba "Pago exitoso" y se vaciaba el carrito cuando la confirmación fallaba con `503` o `timeout_confirmacion`.** Ahora falla cerrado: muestra error, conserva el carrito e indica no volver a pagar y contactar a soporte con el `clientTransactionId`.
- El checkout principal deshabilita Payphone, con su motivo, cuando no está disponible.
- En la configuración del emprendedor, el token es de solo escritura: muestra "Token configurado", si el campo queda vacío conserva el token guardado, se limpia del estado al guardar y el App ID pasa a ser opcional.

**Pruebas:**

| Suite | Casos | Resultado |
|---|---|---|
| `PayphoneTokenNoExpuestoTest` (demo) | La configuración del emprendedor y la respuesta de guardado no contienen el token. Un token vacío conserva el guardado. La primera configuración sin token se rechaza. `PayphoneDto` acepta el token de entrada pero no lo serializa ni lo muestra en `toString()`. La entidad tampoco lo muestra en `toString()`. Disponibilidad por empresa. | 7/7 |
| `PayphonePagoServiceTest` (demo, `MockRestServiceServer`) | El token solo viaja en `Authorization: Bearer`. Cada empresa usa su propio token. Sin configuración no hay ninguna llamada a Payphone. Los montos inconsistentes o negativos se rechazan sin llamar. Se descartan enlaces de pago que no son de Payphone. La lista blanca elimina token, correo, teléfono, documento y `storeId`. Un error de Payphone no filtra el token ni el cuerpo. Parámetros inválidos. Respuesta parcial. | 9/9 |
| `ConfiguracionPagosControllerPayphoneTest` (demo) | El endpoint público no contiene `payphoneToken`, `payphoneAppId` ni "token". Preparar y confirmar devuelven cuerpos sin credenciales. Un cuerpo inválido da 400 sin llamar al servicio; una empresa sin Payphone da 404; un fallo de Payphone da 502 saneado. | 7/7 |
| `tests/payphone.test.mjs` (frontend) | Desglose en centavos que siempre cuadra. Resolución de empresa en la confirmación, que falla cerrado. **Barrido del código fuente**: no hay `NEXT_PUBLIC_PAYPHONE`, ni `payphoneToken` fuera del formulario del emprendedor, ni lectura de `.payphoneToken` en respuestas, ni SDK `payphone.Button`/`api/button/js`, ni `console.*` con token o configuración. El barrido falla con la versión anterior del código (`HEAD`). | 15/15 |

**Resultados globales:**
- `mvnw test` del servicio `demo`, sin `LoginApplicationTests`: **49/49 OK**.
- `npm test` del frontend: **35/35 OK** (20 de C-01 y 15 de C-02).
- `tsc --noEmit`: sin errores.

**Pendiente de validar con Payphone (cuando se tenga el token):**
1. El contrato de `POST /api/button/Prepare` y `POST /api/button/V2/Confirm`: nombres de campos, `responseUrl`/`cancellationUrl` y si exige `storeId`. La URL base se puede cambiar con `PAYPHONE_API_BASE_URL`.
2. En producción, definir `PAYPHONE_RESPONSE_URL=https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion`, que coincide con la URL de respuesta registrada en el portal, y `PAYPHONE_CANCELLATION_URL`.
3. **Rotar el token actual.** Estuvo expuesto públicamente, así que debe considerarse comprometido. Hay que generar uno nuevo en Payphone Developer y registrarlo desde la pantalla del emprendedor.

**Pruebas manuales:**
- **MP-15.** `GET /api/emprendedor/configuracion-pagos/payphone/{empresaId}` sin token. Resultado esperado: `200 {"empresaId":…, "disponible":true|false}`, sin `payphoneToken` ni `payphoneAppId`.
- **MP-15b.** Como EMP: `GET /api/emprendedor/configuracion-pagos`. Resultado esperado: `payphone.tokenConfigurado=true`, sin token.
- **MP-15c.** `POST …/payphone/{empresaId}/preparar` sin `Authorization`. Resultado esperado: 401.
- **MP-15d.** Checkout con Payphone y el token de pruebas. Resultado esperado: en la pestaña Red no aparece ningún token; se redirige a `*.payphonetodoesposible.com`; al volver, la confirmación pasa por el backend.

**Siguen fuera del alcance:**
- **M-06:** el webhook recibe la notificación pero no la procesa.
- **A-03/A-04:** el monto se calcula a partir del carrito del navegador y no se valida contra una venta del servidor. La venta se crea después del pago, en `payphone-confirmacion`, y la llamada a `/completar` sigue dando 403 (regresión PayPhone ya documentada en `ESTADO-AVANCE-CRONOGRAMA-Y-PLAN-DE-CAMBIOS.md`). Para que el monto sea autoritativo, la venta debe crearse **antes** de preparar el cobro.
- **Cifrado en reposo:** el token se guarda en texto plano en `configuracion_metodos_pago.payphone_token`. Se recomienda cifrarlo en reposo, por ejemplo con un `AttributeConverter` y una clave de entorno.

### 5.0.7 Revisión de C-02 y límites para producción (2026-11-15)

La implementación revisada corrige la exposición del token en el navegador: el endpoint público ya no debe devolver `payphoneToken`, el formulario ya no usa el SDK con token en cliente y Prepare/Confirm se ejecutan desde el backend. La separación de responsabilidades es correcta y las pruebas documentadas cubren la no exposición.

Sin embargo, C-02 no debe marcarse como listo para producción hasta completar estas condiciones externas o pendientes de seguridad:

1. **Rotación:** el token históricamente expuesto debe considerarse comprometido y revocarse/generarse nuevamente en Payphone. No se puede completar sin que el administrador proporcione o registre la nueva credencial.
2. **Contrato Payphone:** sin token de pruebas no se puede confirmar que Prepare y V2/Confirm acepten exactamente los nombres, montos, moneda, URLs y formato de respuesta implementados. Esto queda bloqueado hasta disponer de credencial sandbox.
3. **URLs de retorno:** el servidor debe definir `PAYPHONE_RESPONSE_URL` y `PAYPHONE_CANCELLATION_URL` con URLs HTTPS registradas en Payphone Developer. No se debe depender del valor local por defecto en producción.
4. **Integridad del monto (A-03/A-04):** el backend valida que el desglose recibido sume, pero todavía acepta el desglose enviado por el navegador. Eso evita inconsistencias aritméticas, pero no evita que el cliente altere el precio. La venta debe existir o reservarse antes de Prepare y el backend debe calcular/validar el monto desde sus propios datos.
5. **Finalización y stock:** la venta se crea después de la confirmación y el flujo documenta un `403` en `/completar`; por ello no se debe declarar una compra Payphone completa hasta verificar stock, estado de venta y factura.
6. **Webhook M-06:** la ruta de notificación responde, pero no procesa la notificación. Un cierre del navegador o una interrupción posterior a Payphone puede dejar el pago sin registrar.
7. **Cifrado en reposo:** el token continúa almacenado en texto plano en `configuracion_metodos_pago.payphone_token`. La mitigación actual evita exponerlo por API, pero no protege una lectura directa de la base de datos o una copia de respaldo.

**Conclusión:** la corrección C-02 está preparada a nivel de diseño y código para validarse, pero el flujo Payphone no debe habilitarse en producción hasta rotar la credencial, validar el contrato sandbox y acordar explícitamente el plan para A-03/A-04, M-06 y el cifrado en reposo. Estos puntos no son fallos del ocultamiento del token; son pendientes separados de integridad, disponibilidad y gestión de secretos.

### 5.0.8 Diagnóstico integral y plan para cerrar PayPhone (2026-11-15)

El objetivo es dejar el código listo para que, después de registrar los comercios y proporcionar las credenciales, solo sea necesario configurar las variables y ejecutar la validación sandbox. El diagnóstico separa los pendientes de integración de los cambios de negocio que no deben ocultarse bajo C-02.

| Pendiente | Diagnóstico | Se puede resolver sin token | Condición de cierre |
|---|---|---:|---|
| Rotar token expuesto | El token histórico debe revocarse y reemplazarse. La rotación ocurre en Payphone Developer, no en el repositorio. | Parcial | Token anterior revocado, nuevo token registrado solo en backend y sin `NEXT_PUBLIC_`. |
| Contrato Prepare/Confirm | El código ya encapsula las llamadas, valida montos aritméticamente, fija URLs y sanea respuestas. Falta saber si Payphone acepta exactamente el contrato implementado. | Preparación sí; verificación no | Prueba sandbox exitosa de Prepare, redirección y V2/Confirm. |
| URLs de respuesta | El backend ya las toma de configuración, pero el servidor debe recibir las URLs HTTPS registradas en Payphone. | Sí | Variables configuradas y comprobadas en el entorno objetivo. |
| Monto autoritativo (A-03) | Prepare recibe el desglose enviado por el navegador. La suma puede ser correcta y aun así el cliente puede manipular el precio. | Sí | La venta se crea antes de Prepare y el backend calcula el total desde productos, empresa y envío oficiales. |
| Stock y `/completar` (A-04) | La venta PayPhone se crea después de confirmar; el flujo puede terminar con `403` y sin descuento de stock. | Sí | Un servicio interno autorizado completa la venta y descuenta stock exactamente una vez. |
| Webhook (M-06) | La ruta de notificación solo responde 200 y no reconcilia pagos. | Sí | Webhook autenticado/verificado, idempotente y capaz de actualizar una venta pendiente. |
| Token en reposo | El token sigue en texto plano en MySQL. Ocultarlo en API no protege una copia de BD o backup. | Sí, con decisión de clave | Cifrado reversible con clave fuera de BD, migración probada y lectura transparente para el backend. |

#### Solución técnica propuesta

1. **Crear una orden pendiente antes de salir del sistema.** El backend recibe el carrito, vuelve a consultar productos, stock, empresa y costo de envío, calcula el total oficial y crea una orden/venta `PENDIENTE_PAYPHONE` con un identificador interno. El navegador no puede elegir el precio final ni la empresa de destino.
2. **Preparar PayPhone desde la orden.** El endpoint de Prepare debe recibir solo el identificador de la orden o un DTO mínimo. El backend obtiene el monto oficial, genera el `clientTransactionId` asociado a la orden y guarda la relación `ordenId ↔ clientTransactionId ↔ paymentId`.
3. **Confirmar con doble validación.** Antes de marcar la orden como pagada, Confirm debe comprobar que la respuesta de PayPhone corresponda al `clientTransactionId`, empresa, moneda y monto de la orden. La confirmación debe ser idempotente para no duplicar ventas ni stock.
4. **Completar mediante autoridad interna.** El descuento de stock no debe depender del JWT del cliente. Debe ejecutarse dentro de un servicio backend autorizado, con una referencia idempotente por orden, y cambiar la venta a `COMPLETADA` solo si el inventario se descontó correctamente.
5. **Procesar notificaciones.** El webhook debe localizar la orden por `clientTransactionId`, verificar la notificación contra PayPhone o mediante firma/contrato oficial, y reutilizar la misma operación idempotente de confirmación.
6. **Cifrar credenciales.** Aplicar un `AttributeConverter` o servicio de cifrado a `payphone_token`, con una clave de cifrado exclusiva en el servidor. La clave nunca debe guardarse en MySQL, `.env` versionado, frontend ni logs. Antes de migrar, hacer respaldo controlado y probar lectura/escritura en local.

#### Alcance recomendado para cerrar ahora

Se puede cerrar el punto de seguridad C-02 cuando el código garantice que el token no sale del backend, el endpoint público no lo devuelve, los errores/logs no lo exponen y las pruebas automáticas lo comprueben. Para declarar **PayPhone listo para producción**, también deben quedar implementados el monto autoritativo, la finalización idempotente de venta/stock y la configuración de URLs.

El registro de comercios/emprendedores, la generación de credenciales, la rotación del token y la validación real contra PayPhone quedan como dependencias externas. No deben bloquear la preparación del código, pero sí deben permanecer como checklist de liberación.

#### Orden de implementación

1. Cerrar pruebas y revisión de C-02 sin credenciales: no exposición en API, frontend, bundle, logs ni errores.
2. Corregir A-03: crear la orden antes de Prepare y calcular el monto únicamente en backend.
3. Corregir A-04: completar venta y stock con autorización interna e idempotencia.
4. Implementar M-06: webhook/reconciliación segura.
5. Implementar cifrado en reposo del token.
6. Configurar `PAYPHONE_RESPONSE_URL`, `PAYPHONE_CANCELLATION_URL` y `PAYPHONE_API_BASE_URL` sin versionar secretos.
7. Cuando el administrador entregue las credenciales, rotar el token comprometido, registrar los comercios y ejecutar Prepare/Confirm en sandbox.
8. Ejecutar una prueba controlada de extremo a extremo y cerrar PayPhone dejando como pendientes únicamente las operaciones administrativas futuras.

### 5.0.9 Cierre de A-03, A-04, M-06 y cifrado en reposo (2026-11-15, sin commit)

**Estado:** se completaron en código los pasos 1 a 5 del orden de implementación de la sección 5.0.8, y el paso 6 quedó preparado como variables de entorno. Los pasos 7 y 8 necesitan credenciales de Payphone y el sistema levantado.

**Hallazgo nuevo corregido antes de A-03.** `PagoService.crearPago` aprobaba automáticamente los pagos con `TARJETA` y marcaba la venta como `COMPLETADA` en el momento de crearla, sin un cobro verificado y sin descontar stock. Un comprador podía obtener una venta "pagada" con solo crearla con ese método. Además, crear la orden antes de cobrar (A-03) la habría aprobado de inmediato. Ahora todo pago nace `PENDIENTE`, y nunca se crea una segunda fila de pago para la misma venta.

**Arquitectura resultante.** El token sigue sin salir de `auth-service` (C-02).

1. El checkout crea la venta `PENDIENTE` con `POST /api/ventas`. `msvc-ventas` toma los precios de `msvc-producto` y el costo de envío del servicio de configuración. El navegador no decide ningún importe.
2. `POST /api/emprendedor/configuracion-pagos/payphone/ventas/{ventaId}/preparar` (usuario autenticado) hace lo siguiente:
   - `auth-service` lee la venta por el canal interno.
   - Verifica que el usuario sea el comprador (por el `email` del JWT), que la venta esté `PENDIENTE` y que su método sea `TARJETA`.
   - Calcula el desglose en centavos a partir de `subtotal`, `impuesto` y `total`; el envío es `total − subtotal − impuesto`.
   - Genera `clientTransactionId = FD-{ventaId}-{aleatorio}` y llama a `Prepare` con el token de la empresa de la venta.
3. `POST …/payphone/confirmar` (retorno del navegador) y `POST …/payphone/notificacion` (webhook, público) usan el mismo procedimiento:
   - Obtienen el `ventaId` del `clientTransactionId` y leen la venta.
   - Llaman a `V2/Confirm` con el token de la empresa.
   - Verifican que el `clientTransactionId` devuelto coincida, que el estado sea `statusCode=3` y que `amount` sea igual al total de la venta en centavos.
   - Solo entonces llaman a `POST /api/ventas/internal/pagos-pasarela/{ventaId}/aprobar`.
4. `msvc-ventas` registra el pago con el método idempotente `PagoPasarelaService.registrarPagoAprobado`:
   - Lee la venta con `SELECT … FOR UPDATE`, así que el retorno del navegador y el webhook se procesan uno tras otro.
   - Comprueba de nuevo el monto contra el total.
   - Si la misma transacción ya se registró, responde `YA_PROCESADA` sin repetir nada.
   - Si llega otra transacción para una venta ya pagada, o un pago para una venta cancelada, lo rechaza y lo registra en el log como "requiere reembolso".
   - Descuenta stock una sola vez. Si inventario falla, deja el pago `APROBADO` y la venta `PENDIENTE` (`STOCK_PENDIENTE`), y un reintento con la misma transacción solo reintenta el descuento.
   - La factura se genera después del commit (sin `new Thread` + `sleep`).

**Autorización interna (A-04).** `auth-service` emite una credencial de servicio de 2 minutos (`ServicioTokenProvider`): `scope=ROLE_SERVICIO_PAGOS`, `tipo=servicio`, sin `email` ni `empresaId`, firmada con el mismo `JWT_SECRET` que ya validan los servicios.
- `msvc-ventas` solo acepta los endpoints `/api/ventas/internal/**` con ese rol **y** ese claim, mediante `isServicioPagos`. Ningún token de usuario sirve, ni siquiera el de ADMIN.
- `FeignConfig` reenvía esa credencial a inventario, y `salida-lote` la acepta con `isServicioPagos`. Esto resuelve el 403 que antes provocaba el token del comprador.
- El Gateway devuelve 404 para `^~ /api/ventas/internal` desde el exterior. `auth-service` llama directamente a `MSVC_VENTAS_URL`.
- `PUT /api/ventas/{id}/completar` (EMP/ADMIN) usa el mismo bloqueo de fila, para que no se cruce con la pasarela.

**Webhook (M-06).**
- `app/api/payphone/confirm/route.ts`, que es la URL registrada en el portal, reenvía la notificación al backend. Responde 200 si se procesó o ignoró, 400 si el cuerpo es inválido y 503 si un servicio no está disponible, para que Payphone reintente.
- El contenido de la notificación **nunca se da por cierto**: solo se usan `transactionId` y `clientTransactionId` para volver a consultar a Payphone. Una notificación falsificada no puede completar una venta.
- Los `clientTransactionId` que no pertenecen a la tienda se ignoran.
- Así, la venta se registra aunque el comprador cierre el navegador.

**Cifrado en reposo:**
- `PayphoneTokenCifradoConverter` usa AES-256-GCM, con un IV aleatorio por valor y un dato autenticado ligado a la columna. El valor se guarda con el formato `enc:v1:<Base64>`.
- La clave viene de `PAYPHONE_TOKEN_ENC_KEY` (Base64 de 32 bytes) y solo existe en el servidor.
- El sistema falla cerrado: sin clave no se pueden guardar ni leer tokens cifrados, y una clave mal formada impide arrancar.
- Al iniciar, `PayphoneTokenMigracion` cifra los tokens heredados en texto plano. Es idempotente y no registra valores.

**Frontend:**
- `PayphoneForm` crea la venta y llama a `preparar(ventaId)` sin montos. Si el comprador reintenta con el mismo carrito (misma firma `producto:cantidad`), reutiliza la venta pendiente.
- La página de confirmación ya no llama a `processCheckout` ni a `/completar`. Da el pedido por registrado solo si `statusCode=3` y `resultadoVenta` es `COMPLETADA`, `YA_PROCESADA` o `STOCK_PENDIENTE`. Ante `MONTO_NO_COINCIDE` o `REQUIERE_REVISION` indica no volver a pagar y contactar a soporte.

**Pruebas:**

| Suite | Casos clave | Resultado |
|---|---|---|
| `demo` `PayphonePagoServiceTest` | Monto desde la venta (con y sin IVA, e importes inconsistentes). Solo el comprador puede preparar. Una venta que no está pendiente o con otro método no se prepara. Empresa sin Payphone. Enlaces ajenos descartados. Confirmación aprobada con lista blanca. Monto distinto o `clientTransactionId` distinto: no se registra. Cancelación: no toca la venta. Rechazo de la venta: `REQUIERE_REVISION`. Ventas caído: se propaga para reintentar. La notificación verifica con Payphone. Una notificación falsa no completa nada. Notificación ajena o malformada. | 19/19 |
| `demo` `PayphoneTokenCifradoTest` | Ida y vuelta sin texto plano. IV distinto en cada cifrado. Dato alterado o clave incorrecta: falla sin mostrar datos. Sin clave: falla cerrado. Clave mal formada. Valor heredado. Migración selectiva, y sin clave no hace nada. | 8/8 |
| `demo` `VentasInternoClientTest` | Credencial de servicio válida, de 2 minutos y sin datos de usuario. Monto en centavos. 404 → no existe; 4xx → rechazo; 5xx → reintentable. | 3/3 |
| `demo` `ConfiguracionPagosControllerPayphoneTest` | Preparar por `ventaId` con el email del JWT. Venta ajena → 403. Cuerpo inválido → 400. Webhook → 200 sin credenciales. Ventas caído → 503. | 10/10 |
| `msvc-ventas` `PagoPasarelaServiceTest` | Completa la venta y descuenta stock una sola vez. Idempotencia. Otra transacción → rechazo. Monto distinto → no registra ni descuenta. Venta cancelada. Fallo de inventario → `STOCK_PENDIENTE` y luego reintento exitoso. Reutiliza el pago existente. 404. Resumen. Conversión a centavos. | 10/10 |
| `msvc-ventas` `PagoServiceCrearPagoTest` y `VentaAuthorizationServiceTest` (2 casos nuevos) | `TARJETA` nace `PENDIENTE`. No se duplica el pago. Credencial de servicio aceptada; un usuario (incluido ADMIN) o un rol sin `tipo` se rechazan. | OK |
| `msvc-inventario` `InventarioServicioPagosAuthorizationTest` | Credencial de servicio aceptada; usuarios o claim distinto se rechazan. | 2/2 |
| Frontend `payphone.test.mjs` | Firma del carrito y reutilización de la venta. `ventaId` desde `clientTransactionId`. Criterio de pedido registrado. El barrido confirma que `preparar` no envía montos y que la confirmación no llama a `processCheckout` ni a `completarVenta` (además de las comprobaciones de C-02). | 33/33 en total |

**Resultados globales:**

| Suite | Resultado |
|---|---|
| `demo` (sin `LoginApplicationTests`) | 73/73 |
| `msvc-ventas` | 53/53 |
| `msvc-inventario` | 20/20 |
| `npm test` (frontend) | 33/33 |
| `tsc --noEmit` | sin errores |

**Configuración del servidor (paso 6):**

| Variable | Servicio | Valor en producción |
|---|---|---|
| `PAYPHONE_TOKEN_ENC_KEY` | auth-service | `openssl rand -base64 32`; guardarla fuera del repositorio. **Si se pierde, los tokens deben registrarse de nuevo.** |
| `MSVC_VENTAS_URL` | auth-service | URL interna de msvc-ventas (no el Gateway). |
| `PAYPHONE_RESPONSE_URL` | auth-service | `https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion` |
| `PAYPHONE_CANCELLATION_URL` | auth-service | `https://tienda.gadmsigchos.gob.ec:444/checkout` |
| `PAYPHONE_API_BASE_URL` | auth-service | `https://pay.payphonetodoesposible.com` (o la base de pruebas que indique Payphone). |
| `GATEWAY_INTERNAL_URL` | frontend (Next) | URL interna del Gateway, para reenviar el webhook. |

**Pendiente (pasos 7 y 8, requieren credenciales y sistema levantado):**
1. **Rotación:** revocar el token expuesto en Payphone Developer y registrar el nuevo desde la pantalla del emprendedor, que ya lo guarda cifrado.
2. **Sandbox:** validar el contrato de `Prepare` y `V2/Confirm` (nombres de campos, `reference`, si exige `storeId`) y el **formato real del cuerpo del webhook**. El código acepta `transactionId`/`TransactionId`/`id` y `clientTransactionId`/`ClientTransactionId`; si Payphone exige un cuerpo de respuesta específico, se ajusta en `notificacionPayphone`.
3. **Prueba de extremo a extremo** con MP-16:
   - **MP-16a.** Compra con Payphone. Resultado esperado: la venta pasa a `COMPLETADA`, el stock se descuenta una vez, se genera la factura y el pago queda `APROBADO` con la referencia de Payphone.
   - **MP-16b.** Pagar y cerrar el navegador antes de volver. Resultado esperado: el webhook completa la venta.
   - **MP-16c.** Recargar la página de confirmación. Resultado esperado: `YA_PROCESADA` y el stock no cambia.
   - **MP-16d.** `GET https://<gateway>/api/ventas/internal/pagos-pasarela/1` desde el exterior. Resultado esperado: 404.
   - **MP-16e.** Con la base de datos de prueba, comprobar que `configuracion_metodos_pago.payphone_token` empieza con `enc:v1:`.

**Límites conocidos, documentados y no bloqueantes:**
- Las ventas `PENDIENTE` que se abandonan no reservan stock y quedan para que el emprendedor las cancele.
- Si el commit de `msvc-ventas` fallara después de descontar inventario, un reintento podría descontar de nuevo. Es un caso raro; la mitigación completa requiere idempotencia en inventario por referencia.
- Stripe sigue oculto (D-01) y no usa este flujo.

### 5.0.10 Cierre técnico y subtarea derivada PayPhone (2026-11-15)

**Veredicto de revisión:** la sección 5.0.9 es coherente con los cambios del código revisado. El flujo ya no depende de montos enviados por el navegador, la venta se crea antes de Prepare, la confirmación valida la transacción y el monto, el stock se procesa mediante credencial interna, el webhook reconcilia el pago y el token se cifra en reposo. Los archivos principales revisados no presentan errores en el analizador del proyecto y `git diff --check` no reporta errores de contenido en los archivos de PayPhone; el aviso de línea vacía corresponde a `info/GUIA-EJECUTAR-PRUEBAS-AHORA.md`.

**Cierre técnico:** se puede cerrar la implementación de PayPhone en código como preparada para validación externa. No se debe marcar como validación operativa definitiva hasta ejecutar sandbox y una prueba controlada, porque esas comprobaciones requieren credenciales reales de prueba y el sistema levantado.

#### Subtarea derivada: rotación, registro y validación externa PayPhone

**Objetivo:** dejar PayPhone habilitable sin cambios adicionales de código, una vez que el administrador registre los comercios y entregue las credenciales.

**Responsables externos:** administrador/Payphone Developer para revocar y generar credenciales, registrar comercios y autorizar las URLs.

**Pasos:**

1. Revocar el token históricamente expuesto.
2. Crear o registrar cada comercio/emprendedor en Payphone Developer.
3. Registrar la URL de respuesta y cancelación HTTPS aprobadas.
4. Generar `PAYPHONE_TOKEN_ENC_KEY` una sola vez y guardarla fuera del repositorio.
5. Registrar el nuevo token desde la pantalla del emprendedor; no pegarlo en documentos, tickets, capturas ni chat.
6. Configurar `PAYPHONE_RESPONSE_URL`, `PAYPHONE_CANCELLATION_URL`, `PAYPHONE_API_BASE_URL`, `MSVC_VENTAS_URL` y `GATEWAY_INTERNAL_URL` en el entorno correcto.
7. Reiniciar los servicios necesarios y comprobar que el token almacenado comienza con `enc:v1:` sin mostrar su contenido.
8. Ejecutar MP-15, MP-15b, MP-15c y MP-16a a MP-16e.
9. Confirmar que no aparece el token en Network, bundle frontend, logs ni respuestas HTTP.
10. Registrar estado, fecha, comercio probado, códigos HTTP y resultado sin guardar credenciales ni datos completos de pago.

**Criterio de cierre:** PayPhone queda cerrado cuando la rotación fue confirmada, las URLs fueron aceptadas, Prepare/Confirm y webhook funcionan en sandbox, la venta queda `COMPLETADA`, el stock se descuenta una sola vez, la factura existe y una repetición devuelve `YA_PROCESADA` sin duplicar efectos.

**Pendientes que no deben confundirse con esta subtarea:** Stripe, reservas de stock abandonadas e idempotencia distribuida de inventario son trabajos separados. El token de PayPhone no debe considerarse operativo hasta completar esta subtarea.

### 5.0.11 Diagnóstico de autorización A-01 y A-02 (2026-11-15)

## A-01: un emprendedor puede alterar inventario de otra empresa

**Estado actual:** el hallazgo sigue abierto.

`InventarioAuthorizationService.canManageProduct` ya compara `empresaId` del JWT con la empresa del producto y se utiliza para actualizar stock directo y `salida-lote`. Sin embargo, estos métodos de `InventarioController` no tienen el mismo control de propiedad:

- `POST /api/inventarios`;
- `POST /api/inventarios/producto/{productoId}`;
- `PUT /api/inventarios/{id}`;
- `DELETE /api/inventarios/{id}`.

El control de rol general puede permitir `ROLE_EMP`, pero no garantiza que el producto o inventario pertenezca a la empresa del emprendedor. Además, el registro de inventario inicial funciona como *upsert* y puede sobrescribir la cantidad existente. El riesgo permanece aunque la interfaz oculte botones, porque las peticiones se pueden construir directamente contra la API.

**Corrección propuesta:**

1. Para crear inventario, validar `canManageProduct(authentication, requestDto.productoId)` antes de guardar.
2. Para crear por ruta, validar el `productoId` antes de delegar al método general.
3. Para actualizar o eliminar por `inventarioId`, consultar primero el registro, obtener su `productoId` y validar la propiedad con el mismo servicio.
4. Mantener el acceso de `ADMIN` para todas las operaciones.
5. Separar "inventario inicial" de "actualización de stock" o definir expresamente que el inicial solo puede ejecutarse cuando la cantidad existente sea cero; nunca sobrescribir silenciosamente stock de una empresa.
6. Registrar el usuario real desde el JWT y el delta del movimiento, no un usuario fijo ni la cantidad total como movimiento de entrada.
7. Mantener las validaciones en servicio de dominio además de `@PreAuthorize`, para que no dependan exclusivamente del controlador.

**Pruebas necesarias:** con `EMP_A`, `EMP_B`, un producto y un inventario de cada empresa:

- `EMP_A` sobre producto propio: permitido.
- `EMP_A` sobre producto de B: `403` en POST, PUT y DELETE.
- `EMP_B` intentando registrar inventario inicial para producto de A: `403` y stock de A sin cambios.
- `ADMIN` sobre ambos productos: permitido.
- Repetir las pruebas directamente y mediante Gateway.
- Verificar que un intento rechazado no genere movimiento ni altere auditoría.

## A-02: el emprendedor puede reactivar la empresa o modificar el RUC

**Estado actual:** la propiedad de la empresa sí se valida, pero la separación de campos administrativos no está implementada.

`EmpresaController` exige `ROLE_ADMIN` o `ROLE_EMP` y `canManageCompany` valida que el emprendedor opere sobre su empresa. No obstante, `EmpresaMapper.toEntity` asigna `activo=true` cuando el campo no llega, y `EmpresaServiceImpl.actualizarEmpresa` copia tanto `ruc` como `activo`. Por ello:

- una edición normal del perfil puede reactivar una empresa desactivada por el administrador;
- un emprendedor propietario puede cambiar el RUC enviando otro valor;
- ocultar esos campos en el frontend no es suficiente.

**Corrección propuesta:**

1. Crear un DTO de actualización de perfil para `ROLE_EMP` con solo nombre, dirección, teléfono y email.
2. Usar un DTO administrativo separado para `ruc` y `activo`, exclusivo de `ROLE_ADMIN`.
3. En el servicio, actualizar por lista blanca de campos; nunca copiar un DTO completo sobre la entidad.
4. Si se conserva un endpoint PUT compartido, ignorar `ruc` y `activo` para EMP y preservar siempre sus valores existentes.
5. Eliminar el valor predeterminado `activo=true` para actualizaciones; ese valor solo debe existir al crear una empresa nueva.
6. Validar unicidad/formato del RUC en backend y registrar quién hizo el cambio administrativo.

**Pruebas necesarias:**

- ADMIN desactiva empresa A; EMP_A edita teléfono/email sin enviar `activo`: la empresa permanece inactiva.
- EMP_A intenta enviar `activo=true`: el estado permanece inactivo o la API responde `403`/`400` según el contrato elegido.
- EMP_A intenta cambiar el RUC: `403`/`400` y RUC sin cambios.
- ADMIN cambia estado y RUC: permitido y auditado.
- EMP_A intenta editar empresa B: `403`.
- Repetir directamente y por Gateway, verificando que no haya cambios parciales.

**Orden recomendado:** corregir primero A-01 porque permite manipulación directa de stock de terceros; después A-02 porque afecta la autoridad administrativa y los datos tributarios. En ambos casos, la seguridad debe quedar aplicada en backend y acompañada por pruebas de autorización, sin confiar en la interfaz.

### 5.0.12 Corrección aplicada A-01 y A-02 (2026-11-15, sin commit)

Atiende todos los puntos de la sección 5.0.11, incluidos el punto 7 de A-01 (validación en el dominio) y el punto 6 de A-02 (RUC único y auditoría). Para A-02 se eligió el contrato que **responde 400**: un intento de cambiar el RUC o el estado no se ignora en silencio, sino que se rechaza.

#### A-01: inventario entre empresas

**1. Autorización en el controlador (`@PreAuthorize`)**

| Endpoint | Antes | Ahora |
|---|---|---|
| `POST /api/inventarios` | Cualquier EMP | ADMIN, o EMP dueño del `productoId` (se consulta la empresa del producto en msvc-producto) |
| `POST /api/inventarios/producto/{productoId}` | Cualquier EMP | ADMIN, o EMP dueño de `{productoId}` |
| `PUT /api/inventarios/{id}` | Cualquier EMP | ADMIN, o EMP dueño del producto **guardado** en el inventario `{id}` (`canManageInventario`). El `productoId` del cuerpo no se usa para autorizar. |
| `DELETE /api/inventarios/{id}` | Cualquier EMP | Igual que `PUT` |
| `POST /api/movimientos` (**hallazgo adicional**) | Cualquier EMP, y el `usuarioId` venía del cuerpo | ADMIN, o EMP dueño del inventario; el autor siempre se toma del JWT |

Si un EMP no tiene `empresaId` en el token, o el inventario no existe, la operación se rechaza (403).

**2. Defensa en profundidad en el dominio.** `InventarioServiceImpl.exigirPropiedad` se aplica en `registrarInventario`, `actualizarInventario`, `eliminar` y `actualizarStock` (y por tanto en cada ítem de `salida-lote`). Solo permite ADMIN, la credencial interna de servicio de pagos, o el EMP dueño del producto. Sin autenticación tampoco se escribe. En `salida-lote`, un producto ajeno responde 403 (no 500) y revierte el lote completo.

**3. Inventario inicial sin sobrescritura:**
- Solo fija la cantidad si el producto no tiene inventario, o si tiene el marcador automático (stock 0 y sin movimientos).
- En cualquier otro caso responde **409** ("usa el ajuste de stock").
- Exige que el producto exista; si msvc-producto no responde, falla cerrado.

La edición (`PUT /{id}`) no permite reasignar el inventario a otro producto (400).

**4. Movimientos con el delta y el usuario real:**
- Se eliminó el `usuarioId = 1L` fijo: ahora se toma del claim `userId` del JWT (`null` para la credencial de servicio).
- `AJUSTE` guarda el delta con signo (`nuevo − anterior`) y el motivo incluye `(anterior -> nuevo)`. Antes guardaba el total.
- `ENTRADA` y `SALIDA` guardan la cantidad movida; el registro inicial es una `ENTRADA`.
- Un delta de 0 no genera movimiento.

**5. Dependencia corregida en msvc-producto.** Al crear un producto, el inventario con stock 0 se pedía **dentro** de la transacción, antes del commit. Con la validación de propiedad, inventario no habría encontrado el producto. Ahora se crea en `afterCommit`.

#### A-02: RUC y estado administrativos

| Endpoint | Rol | Campos |
|---|---|---|
| `PUT /api/empresas/{id}` | **Solo ADMIN** (antes también EMP) | Nombre, RUC, dirección, teléfono, email y estado activo. Si `activo` no se envía, se conserva el estado actual. |
| `PUT /api/empresas/{id}/contacto` (nuevo) | ADMIN, o EMP dueño | Solo nombre, dirección, teléfono y email (`EmpresaContactoRequestDto`) |

Detalle de la corrección:
- Las dos actualizaciones usan **lista blanca** de campos y nunca copian la entidad recibida. `actualizarDatosContacto` no toca el RUC ni el estado.
- `EmpresaMapper` ya no fija `activo = true` por defecto. Al crear una empresa, el servicio sigue fijando `activo = true`.
- `EmpresaContactoRequestDto` rechaza con **400** cualquier propiedad extra (`ruc`, `activo`, `id`…) mediante `@JsonAnySetter` + `@AssertTrue`. No depende de la configuración global de Jackson, que ignora propiedades desconocidas.
- **RUC único:** al crear y al actualizar se comprueba que ninguna otra empresa lo use, y se responde 400 con un mensaje claro. Antes un duplicado chocaba con la restricción `unique` de la BD y devolvía 500. El formato de 13 dígitos ya se validaba en el DTO.
- **Auditoría:** los cambios de RUC o estado se registran en el log `auditoria.empresas` con el `userId` del JWT, el id de la empresa, si cambió el RUC y el estado anterior y nuevo. No se registran otros datos.
- `SecurityConfig` de msvc-producto también exige autenticación a nivel de URL para `PUT /api/empresas/*/contacto`.
- Frontend:
  - La pantalla del emprendedor usa `actualizarDatosContactoEmpresa`, sin RUC ni estado.
  - El RUC y el interruptor de estado se muestran deshabilitados, con el texto "Solo el administrador…".
  - La pantalla del administrador sigue usando la actualización completa.

#### Pruebas

| Suite | Casos | Resultado |
|---|---|---|
| `InventarioPropiedadEscriturasTest` (`@WebMvcTest` con la seguridad real) | `POST` ajeno/propio/ADMIN. `POST` por ruta ajena. EMP sin `empresaId`. `PUT` ajeno disfrazado con `productoId` propio en el cuerpo, `PUT` propio, `PUT` inexistente. `DELETE` ajeno/propio/ADMIN, sin token, como cliente. En los rechazos se verifica que el servicio no se invoca. | 12/12 |
| `MovimientoPropiedadTest` | Movimiento sobre inventario ajeno → 403. El movimiento propio guarda el `userId` del JWT, no el del cuerpo. | 2/2 |
| `InventarioServiceMovimientosTest` | Registro inicial con stock → 409 sin guardar. Con historial y stock 0 → 409. Marcador → `ENTRADA` con el usuario real. Producto inexistente: EMP → 403, ADMIN → 400, sin guardar nada. `AJUSTE 10→4` → −6. `SALIDA` sin stock no cambia nada. Credencial de servicio → usuario `null`. La edición no reasigna producto. Delta en la edición. **Dominio:** las cuatro escrituras sobre un producto ajeno invocadas directamente → 403 sin guardar. `salida-lote` con un producto ajeno → 403. Sin autenticación no se escribe. | 13/13 |
| `ProductoControllerApiAuthorizationTest` | EMP actualiza el contacto de su empresa con solo esos campos. EMP no puede tocar otra empresa. `ruc`, `activo` o `id` en el contacto → 400 sin llamar al servicio. EMP en `PUT /{id}` → 403 incluso sobre su propia empresa. ADMIN → 200. Sin token → 401. | 12/12 |
| `EmpresaListaBlancaTest` | El contacto no cambia RUC ni estado (una empresa inactiva no se reactiva). ADMIN sin `activo` conserva el estado. ADMIN puede cambiarlo explícitamente. RUC duplicado al actualizar o crear → 400 sin guardar. El mapper ya no pone `true` por defecto. | 6/6 |
| Frontend (`payphone.test.mjs`, bloque A-02) | La pantalla del EMP usa solo `/contacto` y no edita RUC ni estado. El método de contacto no envía `ruc` ni `activo`. | 35/35 en total |

**Resultados globales:**

| Suite | Resultado |
|---|---|
| `msvc-inventario` | 47/47 |
| `msvc-producto` | 42/43 (ver nota) |
| `npm test` | 35/35 |
| `tsc --noEmit` | sin errores |

Nota: la prueba de `msvc-producto` que falla es `MsvcProductoApplicationTests`, que carga el contexto completo y necesita MySQL ("Communications link failure"). Ya fallaba antes de estos cambios.

**Pruebas por Gateway.** El script nuevo `Documentos_Jordy/pruebas-a01-a02-gateway.ps1` (sintaxis verificada; no se ejecutó porque el sistema no está levantado) funciona así:
- Ejecuta **solo intentos que deben ser rechazados**, así que no modifica datos.
- Antes y después compara la cantidad, el estado y el número de movimientos de los inventarios propio y ajeno, y el RUC, estado y contacto de la empresa. Así comprueba que los rechazos no dejan cambios parciales.
- También verifica que `/api/ventas/internal/**` responde 404 desde el exterior.
- Pide las contraseñas de forma oculta y no imprime tokens.
- Se ejecuta en local con `.\Documentos_Jordy\pruebas-a01-a02-gateway.ps1 -Gateway http://localhost:8090`.

Los casos con efectos (EMP_A sobre su propio producto, ADMIN cambiando RUC o estado) se validan con las pruebas manuales de la sección 5.5, en la base local.

**Efectos en el uso:**
- Si un emprendedor intenta "crear inventario" para un producto que ya tiene stock, ahora recibe 409 y debe usar "Ajustar stock". El flujo normal no cambia: al crear el producto se genera el marcador con 0, y el primer registro fija el stock inicial.
- Los movimientos de tipo `AJUSTE` antiguos guardaban el total, no el delta. Los reportes que mezclen ambos periodos deben tenerlo en cuenta.

### 5.1 Preparación

- **Rama:** `integration/version-estable-deuna-pulido`. Se levanta según `GUIA-LEVANTAMIENTO-SISTEMA.md`, con el `.env` cargado. Redis queda desactivado salvo en la sección 5.9.
- **Solo en local.** Ninguna de estas pruebas debe hacerse contra producción: crean ventas, pagos, facturas y movimientos.
- **Usuarios necesarios:**

| Alias | Rol | Dato |
|---|---|---|
| `ADMIN` | `ROLE_ADMIN` | — |
| `EMP_A` | `ROLE_EMP` | Empresa A, con QR Deuna y cuenta bancaria configurados |
| `EMP_B` | `ROLE_EMP` | Empresa B, **sin** QR Deuna ni cuenta bancaria |
| `CLI_1`, `CLI_2` | `ROLE_USER`/`ROLE_CLIENTE` | Clientes distintos |

- **Obtener un token:** `POST http://127.0.0.1:8090/api/auth/login` con `{ "username", "password" }`. Guardarlo en una variable (`$tA`, `$tB`, `$tAdm`, `$tC1`). No pegarlo en documentos.
- **Registrar por cada caso:** fecha, usuario, petición, código HTTP, fragmento de respuesta (sin tokens) y resultado (OK, FALLA o HALLAZGO CONFIRMADO).

### 5.2 Compra autenticada y separación pública/privada (A-23, A-24, A-25)

| ID | Pasos | Resultado esperado |
|---|---|---|
| MP-01 | Abrir `/products` sin sesión. | Se ven productos, precios e imágenes. |
| MP-02 | Sin sesión, agregar al carrito e ir al checkout. | Se pide iniciar sesión; no se crea ninguna venta. |
| MP-03 | `POST /api/ventas` sin token. | 401. |
| MP-04 | `POST /api/ventas` con un token alterado (cambiar un carácter de la firma). | 401. |
| MP-05 | `GET /api/ventas` con `$tC1`. | 403 (solo admin). |
| MP-06 | `GET /api/ventas/cliente/{id de CLI_2}` con `$tC1`. | 403. |
| MP-07 | `GET /api/ventas/emprendedor/{empresa B}` con `$tA`. | 403. |
| MP-08 | `GET /api/ventas/{id venta de CLI_1}` con `$tC1`, `$tA` (si la venta es de la empresa A) y `$tB`. | 200, 200 y 403. |
| MP-09 | `PUT /api/ventas/{id}/completar` con `$tC1`. | 403. |
| MP-10 | Ejecutar `pruebas-gateway-a14.ps1`. | Debe fallar en "GET /api/ventas (msvc-ventas)" (hallazgo B-01). Registrar y corregir el script. |

### 5.3 Checkout con transferencia y Deuna

| ID | Pasos | Resultado esperado |
|---|---|---|
| MP-11 | `CLI_1` compra un producto de la empresa A con **transferencia**. | Se muestran el banco y la cuenta reales de EMP_A (no cuentas de ejemplo). Se crea una venta `PENDIENTE` y un pago `PENDIENTE`. El carrito se vacía. |
| MP-12 | Igual que MP-11, pero con **Deuna**. | Se muestra el QR de EMP_A. Al enviar número de cuenta, referencia y comprobante opcional (máximo 5 MB), el pago queda `PENDIENTE`. |
| MP-13 | **Verifica C-01.** `CLI_1` compra un producto de la empresa B (sin QR) y elige Deuna. | **Esperado correcto:** mensaje de que el comercio no tiene QR. **Si aparece el QR de EMP_A → C-01 confirmado.** |
| MP-14 | **Verifica C-01 (bancos).** `GET /api/emprendedor/configuracion-pagos/bancarios/{empresaId B}` sin token. | **Esperado correcto:** 404. Si devuelve datos de otro emprendedor → confirmado. |
| MP-15 | Repetir MP-13 con un `empresaId` cuyo número coincida con el `emprendedor_id` de EMP_A. | Igual que MP-13 (prueba del Nivel 2). |
| MP-16 | Carrito con productos de las empresas A y B. | La venta se rechaza (regla de P4: una empresa por venta). Verificar que el mensaje sea claro en la interfaz. |
| MP-17 | Manipular `precioUnitario`/`costoEnvio` en la petición (herramientas del navegador). | Total calculado con el precio oficial y la tarifa configurada (P4). |

### 5.4 Aprobación de pagos, stock y factura

| ID | Pasos | Resultado esperado |
|---|---|---|
| MP-18 | Anotar el stock del producto. `EMP_A` aprueba el pago de MP-11 desde su panel. | Pago `APROBADO`, venta `COMPLETADA`; stock reducido **una sola vez** en la cantidad comprada; seguimiento logístico creado; factura PDF generada. |
| MP-19 | `EMP_B` intenta aprobar el pago de MP-12 (`POST /api/pagos/{id}/aprobar`). | 403. |
| MP-20 | Volver a aprobar el pago ya aprobado. | 400 ("solo pagos PENDIENTE"); el stock no cambia. |
| MP-21 | `EMP_A` cancela una venta `PENDIENTE`. | Venta `CANCELADA` y pago `RECHAZADO`; el stock no cambia. |
| MP-22 | Aprobar un pago cuando el stock disponible es menor que la cantidad. | La aprobación falla con un mensaje genérico; la venta sigue `PENDIENTE`; el stock no se modifica. |
| MP-23 | **Verifica A-04.** Comprar con tarjeta (Stripe en modo prueba) y confirmar. | Venta `COMPLETADA`. **Revisar el stock: si no bajó → A-04 confirmado.** |
| MP-24 | **Verifica A-05.** Aprobar 5 pagos seguidos rápidamente. | Todas las facturas existen y tienen datos completos. Registrar cualquier factura faltante o vacía. |

### 5.5 Inventario y permisos de emprendedor

| ID | Pasos | Resultado esperado |
|---|---|---|
| MP-25 | `PUT /api/inventarios/producto/{producto de A}/stock` con `$tB`. | 403 (sí está protegido). |
| MP-26 | **Verifica A-01.** `POST /api/inventarios` con `$tB` y body `{ "productoId": <producto de A>, "cantidad": 0 }`. | **Esperado correcto:** 403. **Si devuelve 200/201 y el stock de A queda en 0 → A-01 confirmado.** Restaurar el stock después. |
| MP-27 | **Verifica A-01.** `PUT /api/inventarios/{id inventario de A}` y `DELETE` con `$tB`. | Esperado correcto: 403. |
| MP-28 | `EMP_A` crea una categoría desde su panel. | Se crea y es visible para todos (B-02); EMP_A no puede editarla. |
| MP-29 | `EMP_A` edita los datos de contacto de su empresa. | 200. |
| MP-30 | `EMP_A` intenta editar la empresa B. | 403. |
| MP-31 | **Verifica A-02.** `ADMIN` desactiva la empresa A; luego `EMP_A` edita solo el teléfono desde su panel (sin tocar el estado). Repetir por API con `"activo": true` y otro `ruc`. | **Esperado correcto:** la empresa sigue inactiva y el RUC no cambia. Si se reactiva (también con la edición simple) o cambia el RUC → confirmado. |
| MP-32 | `EMP_A` registra el inventario inicial de un producto nuevo (que ya tiene un inventario automático con 0). | Se guarda la cantidad indicada. Revisar en movimientos que el registro tenga la cantidad correcta y anotar el `usuarioId` (fijo en 1). |

### 5.6 Paginación (A-19 a A-22)

| ID | Pasos | Resultado esperado |
|---|---|---|
| MP-33 | `GET /api/productos/listado/paginado`. | `pageSize` 12 y los 8 campos del contrato. |
| MP-34 | `...?size=500`, `?size=0`, `?page=-3`. | `pageSize` 50, el tamaño por defecto del endpoint y `pageNumber` 0, respectivamente. |
| MP-35 | `...?sort=passwordHash,asc` o un campo no permitido. | Se ignora y se aplica el orden por defecto, sin error 500. |
| MP-36 | En la vitrina, en los paneles de emprendedor y admin (productos, ventas, pagos, inventario, usuarios), cambiar de página con las herramientas de red abiertas. | Cada cambio hace **una** petición `/paginado` con `page` y `size`; no se descargan listas completas. |
| MP-37 | Filtrar por categoría desde el submenú del navbar. | `/products?categoria=<nombre>` aplica el filtro. |

### 5.7 Administración de usuarios

| ID | Pasos | Resultado esperado |
|---|---|---|
| MP-38 | `ADMIN` desactiva a `CLI_2` (con confirmación). | El usuario queda inactivo, no eliminado. |
| MP-39 | `CLI_2` intenta iniciar sesión. | 403 con el mensaje de cuenta desactivada. |
| MP-40 | **Verifica M-04.** Antes de MP-38, guardar un token de `CLI_2`; después de desactivarlo, usarlo en `GET /api/ventas/cliente/{id}`. | Registrar si sigue respondiendo 200 (limitación conocida) y hasta cuándo. |
| MP-41 | Con un solo admin activo, intentar desactivarlo, eliminarlo o quitarle el rol, **también por API** (`PUT /api/usuarios/{id}` quitando `ROLE_ADMIN`, `PATCH /api/usuarios/{id}/estado`, `DELETE /api/usuarios/{id}`). | La interfaz oculta los botones y la API responde 400 (`IllegalStateException` → `BAD_REQUEST`). |

### 5.8 Pulido de interfaz

| ID | Pasos | Resultado esperado |
|---|---|---|
| MP-42 | Exportar CSV de ventas (emprendedor y admin) con filtros activos. | Se descarga un `.csv` que abre bien en Excel (tildes y ñ); incluye todas las páginas filtradas. |
| MP-43 | Crear una venta cuyo nombre de cliente empiece con `=1+1` y exportar. | La celda muestra el texto literal, no una fórmula. |
| MP-44 | Abrir una ruta inexistente (`/checkout/fse`, `/xyz`). | Página 404 en español, sin "Application error". |
| MP-45 | Como EMP_A en la vitrina: el botón "Gestionar" aparece solo en productos de A y lleva a `/emprendedor/productos/{id}/editar`. Como ADMIN aparece en todos y lleva a `/administrador/...`. | Según lo descrito. |
| MP-46 | Formularios de checkout y de configuración de pagos: letras en la cuenta, cédula con 9 dígitos, teléfono inválido. | Se bloquean en la interfaz. **Repetir por API** (B-03) y registrar si el backend los acepta. |

### 5.9 Redis (opcional, solo con el entorno de pruebas)

| ID | Pasos | Resultado esperado |
|---|---|---|
| MP-47 | Con `CACHE_REDIS_ENABLED=false`: `GET /actuator/health/cache` (con ADMIN si aplica). | `UP` sin conexión a Redis. |
| MP-48 | Activar `CACHE_REDIS_ENABLED` y `CACHE_REDIS_PRODUCTOS_LISTADO_ENABLED`; llamar dos veces a `/api/productos/listado`. | La segunda es un *hit* (métrica `feriadigital.redis.cache.requests{result=hit}`). |
| MP-49 | Editar el precio de un producto y repetir `/listado`. | El precio nuevo aparece de inmediato (invalidación tras commit). |
| MP-50 | Detener Redis y llamar a `/listado`. | Responde desde MySQL (fallback); health `cache` = `DOWN` sin detalles; la aplicación sigue `UP`. |
| MP-51 | **Verifica M-01.** Con Redis activo, navegar por `/products` y revisar las métricas. | Si solo aumentan los *hits* al entrar a la portada (destacados) y no al paginar la vitrina → M-01 confirmado. |

---

## 6. Pendientes propuestos para la planificación

| Prioridad | Tarea | Hallazgos |
|---|---|---|
| 1 | Corregir la resolución de QR y datos bancarios (sin Nivel 2 ni Nivel 3; 404 si no existe) | C-01 |
| 1 | Revisar la exposición del token de Payphone y mover la operación al backend si corresponde | C-02 |
| 1 | Autorización por propiedad en todas las escrituras de inventario y movimientos; revisar el *upsert* | A-01 |
| 1 | Restringir los campos que un emprendedor puede editar de su empresa | A-02 |
| 2 | Validar monto, moneda y `venta_id` del PaymentIntent, con `paymentIntentId` único; implementar el webhook | A-03, M-06 |
| 2 | Unificar "completar venta" (transferencia, Deuna y tarjeta) con descuento de stock por credencial de servicio, idempotencia y compensación | A-04, M-02 |
| 2 | Generar facturas después del commit con un mecanismo asíncrono gestionado | A-05 |
| 3 | Decidir el alcance de Redis respecto a la vitrina paginada y actualizar A18 | M-01, B-07 |
| 3 | Proyección pública de empresas y movimientos | M-03, M-05 |
| 3 | Política para tokens de usuarios desactivados | M-04 |
| 4 | Actualizar el script A-14 con casos A-24 (token cliente, emprendedor y admin) | B-01 |
| 4 | Validaciones equivalentes en el backend | B-03 |
| 4 | Trazabilidad: renombrar en documentos los códigos A-19/A-20 reutilizados, corregir enlaces `file:///` y documentar la ejecución de los `*IT` de Redis | B-04, B-05, B-06 |
| 4 | Retomar el flujo del plan: ramas `etapa/*`, `develop`, `release/corte-*` y PR con revisión cruzada | §2 |

### 6.1 Estado y decisiones registradas (2026-11-15)

- **Rama cargada en local:** `integration/version-estable-deuna-pulido` (commit `432f92b`), rama local que sigue a `origin`. Se obtuvo por *fast-forward* lógico desde `integration/base-pre-redis`, sin conflictos y sin cambios de código. Ningún hallazgo se ha corregido todavía.
- **Verificación pendiente:** los hallazgos se confirmarán con la guía de la sección 5 cuando el sistema esté montado en local (MySQL, servicios, gateway y frontend).
- **C-02 (Payphone):** no se modifica en esta etapa. El botón de pago de Payphone del checkout (`app/(main)/checkout/components/PayphoneForm.tsx:209`) usa `payphoneToken` en el navegador, y la confirmación del servidor (`app/api/payphone/confirm/route.ts:56-68`) lo obtiene del mismo endpoint público. Ocultar el token dejaría sin funcionar el método Payphone, así que la solución debe planificarse junto con el rediseño de ese flujo.
- **A-04 (stock en pagos con tarjeta):** **pendiente por decisión del equipo.** Queda registrado para la documentación de cierre. La alternativa evaluada es mantener la venta `PENDIENTE` tras un pago con tarjeta aprobado y que el emprendedor la complete, lo que descuenta el stock con su token, igual que Deuna y transferencia. La otra opción es un token de servicio interno para que Ventas descuente automáticamente.

### 5.0.13 Verificacion de cierre A-01 y A-02 (2026-11-15)

Se contrasto la seccion 5.0.12 contra el codigo actual y contra los problemas del proyecto.

**A-01, propiedad del inventario:** verificado en codigo.

- InventarioController aplica @PreAuthorize a las cuatro operaciones de escritura revisadas: creacion general, creacion por producto, actualizacion y eliminacion.
- InventarioAuthorizationService resuelve la empresa del producto mediante el servicio de productos para las operaciones por productoId, y para PUT/DELETE obtiene primero el productoId persistido del inventario. No confia en el productoId enviado en el cuerpo.
- InventarioServiceImpl.exigirPropiedad repite la validacion en la capa de dominio para las escrituras, incluyendo la credencial interna de pagos, y rechaza al emprendedor de otra empresa.
- El registro inicial no sobrescribe un inventario que ya tiene historial o stock: responde 409. Los ajustes registran el delta con signo y el usuario del JWT.

**A-02, RUC/estado de la empresa:** verificado en codigo.

- PUT /api/empresas/{id} queda restringido a ROLE_ADMIN; es la unica ruta que modifica RUC y estado activo.
- PUT /api/empresas/{id}/contacto permite ADMIN o EMP dueno y solo actualiza nombre y datos de contacto. El DTO rechaza propiedades adicionales como ruc, activo o id.
- El servicio conserva activo cuando no se envia en una actualizacion administrativa y valida la unicidad del RUC antes de guardar.
- El mapper no establece activo=true por defecto durante una actualizacion; el valor inicial se fija unicamente al crear una empresa.

**Resultado:** las dos actividades pueden cerrarse tecnicamente en codigo. La documentacion de pruebas registra las suites especificas como exitosas (12/12, 2/2, 13/13 y 6/6), pero esos resultados se consideran evidencia documentada y no una nueva ejecucion realizada durante esta revision.

**Pendiente antes de commit final:** corregir los avisos de git diff --check en los archivos modificados, especialmente EmpresaController.java, y volver a ejecutar la comprobacion. No se detectaron errores en el analizador Java para los archivos revisados.

### 5.0.14 Estado de tareas 4 y 5 frente a Stripe (2026-11-15)

Se reviso el estado de las tareas relacionadas con pagos con tarjeta y generacion de facturas.

**Tarea 4, descuento de stock:** la nueva ruta generica de pago de pasarela (PagoPasarelaService) ya descuenta el stock mediante la credencial interna, utiliza bloqueo de la venta e idempotencia, y deja STOCK_PENDIENTE si el inventario no esta disponible. Esto cubre PayPhone y las pasarelas que utilicen esa ruta.

Sin embargo, el flujo antiguo de Stripe sigue presente en PagoService.procesarPagoTarjeta y StripeController: marca el pago y la venta como completados, pero no invoca el descuento de inventario. Por tanto, la tarea 4 no esta cerrada globalmente mientras /api/stripe/** siga habilitado.

**Tarea 5, factura:** la ruta nueva de pasarela usa TransactionSynchronizationManager para generar la factura despues del commit. El flujo antiguo de Stripe todavia contiene new Thread y Thread.sleep(100) para generar la factura. Por tanto, la sustitucion no es completa mientras el flujo Stripe permanezca activo.

**Decision recomendada si Stripe no se utilizara:** no implementar ni probar Stripe. Se debe desactivar o retirar completamente su superficie de ejecucion: endpoints /api/stripe/**, StripeController, StripeService, DTOs y dependencia/configuracion de Stripe, segun el alcance que apruebe el equipo. La ruta webhook no debe permanecer permitAll si el servicio no se utiliza. Despues de esa retirada, las tareas 4 y 5 se cierran para el medio de pago vigente (PayPhone) y Stripe queda documentado como no aplicable.

**Criterio de cierre:** no basta con ocultar Stripe en el frontend. Debe comprobarse que una peticion directa al backend no pueda ejecutar el flujo antiguo y que una busqueda de new Thread/Thread.sleep en codigo fuente no encuentre la implementacion de factura de pagos.

### 5.0.15 Cierre de tareas 4 y 5: Stripe inactivo y factura tras commit en todos los flujos (2026-11-15, sin commit)

Se aplicó la decisión recomendada en 5.0.14: Stripe queda **inactivo por decisión del proyecto, conservado para evaluación futura**. Además se corrigió el mismo patrón de factura en los flujos de transferencia y Deuna, que son los métodos que se usarán mientras no exista token de PayPhone.

**1. Stripe deshabilitado (backend, Gateway, configuración y frontend)**

| Capa | Cambio |
|---|---|
| Bandera | `stripe.enabled=${STRIPE_ENABLED:false}`; las claves pasan a ser opcionales (`${STRIPE_API_KEY:}`). |
| msvc-ventas | `StripeController` y `StripeService` con `@ConditionalOnProperty(stripe.enabled=true)`. Deshabilitado, ningún bean de Stripe existe y el servicio arranca sin claves. Si se activa sin `STRIPE_API_KEY`, el arranque falla. |
| Webhook | Se retiró `permitAll` de `/api/stripe/webhook` y el propio endpoint (respondía 200 sin verificar la firma). |
| Atajo de pago | Se eliminó `PagoService.procesarPagoTarjeta` (completaba la venta sin descontar stock y generaba la factura en un hilo con espera). Aunque se active la bandera, `confirm-payment` responde 501 hasta integrarse con `PagoPasarelaService`. |
| Gateway | Se eliminó `location /api/stripe`: la ruta cae en el 404 JSON por defecto. Se retiró la cabecera `Stripe-Signature` de CORS y del proxy. |
| Despliegue | `.env.example`, `docker-compose.yml` y `docker-compose.light.yml` ya no exigen claves de Stripe (antes `docker compose` fallaba sin `STRIPE_API_KEY`). |
| Frontend | Las dos páginas de checkout ya no importan `stripeService` ni `StripeCheckoutForm`, no tienen rama `TARJETA` y rechazan cualquier método distinto de transferencia o Deuna antes de crear la venta. PayPhone conserva su propio flujo. `StripeCheckoutForm.tsx` y `stripeService.ts` se conservan sin uso. |

Comportamiento verificado con Stripe deshabilitado: `/api/stripe/**` responde **401 sin token y 404 con token** (incluido ADMIN), sin invocar `StripeService` ni `PagoService`.

**2. Factura después del commit en todos los flujos (tarea 5)**

Nuevo componente `GeneradorFacturaTrasCommit`:
- Programa la factura en `afterCommit`; si hay rollback no se genera.
- La ejecuta en un ejecutor acotado propio (1–2 hilos, cola de 200, `CallerRunsPolicy`), sin bloquear la respuesta ni crear hilos sin límite.
- Un error al generar se registra y no afecta a la venta confirmada.

| Flujo | Antes | Ahora |
|---|---|---|
| `PagoService.aprobarPago` (transferencia / Deuna) | hilo + `sleep(100)` | `generadorFactura.programar` |
| `VentaServiceImpl.completarVenta` | hilo + `sleep(100)` | `generadorFactura.programar` |
| `PagoPasarelaService` (PayPhone) | `afterCommit` síncrono | mismo componente (ya no bloquea la respuesta) |
| `PagoService.procesarPagoTarjeta` (Stripe) | hilo + `sleep(100)` | eliminado |

Una búsqueda de `new Thread(` / `Thread.sleep` en `src/main/java` de los microservicios solo encuentra la fábrica de hilos del ejecutor acotado.

**3. Pago creado en la misma transacción que la venta**

`VentaApplicationService.crearVenta` creaba el pago PENDIENTE en un hilo con `sleep(500)`. Si fallaba, la venta quedaba sin pago y no aparecía en "pagos pendientes" del emprendedor. Ahora el pago se crea dentro de la transacción de la venta: si falla, la venta hace rollback. Un método desconocido sigue creando la venta sin pago, igual que antes.

**4. Correcciones adicionales encontradas durante el cambio**

- **Doble aprobación:** `aprobarPago` leía el pago sin bloquear la venta. Dos aprobaciones simultáneas (doble clic, dos pestañas o aprobación junto a `completarVenta`) podían descontar stock dos veces. Ahora bloquea la fila de la venta (`findByIdForUpdate`) antes de leer el pago, con la misma estrategia que `completarVenta` y `PagoPasarelaService`.
- **Venta cancelada:** aprobar el pago de una venta CANCELADA la dejaba COMPLETADA sin descontar stock. Ahora se rechaza.
- **Rutas inexistentes:** el `GlobalExceptionHandler` de ventas convertía en 500 las rutas inexistentes, los métodos no permitidos y los tipos de contenido no soportados. Ahora responde con su código 4xx (404/405/415).

**5. Pruebas**

| Suite | Casos | Verifica |
|---|---|---|
| `StripeDeshabilitadoTest` | 5 | Sin bandera o con `false` no hay beans; con `true` y sin clave falla el arranque; con clave se registra; ya no existen el webhook ni `procesarPagoTarjeta`. |
| `VentaControllerApiAuthorizationTest` (caso Stripe reescrito) | 1 | 401 sin token; 404 en webhook, create-payment-intent, confirm-payment (ADMIN) y payment-intent; sin interacción con servicios. |
| `GeneradorFacturaTrasCommitTest` | 4 | Solo tras commit; nada con rollback; inmediato sin transacción; un error no se propaga. |
| `PagoServiceAprobarPagoTest` | 5 | Bloqueo antes de leer el pago; segunda aprobación sin descuento; cancelada rechazada; otro emprendedor rechazado; fallo de inventario sin factura. |
| `VentaApplicationServicePagoSincronoTest` | 3 | Pago creado antes de responder; fallo del pago propaga la excepción (rollback); método desconocido sin pago. |
| `tests/stripeInactivo.test.mjs` (frontend) | 6 | Checkouts sin Stripe ni `TARJETA`; guardia antes de `processCheckout`; selectores sin opción Stripe. |

Resultados: **msvc-ventas 70/70** (incluye `MsvcVentasApplicationTests`, que arranca el contexto completo sin claves de Stripe), **frontend 41/41**, **`tsc --noEmit` sin errores**, `git diff --check` sin avisos de espacios.

**6. Estado resultante**

| Hallazgo | Estado |
|---|---|
| C-01 Deuna | Cerrado en código. |
| C-02 PayPhone | Cerrado en código; pendiente rotación y validación externa. |
| A-01 Inventario | Cerrado. |
| A-02 Empresa / RUC / activo | Cerrado. |
| A-03 Validación del pago | Cerrado para PayPhone; Stripe inactivo y sin ruta de aprobación. |
| A-04 Descuento de stock | Cerrado para PayPhone y para la aprobación manual (con bloqueo); Stripe inactivo. |
| A-05 Factura tras commit | Cerrado en todos los flujos activos (PayPhone, transferencia, Deuna, completar venta). |
| Pendientes operativos | Variables reales, credenciales, sandbox de PayPhone y pruebas de producción. |
| Redis | Siguiente etapa. |

**Efectos en el uso:**
- Si en el futuro se reactiva Stripe, no basta con `STRIPE_ENABLED=true`: hay que integrar `confirm-payment` con `PagoPasarelaService` (monto verificado, credencial interna e idempotencia) y reimplementar un webhook con verificación de firma.
- Aprobar el pago de una venta cancelada ahora responde 400.
- Una ruta inexistente en ventas responde 404 en lugar de 500.

### 6.2 Actualización posterior del código de estabilización

La rama actual incorpora cambios posteriores al corte descrito en esta sección. T01 está preparado para systemd y AlmaLinux; T07 optimiza la autorización de inventario en lote; T08 elimina los comodines CORS y configura los encabezados reenviados; T09 incorpora la plantilla de Gateway y sus límites; y T11 implementa la vinculación administrativa entre emprendedor y empresa con índice único y migración.

El administrador conserva acceso global sobre productos e inventarios por decisión del equipo. Las categorías permanecen restringidas a `ROLE_ADMIN`. Redis queda desactivado por defecto y se reserva para una etapa posterior. La reducción del TTL del access token a 15 minutos por defecto reduce la ventana de una vinculación anterior, pero no implementa revocación inmediata.

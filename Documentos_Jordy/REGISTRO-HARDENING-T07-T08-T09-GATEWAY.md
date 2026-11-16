# Registro de Cierre T07, T08 y T09 (Autorización de Movimientos/Categorías, Superficie y Gateway Nginx)

- **Rama de trabajo:** `feature/hardening-t07-t08-t09-gateway` (originada desde `integration/seguridad-pagos-redis-pulido`, commit base `4436668`)
- **Alcance:** Cierre de los puntos pendientes T07 (protección de `GET /api/movimientos/**` y creación de categorías solo para `ROLE_ADMIN`), T08 (deshabilitación/restricción de Swagger OpenAPI y eliminación de `@CrossOrigin` en controladores) y T09 (rate limiting `limit_req`, cabeceras HTTP de seguridad, bloqueos perimetrales y plantilla Nginx lista para producción).

---

## 1. Cambios Implementados

### 1.1 T07 — Autorización en consultas de movimientos (`msvc-inventario`) y categorías (`msvc-producto` + Frontend)
- **Protección de `GET /api/movimientos/**` por rol y empresa:**
  - **`Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/infrastructure/config/SecurityConfig.java`**:
    - Se retiró `/api/movimientos/**` de `permitAll()`, manteniendo públicos únicamente los `GET` de consulta de stock (`/api/inventarios/**`).
    - `GET /api/movimientos/**` exige ahora `ROLE_ADMIN` o `ROLE_EMP`.
  - **`Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/application/service/InventarioAuthorizationService.java`**:
    - Se añadieron `canManageMovimiento(Authentication, Long movimientoId)` y `canManageAllInventarios(Authentication, List<Long> inventarioIds)` para verificar propiedad de la empresa mediante el claim `empresaId` del JWT y la relación `movimiento -> inventario -> producto`.
  - **`Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/presentation/controller/MovimientoInventarioController.java`**:
    - `GET /api/movimientos/{id}`: `@PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageMovimiento(authentication, #id))")`.
    - `GET /api/movimientos/inventario/{inventarioId}` y `GET /api/movimientos/inventario/{inventarioId}/paginado`: restringidos a `ROLE_ADMIN` o `ROLE_EMP` dueño (`canManageInventario`).
    - `GET /api/movimientos/paginado`: exige `ROLE_ADMIN` o `ROLE_EMP`; para `ROLE_EMP` valida `inventarioId` / `inventarioIds` propios y rechaza con `AccessDeniedException` (`403`) consultas globales sin filtro o inventarios ajenos (compatible con `MovimientosTab.tsx`).
    - `GET /api/movimientos` (listado completo sin paginar): restringido a `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`.
- **Creación de categorías restringida a `ROLE_ADMIN`:**
  - **`Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/presentation/controller/CategoriaController.java`**: `POST /api/categorias` pasó de `hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')` a `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`.
  - **`avalon-react-10.1.0/app/(full-page)/emprendedor/categorias/page.tsx`**: se ocultó el botón `"Nueva Categoría"` para usuarios que no sean administradores (`{isAdmin && ...}`).
  - **`avalon-react-10.1.0/app/(full-page)/emprendedor/categorias/crear/page.tsx`**: se añadió verificación de rol (`authService.isAdmin()`) con redirección a `/emprendedor/categorias` si accede un no administrador.

### 1.2 T08 — Reducción de superficie expuesta (Swagger OpenAPI y `@CrossOrigin`)
- **Deshabilitación por defecto y restricción de Swagger/OpenAPI en los 4 microservicios:**
  - **`Microservicios/demo/src/main/resources/application.properties`**
  - **`Microservicios/msvc-inventario/src/main/resources/application.properties`**
  - **`Microservicios/msvc-producto/src/main/resources/application.properties`**
  - **`Microservicios/msvc-ventas/src/main/resources/application.properties`**
    - Se añadieron `springdoc.api-docs.enabled=${SPRINGDOC_ENABLED:false}` y `springdoc.swagger-ui.enabled=${SPRINGDOC_ENABLED:false}`.
  - En los 4 archivos `SecurityConfig.java` (`demo`, `msvc-inventario`, `msvc-producto`, `msvc-ventas`):
    - `/swagger-ui/**`, `/swagger-ui.html`, `/api-docs/**` y `/v3/api-docs/**` pasaron de `permitAll()` a `.hasAuthority("ROLE_ADMIN")`.
- **Eliminación de `@CrossOrigin(originPatterns = "*")` en controladores:**
  - Se retiró `@CrossOrigin(originPatterns = "*")` de los 9 controladores restantes para centralizar CORS en el Gateway Nginx:
    1. `Microservicios/demo/.../AuthController.java`
    2. `Microservicios/msvc-inventario/.../InventarioController.java`
    3. `Microservicios/msvc-inventario/.../MovimientoInventarioController.java`
    4. `Microservicios/msvc-producto/.../CategoriaController.java`
    5. `Microservicios/msvc-producto/.../ProductoController.java`
    6. `Microservicios/msvc-ventas/.../ClienteController.java`
    7. `Microservicios/msvc-ventas/.../PagoController.java`
    8. `Microservicios/msvc-ventas/.../SeguimientoLogisticaController.java`
    9. `Microservicios/msvc-ventas/.../VentaController.java`

### 1.3 T09 — Endurecimiento de Gateway Nginx y plantilla para producción
- **`gateway/snippets/security-headers.conf`** *(nuevo)* y **`gateway/snippets/cors-headers.conf`**:
  - Añadidas cabeceras HTTP de seguridad (`X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: geolocation=(), microphone=(), camera=()`) incluidas también en las respuestas CORS y errores del Gateway.
- **`gateway/nginx.conf`**:
  - Añadidas zonas `limit_req_zone` (`api_auth` a `5r/s` con `burst=15 nodelay` en `/api/auth`, y `api_general` a `20r/s` con `burst=40 nodelay` en los demás endpoints) con `limit_req_status 429;` conectado al manejador JSON `@error_429`.
  - Añadidos bloqueos explícitos `403` JSON para `/(swagger-ui(\.html)?|api-docs|v3/api-docs)(/.*)?$` y `/(camunda|engine-rest)(/.*)?$` (adicionales a `/actuator` y `/sincronizar-stock`).
  - Actualizado `map $http_origin $cors_origin` para admitir puertos en `gadmsigchos.gob.ec` (`(:[0-9]+)?$`, como `:444`).
- **`gateway/tienda.prod.conf`** *(nuevo)*:
  - Plantilla autocontenida para `/etc/nginx/conf.d/tienda.conf` en el servidor real (AlmaLinux 10.1 con `systemd` nativo, sin tocar el servidor en caliente durante el desarrollo), integrando:
    - Bloque `listen 80` con `/.well-known/acme-challenge/` para Certbot y redirección HTTPS `:444`.
    - Bloque `listen 444 ssl` con SSL Let's Encrypt, HSTS + cabeceras de seguridad, `limit_req`, bloqueos `403`/`404`, enrutamiento hacia `auth-service` (`127.0.0.1:8084`, incluyendo `/uploads/qr-deuna`), `msvc-producto` (`8081`), `msvc-inventario` (`8082`), `msvc-ventas` (`8083`) y frontend Next.js (`3000`).
    - Bloque interno solo loopback (`listen 127.0.0.1:8080`) para `GATEWAY_INTERNAL_URL` de Next.js.

---

## 2. Pruebas y Verificaciones Ejecutadas
- **`msvc-inventario`** (`.\mvnw.cmd -o test`, actualizando `InventarioControllerApiAuthorizationTest`, `InventarioPaginationTest` y `MovimientoPropiedadTest`): **48/48 pasaron** (0 fallos, 0 errores, 0 omitidos).
- **`msvc-ventas`** (`.\mvnw.cmd -o test`, actualizando `VentaControllerApiAuthorizationTest`): **71/71 pasaron** (0 fallos, 0 errores, 0 omitidos).
- **`msvc-producto`** (`.\mvnw.cmd -o test "-Dtest=*Test"`, actualizando `ProductoControllerApiAuthorizationTest`): **44/44 pruebas unitarias/WebMvc pasaron** *(excluyendo `MsvcProductoApplicationTests` y `*IT`, que requieren MySQL/Redis levantados localmente)*.
- **`demo` (`msvc-auth`)** (`.\mvnw.cmd -o test "-Dtest=!LoginApplicationTests"`): **73/73 pruebas unitarias/WebMvc pasaron** *(excluyendo `LoginApplicationTests` por requerir MySQL activo localmente)*.
- **Frontend (`avalon-react-10.1.0`):** `npx tsc --noEmit` sin errores y `41/41` pruebas en `tests/checkoutEmpresa.test.mjs`, `tests/payphone.test.mjs` y `tests/stripeInactivo.test.mjs`.
- **Nginx Gateway (`gateway/nginx.conf` + `gateway/snippets/*`):** validado con `nginx -t` en contenedor `nginx:alpine` (`syntax is ok` / `test is successful`).

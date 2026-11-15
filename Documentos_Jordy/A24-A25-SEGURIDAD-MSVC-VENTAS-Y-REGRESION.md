# Etapa 7 — Seguridad en `msvc-ventas` (`A-24`), Endurecimiento de Inventario y Regresión Público/Privado (`A-25`)

**Rama de trabajo:** `feature/a24-a25-seguridad-ventas` (creada desde `feature/a19-a22-paginacion`, sin merge a `main` y sin `push` hasta autorización)  
**Microservicios intervenidos:** `Microservicios/msvc-ventas`, `Microservicios/msvc-inventario`  
**Frontend intervenido:** `avalon-react-10.1.0`

---

## 1. Resumen de Implementación (`A-24`)

### 1.1 `Microservicios/msvc-ventas`: Resource Server JWT y Autorización por Rol y Propiedad

1. **Dependencias y configuración JWT:**
   - [pom.xml](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/pom.xml): incorporación de `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` y `spring-security-test` (alcance `test`).
   - [application.properties](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/resources/application.properties): configuración de `jwt.secret=${JWT_SECRET}`.
   - [application-test.properties](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/test/resources/application-test.properties): definición de `jwt.secret` de prueba para perfil `test`.

2. **Filtro HTTP Stateless ([SecurityConfig.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/infrastructure/config/SecurityConfig.java)):**
   - `@Configuration` + `@EnableMethodSecurity`, sesiones `STATELESS` y CSRF deshabilitado.
   - Respuestas estándar con `BearerTokenAuthenticationEntryPoint` (`401`) y `BearerTokenAccessDeniedHandler` (`403`).
   - Validación HS256 (`JwtDecoder`) compartiendo `JWT_SECRET` y mapeo directo de autoridades desde el claim `scope` sin prefijo adicional (alineado con `demo`, `msvc-producto` y `msvc-inventario`).
   - Reglas de acceso HTTP:
     - Público: `OPTIONS /**` (preflight CORS), `/actuator/health/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/api-docs/**`, `/v3/api-docs/**` y `POST /api/stripe/webhook`.
     - Solo `ROLE_ADMIN`: `/actuator/metrics/**`, `/engine-rest/**`, `/camunda/**`, `/app/**`, `/lib/**` y `/api/bpm/ventas/**`.
     - Denegado explícitamente: `/actuator/**`.
     - Resto de rutas (`/api/ventas/**`, `/api/clientes/**`, `/api/pagos/**`, `/api/seguimiento-logistica/**`, `/api/stripe/**`): `.authenticated()` + verificación granular con `@PreAuthorize`.

3. **Manejador global de excepciones ([GlobalExceptionHandler.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/presentation/controller/GlobalExceptionHandler.java)):**
   - Se agregan `@ExceptionHandler(AccessDeniedException.class)` (`403 FORBIDDEN`) y `@ExceptionHandler(AuthenticationException.class)` (`401 UNAUTHORIZED`) para evitar que las denegaciones de `@PreAuthorize` caigan en `@ExceptionHandler(Exception.class)` (`500`).

4. **Servicio de autorización de negocio ([VentaAuthorizationService.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/application/service/VentaAuthorizationService.java)):**
   - `canManageCompany(auth, empresaId)`: permite `ROLE_ADMIN` o `ROLE_EMP` cuyo claim `empresaId` coincide con la empresa consultada.
   - `canAccessCliente(auth, clienteId)` / `canAccessClienteByEmail(auth, email)` / `canCreateClienteWithEmail(auth, email)`: permite `ROLE_ADMIN` o al usuario cuyo claim `email` (o `subject` de respaldo) coincide (case-insensitive) con el email del `Cliente`.
   - `canViewVenta(auth, ventaId)` / `canViewVentaByFactura(auth, numeroFactura)`: permite `ROLE_ADMIN`, al emprendedor dueño (`venta.emprendedorId == token.empresaId`) o al cliente comprador dueño (`venta.cliente.email` coincide con el token).
   - `canManageVenta(auth, ventaId)`: restringido exclusivamente a `ROLE_ADMIN` o `ROLE_EMP` dueño (`venta.emprendedorId == token.empresaId`).
   - `canViewPago(auth, pagoId)`, `canManagePago(auth, pagoId)` y `canUploadComprobantePago(auth, pagoId)`: separan lectura (admin/emprendedor dueño/cliente comprador), aprobación/rechazo (solo admin/emprendedor dueño) y carga de comprobante (admin o cliente comprador).

---

## 2. Matriz de Autorización por Controlador (`msvc-ventas`)

| Controlador | Endpoint / Grupo | Regla aplicada |
|---|---|---|
| [VentaController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/presentation/controller/VentaController.java) | `POST /api/ventas` | Roles `ROLE_USER`, `ROLE_CLIENTE`, `ROLE_EMP`, `ROLE_ADMIN`; además `canAccessCliente` sobre `requestDto.clienteId` (si no es nulo, validado por `@Valid`) |
| | `GET /api/ventas/{id}`, `GET /api/ventas/factura/{numeroFactura}`, `GET /api/ventas/{id}/factura` | `canViewVenta` / `canViewVentaByFactura` (`ROLE_ADMIN`, emprendedor dueño o cliente comprador) |
| | `GET /api/ventas/cliente/{clienteId}` y `/paginado` | `canAccessCliente` (`ROLE_ADMIN` o cliente dueño) |
| | `GET /api/ventas/emprendedor/{emprendedorId}` y `/paginado` | `canManageCompany` (`ROLE_ADMIN` o `ROLE_EMP` propietario) |
| | `GET /api/ventas` y `/paginado` | `hasAuthority('ROLE_ADMIN')` |
| | `PUT /api/ventas/{id}/completar`, `/cancelar`, `POST /api/ventas/{id}/factura/generar` | `ROLE_ADMIN` o `ROLE_EMP` con `canManageVenta` |
| [ClienteController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/presentation/controller/ClienteController.java) | `POST /api/clientes` | `canCreateClienteWithEmail` (`ROLE_ADMIN` o email propio del JWT) |
| | `PUT /api/clientes/{id}` | `canAccessCliente` y `canCreateClienteWithEmail` |
| | `GET /api/clientes/{id}`, `/email/{email}` | `canAccessCliente` / `canAccessClienteByEmail` |
| | `GET /api/clientes`, `/paginado`, `DELETE /api/clientes/{id}` | `hasAuthority('ROLE_ADMIN')` |
| [PagoController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/presentation/controller/PagoController.java) | `POST /api/pagos`, `GET /api/pagos/venta/{ventaId}` | `canViewVenta` |
| | `GET /api/pagos/{id}` | `canViewPago` |
| | `POST /api/pagos/{id}/aprobar`, `/rechazar` | `ROLE_ADMIN` o `ROLE_EMP` + `canManagePago` + `canManageCompany` sobre `aprobarDto.emprendedorId` |
| | `POST /api/pagos/{id}/comprobante` | `canUploadComprobantePago` (`ROLE_ADMIN` o cliente propietario de la venta) |
| | `GET /api/pagos/emprendedor/{emprendedorId}/**` | `canManageCompany` |
| | `GET /api/pagos/estado/{estado}` y `/paginado` | `hasAuthority('ROLE_ADMIN')` |
| [SeguimientoLogisticaController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/presentation/controller/SeguimientoLogisticaController.java) | `POST /api/seguimiento-logistica`, `PUT /api/seguimiento-logistica` | `ROLE_ADMIN` o `ROLE_EMP` con `canManageVenta` |
| | `GET /api/seguimiento-logistica/venta/{ventaId}` y `/ultimo` | `canViewVenta` |
| | `GET /api/seguimiento-logistica/emprendedor/{emprendedorId}/**` | `canManageCompany` |
| | `GET /api/seguimiento-logistica/estados` | `isAuthenticated()` |
| [StripeController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/presentation/controller/StripeController.java) | `POST /api/stripe/create-payment-intent`, `/confirm-payment` | `canViewVenta` |
| | `GET /api/stripe/payment-intent/{paymentIntentId}` | `isAuthenticated()` |
| | `POST /api/stripe/webhook` | Público (`permitAll` en `SecurityConfig`) |
| [ProcesoVentaBpmController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvcventas/infrastructure/controllers/ProcesoVentaBpmController.java) | `/api/bpm/ventas/**` | `hasAuthority('ROLE_ADMIN')` (nivel clase + URL HTTP) |

---

## 3. Endurecimiento Coordinado en `msvc-inventario` y Frontend

1. **`Microservicios/msvc-inventario`:**
   - [ProductoDto.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/application/dto/ProductoDto.java): se agrega `@JsonIgnoreProperties(ignoreUnknown = true)` y `EmpresaDto` (`id`) para leer la empresa propietaria desde `msvc-producto`.
   - [InventarioAuthorizationService.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/application/service/InventarioAuthorizationService.java): valida propiedad por producto (`canManageProduct`) y por lote (`canManageSalidaLote`), permitiendo únicamente `ROLE_ADMIN` o `ROLE_EMP` cuya `empresaId` coincida con cada producto y fallando cerrado si falta empresa o falla Feign.
   - [SecurityConfig.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/infrastructure/config/SecurityConfig.java), [GlobalExceptionHandler.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/presentation/controller/GlobalExceptionHandler.java) y [InventarioController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/presentation/controller/InventarioController.java): restringen `POST /api/inventarios/salida-lote` y `PUT /api/inventarios/producto/{productoId}/stock` a `ROLE_ADMIN` o `ROLE_EMP` propietario, devolviendo `403 FORBIDDEN` en accesos no autorizados.

2. **Frontend (`avalon-react-10.1.0`):**
   - [historial-compras/page.tsx](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(main)/profile/historial-compras/page.tsx): añade `authService.getAuthHeaders()` al consultar `GET /api/clientes/email/{email}`.
   - [stripeService.ts](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/services/stripeService.ts): añade `authService.getAuthHeaders()` en `crearPaymentIntent`, `confirmarPago` y `obtenerEstadoPago`.

---

## 4. Pruebas Agregadas y Resultados de Regresión (`A-25`)

### Pruebas añadidas o actualizadas
- [VentaAuthorizationServiceTest.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/test/java/com/example/msvc_ventas/application/service/VentaAuthorizationServiceTest.java): 4 pruebas unitarias sobre reglas de empresa, cliente, vista/gestión de ventas y vista/gestión/comprobante de pagos.
- [VentaControllerApiAuthorizationTest.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/test/java/com/example/msvc_ventas/VentaControllerApiAuthorizationTest.java): 6 pruebas `@WebMvcTest` que validan `401`, `403`, `200`/`201`, CORS preflight, `/api/stripe/webhook` público y restricción administrativa de `/api/bpm/ventas/**`, `/camunda/**`, `/engine-rest/**` y `/actuator/metrics/**`.
- [VentaIntegrationTest.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/test/java/com/example/msvc_ventas/integration/VentaIntegrationTest.java): las 14 pruebas de integración H2 actualizadas con tokens JWT simulados (`ROLE_USER` / `ROLE_ADMIN`) manteniendo intactas todas las validaciones de negocio.
- [InventarioControllerApiAuthorizationTest.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-inventario/src/test/java/com/example/msvc_inventario/InventarioControllerApiAuthorizationTest.java): ampliado con 7 casos nuevos para `/salida-lote` y `/producto/{productoId}/stock`.

### Resultados observados tras la última edición

| Módulo | Comando / Alcance | Resultado observado |
|---|---|---|
| `Microservicios/msvc-ventas` | `.\mvnw.cmd -o test` (suite completa con `-javaagent` ByteBuddy) | **39/39 aprobadas** (`Failures: 0, Errors: 0, Skipped: 0`): `VentaAuthorizationServiceTest` (4), `VentaControllerApiAuthorizationTest` (6), `VentaIntegrationTest` (14), `VentaServiceTest` (12), `VentaPaginationTest` (2), `MsvcVentasApplicationTests` (1) |
| `Microservicios/msvc-inventario` | `.\mvnw.cmd -o test` (suite completa con `-javaagent` ByteBuddy) | **18/18 aprobadas** (`Failures: 0, Errors: 0, Skipped: 0`): `InventarioControllerApiAuthorizationTest` (15), `InventarioPaginationTest` (2), `MsvcInventarioApplicationTests` (1) |
| `Microservicios/msvc-producto` | `.\mvnw.cmd -o test "-Dtest=ProductoAuthorizationServiceTest,ProductoControllerApiAuthorizationTest,ProductoCatalogoCacheServiceTest,ProductoPaginationTest,ProductoServiceInventoryBatchTest,RedisCacheConfigTest"` | **32/32 aprobadas** (`Failures: 0, Errors: 0, Skipped: 0`). Nota: al ejecutar sin filtro, `MsvcProductoApplicationTests` y las clases `*IT` requieren MySQL y Redis activos en local |
| `Microservicios/demo` | `.\mvnw.cmd -o test "-Dmaven-surefire-plugin.version=3.5.3" "-Dtest=UsuarioPaginationTest"` | **2/2 aprobadas** en `UsuarioPaginationTest` (`Failures: 0, Errors: 0, Skipped: 0`) |
| `avalon-react-10.1.0` | `.\node_modules\.bin\tsc.cmd --noEmit` | **Exit code 0** (sin errores de tipos) |
| `avalon-react-10.1.0` | `.\node_modules\.bin\next.cmd build --no-lint` | **Exit code 0** (compilación y generación de 105 páginas estáticas completadas) |

# Prueba 3: Verificar T11 con Cuentas Reales de Prueba

- **Objetivo:** Verificar la restricción de 1 empresa única por emprendedor (`T11`) y la sincronización entre `auth-service` (`demo`) y `msvc-producto` usando cuentas reales de prueba (`ROLE_ADMIN` y `ROLE_EMP`).
- **Estado:** **VERIFICACIÓN PARCIAL (solo lectura en producción; sin mutación bajo Opción A)** — Base de datos (`uk_emprendedor_empresa`), `MSVC_PRODUCTO_URL` en proceso vivo, scoping `ROLE_EMP` (`Sigcholac`) e interfaz admin de vinculación T11 (`ROLE_ADMIN` en `/administrador/usuarios/5/editar`) verificadas en vivo; las mutaciones `PUT`/`DELETE` (`409 Conflict` / desvincular) no se ejecutaron porque las `4/4` empresas de producción ya están asignadas a los `4` emprendedores activos.

---

## 1. Evidencia verificada hoy en producción (BD + Runtime)

1. **Restricción única en MySQL (`auth_service.emprendedores`):**
   - `SHOW INDEX FROM auth_service.emprendedores` confirma el índice único activo:
     - `Key_name`: **`uk_emprendedor_empresa`**
     - `Non_unique`: **`0`**
     - `Column_name`: **`empresa_id`**
     - `Index_type`: `BTREE`
   - Integridad de datos actuales:
     - `dup_empresas = 0` (no existe ninguna empresa asociada a más de un emprendedor).
     - `4` emprendedores registrados, los `4` con `empresa_id` asignado (`con_empresa = 4`).
2. **Integración `auth-service -> msvc-producto` en memoria:**
   - Archivo `/etc/tienda-virtual/backend.env`: `MSVC_PRODUCTO_URL=http://127.0.0.1:8081`.
   - Inspección directa del proceso vivo de `auth-service` (`/proc/<PID>/environ`): **`MSVC_PRODUCTO_URL=http://127.0.0.1:8081` cargado en memoria**.
   - Controlador de negocio disponible en [`EmprendedorEmpresaController.java`](../../Microservicios/demo/src/main/java/com/example/demo/presentation/controller/EmprendedorEmpresaController.java) bajo `/api/usuarios/{usuarioId}/empresa` (`GET`, `PUT`, `DELETE`).
3. **Evidencia en navegador como `ROLE_EMP` y `ROLE_ADMIN`:**
   - **Como `ROLE_EMP`:** el panel queda acotado por T11 a `empresaId=2` (`Sigcholac`): `/emprendedor/empresas` muestra `1` empresa (`Sigcholac`), `/emprendedor/productos` consulta `GET /api/productos/empresa/2/paginado` (`10` productos) y `/emprendedor/ventas` consulta `GET /api/ventas/emprendedor/2/paginado` (`6` ventas).
   - **Como `ROLE_ADMIN` (`/administrador/usuarios/5` y `/administrador/usuarios/5/editar`):**
     - `GET /api/usuarios/5` -> **`200`** devuelve explícitamente `"empresaId":4` (`Grandes Foods`).
     - La pantalla de edición renderiza el módulo T11 **`"Empresa vinculada"`** con `"Empresa actual: Grandes Foods (#4)"`, selector de empresas activas (`GET /api/empresas` -> `200`), botones `"Vincular"` / `"Desvincular"` y el aviso `"Una empresa solo puede pertenecer a un emprendedor. El cambio se aplica cuando el emprendedor vuelve a iniciar sesión."`.
     - **Hallazgo menor UI en `/administrador/usuarios/[id]/editar`:** el botón `"Cancelar"` dentro del formulario dispara `PUT /api/usuarios/5` (`200`) con el formulario sin cambios (por faltar `type="button"`), aunque no alteró `empresaId=4` ni ningún dato.
4. **Cuentas existentes (conteo agregado sin PII):**
   - `ROLE_ADMIN`: `1`
   - `ROLE_EMP`: `4`
   - `ROLE_USER`: `9`
5. **Protección sin autenticación:**
   - `GET /api/usuarios` sin JWT responde `401`.

---

## 2. Casos de mutación T11 no ejecutados en producción (Opción A)

Como las `4` empresas existentes ya están vinculadas a los `4` emprendedores reales en producción (sin empresa libre de prueba), bajo **Opción A** (no destructiva) quedaron sin ejecutar las mutaciones sobre BD real:

| Caso T11 | Acción | Resultado esperado | Estado |
|---|---|---|---|
| Consulta inicial y UI T11 (`ROLE_ADMIN` / `ROLE_EMP`) | `/administrador/usuarios/5/editar` + `GET /api/usuarios/5` + `ROLE_EMP` scoping | `200` (`empresaId` asignado visible y acotado) | **PASS** *(verificado en vivo)* |
| Vinculación válida | `PUT /api/usuarios/{usuarioId}/empresa` hacia una empresa libre | `200`, `empresa_id` persistido y reflejado en el JWT al re-loguearse | No ejecutado *(sin empresa libre en prod)* |
| Conflicto de empresa única | `PUT /api/usuarios/{otroEmpId}/empresa` apuntando a `empresaId` ya ocupada | **`409 Conflict`** (`VinculacionEmpresaConflictoException` + `uk_emprendedor_empresa`) | No ejecutado *(protegido por Opción A)* |
| Desvinculación | `DELETE /api/usuarios/{usuarioId}/empresa` | `200`, `empresa_id = NULL` sin afectar a otros emprendedores | No ejecutado *(protegido por Opción A)* |
| Bloqueo UI por rol (`ROLE_USER` / `ROLE_EMP`) | Acceso a `/administrador/usuarios` desde navegador | Bloqueado (`auth/login2` / `auth/access2`) | **PASS** *(verificado en vivo)* |

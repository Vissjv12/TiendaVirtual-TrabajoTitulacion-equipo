# Prueba 5: Pruebas de Regresión por Roles

- **Objetivo:** Verificar el control de acceso por roles (`ANÓNIMO`, `ROLE_USER`, `ROLE_EMP` con aislamiento multiempresa y `ROLE_ADMIN`) a través del Gateway real en producción.
- **Estado:** **VERIFICADA EN NAVEGADOR POR LOS 4 NIVELES** (`ANÓNIMO`, `ROLE_USER`, `ROLE_EMP` y `ROLE_ADMIN`, más bloqueos de perímetro Gateway), en modo solo lectura bajo Opción A (mutaciones API directas entre empresas distintas quedan únicamente sin disparar para no alterar datos productivos).

---

## 1. Inventario de Roles en Producción (`auth_service.usuario_roles`)

Conteo agregado consultado en producción (sin exponer correos ni credenciales):
- `ROLE_USER`: `9` usuarios
- `ROLE_EMP`: `4` usuarios (los 4 vinculados a empresas distintas, `dup_empresas = 0`)
- `ROLE_ADMIN`: `1` usuario

---

## 2. Matriz ya verificada en vivo (`ANÓNIMO` / Sin JWT + Perímetro)

| Rol / Escenario | Endpoint probado en `https://tienda.gadmsigchos.gob.ec:444` | Esperado | Observado | Resultado |
|---|---|---:|---:|:---:|
| Anónimo | `GET /landing` | `200` | `200` | PASS |
| Anónimo | `GET /api/health` | `200` | `200` | PASS |
| Anónimo | `GET /api/productos` | `200` | `200` | PASS |
| Anónimo | `GET /api/productos/listado` | `200` | `200` | PASS |
| Anónimo | `GET /api/productos/listado/paginado` | `200` | `200` | PASS |
| Anónimo | `GET /api/categorias` | `200` | `200` | PASS |
| Anónimo | `GET /api/empresas` | `200` | `200` | PASS |
| Anónimo | `GET /api/inventarios` | `200` | `200` | PASS |
| Anónimo | `GET /uploads/qr-deuna/2_8d69ec19-f893-4db5-9f65-5f4859ae7a30_test_qr.png` | `200` | `200` | PASS |
| Anónimo | `GET /api/emprendedor/configuracion-pagos/payphone/2` | `200` | `200` | PASS |
| Anónimo | `GET /api/emprendedor/configuracion-pagos/costo-envio/1` | `200` | `200` | PASS |
| Anónimo | `GET /api/ventas` | `401` | `401` | PASS |
| Anónimo | `GET /api/usuarios` | `401` | `401` | PASS |
| Anónimo | `GET /api/emprendedor/configuracion-pagos` | `401` | `401` | PASS |
| Anónimo | `POST /api/emprendedor/configuracion-pagos/payphone` | `401` | `401` | PASS |
| Anónimo | `POST /api/emprendedor/configuracion-pagos/payphone/ventas/1/preparar` | `401` | `401` | PASS |
| Anónimo | `GET /api/movimientos` | `401` | `401` | PASS |
| Perímetro Gateway | `GET /actuator/health`, `/swagger-ui.html`, `/api-docs`, `/v3/api-docs`, `/camunda`, `/engine-rest` | `403` | `403` | PASS |
| Perímetro Gateway | `PUT /api/productos/1/sincronizar-stock` | `403` | `403` | PASS |
| Perímetro Gateway | `GET /api/ventas/internal/test` | `404` | `404` | PASS |

---

## 3. Verificado en navegador autenticado (`ROLE_USER` — `cliente 1`)

- **Acceso legítimo de comprador (`PASS`):**
  - Navegación a `/cart` (`200`), `/checkout` (`200`) y `/profile/historial-compras` (`200`).
  - Llamadas exitosas con sesión activa: `GET /api/clientes/email/...` (`200`), `GET /api/ventas/cliente/1/paginado?page=0&size=10&sort=fechaVenta%2Cdesc` (`200`), `GET /api/seguimiento-logistica/venta/6` (`200`) y `GET /api/ventas/6/factura` (`200`, PDF).
- **Bloqueos de rutas restringidas en UI para `ROLE_USER` (`PASS`):**
  - Navegar directamente como `ROLE_USER` a `/administrador`, `/administrador/usuarios`, `/emprendedor` (`"Verificando permisos..."`) y `/emprendedor/configuracion-pagos` bloquea el acceso en las **4 rutas**, redirige inmediatamente a `/auth/login2`, cierra la sesión no autorizada y **no dispara ninguna llamada de datos hacia `/api/...`**.
- **Nota técnica sobre pruebas `fetch()` directas desde DevTools:** `chrome_devtools/evaluate_script` está bloqueado por una regla local de seguridad MCP del entorno, por lo que las llamadas API REST con JWT se verifican vía pantallas reales o con `curl` si se aporta token.

---

## 4. Verificado en navegador autenticado (`ROLE_EMP` — Emprendedor de `Sigcholac`, `empresaId=2`)

Probado en modo estrictamente solo lectura sin modificar productos, stock ni configuraciones:

| Vista / Endpoint | Esperado | Observado en vivo | Resultado |
|---|---:|---|---:|
| `/emprendedor` + `GET /api/pagos/emprendedor/2/pendientes` | `200` | `200` *(acotado a `empresaId=2`)* | **PASS** |
| `/emprendedor/empresas` (`GET /api/empresas/paginado`) | Solo empresa propia | `200` (`1 a 1 de 1 empresas`: `Sigcholac`) | **PASS** |
| `/emprendedor/productos` (`GET /api/productos/empresa/2/paginado` + `batch` stock) | Solo productos propios | `200` (`1 a 10 de 10 productos` de `Sigcholac`) | **PASS** |
| `/emprendedor/inventario` (`POST /api/inventarios/cantidad/batch`) | Solo inventario propio | `200` (`10` productos con stock real `49..500`) | **PASS** |
| `/emprendedor/ventas` (`GET /api/ventas/emprendedor/2/paginado`) | Solo ventas propias | `200` (`6 Completadas`, `$53,31`) | **PASS** |
| `/emprendedor/configuracion-pagos` (`GET /api/emprendedor/configuracion-pagos`) | Sin fuga de token | `200` (`tokenConfigurado: true`, QR DeUna `200`, token oculto) | **PASS** |
| `/administrador` y `/administrador/usuarios` como `ROLE_EMP` | Bloqueado | Redirige a `/auth/access2` (`401 / Access Denied`) sin llamadas API admin | **PASS** |

- **Observaciones menores de UI detectadas en `/emprendedor`:**
  1. Las tarjetas KPI del tablero principal (`/emprendedor`) muestran valores estáticos de plantilla (`Total Ventas: 156`, `iPhone 15 Pro`, etc.), mientras los submódulos reales (`/productos`, `/inventario`, `/ventas`) muestran los datos reales de BD.
  2. En `/emprendedor/inventario`, aunque `"Stock Actual"` muestra las cantidades vivas correctas (`297`, `500`, etc.), el badge de la columna `"Ubicación/Estado"` marca `"Sin stock"` en las filas.

---

## 5. Verificado en navegador autenticado (`ROLE_ADMIN` — Administrador del Sistema)

Probado en modo solo lectura comprobando visibilidad global multiempresa y acceso completo a administración:

| Vista / Endpoint | Esperado | Observado en vivo | Resultado |
|---|---:|---|---:|
| `/administrador` | Acceso admin | `200` (panel completo con todos los módulos del sistema) | **PASS** |
| `/administrador/usuarios` (`GET /api/usuarios/paginado` + `/estadisticas`) | Listado global | `200` (`14` usuarios totales en 2 páginas) | **PASS** |
| `/administrador/usuarios/5` y `/editar` (`GET /api/usuarios/5` + `GET /api/empresas`) | Detalle + T11 | `200` (`empresaId: 4`, sección `"Empresa vinculada"` visible) | **PASS** |
| `/administrador/empresas` (`GET /api/empresas/paginado`) | Todas las empresas | `200` (`1 a 4 de 4 empresas`) | **PASS** |
| `/administrador/categorias` (`GET /api/categorias/paginado`) | Todas las categorías | `200` (`1 a 6 de 6 categorías`) | **PASS** |
| `/administrador/productos` (`GET /api/productos/paginado` + `batch`) | Catálogo completo | `200` (`39` productos de todas las empresas) | **PASS** |
| `/administrador/inventario` (`GET /api/productos/paginado` + `batch`) | Inventario global | `200` (`Total Productos: 39`) | **PASS** |
| `/administrador/ventas` (`GET /api/ventas/paginado`) | Ventas globales | `200` (`6 Completadas`, `$53,31`) | **PASS** |
| `/emprendedor` como `ROLE_ADMIN` | Permitido sin fuga | `200` (permite ver vista; al no tener `empresaId`, no consulta `/api/pagos/emprendedor/...`) | **PASS** |

- **Nota de alcance Opción A:** no se ejecutaron mutaciones `PUT`/`DELETE` cruzadas contra recursos de otra empresa ni órdenes falsas para proteger la integridad de producción (`evaluate_script` también bloqueado por regla MCP local).
- **Observación UI compartida:** en `/administrador/inventario` también aparece el badge `"Sin stock"` en la columna `"Ubicación/Estado"` aunque `"Stock Actual"` muestra existencias válidas, igual que en `/emprendedor/inventario`. En `/administrador/usuarios/5/editar`, el botón `"Cancelar"` envía el `<form>` sin modificar campos (`PUT /api/usuarios/5` `200`).

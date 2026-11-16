# A-19 — Contrato Común de Paginación de APIs REST

- **Etapa:** Etapa 6 — Paginación (`A-19` a `A-22`)
- **Alcance:** Microservicios `msvc-producto` (`:8081`), `msvc-inventario` (`:8082`), `msvc-ventas` (`:8083`), `auth-service / demo` (`:8084`) y Frontend Next.js (`avalon-react-10.1.0`), expuestos mediante el API Gateway (`:8080`).
- **Principio rector:** Ningún nuevo listado paginado carga colecciones completas en memoria; las consultas se ejecutan en base de datos con `Pageable`, límite máximo estricto por página y lista blanca de ordenamiento. Los endpoints históricos sin `/paginado` se conservan intactos para compatibilidad con selectores desplegables, Feign internos y caché Redis de catálogo (`A-17`).

---

## 1. Parámetros de Consulta Estándar (Query Params)

| Parámetro | Tipo | Base | Valor por defecto | Reglas y acotamiento |
|---|---|---|---|---|
| `page` | `int` | `0` | `0` | Si es negativo (`page < 0`), se normaliza a `0`. |
| `size` | `int` | - | **`12`** en vitrina pública (`/api/productos/listado/paginado`)<br>**`10`** en tablas administrativas y de gestión | **Máximo estricto: `50`**. Si `size > 50` se acota a `50`; si `size <= 0` se aplica el valor por defecto del endpoint. |
| `sort` | `string` | - | Depende del recurso (ej. `id,desc` o `nombre,asc`) | Formato `propiedad,asc\|desc`. Toda propiedad se valida contra la lista blanca de la entidad; propiedades no autorizadas se ignoran y se aplica el orden por defecto seguro. |

---

## 2. Estructura JSON de Respuesta (`PageResponseDto<T>`)

Todos los endpoints `/paginado` devuelven exactamente el siguiente esquema JSON:

```json
{
  "content": [
    {
      "id": 101,
      "nombre": "Mermelada de Mortiño 250g"
    }
  ],
  "pageNumber": 0,
  "pageSize": 12,
  "totalElements": 48,
  "totalPages": 4,
  "first": true,
  "last": false,
  "empty": false
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `content` | `T[]` | Elementos de la página actual (máximo `50` registros). |
| `pageNumber` | `int` | Índice de página actual (base `0`). |
| `pageSize` | `int` | Tamaño efectivo aplicado tras validación (`1..50`). |
| `totalElements` | `long` | Total de registros que cumplen el filtro en base de datos. |
| `totalPages` | `int` | Total de páginas disponibles (`0` cuando `totalElements == 0`). |
| `first` | `boolean` | `true` si corresponde a la primera página (`pageNumber == 0`). |
| `last` | `boolean` | `true` si corresponde a la última página o está vacía. |
| `empty` | `boolean` | `true` si `content` no contiene elementos. |

---

## 3. Catálogo Oficial de Endpoints Paginados

### 3.1 `msvc-producto` (`/api/productos`, `/api/categorias`, `/api/empresas`)

| Endpoint | Acceso | `size` defecto | Filtros opcionales | `sort` permitidos (whitelist) |
|---|---|---|---|---|
| `GET /api/productos/listado/paginado` | Público | `12` | `q`, `categoria`, `empresa` | `id`, `nombre`, `precio`, `fechaCreacion` (default `nombre,asc`) |
| `GET /api/productos/paginado` | Público | `10` | `q`, `empresaId`, `categoriaId`, `activo` | `id`, `nombre`, `precio`, `activo`, `fechaCreacion`, `fechaActualizacion` (default `id,desc`) |
| `GET /api/productos/empresa/{empresaId}/paginado` | Público / Token | `10` | `q` | `id`, `nombre`, `precio`, `activo`, `fechaCreacion`, `fechaActualizacion` (default `id,desc`) |
| `GET /api/productos/categoria/{categoriaId}/paginado` | Público | `10` | - | `id`, `nombre`, `precio`, `fechaCreacion` (default `id,desc`) |
| `GET /api/categorias/paginado` | Público | `10` | `q` | `id`, `nombre`, `activo`, `fechaCreacion`, `fechaActualizacion` (default `id,desc`) |
| `GET /api/empresas/paginado` | Público | `10` | `q` | `id`, `nombre`, `ruc`, `activo`, `fechaCreacion`, `fechaActualizacion` (default `id,desc`) |

> **Nota sobre stock vivo y Redis:** En `GET /api/productos/listado/paginado`, la página se obtiene de MySQL con `Pageable` y el frontend/cliente realiza el refresco en lote (`/api/inventarios/cantidad/batch`) únicamente para los IDs visibles en la página actual (máximo 12–50), manteniendo compatibilidad total con la decisión de stock en vivo de `A-16`/`A-17`.

### 3.2 `msvc-inventario` (`/api/inventarios`, `/api/movimientos`)

| Endpoint | Acceso | `size` defecto | Filtros opcionales | `sort` permitidos (whitelist) |
|---|---|---|---|---|
| `GET /api/inventarios/paginado` | Público (lectura) | `10` | `soloActivos` (`boolean`) | `id`, `productoId`, `cantidad`, `ubicacion`, `activo`, `fechaCreacion`, `fechaActualizacion` (default `id,desc`) |
| `GET /api/movimientos/paginado` | Público (lectura) | `10` | `tipo`, `inventarioId`, `inventarioIds` (csv), `motivo`, `fechaDesde`, `fechaHasta` | `id`, `inventarioId`, `tipoMovimiento`, `cantidad`, `fechaMovimiento`, `usuarioId` (default `fechaMovimiento,desc`) |
| `GET /api/movimientos/inventario/{inventarioId}/paginado` | Público (lectura) | `10` | - | `id`, `tipoMovimiento`, `cantidad`, `fechaMovimiento`, `usuarioId` (default `fechaMovimiento,desc`) |

> **Optimización N+1 / memoria:** `GET /api/inventarios/paginado` consulta los detalles de producto únicamente para los IDs contenidos en la página actual.

### 3.3 `msvc-ventas` (`/api/ventas`, `/api/pagos`, `/api/seguimiento-logistica`, `/api/clientes`)

| Endpoint | Acceso | `size` defecto | Filtros opcionales | `sort` permitidos (whitelist) |
|---|---|---|---|---|
| `GET /api/ventas/paginado` | Según `msvc-ventas` | `10` | `q`, `estado` | `id`, `numeroFactura`, `total`, `estado`, `fechaVenta`, `fechaCreacion` (default `fechaVenta,desc`) |
| `GET /api/ventas/cliente/{clienteId}/paginado` | Según `msvc-ventas` | `10` | - | `id`, `numeroFactura`, `total`, `estado`, `fechaVenta`, `fechaCreacion` (default `fechaVenta,desc`) |
| `GET /api/ventas/emprendedor/{emprendedorId}/paginado` | Según `msvc-ventas` | `10` | `q`, `estado` | `id`, `numeroFactura`, `total`, `estado`, `fechaVenta`, `fechaCreacion` (default `fechaVenta,desc`) |
| `GET /api/pagos/emprendedor/{emprendedorId}/paginado` | Según `msvc-ventas` | `10` | - | `id`, `monto`, `metodoPago`, `estadoPago`, `fechaPago`, `fechaCreacion` (default `fechaCreacion,desc`) |
| `GET /api/pagos/emprendedor/{emprendedorId}/pendientes/paginado` | Según `msvc-ventas` | `10` | - | `id`, `monto`, `metodoPago`, `estadoPago`, `fechaCreacion` (default `fechaCreacion,desc`) |
| `GET /api/pagos/estado/{estado}/paginado` | Según `msvc-ventas` | `10` | - | `id`, `monto`, `metodoPago`, `estadoPago`, `fechaCreacion` (default `fechaCreacion,desc`) |
| `GET /api/seguimiento-logistica/emprendedor/{emprendedorId}/paginado` | Según `msvc-ventas` | `10` | `estado` | `id`, `ventaId`, `estadoLogistica`, `fechaActualizacion`, `fechaCreacion` (default `fechaCreacion,desc`) |
| `GET /api/clientes/paginado` | Según `msvc-ventas` | `10` | - | `id`, `nombre`, `apellido`, `email`, `fechaCreacion` (default `id,desc`) |

### 3.4 `auth-service / demo` (`/api/usuarios`)

| Endpoint | Acceso | `size` defecto | Filtros opcionales | `sort` permitidos (whitelist) |
|---|---|---|---|---|
| `GET /api/usuarios/paginado` | `ROLE_ADMIN` | `10` | `q`, `rol`, `activo` | `id`, `username`, `email`, `nombre`, `apellido`, `activo` (default `id,desc`) |

---

## 4. Contrato para Capa de Presentación (PrimeReact / Next.js)

1. **Vitrina pública (`/products`):** Utiliza `<Paginator>` controlado (`first`, `rows={12}`, `totalRecords`, `rowsPerPageOptions={[12, 24, 36, 48]}`) conectado a `GET /api/productos/listado/paginado`, enviando búsqueda (`q`), categoría (`categoria`), empresa (`empresa`) y ordenamiento (`sort`).
2. **Tablas de administración, emprendedor y cliente:** Utilizan `<DataTable lazy ...>` o `<Paginator>` controlado conectado a los endpoints `/paginado`, enviando `page`, `size` (máx. `50`), `sort` y filtros al backend en cada evento `onPage` / `onSort`.

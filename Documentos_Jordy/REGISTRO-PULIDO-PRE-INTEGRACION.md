# Registro de Pulido Pre-Integración (Frontend + UX)

- **Rama de trabajo:** `feature/pulido-export-validaciones-404` (sin merge a `main`)
- **Alcance:** Exportación real `.csv` en ventas, endurecimiento de validaciones de formularios, corrección del error de cliente en pantalla `404`, submenú dinámico de `Categorías` en `LandingNavbar`, control por empresa del botón `Gestionar` en vitrina `/products` y productos destacados, y diagnóstico de la latencia en primera carga del panel Emprendedor.

---

## 1. Cambios Implementados

### 1.1 Exportación real de Ventas a `.csv`
- **`avalon-react-10.1.0/services/csvExport.ts`** *(nuevo)*:
  - `sanitizeCsvCell`: escapa celdas según RFC 4180 y protege contra inyección de fórmulas en Excel/LibreOffice (`=`, `+`, `-`, `@`, tabuladores).
  - `buildCsvContent`: agrega BOM UTF-8 (`\uFEFF`) y saltos `\r\n` para correcta apertura de tildes/ñ en Excel en español.
  - `downloadCsvFile`: genera `Blob` y dispara descarga en navegador.
  - `fetchAllPaginatedRecords`: recorre todas las páginas (`size = 50`, alineado con `PaginationUtils.MAX_PAGE_SIZE`) manteniendo el filtro y ordenamiento activos.
  - `exportVentasToCsv`: exporta columnas `N° Factura`, `Fecha`, `Cliente`, `Email`, `Teléfono`, `Dirección`, `Estado`, `Subtotal (USD)`, `Impuesto (USD)`, `Total (USD)` y `Productos`.
- **`avalon-react-10.1.0/app/(full-page)/emprendedor/ventas/page.tsx`** y **`avalon-react-10.1.0/app/(full-page)/administrador/ventas/page.tsx`**:
  - Se reemplazó el toast `"En desarrollo — Función de exportación próximamente"` en `exportarVentas` por la exportación real `.csv` con estado de carga (`exporting`) y botón `"Exportar CSV"`.

### 1.2 Validaciones de formularios y sanitización de entradas
- **`avalon-react-10.1.0/services/formValidations.ts`** *(nuevo)*:
  - Helpers reutilizables: `EMAIL_REGEX`, `onlyDigits`, `sanitizeReferenceCode`, `sanitizePersonName`, `isValidBankAccount`, `isValidTransferReference`, `isValidPersonName`, `isValidEcuadorianPhone`, `isValidEcuadorianID` y `isValidCedulaOrRucFormat`.
- **Checkout (`app/(main)/checkout/components/` y `app/(landing)/checkout/components/`):**
  - `TransferenciaBancariaForm.tsx` *(ambas variantes)*: `numeroCuenta` ahora restringe entrada con `keyfilter="pint"`, `inputMode="numeric"` y `onlyDigits(..., 20)` + `isValidBankAccount` (rechaza letras o dígitos idénticos); `numeroReferencia` restringe a `[A-Za-z0-9-]` con `sanitizeReferenceCode` + `isValidTransferReference` (requiere 6–50 caracteres alfanuméricos con al menos un dígito).
  - `DeunaTransferenciaForm.tsx` *(ambas variantes)*: mismas restricciones numéricas y de referencia que transferencia bancaria.
  - `CheckoutClienteForm.tsx` *(ambas variantes)*: sanitización en `nombre` y `apellido` (`sanitizePersonName`), validación con `isValidPersonName`, `EMAIL_REGEX`, `isValidEcuadorianPhone`, longitud mínima de `direccion` (>= 5) y reutilización de `isValidEcuadorianID` desde `formValidations.ts`.
- **Panel Emprendedor / Administrador:**
  - `app/(full-page)/emprendedor/configuracion-pagos/page.tsx`: filtro numérico (`keyfilter="pint"` + `onlyDigits`) y validación en `numeroCuenta` (10–20 dígitos) y `cedulaRuc` (10 o 13 dígitos vía `isValidCedulaOrRucFormat`, preservando compatibilidad con semilla `1234567890`), sanitización/validación en `titular` (`sanitizePersonName`, `isValidPersonName`), `EMAIL_REGEX` y recorte + longitud mínima (>= 6) en `payphoneAppId` / `payphoneToken`.
  - `app/(full-page)/emprendedor/productos/crear/page.tsx` y `app/(full-page)/administrador/productos/crear/page.tsx`: se añadió validación explícita `precio > 0` en `validarFormulario` y `min={0.01}` en `InputNumber` (alineado con editar producto).

### 1.3 Corrección de `Application error` en pantalla `404`
- **Causa raíz reproducida en Chrome headless sobre build de producción (`/checkout/fse`):**
  1. `app/(full-page)/pages/notfound/page.tsx` invocaba `changeTheme(layoutConfig.colorScheme, 'light', 'theme-link', ...)` en `useEffect`, provocando que PrimeReact llamara a `linkElement.remove()` sobre el `<link id="theme-link">` administrado por React en `app/layout.tsx`.
  2. Al hacer clic en `"Go To Dashboard"` (`router.push('/')`), la transición suave desde `NotFoundBoundary` de Next.js lanzaba `DOMException: NotFoundError: Failed to execute 'removeChild' on 'Node': The node to be removed is not a child of this node.`, mostrando `"Application error: a client-side exception has occurred"`.
  3. Además existía un error de sintaxis CSS en `backgroundImage: 'url(/layout/images/pages/exception/bg-404.png'` (faltaba el paréntesis de cierre `)`) y desplazamiento fijo negativo.
- **Solución aplicada:**
  - **`avalon-react-10.1.0/app/(full-page)/pages/notfound/page.tsx`**: se eliminó el uso de `LayoutContext`, `PrimeReactContext` y `changeTheme`; se corrigió el CSS del fondo, se centró el contenedor de forma responsiva, se actualizó el texto a español (`404` / `Página no encontrada` / botón `Ir al Inicio`) y se reemplazó `router.push('/')` por `window.location.assign('/')`.
  - **`avalon-react-10.1.0/app/(full-page)/pages/notfound2/page.tsx`**: se actualizó `navigateToDashboard` para usar `window.location.assign('/')`.

### 1.4 Submenú dinámico de Categorías en `LandingNavbar`
- **`avalon-react-10.1.0/app/(main)/components/LandingNavbar.tsx`** y **`avalon-react-10.1.0/app/(landing)/components/LandingNavbar.tsx`**:
  - En lugar de apuntar a `/products?categories=true` (parámetro ignorado en `products/page.tsx`), el navbar consulta `productService.obtenerCategorias()` al montar.
  - Bajo **Productos -> Categorías**, se despliega un submenú dinámico con cada categoría real; al seleccionar una categoría navega a `/products?categoria=<Nombre>` usando `encodeURIComponent(cat.nombre)` (y fallback `"Ver catálogo"` hacia `/products` si aún no cargan categorías).
  - En el menú móvil (`Sidebar`), se habilitaron los subelementos de `Todos los productos` y la lista de `Categorías` para evitar botones sin acción en pantallas pequeñas.

### 1.5 Botón `Gestionar` por empresa y ruta directa de edición en vitrina
- **`avalon-react-10.1.0/app/(main)/products/page.tsx`** y **`avalon-react-10.1.0/app/(landing)/products/page.tsx`**:
  - Tanto en la tarjeta del catálogo como en el modal de detalle del producto, cuando el usuario autenticado es empleado o administrador (`authService.isEmployee()`), se evalúa `authService.canManageEmpresa(targetEmpresaId)` (usando `producto.empresa?.id` con respaldo por nombre en `empresas`).
  - Para **Emprendedor (`ROLE_EMP`)**, el botón **`Gestionar`** (ícono `pi pi-cog`) se muestra **únicamente** en productos de su empresa y navega a **`/emprendedor/productos/${producto.id}/editar`**; en productos de otras empresas no se renderiza botón y solo queda visible el precio.
  - Para **Administrador (`ROLE_ADMIN`)**, como no pertenece a una empresa puntual (`canManageEmpresa` retorna `true` globalmente), el botón **`Gestionar`** se muestra en todos los productos y navega a **`/administrador/productos/${producto.id}/editar`**.
- **`avalon-react-10.1.0/app/(main)/components/FeaturedProducts.tsx`** y **`avalon-react-10.1.0/app/(landing)/components/FeaturedProducts.tsx`**:
  - Se aplicó el mismo criterio de visibilidad y enrutamiento (`/emprendedor/productos/${producto.id}/editar` para `ROLE_EMP` en sus propios productos y `/administrador/productos/${producto.id}/editar` para `ROLE_ADMIN` en todos), corrigiendo la ruta muerta anterior `/dashboard/productos/${producto.id}`.

---

## 2. Verificación Técnica Ejecutada

| Verificación | Alcance | Resultado |
| :--- | :--- | :--- |
| TypeScript (`.\node_modules\.bin\tsc.cmd --noEmit`) | Todo `avalon-react-10.1.0` tras la última edición | Exit code `0` (0 errores) |
| Pruebas unitarias de helpers (`verify_helpers.js`) | `formValidations.ts` y `csvExport.ts` (rechazo de letras en cuenta/referencia, cédula/teléfono, BOM UTF-8, escaping RFC 4180 y anti-fórmulas) | Exit code `0` (`ALL HELPER UNIT CHECKS PASSED (22 assertions)`) |
| Compilación de producción (`.\node_modules\.bin\next.cmd build --no-lint`) | App completa Next.js (`105` páginas generadas) | Exit code `0` |
| Prueba en navegador headless Chrome (`repro404.js` sobre `next start -p 3099`) | Visita a `/checkout/fse` (HTTP `404`), clic en texto de la pantalla y clic en botón `Ir al Inicio` | Navega a `http://127.0.0.1:3099/`, carga el storefront completo con `0` errores/excepciones, exit code `0` |

---

## 3. Notas de Diagnóstico (UX y Rendimiento)

1. **Navbar (`Productos -> Categorías`):**
   - Anteriormente ambos ítems (`Todos los productos` y `Categorías`) abrían el mismo listado sin filtro porque `app/(main)/products/page.tsx` y `app/(landing)/products/page.tsx` leen `categoria`, `empresa` y `q`, pero no `categories`. Con el cambio de la sección 1.4, cada categoría seleccionada filtra directamente `/products?categoria=<Nombre>`.
2. **Latencia solo la primera vez al cambiar de opción en el panel Emprendedor:**
   - **Modo desarrollo (`next dev`):** como mostró la consola en `wait compiling /(full-page)/emprendedor/logistica/page (client and server)... (799 modules)`, Next.js compila bajo demanda ~800 módulos la primera vez que se visita cada ruta; las visitas posteriores ya quedan compiladas en caché. En build de producción (`next build && next start`) esto desaparece.
   - **Efecto secundario en `EmprendedorSidebar.tsx` (líneas 18–47):** además, un `useEffect(..., [pathname])` dispara `listarPagosPendientes(emprendedorId)` en cada cambio de ruta (aunque ya hay un listener `pagosUpdated` y un intervalo cada 30s), sumando una llamada de red extra en cada navegación.

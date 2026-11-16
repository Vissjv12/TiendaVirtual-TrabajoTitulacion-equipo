# Registro de Cambios: Pagos Deuna / Checkout (`feature/a19-pagos-deuna-checkout`) y Permisos / UI de Emprendedor (`feature/a20-permisos-emprendedor-inventario`)

Este documento completa la trazabilidad en `Documentos_Jordy/` de las mejoras implementadas después de la integración de Redis (`7d54d35`) y previas a la Etapa 6 de paginación (`80da40b`), excluyendo actividades pendientes de despliegue a producción.

---

## 1. Pagos Deuna, Comprobantes, Checkout y Sincronización Ventas–Pagos (`feature/a19-pagos-deuna-checkout`)

Commits de referencia: `fdb2ffd`, `e051dca`, `46f6f8b`.

### 1.1 Carga segura y resolución de QR Deuna (`Microservicios/demo`)
- **[ConfiguracionPagosService.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/demo/src/main/java/com/example/demo/domain/service/ConfiguracionPagosService.java):**
  - Se implementó validación binaria estricta (`validarArchivoImagenQr` / `validarYExtraerImagenQr`) con límite de `5 MB`, lista blanca de MIME (`image/jpeg`, `image/jpg`, `image/png`, `image/webp`), verificación de extensión y firma mágica + decodificación `ImageIO` (PNG/JPG) y cabecera WEBP.
  - Se protegieron las rutas al guardar y eliminar archivos QR normalizando el destino dentro de `StaticResourceConfiguration.resolveQrUploadDirectory()` con prefijo seguro `qr_emp_<id>_<uuid>.<ext>`.
  - En `obtenerQrDeuna(idOrEmpresaId)` y `obtenerDatosBancarios(idOrEmpresaId)`, se implementó resolución jerárquica por `empresa_id -> emprendedor_id` (y verificación física de existencia del archivo QR en disco, con respaldo local al QR válido más reciente si el consultado no tiene archivo en disco).
- **[StaticResourceConfiguration.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/demo/src/main/java/com/example/demo/infrastructure/config/StaticResourceConfiguration.java):**
  - Se expuso `resolveQrUploadDirectory()` usando ruta absoluta normalizada (`Paths.get("uploads", "qr-deuna").toAbsolutePath().normalize()`) como URI para servir `/uploads/qr-deuna/**`.
- **[SecurityConfig.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/demo/src/main/java/com/example/demo/infrastructure/security/SecurityConfig.java) y [ConfiguracionPagosController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/demo/src/main/java/com/example/demo/presentation/controller/ConfiguracionPagosController.java):**
  - Se habilitó acceso público `permitAll()` para `/uploads/qr-deuna/**` y se delegó la validación de la imagen cargada al servicio devolviendo `400 Bad Request` ante archivos inválidos.

### 1.2 Flujo Deuna y Transferencia en Frontend (`avalon-react-10.1.0`)
- **[configuracion-pagos/page.tsx](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(full-page)/emprendedor/configuracion-pagos/page.tsx):**
  - Se restringió el selector de imagen QR a JPG, PNG y WEBP (`accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"`), validando extensión, tipo MIME, tamaño máximo `5 MB`, limpiando la selección tras error o finalización y mostrando el mensaje exacto del backend.
- **`DeunaTransferenciaForm.tsx` en [(main)](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(main)/checkout/components/DeunaTransferenciaForm.tsx) y [(landing)](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(landing)/checkout/components/DeunaTransferenciaForm.tsx):**
  - Nuevo componente para verificación manual de pago Deuna: muestra el código QR del emprendedor, número de cuenta origen, número de referencia/comprobante y carga opcional de comprobante (imagen o PDF hasta `5 MB`) con `itemTemplate` limpio (`Tag` `"Adjunto"` y botón de eliminación).
- **`checkout/page.tsx`, `PaymentMethodSelector.tsx`, `DeunaForm.tsx` y `TransferenciaBancariaForm.tsx` en `(main)` y `(landing)`:**
  - `PaymentMethodSelector.tsx` ahora sincroniza el estado con `selectedMethod` controlado (`'transferencia'` por defecto en `checkout/page.tsx`).
  - `DeunaForm.tsx` resuelve `empresaId` del primer producto del carrito y distingue el error `404` cuando el emprendedor aún no configura su QR.
  - `checkout/page.tsx` reemplaza `<DeunaForm>` por `<DeunaTransferenciaForm>` y ejecuta `cartService.clearCart()` tras registrar la orden.
  - `TransferenciaBancariaForm.tsx` unifica la vista previa de comprobante adjunto con `itemTemplate` y oculta el botón redundante de subida manual (`uploadOptions={{ className: 'hidden' }}`).

### 1.3 Sincronización de Ventas, Pagos, Inventario y Sesión (`msvc-ventas` y Frontend)
- **[VentaServiceImpl.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/application/service/VentaServiceImpl.java) y [VentaMapper.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/application/mapper/VentaMapper.java):**
  - Al completar una venta (`COMPLETADA`), se sincroniza `estadoPago = APROBADO`, se actualiza el registro en `PagoRepository` si estaba `PENDIENTE`, se inicializa el seguimiento logístico y se dispara la generación de factura PDF en segundo plano.
  - Al cancelar una venta (`CANCELADA`), si el pago seguía `PENDIENTE`, pasa a `RECHAZADO` tanto en venta como en `PagoEntity`.
  - `VentaMapper` protege el mapeo cuando `venta.getDetalles()` retorna nulo tras persistencia JPA.
- **[PagoService.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/domain/service/PagoService.java):**
  - Al aprobar un pago (`APROBADO`), si la venta seguía en estado `PENDIENTE`, descuenta el stock en `msvc-inventario` mediante `inventarioClient.procesarSalidaLote(...)` y actualiza `venta.setEstadoPago(APROBADO)`.
  - Al rechazar un pago (`RECHAZADO`), también actualiza `venta.setEstadoPago(RECHAZADO)`.
- **[FeignConfig.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/infrastructure/config/FeignConfig.java):**
  - Evita adjuntar encabezados `Authorization: Bearer ...` en peticiones `GET` internas públicas o cuando el token está vacío, es `"null"`, `"undefined"` o no cumple formato JWT de 3 segmentos.
- **Autenticación y refresco de pagos pendientes en Frontend:**
  - [cartService.ts](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/services/cartService.ts) y [clientesService.ts](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/services/clientesService.ts) migraron de `localStorage.getItem('token')` a `authService.getAuthHeaders()`.
  - [EmprendedorSidebar.tsx](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(full-page)/emprendedor/components/EmprendedorSidebar.tsx) escucha el evento `pagosUpdated` y cambios de `pathname` para actualizar en tiempo real el contador de pagos pendientes cuando se procesan pagos en [PagosEmprendedor.tsx](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(full-page)/emprendedor/pagos/PagosEmprendedor.tsx), [emprendedor/ventas/page.tsx](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(full-page)/emprendedor/ventas/page.tsx) y [emprendedor/ventas/[id]/page.tsx](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(full-page)/emprendedor/ventas/[id]/page.tsx).

---

## 2. Permisos de Emprendedor, Registro Inicial de Inventario y Fix de Barra Superior (`feature/a20-permisos-emprendedor-inventario`)

Commits de referencia: `1dcb7e8`, `225a594`.

### 2.1 Permisos para `ROLE_EMP` en `msvc-producto`
- **[SecurityConfig.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/config/SecurityConfig.java):**
  - Exige autenticación explícita también para `POST`, `PUT` y `DELETE` sobre `/api/categorias/**` y `/api/empresas/**`.
- **[CategoriaController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/presentation/controller/CategoriaController.java):**
  - `POST /api/categorias` habilitado para `hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')`.
- **[EmpresaController.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/presentation/controller/EmpresaController.java):**
  - `PUT /api/empresas/{id}` habilitado para `hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')` validando propiedad con `productoAuthorizationService.canManageCompany(authentication, id)` (`AccessDeniedException` `403` si intenta editar otra empresa).
- **[ProductoControllerApiAuthorizationTest.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-producto/src/test/java/com/example/msvc_producto/ProductoControllerApiAuthorizationTest.java):**
  - Se añadieron 3 pruebas `@WebMvcTest`: `emprendedorPuedeCrearCategoria`, `emprendedorPuedeActualizarSuPropiaEmpresa` y `emprendedorNoPuedeActualizarOtraEmpresa`.

### 2.2 Registro Inicial de Inventario (`msvc-inventario` y Frontend)
- **[InventarioServiceImpl.java](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/application/service/InventarioServiceImpl.java):**
  - Cuando un producto ya posee un inventario autocreado en `0`, `crearInventario(...)` actualiza cantidad, ubicación y estado activo en lugar de fallar con `"Ya existe un inventario para este producto"`, registra un `MovimientoInventario` de tipo `ENTRADA` con motivo `"Registro inicial de inventario"` y notifica asíncronamente al catálogo mediante `notificarStockProductoAsync`.
- **[inventarioService.ts](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/services/inventarioService.ts):**
  - Extrae mensajes estructurados JSON (`mensaje`) o mediante `authService.extractErrorMessage` ante respuestas de error.

### 2.3 Corrección Visual de Barra Superior en Paneles
- **[AdminHeader.tsx](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(full-page)/administrador/components/AdminHeader.tsx), [EmprendedorNavbar.tsx](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/app/(full-page)/emprendedor/components/EmprendedorNavbar.tsx), [admin-layout.scss](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/styles/layout/admin-layout.scss) y [emprendedor-layout.scss](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/avalon-react-10.1.0/styles/layout/emprendedor-layout.scss):**
  - Se reemplazó `.layout-topbar` (posicionamiento fijo superpuesto) por `.admin-navbar-inner` / `.emprendedor-navbar-inner` con `position: relative`, `height: 70px` y `overflow-x: clip` en los wrappers para conservar `position: sticky` sin tapar los títulos del contenido.

---

## 3. Índice de Documentación Vigente en `Documentos_Jordy/` (desde Deuna)

| Etapa / Rama | Documento en `Documentos_Jordy/` |
| :--- | :--- |
| `feature/a19-pagos-deuna-checkout` y `feature/a20-permisos-emprendedor-inventario` | [`REGISTRO-DEUNA-CHECKOUT-Y-PERMISOS-EMPRENDEDOR.md`](REGISTRO-DEUNA-CHECKOUT-Y-PERMISOS-EMPRENDEDOR.md) |
| Etapa 6 — Paginación (`feature/a19-a22-paginacion`) | [`A19-CONTRATO-PAGINACION.md`](A19-CONTRATO-PAGINACION.md) y [`A22-RESULTADOS-PAGINACION.md`](A22-RESULTADOS-PAGINACION.md) |
| Etapa 7 — Seguridad `msvc-ventas` y Regresión (`feature/a24-a25-seguridad-ventas`) | [`A24-A25-SEGURIDAD-MSVC-VENTAS-Y-REGRESION.md`](A24-A25-SEGURIDAD-MSVC-VENTAS-Y-REGRESION.md) |
| Pulido pre-integración (`feature/pulido-export-validaciones-404`) | [`REGISTRO-PULIDO-PRE-INTEGRACION.md`](REGISTRO-PULIDO-PRE-INTEGRACION.md) |

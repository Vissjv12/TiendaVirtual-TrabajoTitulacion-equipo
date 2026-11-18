# Prueba 2: Ejecutar el Flujo Completo de Compra

- **Objetivo:** Validar el circuito completo desde la vitrina pública (`/landing`) hasta la confirmación del pago, descuento único de inventario y emisión de factura PDF.
- **URL probada:** `https://tienda.gadmsigchos.gob.ec:444/landing`
- **Estado:** **APROBADA AL 100% EN VIVO (flujo completo automático de primera intención en producción)** — Validado de punta a punta con compra nueva desde cero (`ventaId = 9`, `FACT-1794933559721`, `$7.76`): `POST /api/ventas` (`201`) -> `.../preparar` (`200`) -> PayPhone Sandbox (`Approved`) -> `/checkout/payphone-confirmacion` con **`resultadoVenta = "COMPLETADA"` directo al primer intento**, descuento automático de stock (`296 -> 295`), **pre-generación asíncrona automática del PDF** (`factura_FACT-1794933559721.pdf`, `FACTURAS_TOTAL = 20`, `0` errores) y descarga HTTP **`200`** en `/profile/historial-compras`.

---

## 1. Evidencia real observada en Chrome DevTools (`ROLE_USER` autenticado)

1. **Sesión autenticada (`cliente 1`):**
   - Navbar muestra usuario autenticado (`Mi Cuenta` -> rol `Cliente`, `ID Usuario #6`).
   - En `/profile/historial-compras` (`200`): `GET /api/clientes/email/...` (`200`) y `GET /api/ventas/cliente/1/paginado?page=0&size=10&sort=fechaVenta%2Cdesc` (`200`) renderizan `3` compras previas `COMPLETADA`.
   - **Verificación de detalle y descarga real de factura PDF (solo lectura sobre `FACT-1784306382838`, `$6.15`):**
     - `GET /api/seguimiento-logistica/venta/6` -> **`200`** (modal `"Detalle de la Compra"`, `Pago aprobado` / `Pago automático con tarjeta`).
     - `GET /api/ventas/6/factura` -> **`200`** (descarga PDF `"Descargando: Generando factura PDF..."`).
2. **Selección de producto de `Sigcholac` (`empresaId=2`) y carrito (`/cart`):**
   - Producto agregado: **`Queso fresco | 500g`** (ID `14`, categoría `Lácteos`, precio `$2.40`, `inventarioCantidad = 297`).
   - `/cart` (`200`) consultó `GET /api/emprendedor/configuracion-pagos/costo-envio/2` -> **`200`** (`{"empresaId":2,"costoEnvio":5.00}`).
3. **Pantalla `/checkout` (`200`) — Pasos 1, 2 y 3:**
   - Llamadas verificadas al Gateway (`200`):
     - `GET /api/emprendedor/configuracion-pagos/bancarios/2` -> `200`
     - `GET /api/emprendedor/configuracion-pagos/deuna-qr/2` -> `200`
     - `GET /api/emprendedor/configuracion-pagos/payphone/2` -> `200` (`{"empresaId":2,"disponible":true}`)
     - `GET /uploads/qr-deuna/2_8d69ec19-f893-4db5-9f65-5f4859ae7a30_test_qr.png` -> `200` (QR cargado en pantalla)
   - **Paso 1 (`Datos de Envío`):** pre-carga nombre/apellido/email y valida cédula, teléfono y dirección.
   - **Paso 2 (`Revisión`):** `1x Queso fresco | 500g` -> Subtotal `$2.40` + Impuestos (15%) `$0.36` + Envío `$5.00` = **Total `$7.76`**.
   - **Paso 3 (`Pago`):** muestra activos los 3 métodos configurados para Sigcholac:
     1. **Transferencia Bancaria** (muestra cuenta bancaria configurada + campos de banco origen, cuenta origen, referencia y comprobante).
     2. **Deuna** (muestra imagen QR de Sigcholac en `200` + campos de comprobante).
     3. **Payphone** (`"Pagar con app Payphone"` y `"Pagar con tarjeta"` por `$7.76`).
   - **Observaciones menores en consola:** los 2 avisos `404` de fuentes Roboto 700, advertencias de `google.accounts.id.initialize()` múltiple y atributos `for`/`id`/`name` en formularios; `0` errores 5xx o fallos de API de negocio.

---

## 2. Infraestructura e integridad previa verificada (BD + disco)

- `9/9` servicios `active` (`msvc-ventas` con Camunda `v7.20.0`, `msvc-inventario` y `auth-service` `UP` en Eureka).
- `ventas.ventas` contiene `6` ventas previas (`COMPLETADA`), `/var/www/tienda-virtual-backend/msvc-ventas/facturas` conserva `17` PDFs y `uploads/qr-deuna` conserva la imagen QR de Sigcholac.
- `POST /api/ventas` sin autenticación devuelve `401`.

---

## 3. Ejecución de transacción real de prueba en PayPhone Sandbox (`ventaId = 7`)

Con autorización del usuario se ejecutó el flujo completo de pago con tarjeta de pruebas en PayPhone Sandbox:

1. **Creación y preparación:**
   - `POST /api/ventas` -> **`201 Created`** (`ventaId = 7`, `numero_factura = FACT-1794928839706`, `1x Queso fresco | 500g`, `TARJETA`, total `$7.76`).
   - `POST /api/emprendedor/configuracion-pagos/payphone/ventas/7/preparar` -> **`200 OK`** (`auth-service` descifró el token `enc:v1:` de Sigcholac con `PAYPHONE_TOKEN_ENC_KEY`).
   - Redirección correcta a `https://pay.payphonetodoesposible.com/Anonymous/Index` mostrando `TOTAL: (TEST) $7.76`.
2. **Aprobación y retorno:**
   - Tras completar el pago sandbox en Chrome, PayPhone redirigió a `https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion?id=93752016&clientTransactionId=FD-7-e08959b6534546088cb3` (`200`).
   - Frontend invocó `POST /api/emprendedor/configuracion-pagos/payphone/confirmar` -> **`200 OK`**: `statusCode: 3`, `transactionStatus: "Approved"`, `authorizationCode: "W93752016"`, `amount: 776`, **`resultadoVenta: "STOCK_PENDIENTE"`**.
3. **Bug real detectado en producción y corrección aplicada localmente:**
   - Log real en `msvc-ventas` (`PagoPasarelaService`):
     `Pago aprobado para venta 7 pero no se pudo descontar inventario: Forbidden`
   - **Causa raíz:** [`SecurityConfig.java`](../../Microservicios/msvc-inventario/src/main/java/com/example/msvc_inventario/infrastructure/config/SecurityConfig.java#L50-L51) de `msvc-inventario` permitía `POST /api/inventarios/salida-lote` únicamente a `ROLE_ADMIN` y `ROLE_EMP`, bloqueando con `403` la credencial interna `ROLE_SERVICIO_PAGOS` emitida por `auth-service` (a pesar de que `InventarioController` e `InventarioAuthorizationService` sí la aceptaban).
   - **Estado actual de `ventaId = 7` en el VPS antes de desplegar el fix:** quedó registrada en `ventas.ventas` como `PENDIENTE` (pago `APROBADO`), `producto_id = 14` conserva stock `297`, y `facturas/` mantiene `18` PDFs.
   - **Corrección ya lista en local (`feature/fix-inventario-servicio-pagos` -> `main` `eac5ec7`):** agregado `ROLE_SERVICIO_PAGOS` en `SecurityConfig.java` + 3 tests en [`InventarioControllerApiAuthorizationTest.java`](../../Microservicios/msvc-inventario/src/test/java/com/example/msvc_inventario/InventarioControllerApiAuthorizationTest.java) (`59/59` pasando) y `.jar` reempaquetado listo para subir en `documentos-trabajo/artefactos-produccion/backend/msvc-inventario/`.

---

## 4. Despliegue del fix y cierre exitoso de `ventaId = 7` en producción

1. **Despliegue de `msvc-inventario` (`main` `eac5ec7`):**
   - Subido `msvc-inventario-0.0.1-SNAPSHOT.jar` (SHA-256 `9eeaf2b8d3d05ef49d48b2b2f36a0733c29185f71d86b18cd41d782f988f1c80`), contexto SELinux `httpd_sys_content_t`, respaldo previo en `/root/backups/msvc-inventario_pre_fix_20261117.jar` y `MSVC-INVENTARIO` en Eureka **`UP`**.
2. **Reconfirmación vía webhook real (`POST https://tienda.gadmsigchos.gob.ec:444/api/payphone/confirm`):**
   - **1.ª llamada webhook:** `{"recibido":true}` (`HTTP 200`).
   - **2.ª llamada webhook (prueba de idempotencia):** `{"recibido":true}` (`HTTP 200`).
3. **Verificación final en MySQL:**
   - `ventas.ventas` (`id=7`, `FACT-1794928839706`): **`estado = COMPLETADA`**, **`estado_pago = APROBADO`**, `metodo_pago = TARJETA`, `total = 7.76`, `referencia_transaccion = 93752016`.
   - `ventas.pagos` (`venta_id=7`): `monto = 7.76`, `estado_pago = APROBADO`.
   - `inventario.inventarios` (`producto_id=14`, `Queso fresco | 500g`): **`cantidad = 296`** *(descontado exactamente **1 vez** desde `297` a pesar de las 2 llamadas de webhook)*.
   - `inventario.movimientos_inventario`: registro único `id = 79` (`SALIDA`, `cantidad = 1`, `motivo = Venta #FACT-1794928839706`).
4. **Corrección desplegada para generación asíncrona de factura (`msvc-ventas`, `main` `f8a2006`):**
   - El hilo asíncrono `[factura-1]` había registrado `LazyInitializationException` sobre `ClienteEntity#1` en [`FacturaServiceImpl.generarFacturaPDF`](../../Microservicios/msvc-ventas/src/main/java/com/example/msvc_ventas/application/service/FacturaServiceImpl.java#L41) por carecer de sesión Hibernate fuera de una petición HTTP.
   - Se agregó `@Transactional(readOnly = true)` a `FacturaServiceImpl` junto con el test [`FacturaServiceTransaccionalTest.java`](../../Microservicios/msvc-ventas/src/test/java/com/example/msvc_ventas/application/service/FacturaServiceTransaccionalTest.java) (`72/72` pruebas pasando en `msvc-ventas`), se fusionó a `main` (`f8a2006`), se respaldó el JAR previo en `/root/backups/msvc-ventas_pre_fix_20261117.jar` y se desplegó el nuevo `.jar` (SHA-256 `41504fa234d582cf7620164ca738b8bb8c522fe168c6de7cf379f84781101ef0`).
5. **Verificación final en Chrome como `cliente 1` (`FACT-1794928839706`, `ventaId = 7`):**
   - `/profile/historial-compras` (`GET /api/ventas/cliente/1/paginado` -> `200`) muestra `FACT-1794928839706` en estado **`COMPLETADA`**.
   - `"Detalle"` (`GET /api/seguimiento-logistica/venta/7` -> `200`) muestra `estadoLogistica: PAGO_APROBADO` (`Pago PAYPHONE verificado`, `"El pago ha sido verificado y aprobado por el emprendedor"`).
   - Descarga de factura PDF (`GET /api/ventas/7/factura` -> **`200 OK`**, toast `"Éxito: Factura descargada correctamente"`), confirmando en el VPS la creación de `/var/www/tienda-virtual-backend/msvc-ventas/facturas/factura_FACT-1794928839706.pdf` (`1.9K`, **`FACTURAS_TOTAL = 19`**, **`RECENT_ERRORS = 0`** en `msvc-ventas`).
---

## 5. Prueba final en vivo de punta a punta sin reintentos (`ventaId = 9`, `main` `f8a2006`)

Tras desplegar ambos fixes (`msvc-inventario` `eac5ec7` y `msvc-ventas` `f8a2006`), se ejecutó una compra **nueva desde cero** en Chrome (`cliente 1`) con `1` unidad de **`Queso fresco | 500g`** (`Sigcholac`, stock inicial `296`):

1. **Creación, preparación y pasarela:**
   - `POST /api/ventas` -> **`201 Created`** (`ventaId = 9`).
   - `POST /api/emprendedor/configuracion-pagos/payphone/ventas/9/preparar` -> **`200 OK`**.
   - PayPhone Sandbox abrió en `TOTAL: (TEST) $7.76` y se aprobó la transacción `93755596`.
2. **Retorno automático al primer intento (`200 OK`):**
   - URL de retorno: `https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion?id=93755596&clientTransactionId=FD-9-d4912170b71d480aa2a4`.
   - `POST /api/emprendedor/configuracion-pagos/payphone/confirmar` devolvió **`resultadoVenta: "COMPLETADA"`** de inmediato (`statusCode: 3`, `transactionStatus: "Approved"`, `authorizationCode: "W93755596"`, `numeroFactura: "FACT-1794933559721"`), mostrando `"¡Pago Exitoso!"` sin aviso de stock pendiente.
3. **Efectos automáticos verificados en BD y disco (antes de hacer clic en descargar PDF):**
   - `ventas.ventas` (`id=9`): **`estado = COMPLETADA`**, **`estado_pago = APROBADO`**, `total = 7.76`, `referencia_transaccion = 93755596`.
   - `inventario.inventarios` (`producto_id=14`): **`cantidad = 295`** (descontado automáticamente **`296 -> 295`**, movimiento `id=80` `SALIDA`, `Venta #FACT-1794933559721`).
   - Disco (`/var/www/tienda-virtual-backend/msvc-ventas/facturas/`): creado automáticamente en segundo plano **`factura_FACT-1794933559721.pdf`** (`1859` bytes, **`FACTURAS_TOTAL = 20`**) con **`0` errores** en `journalctl`.
4. **Descarga en `/profile/historial-compras`:**
   - `FACT-1794933559721` mostró **`COMPLETADA`** de inmediato y `GET /api/ventas/9/factura` respondió **`200 OK`** (`"Éxito: Factura descargada correctamente"`).

---

## 6. Único caso opcional no ejecutado en transacción real (Transferencia / DeUna)
- Aunque en `/checkout` se validaron `GET /api/emprendedor/configuracion-pagos/bancarios/2` (`200`), `deuna-qr/2` (`200`) y la carga de la imagen QR (`200`), no se envió una orden separada por **Transferencia / DeUna** para evitar crear ventas adicionales con comprobantes ficticios en producción.

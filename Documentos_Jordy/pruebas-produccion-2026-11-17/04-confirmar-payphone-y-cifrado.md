# Prueba 4: Confirmar PayPhone y Cifrado en la Configuración Desplegada

- **Objetivo:** Verificar que `PAYPHONE_TOKEN_ENC_KEY` está configurada con clave AES-256-GCM válida, que los tokens guardados en `auth_service.configuracion_metodos_pago` están cifrados con `enc:v1:`, que las URLs de PayPhone apuntan a producción (`:444`), que Stripe sigue apagado y que los endpoints de PayPhone no filtran secretos ni aceptan payloads inválidos.
- **Estado:** **APROBADA AL 100% EN VIVO** — Configuración, clave AES-256-GCM en runtime, cifrado `enc:v1:` en BD, seguridad de endpoints (sin fuga de token) y transacción E2E en PayPhone Sandbox (`ventaId = 7`, conciliación idempotente vía webhook `/api/payphone/confirm` hasta `COMPLETADA`) verificadas completamente en producción.

---

## 1. Variables de Entorno y Runtime de `auth-service`

Verificado sobre `/etc/tienda-virtual/backend.env` (`chmod 600`, contexto SELinux `etc_t`) y sobre el proceso vivo de `auth-service` (`/proc/<PID>/environ`, sin imprimir valores secretos):

| Variable | Estado / Valor verificado | Fuente |
|---|---|---|
| `PAYPHONE_TOKEN_ENC_KEY` | **PRESENTE**, decodifica exactamente **`32` bytes** (`encoded_len=44` en Base64) | `.env` + proceso vivo `auth-service` |
| `PAYPHONE_API_BASE_URL` | `https://pay.payphonetodoesposible.com` | `/etc/tienda-virtual/backend.env` |
| `PAYPHONE_RESPONSE_URL` | `https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion` | `.env` + proceso vivo `auth-service` |
| `PAYPHONE_CANCELLATION_URL` | `https://tienda.gadmsigchos.gob.ec:444/checkout` | `/etc/tienda-virtual/backend.env` |
| `MSVC_VENTAS_URL` | `http://127.0.0.1:8083` | `/etc/tienda-virtual/backend.env` |
| `STRIPE_ENABLED` | `false` | `/etc/tienda-virtual/backend.env` |

### Implementación desplegada en `auth-service` (`demo`)
- [`PayphoneTokenCifradoConverter.java`](../../Microservicios/demo/src/main/java/com/example/demo/infrastructure/security/PayphoneTokenCifradoConverter.java): `AES/GCM/NoPadding`, IV aleatorio de `12` bytes, etiqueta de `128` bits, `AAD = "configuracion_metodos_pago.payphone_token"`, prefijo `enc:v1:`, comportamiento **fail-closed** si falta o es inválida la clave de 32 bytes.
- [`PayphoneTokenMigracion.java`](../../Microservicios/demo/src/main/java/com/example/demo/infrastructure/security/PayphoneTokenMigracion.java): migración idempotente al arranque (`ApplicationRunner`) que cifra tokens heredados en texto plano sin registrar tokens en logs.

---

## 2. Verificación en Base de Datos (`auth_service.configuracion_metodos_pago`)

- **Esquema de columnas relevantes:**
  - `payphone_token`: tipo `text` (soporta longitud completa del token cifrado).
  - `costo_envio`: `decimal(10,2) DEFAULT 5.00`.
- **Estado de los registros:**
  - `total_configs`: `1`
  - `cifrados_enc_v1`: **`1`**
  - `texto_plano_pendiente`: **`0`**
  - Registro `id=1` (`emprendedor_id=2`): `tiene_app_id=1`, `prefijo_token = enc:v1:`, `len_cifrado = 507`, `costo_envio = 5.00`.

---

## 3. Verificación HTTP/HTTPS de Endpoints PayPhone y Webhook Next.js

| Endpoint | Prueba realizada | Resultado observado |
|---|---|---|
| `GET /api/emprendedor/configuracion-pagos/payphone/2` | Disponibilidad pública para empresa `2` | **`200`** -> `{"empresaId":2,"disponible":true}` *(no expone token ni `appId`)* |
| `GET /api/emprendedor/configuracion-pagos/payphone/{1,3,4}` | Disponibilidad pública para empresas `1`, `3`, `4` | **`200`** -> `{"empresaId":<id>,"disponible":false}` |
| `GET /api/emprendedor/configuracion-pagos/costo-envio/1` | Consulta pública de envío | **`200`** -> `{"empresaId":1,"costoEnvio":5.00}` |
| `GET /api/emprendedor/configuracion-pagos` | Sin JWT | **`401`** |
| `POST /api/emprendedor/configuracion-pagos/payphone` | Guardar token sin JWT | **`401`** |
| `POST /api/emprendedor/configuracion-pagos/payphone/ventas/1/preparar` | Preparar pago sin JWT | **`401`** |
| `POST /api/payphone/confirm` | Webhook Next.js ([`route.ts`](../../avalon-react-10.1.0/app/api/payphone/confirm/route.ts)) con cuerpo vacío inválido | **`400`** *(validado antes de reenviar al backend interno `127.0.0.1:8080`, sin alterar BD)* |
| `GET /api/emprendedor/configuracion-pagos` | Sesión autenticada **`ROLE_EMP`** en Chrome (`/emprendedor/configuracion-pagos`) | **`200`** -> devuelve `tokenConfigurado: true` y `payphoneAppId` sin exponer **ni el token en claro ni el texto cifrado `enc:v1:`**; la UI muestra `"Token configurado. Por seguridad no se muestra."` *(Hallazgo C-02 confirmado en vivo)* |

---

## 4. Prueba Sandbox Ejecutada en Vivo (`ventaId = 7`, `$7.76`)

- **Preparación y pasarela (`200 OK`):** `POST /api/emprendedor/configuracion-pagos/payphone/ventas/7/preparar` descifró correctamente el token `enc:v1:` de `Sigcholac` usando `PAYPHONE_TOKEN_ENC_KEY`, validó el total (`$7.76`) y abrió PayPhone en modo **`(TEST) $7.76`**.
- **Retorno y confirmación (`200 OK`):** tras aprobar el pago sandbox, PayPhone redirigió a `https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion?id=93752016&clientTransactionId=FD-7-e08959b6534546088cb3` y `POST .../payphone/confirmar` respondió `statusCode: 3`, `transactionStatus: "Approved"`, `authorizationCode: "W93752016"`, `resultadoVenta: "STOCK_PENDIENTE"`.
- **Bloqueo detectado, corregido y verificado en producción (`eac5ec7` y `f8a2006`):**
  - Inicialmente `msvc-inventario` rechazó con `403` el token interno `ROLE_SERVICIO_PAGOS` en `ventaId = 7`; tras desplegar el fix, la doble llamada al webhook **`POST /api/payphone/confirm`** (`HTTP 200`) cerró `ventaId = 7` a **`COMPLETADA`** / **`APROBADO`** con descuento único `297 -> 296`.
  - Posteriormente se desplegó `@Transactional(readOnly = true)` en `FacturaServiceImpl` (`f8a2006`) y se repitió una compra **nueva desde cero (`ventaId = 9`, `FACT-1794933559721`, ref `93755596`)**: `confirmar` devolvió **`resultadoVenta = "COMPLETADA"` al primer intento**, el stock bajó **`296 -> 295`** (`movimiento id=80`), el PDF se pre-generó automáticamente en segundo plano (`FACTURAS_TOTAL = 20`) y se descargó en **`200 OK`**.

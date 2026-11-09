# A-11 — Diseño de la Estrategia del API Gateway (Rutas, Puertos, CORS, Errores y Responsabilidades)

- **Actividad del cronograma:** `A-11` — Diseñar estrategia del gateway
- **Etapa:** `Etapa 4 — API Gateway`
- **Rama:** `feature/a11-diseno-gateway` (derivada de `integration/etapa5-redis`)
- **Observaciones de auditoría atendidas:** `O-11` (Ausencia de un API Gateway centralizado), `O-13` (Políticas CORS dispersas e inconsistentes) y `O-06` (Alineación de ruta de Deuna QR)
- **Riesgo técnico mitigado:** `R-01` (Caída de memoria OOM en el servidor municipal de Sigchos)

---

## 1. Propósito y Alcance

Este documento establece el diseño técnico y el contrato formal de la **Etapa 4 (API Gateway)** para la plataforma **Feria Digital Sigchos**. Define la arquitectura perimetral, la tabla única de enrutamiento hacia los cuatro microservicios de negocio, el esquema de puertos públicos e internos, la centralización de políticas CORS, el contrato de errores en formato JSON, las reglas de propagación de cabeceras y las responsabilidades de cada integrante para las actividades **A-12**, **A-13** y **A-14**.

El diseño respeta las garantías de seguridad e inventario ya integradas en las etapas previas (`A-01` a `A-08`) y es completamente independiente de la implementación de caché en Redis (`Etapa 5`), evitando bloqueos o conflictos entre paquetes de trabajo.

---

## 2. Decisión Arquitectónica: Selección de NGINX como API Gateway Único

De acuerdo con las especificaciones del servidor municipal de Sigchos (`AlmaLinux`, 2 vCPU, `3.6 GB` de RAM total y `~1.4 GB` de RAM disponible), se evaluaron las dos alternativas de enrutamiento perimetral y se seleccionó **NGINX Reverse Proxy / API Gateway** como la única implementación activa para pruebas y producción, descartando `Spring Cloud Gateway` para evitar sobrecarga de memoria (`R-01`):

| Criterio | NGINX Reverse Proxy / API Gateway (Seleccionado) | Spring Cloud Gateway (`msvc-gateway` — Descartado) |
|---|---|---|
| **Huella de Memoria RAM** | **~15 MB – 25 MB** (`mem_limit: 64m` en Docker) | **~300 MB – 450 MB** (requiere una JVM adicional) |
| **Tiempo de Arranque** | `< 1 segundo` | `15 – 30 segundos` |
| **Impacto sobre R-01 (OOM)** | **Nulo:** preserva más del 98% de la RAM libre para MySQL, Redis y microservicios | **Alto en servidor de 3.6 GB:** riesgo de caídas por falta de memoria (`OOM`) |
| **Gestión de Rutas y CORS** | Bloques `location` deterministas, manejo nativo de `OPTIONS` y `proxy_hide_header` | Filtros en `application.yml` con sobrecarga reactiva Netty |
| **Decisión en el Proyecto** | **Implementación única y activa** en contenedor ligero (`gateway/nginx.conf`) para `A-12`, `A-13` y `A-14` | **Descartado** para no saturar recursos en el servidor municipal ni en las máquinas de desarrollo |

### 2.1 Separación de Tráfico Norte-Sur vs. Este-Oeste

```
[ Navegador / Cliente Next.js (Puerto 3000) ]
                     │
                     │ Tráfico Norte-Sur (HTTP/HTTPS único punto de entrada)
                     ▼
      ┌──────────────────────────────┐
      │   API GATEWAY (Puerto 8080)  │  ◄── CORS centralizado, validación perimetral,
      │   (o 8090 en Windows local)  │      bloqueo de rutas internas, errores JSON
      └──────────────┬───────────────┘
                     │ Red interna (127.0.0.1 / feria-network)
       ┌─────────────┼─────────────┬────────────────┐
       ▼             ▼             ▼                ▼
┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
│auth-service│ │msvc-produc.│ │msvc-invent.│ │msvc-ventas │
│   :8084    │ │   :8081    │ │   :8082    │ │   :8083    │
└────────────┘ └─────┬──────┘ └─────▲──────┘ └─────┬──────┘
                     │              │              │
                     └──────────────┴──────────────┘
                      Tráfico Este-Oeste (Feign directo + Bearer Token A-07)
```

1. **Tráfico Norte-Sur (Cliente ↔ Backend):** Todas las solicitudes originadas en el navegador del cliente o en el servidor Next.js ingresan exclusivamente a través del **API Gateway**.
2. **Tráfico Este-Oeste (Microservicio ↔ Microservicio):** Las llamadas internas mediante clientes Feign (`msvc-producto` ↔ `msvc-inventario` y `msvc-ventas` → `msvc-producto` / `msvc-inventario`) **no pasan por el Gateway público**; se mantienen directas por la red interna usando `app.msvc-producto.url` y `app.msvc-inventario.url` con el `FeignConfig` implementado en `A-07` que propaga el encabezado `Authorization: Bearer <token>`. Esto evita latencia adicional de doble salto y previene bucles de red.

---

## 3. Mapa de Puertos, Red y Variables de Entorno

### 3.1 Asignación de Puertos y Exposición Perimetral

| Componente | Nombre de Servicio / Contenedor | Puerto Estándar | Exposición sin Gateway (Actual) | Exposición con Gateway (Objetivo Etapa 4) |
|---|---|---:|---|---|
| **Frontend Web** | `avalon-react` / `frontend-ecommerce` | `3000` | Público (`0.0.0.0:3000`) | **Público** (`0.0.0.0:3000` / `80` / `443`) |
| **API Gateway** | `api-gateway` (`nginx-gateway` / `msvc-gateway`) | `8080` *(o `8090` local)* | No existe | **Público** (Único punto de entrada API) |
| **Autenticación y Pagos Config** | `demo` (`login` / `auth-service`) | `8084` | Directo (`localhost:8084`) | **Interno** (`127.0.0.1:8084` / red Docker) |
| **Catálogo, Categorías y Empresas** | `msvc-producto` | `8081` | Directo (`localhost:8081`) | **Interno** (`127.0.0.1:8081` / red Docker) |
| **Inventario y Movimientos** | `msvc-inventario` | `8082` | Directo (`localhost:8082`) | **Interno** (`127.0.0.1:8082` / red Docker) |
| **Ventas, Pagos, Clientes y BPM** | `msvc-ventas` | `8083` | Directo (`localhost:8083`) | **Interno** (`127.0.0.1:8083` / red Docker) |
| **Descubrimiento Eureka** | `eureka-server` | `8761` | Local (`localhost:8761`) | **Interno** (Solo registro de servicios) |
| **Base de Datos Principal** | `mysql-feria-digital` | `3306` | Local (`localhost:3306`) | **Interno** (`127.0.0.1:3306` / red Docker) |
| **Base de Datos de Pruebas (A-02)** | `mysql-feria-digital-test` | `3307` | Local (`localhost:3307`) | **Interno** (Aislado en `feria-test-network`) |
| **Caché Redis (Etapa 5)** | `redis-feria-digital` / `test` | `6379` / `6380` | Local | **Interno** (`127.0.0.1` / red Docker) |

### 3.2 Consideración Operativa del Puerto `8080` en Windows Local

Tal como se documentó en `A01-INVENTARIO-RECURSOS-Y-PERMISOS.md` (sección *Superficie de API*) y en `REGISTRO-SEGURIDAD-A08-A10.md` (sección `1.4`), en máquinas de desarrollo Windows con VMware instalado, el proceso del sistema `vmnat.exe` suele ocupar el puerto `8080`.

Para evitar bloqueos en el equipo:
- El puerto externo del Gateway se parametriza mediante la variable de entorno **`GATEWAY_PORT`** (valor por defecto `8080` en Linux/producción, pudiendo definirse como `8090` o `8080` en el `.env` local según disponibilidad).
- En el frontend (`A-13`), la URL del Gateway se consume desde **`NEXT_PUBLIC_GATEWAY_URL`** (por ejemplo `http://localhost:8080` o `http://localhost:8090`), sin codificar puertos fijos en los servicios TypeScript.

---

## 4. Tabla Oficial de Rutas del API Gateway

Todos los controladores actuales utilizan prefijos de recurso bien diferenciados bajo `/api/...` (además de `/uploads/qr-deuna/**` en `auth-service`), por lo que **no existen colisiones de rutas entre microservicios** y el Gateway puede preservar el path íntegro (`proxy_pass` directo sin reescritura destructiva de prefijos).

### 4.1 Rutas Enrutadas hacia `auth-service` (`demo` — Puerto `8084`)

| ID Ruta | Prefijo / Patrón en Gateway | Métodos Permitidos | Destino Upstream (`8084`) | Autorización Efectiva | Consumidor en Frontend |
|---|---|---|---|---|---|
| **GW-AUTH-01** | `/api/auth/login`<br>`/api/auth/registro`<br>`/api/auth/google` | `POST, OPTIONS` | `http://auth-service:8084/api/auth/*` | **Público** (`permitAll`) | `authService.ts` |
| **GW-AUTH-02** | `/api/auth/recuperar-password/**` | `POST, OPTIONS` | `http://auth-service:8084/api/auth/recuperar-password/*` | **Público** (`permitAll`) | `authService.ts` |
| **GW-AUTH-03** | `/api/usuarios`<br>`/api/usuarios/**` | `GET, POST, PUT, PATCH, DELETE, OPTIONS` | `http://auth-service:8084/api/usuarios/*` | **Protegido JWT** (`ROLE_ADMIN`) | `usuariosService.ts` |
| **GW-AUTH-04** | `/api/emprendedor/configuracion-pagos/payphone/*`<br>`/api/emprendedor/configuracion-pagos/bancarios/*`<br>`/api/emprendedor/configuracion-pagos/deuna-qr/*`<br>`/api/emprendedor/configuracion-pagos/costo-envio/*` | `GET, OPTIONS` | `http://auth-service:8084/api/emprendedor/configuracion-pagos/*` | **Público** (`permitAll` para checkout) | `PayphoneForm.tsx`, `DeunaForm.tsx`, `cartService.ts`, `app/api/payphone/confirm/route.ts` |
| **GW-AUTH-05** | `/api/emprendedor/configuracion-pagos`<br>`/api/emprendedor/configuracion-pagos/bancarios`<br>`/api/emprendedor/configuracion-pagos/payphone`<br>`/api/emprendedor/configuracion-pagos/deuna-qr`<br>`/api/emprendedor/configuracion-pagos/costo-envio` | `GET, POST, PUT, DELETE, OPTIONS` | `http://auth-service:8084/api/emprendedor/configuracion-pagos/*` | **Protegido JWT** (`ROLE_EMP`)<br>*(Soporta `multipart/form-data` hasta 5 MB en `/deuna-qr`)* | `emprendedor/configuracion-pagos/page.tsx` |
| **GW-AUTH-06** | `/uploads/qr-deuna/**` | `GET, HEAD, OPTIONS` | `http://auth-service:8084/uploads/qr-deuna/*` | **Público** (Archivos de imagen QR estáticos) | `configuracion-pagos/page.tsx`, `DeunaForm.tsx` |

### 4.2 Rutas Enrutadas hacia `msvc-producto` (Puerto `8081`)

| ID Ruta | Prefijo / Patrón en Gateway | Métodos Permitidos | Destino Upstream (`8081`) | Autorización Efectiva | Consumidor en Frontend |
|---|---|---|---|---|---|
| **GW-PROD-01** | `/api/productos`<br>`/api/productos/listado`<br>`/api/productos/{id}`<br>`/api/productos/empresa/{empresaId}`<br>`/api/productos/categoria/{categoriaId}` | `GET, OPTIONS` | `http://msvc-producto:8081/api/productos/*` | **Público** (`permitAll`) | `productService.ts`, `emprendedorService.ts` |
| **GW-PROD-02** | `/api/productos`<br>`/api/productos/{id}` | `POST, PUT, DELETE, OPTIONS` | `http://msvc-producto:8081/api/productos/*` | **Protegido JWT** (`ROLE_ADMIN` o `ROLE_EMP` propietario de `empresaId`, validado en A-03) | `productService.ts`, `emprendedorService.ts` |
| **GW-PROD-03** | `/api/categorias`<br>`/api/categorias/{id}` | `GET, POST, PUT, DELETE, OPTIONS` | `http://msvc-producto:8081/api/categorias/*` | Lectura pública; escritura reservada a `ROLE_ADMIN` | `categoriasEmpresasService.ts`, `productService.ts` |
| **GW-PROD-04** | `/api/empresas`<br>`/api/empresas/{id}` | `GET, POST, PUT, DELETE, OPTIONS` | `http://msvc-producto:8081/api/empresas/*` | Lectura pública; escritura reservada a `ROLE_ADMIN` | `categoriasEmpresasService.ts`, `productService.ts` |

### 4.3 Rutas Enrutadas hacia `msvc-inventario` (Puerto `8082`)

| ID Ruta | Prefijo / Patrón en Gateway | Métodos Permitidos | Destino Upstream (`8082`) | Autorización Efectiva | Consumidor en Frontend |
|---|---|---|---|---|---|
| **GW-INV-01** | `/api/inventarios`<br>`/api/inventarios/{id}`<br>`/api/inventarios/producto/{productoId}`<br>`/api/inventarios/cantidad/producto/{productoId}` | `GET, OPTIONS` | `http://msvc-inventario:8082/api/inventarios/*` | **Público** (`permitAll`) | `inventarioService.ts`, `productService.ts`, `emprendedorService.ts` |
| **GW-INV-02** | `/api/inventarios/cantidad/batch` | `POST, OPTIONS` | `http://msvc-inventario:8082/api/inventarios/cantidad/batch` | **Público** (`permitAll`, consulta por lote de stock) | `productService.ts`, `emprendedorService.ts`, `inventarioService.ts` |
| **GW-INV-03** | `/api/inventarios`<br>`/api/inventarios/producto/{productoId}`<br>`/api/inventarios/{id}` | `POST, PUT, DELETE, OPTIONS` | `http://msvc-inventario:8082/api/inventarios/*` | **Protegido JWT** (`ROLE_ADMIN`, `ROLE_EMP`, validado en A-07) | `inventarioService.ts` |
| **GW-INV-04** | `/api/inventarios/producto/{productoId}/stock`<br>`/api/inventarios/salida-lote` | `PUT, POST, OPTIONS` | `http://msvc-inventario:8082/api/inventarios/*` | **Protegido JWT** (`authenticated()`) | `inventarioService.ts`, `cartService.ts` |
| **GW-INV-05** | `/api/movimientos`<br>`/api/movimientos/{id}`<br>`/api/movimientos/inventario/{inventarioId}` | `GET, POST, OPTIONS` | `http://msvc-inventario:8082/api/movimientos/*` | `GET` público; `POST` protegido (`ROLE_ADMIN`, `ROLE_EMP`) | `inventarioService.ts` |

### 4.4 Rutas Enrutadas hacia `msvc-ventas` (Puerto `8083`)

| ID Ruta | Prefijo / Patrón en Gateway | Métodos Permitidos | Destino Upstream (`8083`) | Autorización Efectiva | Consumidor en Frontend |
|---|---|---|---|---|---|
| **GW-VEN-01** | `/api/ventas`<br>`/api/ventas/**` | `GET, POST, PUT, OPTIONS` | `http://msvc-ventas:8083/api/ventas/*` | Operación comercial *(se endurece con JWT en A-24; soporta descarga binaria `application/pdf` en `/{id}/factura`)* | `ventasService.ts`, `cartService.ts`, `emprendedorService.ts` |
| **GW-VEN-02** | `/api/clientes`<br>`/api/clientes/**` | `GET, POST, PUT, DELETE, OPTIONS` | `http://msvc-ventas:8083/api/clientes/*` | Operación comercial *(se endurece con JWT en A-24)* | `clientesService.ts`, `cartService.ts`, `historial-compras/page.tsx` |
| **GW-VEN-03** | `/api/pagos`<br>`/api/pagos/**` | `GET, POST, OPTIONS` | `http://msvc-ventas:8083/api/pagos/*` | Operación comercial *(aprobación/rechazo y comprobantes)* | `pagoService.ts` |
| **GW-VEN-04** | `/api/seguimiento-logistica`<br>`/api/seguimiento-logistica/**` | `GET, POST, PUT, OPTIONS` | `http://msvc-ventas:8083/api/seguimiento-logistica/*` | Consulta y actualización de estados logísticos | `logisticaService.ts` |
| **GW-VEN-05** | `/api/stripe`<br>`/api/stripe/**` | `GET, POST, OPTIONS` | `http://msvc-ventas:8083/api/stripe/*` | Pasarela Stripe *(requiere propagar header `Stripe-Signature` en `/api/stripe/webhook`)* | `stripeService.ts` |
| **GW-VEN-06** | `/api/bpm/ventas/**` | `GET, POST, OPTIONS` | `http://msvc-ventas:8083/api/bpm/ventas/*` | Motor de procesos Camunda BPM | `ventasService.ts` / pruebas BPM |

### 4.5 Rutas Propias del Gateway y Rutas Internas Bloqueadas

| Tipo | Ruta / Patrón | Comportamiento en el Gateway | Justificación Técnica |
|---|---|---|---|
| **Salud del Gateway** | `GET /health`<br>`GET /api/gateway/health` | Responde directamente `200 OK` con JSON: `{"status":"UP","service":"api-gateway"}` | Permite que Docker (`healthcheck`), scripts de validación (`A-14`) y monitoreo verifiquen la disponibilidad del Gateway sin depender de la base de datos. |
| **Bloqueo de Ruta Interna Feign** | `PUT /api/productos/*/sincronizar-stock` | Rechaza llamadas externas con `403 Forbidden` JSON | Este endpoint existe únicamente para que `msvc-inventario` notifique cambios de stock a `msvc-producto` por la red interna (identificado como riesgo en `REGISTRO-ETAPA-1-A01-A06.md`). |
| **Documentación y Actuators en Producción** | `/actuator/**`<br>`/swagger-ui/**`<br>`/api-docs/**`<br>`/v3/api-docs/**` | Bloqueado en el perfil de producción (`403`/`404`) o restringido a `127.0.0.1` | Evita exponer metadatos internos y topología de servicios en el servidor público municipal. |

---

## 5. Política Centralizada de CORS y Manejo de Preflight (`OPTIONS`)

Para resolver definitivamente la observación **O-13** (*Políticas CORS dispersas e inconsistentes*) y prevenir regresiones como el bloqueo de preflight detectado en `A-07`:

### 5.1 Reglas de Cabeceras CORS en el Gateway

1. **Orígenes Permitidos (`Access-Control-Allow-Origin`):**
   - El Gateway valida el header `Origin` entrante contra una lista blanca configurable (`CORS_ALLOWED_ORIGINS`):
     - Entorno local: `http://localhost:3000`, `http://127.0.0.1:3000`
     - Entorno productivo: `https://tienda.gadmsigchos.gob.ec` (o el dominio municipal asignado)
   - Nunca se devuelve el comodín literal `*` cuando `Access-Control-Allow-Credentials` es `true`, cumpliendo el estándar Fetch/CORS de los navegadores.
2. **Métodos Permitidos (`Access-Control-Allow-Methods`):**
   - `GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD`
3. **Cabeceras Permitidas en Solicitud (`Access-Control-Allow-Headers`):**
   - `Authorization, Content-Type, Accept, Origin, X-Requested-With, Cache-Control, Stripe-Signature`
4. **Credenciales (`Access-Control-Allow-Credentials`):**
   - `true`
5. **Cabeceras Expuestas al Cliente (`Access-Control-Expose-Headers`):**
   - `Authorization, Content-Type, Content-Disposition` *(necesario para que el navegador lea el nombre del archivo PDF al descargar facturas en `/api/ventas/{id}/factura`)*.
6. **Caché de Preflight (`Access-Control-Max-Age`):**
   - `3600` segundos (1 hora).

### 5.2 Resolución Directa de Solicitudes `OPTIONS` (Preflight)

Toda solicitud `OPTIONS` que llegue al Gateway recibe respuesta inmediata **`204 No Content`** (con las cabeceras CORS anteriores y `Content-Length: 0`) directamente desde el Gateway:
- No requiere token `Authorization`.
- No consume hilos de Tomcat ni conexiones de base de datos en los microservicios.

### 5.3 Prevención de Cabeceras CORS Duplicadas (Convivencia A-12 y Retiro Gradual en A-13)

Actualmente los microservicios contienen anotaciones `@CrossOrigin` y clases `CorsConfig` (`demo`, `msvc-producto`, `msvc-inventario`, `msvc-ventas`). Si tanto el microservicio como el Gateway agregan `Access-Control-Allow-Origin`, el navegador rechaza la respuesta por cabecera duplicada (`multiple values 'http://localhost:3000, *'`).

Para garantizar **cero rupturas** durante la transición:
1. **En `A-12` (mientras el frontend aún no migra):** Los microservicios conservan su configuración CORS local para no interrumpir las pruebas de los demás integrantes en los puertos `8081`–`8084`, pero **el Gateway NGINX oculta las cabeceras CORS provenientes del upstream** (`proxy_hide_header Access-Control-Allow-Origin`, `proxy_hide_header Access-Control-Allow-Credentials`, `proxy_hide_header Access-Control-Allow-Methods`, `proxy_hide_header Access-Control-Allow-Headers`, `proxy_hide_header Access-Control-Expose-Headers`) antes de escribir las suyas.
2. **En `A-13` / cierre de etapa:** Una vez que el frontend consuma el Gateway de forma predeterminada, la política del Gateway queda como única fuente de verdad perimetral.

---

## 6. Propagación de Cabeceras, Límites de Carga y Timeouts

### 6.1 Cabeceras Propagadas hacia los Microservicios

El Gateway debe reenviar de forma transparente las siguientes cabeceras en cada petición:
- `Authorization`: **Crítico.** Se transmite intacto (`Bearer <JWT>`) hacia `auth-service`, `msvc-producto`, `msvc-inventario` y `msvc-ventas` para que cada Resource Server valide firma, roles (`scope`) y aislamiento multi-tenant (`empresaId`, `userId`).
- `Content-Type` y `Accept`: Preservados íntegramente para `application/json`, `multipart/form-data` (con su `boundary`) y `application/pdf`.
- `Stripe-Signature`: Preservado para la verificación criptográfica del webhook en `/api/stripe/webhook`.
- Cabeceras de trazabilidad de proxy inverso:
  - `Host`: `$host`
  - `X-Real-IP`: `$remote_addr`
  - `X-Forwarded-For`: `$proxy_add_x_forwarded_for`
  - `X-Forwarded-Proto`: `$scheme`
  - `X-Forwarded-Host`: `$http_host`

### 6.2 Límites de Tamaño de Cuerpo y Timeouts

| Parámetro | Valor Definido | Justificación en el Código Actual |
|---|---|---|
| **Tamaño máximo de payload (`client_max_body_size`)** | `10 MB` | `ConfiguracionPagosController.java` permite subir imágenes QR de Deuna de hasta `5 MB` (`5 * 1024 * 1024` bytes). El valor por defecto de NGINX (`1 MB`) rechazaría la subida con `413`. |
| **Timeout de conexión (`proxy_connect_timeout`)** | `5 s` | Detecta rápidamente si un microservicio interno está apagado o inalcanzable y devuelve `502`/`503` sin colgar al navegador. |
| **Timeout de lectura general (`proxy_read_timeout`)** | `30 s` | Alineado con `server.tomcat.connection-timeout=30000` de los cuatro microservicios. |
| **Timeout de lectura en Ventas/BPM (`/api/ventas/**`, `/api/bpm/**`)** | `60 s` | La creación de ventas con generación de PDF (`FacturaService`) y la orquestación Camunda BPM requieren mayor margen de tiempo. |

---

## 7. Estrategia de Manejo de Errores y Contrato JSON Uniforme

### 7.1 Principio de No Interferencia con Errores de Dominio y Seguridad

Cuando un microservicio responde con un código de error controlado, **el Gateway no intercepta ni reemplaza el cuerpo de la respuesta** (`proxy_intercept_errors off` para respuestas de aplicación):
- `400 Bad Request`: Errores de validación (`MethodArgumentNotValidException` o `IllegalArgumentException`).
- `401 Unauthorized`: Credenciales inválidas en login o token JWT ausente/expirado/inválido (`BearerTokenAuthenticationEntryPoint`).
- `403 Forbidden`: Rol insuficiente o intento de acceso a una empresa ajena (`AccessDeniedException` → `{"code":"FORBIDDEN","message":"...","timestamp":"..."}`).
- `404 Not Found`: Recurso inexistente en base de datos (`NoSuchElementException`).
- `409 Conflict`: Conflicto de estado en seguimiento logístico (`IllegalStateException` en `SeguimientoLogisticaController`).
- `500 Internal Server Error`: Excepción interna del microservicio reportada por su `GlobalExceptionHandler`.

### 7.2 Contrato JSON Estandarizado para Errores Perimetrales del Gateway

Cuando el fallo ocurre en la capa de red o perimetral (por ejemplo, un microservicio está apagado, tarda demasiado o se solicita una ruta que no existe en la tabla de enrutamiento), el Gateway **nunca debe devolver páginas HTML por defecto**. Debe responder siempre con `Content-Type: application/json; charset=utf-8` y la misma estructura `ErrorResponse` que utilizan los microservicios (`code`, `message`, `timestamp`):

| Código HTTP | Código Interno (`code`) | Cuándo lo emite el Gateway | Cuerpo JSON de Respuesta |
|---:|---|---|---|
| **403** | `FORBIDDEN_INTERNAL_ROUTE` | Intento externo de invocar una ruta de uso interno exclusivo (ej. `/api/productos/*/sincronizar-stock`) | `{"code":"FORBIDDEN","message":"Ruta de uso interno no accesible desde el exterior","status":403}` |
| **404** | `GATEWAY_ROUTE_NOT_FOUND` | Solicitud hacia un prefijo inexistente fuera de la tabla de rutas oficial | `{"code":"NOT_FOUND","message":"La ruta solicitada no existe en el API Gateway","status":404}` |
| **413** | `PAYLOAD_TOO_LARGE` | Archivo o cuerpo HTTP superior a `10 MB` | `{"code":"PAYLOAD_TOO_LARGE","message":"El tamaño de la solicitud supera el límite permitido (10 MB)","status":413}` |
| **429** | `TOO_MANY_REQUESTS` | Exceso de límite de tasa (`rate limiting`) por IP | `{"code":"TOO_MANY_REQUESTS","message":"Demasiadas solicitudes. Intente nuevamente en unos segundos","status":429}` |
| **502** | `BAD_GATEWAY` | El microservicio destino está apagado (`Connection refused`) o devolvió una respuesta inválida | `{"code":"SERVICE_UNAVAILABLE","message":"El servicio solicitado no se encuentra disponible temporalmente (Bad Gateway)","status":502}` |
| **503** | `SERVICE_UNAVAILABLE` | Servicio en mantenimiento o circuito abierto | `{"code":"SERVICE_UNAVAILABLE","message":"Servicio temporalmente no disponible","status":503}` |
| **504** | `GATEWAY_TIMEOUT` | El microservicio destino superó el tiempo máximo de respuesta (`proxy_read_timeout`) | `{"code":"GATEWAY_TIMEOUT","message":"El servicio tardó demasiado en responder (Gateway Timeout)","status":504}` |

> **Nota para el Frontend (`A-13`):** Todas las respuestas perimetrales incluyen las cabeceras CORS (`always`), de modo que cuando un microservicio esté apagado (`502`/`504`), el navegador entregue el código y mensaje JSON real a `fetch()` en lugar de enmascararlo como un falso error de CORS.

---

## 8. Diagnóstico y Contrato de Normalización de URLs para el Frontend (Preparación de A-13)

Durante el análisis de código para `A-11` se inspeccionaron todos los servicios y componentes de `avalon-react-10.1.0`. Se identificó que actualmente existen **tres convenciones distintas** en las variables de entorno del frontend:

| Grupo | Archivos Afectados en `avalon-react-10.1.0` | Variable Actual | Valor por Defecto Actual | Sufijo que Agrega el Archivo |
|---|---|---|---|---|
| **Grupo A (Base SIN `/api`)** | `services/authService.ts`<br>`services/usuariosService.ts`<br>`services/cartService.ts`<br>`services/clientesService.ts`<br>`services/ventasService.ts`<br>`services/inventarioService.ts`<br>`app/(full-page)/emprendedor/configuracion-pagos/page.tsx`<br>`app/(main)/checkout/components/PayphoneForm.tsx`<br>`app/(main)/profile/historial-compras/page.tsx` | `NEXT_PUBLIC_AUTH_API_URL`<br>`NEXT_PUBLIC_VENTAS_API_URL`<br>`NEXT_PUBLIC_INVENTARIO_API_URL` | `http://localhost:8084`<br>`http://localhost:8083`<br>`http://localhost:8082` | Agrega `/api/auth`, `/api/usuarios`, `/api/ventas`, `/api/clientes`, `/api/inventarios`, `/api/movimientos`, `/uploads/qr-deuna/...` |
| **Grupo B (Base CON `/api`)** | `services/categoriasEmpresasService.ts`<br>`services/emprendedorService.ts`<br>`services/productService.ts` (`PRODUCTOS_URL`)<br>`services/logisticaService.ts`<br>`services/pagoService.ts`<br>`services/stripeService.ts` | `NEXT_PUBLIC_PRODUCTOS_API_URL`<br>`NEXT_PUBLIC_VENTAS_API_URL`<br>`NEXT_PUBLIC_INVENTARIO_API_URL`<br>`NEXT_PUBLIC_API_URL` | `http://localhost:8081/api`<br>`http://localhost:8083/api`<br>`http://localhost:8082/api` | Agrega `/productos`, `/categorias`, `/empresas`, `/inventarios`, `/ventas`, `/pagos`, `/seguimiento-logistica`, `/stripe` |
| **Grupo C (Base con `/api/inventarios` o rutas fijas)** | `services/productService.ts` (`INVENTARIO_URL`)<br>`app/api/payphone/confirm/route.ts`<br>`app/(main)/checkout/components/DeunaForm.tsx`<br>`next.config.js` (`rewrites`) | `NEXT_PUBLIC_INVENTARIO_API_URL`<br>*(Hardcoded `127.0.0.1:8084`)*<br>*(Ruta relativa `/api/emprendedor/deuna-qr` — O-06)* | `http://localhost:8082/api/inventarios` | `productService.ts` agrega `/cantidad/batch`; `route.ts` llama a `127.0.0.1:8084`; `DeunaForm.tsx` tiene el desajuste de ruta `O-06` |

### Regla de Estandarización para `A-13`

Para evitar que al migrar al Gateway se produzcan errores `/api/api/...` o rutas sin `/api`:
1. Se introduce una variable maestra **`NEXT_PUBLIC_GATEWAY_URL`** (por ejemplo `http://localhost:8080` o `http://localhost:8090`, **siempre sin barra final ni `/api`**) y un helper centralizado de resolución de URLs (`services/apiConfig.ts`).
2. Si `NEXT_PUBLIC_GATEWAY_URL` está definida:
   - Los consumidores del **Grupo A** reciben `${GATEWAY_URL}` (ej. `http://localhost:8080`).
   - Los consumidores del **Grupo B** reciben `${GATEWAY_URL}/api` (ej. `http://localhost:8080/api`).
   - `productService.ts` (`INVENTARIO_URL`) recibe `${GATEWAY_URL}/api/inventarios`.
   - `app/api/payphone/confirm/route.ts` y `next.config.js` enrutan hacia el Gateway (`GATEWAY_URL`), manteniendo compatibilidad hacia atrás si en algún entorno de prueba individual se desea apuntar a un microservicio específico.

---

## 9. Matriz de Responsabilidades y Secuencia de Ejecución (Etapa 4)

En cumplimiento de la sección 6 de `Plan_de_trabajo_etapas_y_ramas.md`:

| Actividad | Rama | Responsable Principal | Entregables y Criterios de Aceptación |
|---|---|---|---|
| **A-11: Diseñar estrategia del gateway** | `feature/a11-diseno-gateway` | **Integrante 1** (Backend y Seguridad) | Este documento (`Documentos_Jordy/A11-ESTRATEGIA-Y-DISENO-GATEWAY.md`) con tabla oficial de rutas, mapa de puertos, reglas CORS/preflight, contrato de errores JSON y plan de normalización de URLs para el frontend. |
| **A-12: Implementar gateway en pruebas** | `feature/a12-implementacion-gateway` | **Integrante 1** (Backend y Seguridad) | Configuración ejecutable de NGINX API Gateway (`gateway/nginx.conf`, respuestas JSON de error y contenedor ligero en `docker-compose.gateway.yml`). Todas las rutas de la Sección 4 responden a través del Gateway sin haber modificado aún el frontend. |
| **A-13: Migrar frontend al gateway** | `feature/a13-frontend-gateway` | **Integrante 2** (Frontend e Integración) | Centralización de URLs en `avalon-react-10.1.0` hacia el Gateway (`NEXT_PUBLIC_GATEWAY_URL`), corrección de `app/api/payphone/confirm/route.ts`, `next.config.js` y `DeunaForm.tsx` (`O-06`). El frontend opera login, catálogo, inventario y ventas sin consumir directamente los puertos `8081`–`8084`. |
| **A-14: Validar seguridad y disponibilidad** | `feature/a14-validacion-gateway` | **Integrante 3** (Infraestructura, Datos y Pruebas) | Script automatizado `Documentos_Jordy/pruebas-gateway-a14.ps1` y registro de evidencias validando salud (`/health`), enrutamiento de los 4 servicios, respuestas `200`/`401`/`403`, preflight CORS `OPTIONS` sin cabeceras duplicadas, bloqueo de `/sincronizar-stock` y respuestas JSON ante fallos. |


# Prueba 1: Validar el Gateway Real (Nginx Producción)

- **Objetivo:** Comprobar que el API Gateway (`nginx` nativo usando la configuración derivada de [`gateway/tienda.prod.conf`](../../gateway/tienda.prod.conf)) atiende correctamente HTTPS público (`:444`), redirección HTTP (`:80`), Gateway interno (`127.0.0.1:8080`), enrutamiento a microservicios y frontend, cabeceras de seguridad, control CORS, `limit_req` y aislamiento de puertos internos.
- **URL principal probada:** `https://tienda.gadmsigchos.gob.ec:444/landing`
- **Estado:** **APROBADA** (verificación completa HTTP/TLS/red y auditoría visual/network en Google Chrome DevTools).

---

## 1. Certificado TLS y Redirección HTTP -> HTTPS

- **Conexión TLS observada en vivo:** `TLSv1.3 / TLS_AES_256_GCM_SHA384`, `ALPN server accepted h2`.
- **Certificado Let's Encrypt:**
  - `subject: CN=tienda.gadmsigchos.gob.ec`
  - `issuer: C=US; O=Let's Encrypt; CN=YE2`
  - `start date: Oct 30 23:59:42 2026 GMT`
  - `expire date: Jan 28 23:59:41 2027 GMT`
  - Renovación automática: `certbot-renew.timer` activo en `systemd`.
- **Redirección puerto `:80`:**
  - `GET http://tienda.gadmsigchos.gob.ec/landing` -> **`HTTP/1.1 301 Moved Permanently`**
  - `Location: https://tienda.gadmsigchos.gob.ec:444/landing`

---

## 2. Cabeceras de Seguridad, CORS y Rate Limiting (`T09`)

### Cabeceras verificadas en `https://tienda.gadmsigchos.gob.ec:444/landing`
- `HTTP/2 200`
- `strict-transport-security: max-age=31536000; includeSubDomains`
- `x-content-type-options: nosniff`
- `x-frame-options: SAMEORIGIN`
- `referrer-policy: strict-origin-when-cross-origin`
- `permissions-policy: geolocation=(), microphone=(), camera=()`

### Preflight CORS (`OPTIONS /api/productos`)
- Con `Origin: https://tienda.gadmsigchos.gob.ec:444`: responde **`HTTP/2 200`** sin cabeceras CORS duplicadas (Nginx aplica `proxy_hide_header Access-Control-Allow-*` para operar same-origin detrás del Gateway).
- Con `Origin: https://evil.example`: rechazado con **`HTTP/2 403`**.
- **Observación menor:** en rutas `/api/...`, Spring Security y Nginx agregan cabeceras de seguridad superpuestas (`strict-transport-security`, `x-content-type-options`, `x-frame-options: DENY` + `SAMEORIGIN`), sin romper ninguna petición; puede limpiarse en el futuro si se desea un solo header por tipo.

### Rate Limiting (`limit_req` en Nginx)
- Zonas activas verificadas en la plantilla de Gateway:
  - `prod_api_general`: `20r/s` (`burst=40 nodelay`)
  - `prod_api_auth`: `5r/s` (`burst=15 nodelay`)
  - `prod_api_auth_sensitive`: `10r/m` (`burst=5 nodelay`)
- Durante las ráfagas intensivas de la Prueba 6, Nginx activó efectivamente la limitación de peticiones cuando se excedió la ráfaga en `/api/categorias` y `/api/productos`.

---

## 3. Matriz de Enrutamiento y Bloqueos Probados

### Gateway interno (`http://127.0.0.1:8080`, usado por Next.js SSR/rewrites y webhook PayPhone)
| Ruta | Código HTTP observado | Destino esperado |
|---|:---:|---|
| `/health` | `200` | Respuesta directa Nginx (`{"status":"UP","service":"api-gateway","engine":"nginx"}`) |
| `/api/productos/listado` | `200` | `msvc-producto` (`127.0.0.1:8081`) |
| `/api/categorias` | `200` | `msvc-producto` (`127.0.0.1:8081`) |
| `/api/empresas` | `200` | `msvc-producto` (`127.0.0.1:8081`) |
| `/api/inventarios` | `200` | `msvc-inventario` (`127.0.0.1:8082`) |
| `/api/ventas` (sin JWT) | `401` | `msvc-ventas` (`127.0.0.1:8083`) |
| `/api/usuarios` (sin JWT) | `401` | `auth-service` (`127.0.0.1:8084`) |

### Gateway público (`https://tienda.gadmsigchos.gob.ec:444`)
| Ruta | Código HTTP observado | Comportamiento verificado |
|---|:---:|---|
| `/landing` | `200` | Frontend Next.js (`127.0.0.1:3000`) |
| `/api/health` | `200` | Salud Gateway Nginx |
| `/api/productos` | `200` | Catálogo `msvc-producto` |
| `/api/productos/listado` | `200` | Listado optimizado `msvc-producto` |
| `/api/productos/listado/paginado` | `200` | Listado paginado `msvc-producto` |
| `/api/categorias` | `200` | Categorías `msvc-producto` |
| `/api/empresas` | `200` | Empresas `msvc-producto` |
| `/api/inventarios` | `200` | Inventarios `msvc-inventario` |
| `/uploads/qr-deuna/2_8d69ec19-f893-4db5-9f65-5f4859ae7a30_test_qr.png` | `200` | Archivos estáticos `auth-service` (`WorkingDirectory`) |
| `/actuator/health` | `403` | Bloqueado por Gateway |
| `/swagger-ui.html` | `403` | Bloqueado por Gateway |
| `/api-docs` y `/v3/api-docs` | `403` | Bloqueado por Gateway |
| `/camunda` y `/engine-rest` | `403` | Bloqueado por Gateway |
| `PUT /api/productos/1/sincronizar-stock` | `403` | Endpoint interno bloqueado por Gateway |
| `/api/ventas/internal/test` | `404` | Endpoint interno bloqueado por Gateway |
| `/api/ventas` (sin JWT) | `401` | Protegido por Resource Server JWT |
| `/api/usuarios` (sin JWT) | `401` | Protegido por Auth JWT |
| `/api/emprendedor/configuracion-pagos` (sin JWT) | `401` | Protegido por Auth JWT |
| `/api/movimientos` (sin JWT) | `401` | Protegido por Resource Server JWT |

---

## 4. Aislamiento de Red, SELinux y Firewall

- **SELinux:** `Enforcing` con `httpd_can_network_connect --> on`.
- **`firewalld` del VPS (`public`):** `ports: 444/tcp 80/tcp`.
- **Prueba TCP externa desde Internet (`tienda.gadmsigchos.gob.ec`):**
  - `80` y `444`: **OPEN**
  - `8080`, `8081`, `8082`, `8083`, `8761`, `3000`, `3306`, `6379`: **CLOSED_OR_FILTERED**
  - *Aclaración sobre puerto `8084`:* al sondear `tienda.gadmsigchos.gob.ec:8084` desde fuera responde `HTTP/1.1 400 Bad Request` con cabecera `Server: nginx/1.26.3`, lo cual corresponde a un proxy Nginx SSL externo en la IP pública compartida institucional y **no** al microservicio `auth-service` (Tomcat/Spring Boot), que en nuestro VPS permanece bloqueado por `firewalld`.

---

## 5. Auditoría en Navegador Real (Google Chrome DevTools sobre `/landing`, `/products`, `/checkout` y `/auth/login2`)

Ejecutada conectando DevTools sobre Chrome (`127.0.0.1:9222`):

1. **`https://tienda.gadmsigchos.gob.ec:444/landing` (`HTTP 200`, título `Sigchos E-commerce`):**
   - Llamadas al Gateway verificadas en Network:
     - `GET /api/productos/listado` -> `200` (`39` productos)
     - `GET /api/categorias` -> `200` (`6` categorías)
     - `POST /api/inventarios/cantidad/batch` -> `200` (`[1..39]`)
   - Renderizado correcto de barra de navegación, banner hero, métricas, 3 productos destacados, secciones informativas y formulario de contacto.
2. **Catálogo público `https://tienda.gadmsigchos.gob.ec:444/products` (`HTTP 200`):**
   - Llamadas al Gateway: `GET /api/empresas` (`200`, `4` empresas), `GET /api/productos/listado/paginado?page=0&size=12&sort=nombre%2Casc` (`200`, `totalElements: 39`, `totalPages: 4`), `POST /api/inventarios/cantidad/batch` (`200`).
   - Filtros, paginación y modal `"Detalles del Producto"` operativos sin errores API.
3. **Protección de carrito/checkout y Login Google OAuth (`https://tienda.gadmsigchos.gob.ec:444/auth/login2`):**
   - Sin sesión activa, intentar agregar al carrito abre el diálogo `"Iniciar Sesión Requerido"` y navegar a `/checkout` (`200`) redirige correctamente a `/auth/login2`.
   - El botón `"Continuar con Google"` carga Google Identity Services (`https://accounts.google.com/gsi/client` y `/gsi/button`) con el `NEXT_PUBLIC_GOOGLE_CLIENT_ID` de producción en `200`.

### Hallazgos menores detectados en navegador (no bloqueantes para el Gateway)
- **Fuentes estáticas `404`:** en `/landing`, Next.js solicita `/layout/fonts/roboto-v20-latin-ext_latin-700.woff2` y `.woff`, ambos responden `404`.
- **Imágenes externas rotas en 2 productos:** `Queso tipo finca | 1000g` (ID `18`) y `Queso tipo finca | 500g` (ID `17`) apuntan a `https://i.imgur.com/default.png`, URL externa que redirige (`302`) a `https://i.imgur.com/removed.png` mostrando la imagen placeholder de Imgur.
- **Observaciones menores de consola/DOM:** mapa de Google Maps en `/landing` inicia en `about:blank` hasta hacer scroll; en formularios aparecen advertencias menores de `<label for>` / atributos `id`/`name` y campo de contraseña fuera de `<form>`.

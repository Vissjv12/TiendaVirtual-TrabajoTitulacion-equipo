# Reporte de Despliegue a Producción — 16 de noviembre de 2026

**Servidor:** `tienda.gadmsigchos.gob.ec` (AlmaLinux 10.1, SELinux `Enforcing`, puerto público HTTPS `444`)  
**Rama / Commit desplegado:** `main` (`b60b307`)  
**Repositorio:** `https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo.git`  
**Estado general:** Despliegue completado y verificado en vivo (`9/9` servicios activos y pruebas HTTP/HTTPS exitosas desde el VPS y desde Internet).

---

## 1. Resumen de lo desplegado y configuración acordada

- **Backend (5 microservicios Java / Spring Boot):** compilados en local desde `main` (`b60b307`) con `.\mvnw.cmd clean package -DskipTests` (`eureka-server`, `auth-service`/`demo`, `msvc-producto`, `msvc-inventario`, `msvc-ventas`) y desplegados en `/var/www/tienda-virtual-backend/{eureka-server,auth-service,msvc-producto,msvc-inventario,msvc-ventas}/`.
- **Frontend (Next.js):** compilado para producción con `frontend/.env.production.final` (`NEXT_PUBLIC_GATEWAY_URL=https://tienda.gadmsigchos.gob.ec:444`, `GATEWAY_INTERNAL_URL=http://127.0.0.1:8080`) y desplegado en `/var/www/tienda-virtual-frontend/` (retirando `.env.local` del servidor y dejando `.env.production` + `tienda-frontend.service` actualizados).
- **Nginx (`/etc/nginx/conf.d/tienda.conf`):** actualizado desde `gateway/tienda.prod.conf` conservando `root /usr/share/nginx/html;` para Certbot/ACME. Validado con `nginx -t` (`syntax is ok` / `test is successful`).
- **Base de datos y parámetros activos:**
  - `MySQL` con `SET PERSIST max_connections = 250;` e índice único `uk_emprendedor_empresa` (`NON_UNIQUE=0`) ya aplicado y verificado.
  - `PAYPHONE_TOKEN_ENC_KEY` configurada (AES-256-GCM en reposo).
  - `CACHE_REDIS_ENABLED=false` (Valkey instalado, activo y listo en localhost para activación posterior).
  - `STRIPE_ENABLED=false`, `SPRINGDOC_ENABLED=false`.

---

## 2. Incidente resuelto durante el arranque (SELinux + `.env`)

### Diagnóstico (`task-5049` y `task-5057`)
- Al intentar iniciar `auth-service`, `systemd` falló con:
  `auth-service.service: Failed to load environment files: Permission denied` (`Result: resources`).
- La inspección con `ls -laZ` mostró que `/var/www/tienda-virtual-backend/.env` tenía contexto SELinux `unconfined_u:object_r:var_t:s0`, el cual SELinux en modo `Enforcing` impide leer a `systemd` (`init_t`) como `EnvironmentFile=`.
- El comando posterior `ausearch` dentro de `task-5057` quedó colgado en esa sesión, por lo que **fue correcto detener la terminal** una vez que `ls -laZ` ya había confirmado la causa raíz.

### Corrección aplicada
Se ajustó y ejecutó el arranque controlado:
1. Se detuvieron los servicios dependientes (`auth-service`, `msvc-producto`, `msvc-inventario`, `msvc-ventas`, `tienda-frontend`) para limpiar el ciclo de `auto-restart`.
2. Se movió `/var/www/tienda-virtual-backend/.env` a **`/etc/tienda-virtual/backend.env`** (directorio `/etc/tienda-virtual` con permisos `700`, archivo con permisos `600`, dueño `root:root`) y se ejecutó `restorecon -Rv /etc/tienda-virtual`, obteniendo el contexto SELinux correcto **`unconfined_u:object_r:etc_t:s0`**.
3. Se verificó que no quedó ningún `.env*` residual dentro de `/var/www/tienda-virtual-backend/`.
4. Se actualizó `EnvironmentFile=/etc/tienda-virtual/backend.env` en las 4 unidades afectadas:
   - `/etc/systemd/system/auth-service.service`
   - `/etc/systemd/system/msvc-producto.service`
   - `/etc/systemd/system/msvc-inventario.service`
   - `/etc/systemd/system/msvc-ventas.service`
5. Se ejecutó `systemctl daemon-reload`, `systemctl reset-failed`, arranque ordenado (`eureka-server` -> `auth-service` -> `msvc-producto`, `msvc-inventario`, `msvc-ventas` -> `tienda-frontend`) y `nginx -t && systemctl reload nginx`.

---

## 3. Estado verificado de servicios, puertos, seguridad y datos

### Servicios `systemd` (`9/9` en `active`)
- `mysqld`: `active`
- `valkey`: `active` (`valkey-cli ping` -> `PONG`)
- `nginx`: `active`
- `eureka-server`: `active`
- `auth-service`: `active`
- `msvc-producto`: `active`
- `msvc-inventario`: `active`
- `msvc-ventas`: `active`
- `tienda-frontend`: `active`

### Puertos en escucha (`ss -tlnp`) y Firewall (`firewalld`)
- **Expuestos públicamente en firewall (`firewall-cmd --list-ports`):** `80/tcp`, `444/tcp` (puerto `8761/tcp` retirado).
- **Nginx:** `0.0.0.0:80`, `[::]:80`, `0.0.0.0:444`, `[::]:444` y gateway interno solo en `127.0.0.1:8080`.
- **Valkey:** `127.0.0.1:6379` y `[::1]:6379`.
- **MySQL:** `*:3306`.
- **Microservicios y Frontend:** `8761` (`eureka-server`), `8084` (`auth-service`), `8081` (`msvc-producto`), `8082` (`msvc-inventario`), `8083` (`msvc-ventas`), `3000` (`node` Next.js).

### Eureka, migración PayPhone, archivos preservados y logs
- **Eureka (`http://127.0.0.1:8761/eureka/apps`):** registrados en estado `UP` `MSVC-INVENTARIO`, `LOGIN` y `MSVC-PRODUCTO` (y `msvc-ventas` levantado con Camunda `v7.20.0`, respondiendo en `:8083`).
- **Cifrado de PayPhone en BD (`auth_service.configuracion_metodos_pago`):**
  El registro `id=1` presenta `LEFT(payphone_token, 7) = enc:v1:`, confirmando el cifrado en reposo con AES-GCM y ausencia de token en texto plano.
- **Archivos existentes preservados en disco:**
  - `/var/www/tienda-virtual-backend/auth-service/uploads/qr-deuna`: `1` archivo (`2_8d69ec19-f893-4db5-9f65-5f4859ae7a30_test_qr.png`, `56345` bytes).
  - `/var/www/tienda-virtual-backend/msvc-ventas/facturas`: `17` archivos PDF.
- **Logs recientes (`journalctl` desde `21:25:03`):** arranque limpio de los microservicios, Next.js listo en `0.0.0.0:3000` y `msvc-producto` respondiendo peticiones (`39` productos procesados).

---

## 4. Verificación HTTP/HTTPS en vivo

Pruebas realizadas internamente en el VPS (puerto `8080` interno y `:444` con SNI) y **externamente desde Internet** contra `https://tienda.gadmsigchos.gob.ec:444`:

| Endpoint / Ruta | Código HTTP observado | Origen de prueba |
| :--- | :---: | :--- |
| `http://127.0.0.1:8080/health` | `200` | VPS local (`Nginx` interno) |
| `GET /` | `200` (`8395` bytes) | Internet público + VPS SNI |
| `GET /api/health` | `200` (`{"status":"UP","service":"api-gateway","engine":"nginx"}`) | Internet público + VPS SNI |
| `GET /api/productos` | `200` (`31023` bytes) | Internet público + VPS SNI |
| `GET /api/productos/listado` | `200` | VPS SNI (`:444`) |
| `GET /api/categorias` | `200` | Internet público + VPS SNI |
| `GET /api/empresas` | `200` | Internet público + VPS SNI |
| `GET /api/inventarios` | `200` | Internet público + VPS SNI |
| `GET /uploads/qr-deuna/2_8d69ec19-f893-4db5-9f65-5f4859ae7a30_test_qr.png` | `200` | Internet público + VPS SNI |
| `GET /actuator/health` | `403` | Internet público + VPS SNI |
| `GET /swagger-ui.html` | `403` | Internet público + VPS SNI |
| `GET /camunda` | `403` | Internet público + VPS SNI |
| `PUT /api/productos/1/sincronizar-stock` | `403` | Internet público + VPS SNI |
| `GET /api/ventas/internal/test` | `404` | Internet público + VPS SNI |
| `GET /api/ventas` (sin JWT) | `401` | Internet público + VPS SNI |
| `GET /api/usuarios` (sin JWT) | `401` | Internet público + VPS SNI |

Adicionalmente, las comprobaciones directas a los puertos locales de los microservicios en el VPS confirmaron respuesta activa en los 5 puertos (`8761`: `200 {"status":"UP"}`, `8081`: `200 {"status":"UP","groups":["cache","liveness","readiness"]}`, `8084`: `401`, `8082`: `401`, `8083`: `404` JSON controlado).

---

## 5. Notas operativas y respaldos (Rollback)

- **Nueva ubicación del `.env` de backend:** las variables de entorno en producción ahora residen en **`/etc/tienda-virtual/backend.env`** (no en `/var/www/tienda-virtual-backend/.env`).
- **Activación futura de caché Redis/Valkey:** cuando se decida habilitar, editar las variables `CACHE_REDIS_*` en `/etc/tienda-virtual/backend.env` y reiniciar los microservicios que consumen caché (`auth-service`, `msvc-producto`, `msvc-inventario`, `msvc-ventas`).
- **Pruebas funcionales recomendadas en navegador (flujo autenticado):** iniciar sesión (correo/contraseña y Google OAuth), validar panel de administración y configuración PayPhone/DeUna, ejecutar una compra de prueba y verificar la descarga del PDF de factura.
- **Respaldos disponibles en el servidor (intactos):**
  - `/root/backups/pre_deploy_20261116_211143/` (`bd_completa.sql.gz`, uploads, facturas, `systemd/`, `nginx_backup/`, `.env*`)
  - `/root/backups/systemd_pre_env_20261116_205156/`
  - `/var/www/tienda-virtual-backend-bak-20261116_211143`
  - `/var/www/tienda-virtual-frontend-bak-20261116_211143`
  - `/var/backups/tienda`

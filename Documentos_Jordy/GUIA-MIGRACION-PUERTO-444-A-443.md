# Guía Técnica de Migración: Eliminación del Puerto `:444` (`https://tienda.gadmsigchos.gob.ec:444` -> `https://tienda.gadmsigchos.gob.ec`)

**Fecha de elaboración y diagnóstico en vivo:** 2026-11-17  
**Dominio objetivo:** `https://tienda.gadmsigchos.gob.ec` (puerto HTTPS estándar `443`)  
**Estado actual verificado en producción:** Operativo en `https://tienda.gadmsigchos.gob.ec:444`  
**Audiencia:** Guía operativa exacta para ejecutar la transición sin improvisación ni pérdida de disponibilidad.

> [!IMPORTANT]
> **Resumen ejecutivo del diagnóstico en vivo (2026-11-17):**  
> A diferencia de lo supuesto en guías anteriores, el firewall perimetral del municipio (`69.10.49.106`, equipo **pfSense** administrado por **Grupo Mundo Digital / Ing. Miguel Palacios**) **NO reenvía el puerto público `443/tcp` ni al servidor de Turismo ni al servidor de Tienda**. Actualmente `69.10.49.106:443` expira desde Internet y dentro de la LAN responde el propio panel web de pfSense (`CN=pfSense-68c394c5a4053`).  
> Por ello, la eliminación de `:444` depende de un **NAT Port Forward de `WAN TCP 443`** en el pfSense además de los ajustes en los servidores Linux y en la aplicación.

---

## 1. Reglas de Seguridad Operativa e Inventario de Accesos

### 1.1 Accesos SSH de ambos servidores (sin exponer claves aquí)
Las credenciales de acceso se encuentran resguardadas en `documentos-trabajo/docs_produ/guia-wildcat-ssh-ssl.md` (sección **Resumen de tus accesos**). **Nunca** escribir contraseñas, tokens de PayPhone, `JWT_SECRET` ni claves privadas en esta guía ni en commits de Git.

| Rol del servidor | Host SSH | Puerto SSH | IP LAN interna (`ens33`) | Sistema operativo / Web |
|---|---|---|---|---|
| **Servidor de Tienda** | `tienda.gadmsigchos.gob.ec` | `2226` | `192.168.200.5/28` | AlmaLinux 10.1 / Nginx 1.26.3 |
| **Servidor de Turismo** | `turismo.gadmsigchos.gob.ec` | `2244` | `192.168.200.6/28` | AlmaLinux 10.1 / Nginx 1.26.3 |

### 1.2 Activos y reglas críticas que NO se deben tocar ni romper
1. **Datos persistentes en Servidor de Tienda (`192.168.200.5`):**
   - `/var/www/tienda-virtual-backend/auth-service/uploads/qr-deuna` (códigos QR DeUna cargados por emprendedores).
   - `/var/www/tienda-virtual-backend/msvc-ventas/facturas` (facturas PDF generadas por compras).
   - No cambiar `WorkingDirectory=` de los servicios systemd de Java.
2. **Archivo de secretos de backend en Tienda (`/etc/tienda-virtual/backend.env`):**
   - Debe mantenerse en `/etc/tienda-virtual/backend.env` (no en `/var/www/...`) con permisos `0600 root:root` y contexto SELinux `etc_t`.
   - Tras cualquier edición manual, ejecutar siempre:
     ```bash
     chmod 600 /etc/tienda-virtual/backend.env
     chcon -t etc_t /etc/tienda-virtual/backend.env
     ```
     Si queda con contexto `var_t` o `user_tmp_t`, systemd rechaza el arranque de los microservicios Java con `SELinux` `Permission denied`.
3. **Servicios del Servidor de Turismo (`192.168.200.6`):**
   - No modificar ni detener los sitios existentes en Turismo:
     - `/etc/nginx/conf.d/adilcorp.conf` (`listen 448 ssl;` -> Next.js Turismo en `127.0.0.1:3000`).
     - `/etc/nginx/conf.d/admin_app.conf` (`listen 8084 ssl;` -> SPA React/Vite en `/var/www/admin_app/AdministracionTurismoSigchos/dist`).
   - No sobrescribir `/etc/letsencrypt/live/turismo.gadmsigchos.gob.ec/`.

---

## 2. Diagnóstico Real de Red y Topología (Verificado el 2026-11-17)

### 2.1 Lo que asumía la documentación previa vs. la realidad medida
- **Supuesto previo** (en `documentos-trabajo/docs_produ/README-SSL-TIENDA.md` y `documentos-trabajo/GUIA-SUBIDA-PRODUCCION-Y-REDIS.md`): se creía que el proveedor ya enviaba los puertos `80` y `443` de la IP pública `69.10.49.106` hacia el servidor de Turismo, y que bastaba con añadir un bloque `:443` en Turismo sin pedir cambios de firewall.
- **Realidad medida empíricamente desde Windows y por captura de paquetes `AF_PACKET` en `ens33` de ambos servidores:**

| Puerto público (`69.10.49.106`) | Prueba TCP desde Internet | Paquetes recibidos en `ens33` | Servidor destino actual | Uso actual en producción |
|---|---|---|---|---|
| `80/tcp` (HTTP) | `TcpTestSucceeded=True` | `11` paquetes en Turismo | Turismo (`192.168.200.6:80`) | `proxy_tienda.conf` reenvía `http://192.168.200.5` (Certbot ACME de Tienda + redirect a `:444`) |
| `443/tcp` (HTTPS estándar) | **`TcpTestSucceeded=False` (Timeout)** | **`0` en Turismo y `0` en Tienda** | Ningún servidor interno (en LAN `https://69.10.49.106:443/` responde `pfSense GUI`, gateway `192.168.200.1`) | Bloqueado/ocupado por la interfaz administrativa WebGUI de pfSense (`CN=pfSense-68c394c5a4053`) |
| `444/tcp` (HTTPS actual Tienda) | `TcpTestSucceeded=True` | `10` paquetes en Tienda | Tienda (`192.168.200.5:444`) | `/etc/nginx/conf.d/tienda.conf` (Frontend + API Gateway unificado + HSTS) |
| `448/tcp` (HTTPS Turismo web) | `TcpTestSucceeded=True` | Turismo | Turismo (`192.168.200.6:448`) | `/etc/nginx/conf.d/adilcorp.conf` |
| `8084/tcp` (HTTPS Turismo admin) | N/D | Turismo | Turismo (`192.168.200.6:8084`) | `/etc/nginx/conf.d/admin_app.conf` |

### 2.2 Estado real de firewall, SELinux y certificados en cada servidor Linux
1. **Servidor de Tienda (`192.168.200.5`):**
   - `firewalld` activo (`public` en `ens33`): `services: cockpit dhcpv6-client http ssh`, `ports: 444/tcp 80/tcp`. **El puerto `443/tcp` NO está abierto.**
   - Nginx (`nginx/1.26.3`) escucha únicamente en: `80`, `444 ssl`, y `127.0.0.1:8080` (gateway loopback interno).
   - Certificado SSL válido existente: `/etc/letsencrypt/live/tienda.gadmsigchos.gob.ec/{fullchain.pem,privkey.pem}`.
   - Renovación Certbot verificada en `/etc/letsencrypt/renewal/tienda.gadmsigchos.gob.ec.conf`: usa `authenticator = webroot` con `webroot_path = /usr/share/nginx/html`.
2. **Servidor de Turismo (`192.168.200.6`):**
   - `firewalld` activo (`public` en `ens33`): `services: cockpit dhcpv6-client ssh`, `ports: 448/tcp 80/tcp 8084/tcp`. **El puerto `443/tcp` NO está abierto.**
   - `SELinux`: `Enforcing` con `httpd_can_network_connect --> on`.
   - Nginx (`nginx/1.26.3`, compilado con `--with-http_realip_module`, `--with-stream=dynamic`, `--with-stream_ssl_preread_module`) escucha en `80`, `448 ssl` y `8084 ssl`. **NO escucha en `443`.**
   - `/etc/nginx/conf.d/proxy_tienda.conf` solo tiene el bloque `listen 80; server_name tienda.gadmsigchos.gob.ec;` -> `proxy_pass http://192.168.200.5;`.
   - `/etc/letsencrypt/live/` en Turismo **solo** contiene `turismo.gadmsigchos.gob.ec` (NO contiene el certificado de Tienda).
   - Conectividad LAN verificada de Turismo -> Tienda: `https://192.168.200.5:444/health` devuelve `200`, `http://192.168.200.5:80/` devuelve `301`.

### 2.3 Las dos opciones de red cuando Grupo Mundo Digital habilite `443` en pfSense
Como Turismo usa `:448` (y no `:443`), el puerto `443` está libre para `tienda.gadmsigchos.gob.ec`:
- **Alternativa 1 (NAT directo en pfSense -> Tienda `192.168.200.5:443` — RECOMENDADA):**
  - El soporte de pfSense reenvía `WAN TCP 443 -> 192.168.200.5:443` (o `:444`).
  - **Ventaja:** Terminación TLS y renovación automática Certbot permanecen en un solo equipo (`192.168.200.5`), sin copiar certificados ni añadir saltos de proxy.
- **Alternativa 2 (NAT en pfSense -> Turismo `192.168.200.6:443` -> Proxy a Tienda `192.168.200.5:444`):**
  - Si el proveedor prefiere concentrar los puertos web estándar `80` y `443` en Turismo (`192.168.200.6`), Turismo recibe `443`, presenta una copia del certificado de `tienda.gadmsigchos.gob.ec` y hace `proxy_pass https://192.168.200.5:444;`.
  - **Desventaja:** Requiere mantener sincronizada la copia del certificado SSL en Turismo cada vez que Let's Encrypt renueve en Tienda (cada 60-90 días).
- **Decisión operativa:** En la **Fase 1** de esta guía dejamos preparados **ambos** servidores para escuchar `443` de forma segura sin desactivar `:444`, de modo que funcione sin importar cuál de las dos alternativas configure Grupo Mundo Digital en el pfSense.

---

## 3. Alerta Crítica sobre Plataformas Externas (Google Cloud y PayPhone Developer)

Revisadas tus capturas de pantalla del 2026-11-17:

1. **Google Cloud Console (Cliente OAuth `Tienda Virtual Web`):**
   - `Orígenes autorizados de JavaScript` ya tiene ambos orígenes:
     - `URI 1`: `https://tienda.gadmsigchos.gob.ec:444`
     - `URI 2`: `https://tienda.gadmsigchos.gob.ec`
   - ✅ **Listo para transición cero-caídas.** Solo verifica haber hecho clic en **Guardar** al final del formulario en Google Cloud.

2. **PayPhone Developer:**
   - `Dominio web`: ya incluye ambos orígenes separados por `;` (`http://localhost;http://localhost:3000;...;https://tienda.gadmsigchos.gob.ec:444;https://tienda.gadmsigchos.gob.ec`). ✅ **Correcto.**
   - `Url de respuesta`: este campo acepta **un solo valor**, y en tu captura ya quedó sin `:444` (`https://tienda.gadmsigchos.gob.ec/checkout/payphone-confirmacion`).

> [!WARNING]
> **Riesgo operativo inmediato mientras `69.10.49.106:443` siga cerrado:**  
> En este momento el sistema en producción sigue operando en `:444` (`PAYPHONE_RESPONSE_URL=https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion` en `/etc/tienda-virtual/backend.env`), mientras que `69.10.49.106:443` da *Connection timed out*.  
> - **Si NO vas a hacer pruebas reales de compra PayPhone hasta terminar la migración a `:443`**, puedes dejar `Url de respuesta` así en PayPhone Developer y completar primero la Fase 2 y Fase 3.  
> - **Si necesitas que las compras con tarjeta en `:444` sigan funcionando hoy mientras Grupo Mundo Digital abre el puerto `443`**, debes mantener `Url de respuesta` con `:444` (`https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion`) y cambiarlo a sin `:444` únicamente en la **Fase 3**.  
> - Verifica también si en PayPhone Developer existe un campo **URL de Notificación / Webhook** (`/api/payphone/confirm`, atendido por `avalon-react-10.1.0/app/api/payphone/confirm/route.ts`): si existe, debe apuntar a `:444` mientras siga `:444` activo y pasar a `https://tienda.gadmsigchos.gob.ec/api/payphone/confirm` en la Fase 3.

---

## 4. Inventario Exacto de Qué Configurar (Código vs. Servidores)

### 4.1 Código fuente en el repositorio (revisado completo — NO hay `:444` en lógica)
- **Frontend (`avalon-react-10.1.0`):**
  - `avalon-react-10.1.0/services/apiConfig.ts`: lee `process.env.NEXT_PUBLIC_GATEWAY_URL` (se incrusta estáticamente durante `npm run build`).
  - `avalon-react-10.1.0/next.config.js`: `images.domains` ya incluye `'tienda.gadmsigchos.gob.ec'` (sin puerto); `rewrites()` usa `GATEWAY_INTERNAL_URL || NEXT_PUBLIC_GATEWAY_URL || 'http://127.0.0.1:8080'`.
  - `avalon-react-10.1.0/app/api/payphone/confirm/route.ts`: usa `GATEWAY_INTERNAL_URL` en runtime SSR hacia `127.0.0.1:8080`.
  - **Importante:** No hace falta editar código fuente JavaScript/TypeScript para quitar `:444`; basta con recompilar `npm run build` con el nuevo `.env.production`. *(Nota: `HeroSection.tsx` abre `https://turismo.gadmsigchos.gob.ec:448/`, que corresponde al portal externo de Turismo y NO debe cambiarse).*
  - Post-migración, actualizar únicamente la plantilla `avalon-react-10.1.0/.env.production.example`, `gateway/tienda.prod.conf` y la documentación del repo para mantener sincronía.
- **Microservicios Spring Boot (`Microservicios/`):**
  - Todos tienen `server.forward-headers-strategy=native` en sus `application.properties` (`demo`, `msvc-producto`, `msvc-inventario`, `msvc-ventas`).
  - `Microservicios/demo/src/main/java/com/example/demo/domain/service/PayphonePagoService.java`: lee `PAYPHONE_RESPONSE_URL` y `PAYPHONE_CANCELLATION_URL`.
  - `CorsConfig.java` (`demo`, `msvc-producto`) y `WebConfig.java` (`msvc-ventas`): leen `APP_CORS_ALLOWED_ORIGINS` como lista separada por comas (`String[]`).
  - **Importante:** **NO** hace falta recompilar ni resubir ningún `.jar` de Java. Todo se gobierna con `/etc/tienda-virtual/backend.env`.

### 4.2 Servidor de Tienda (`192.168.200.5` — SSH `2226`)
| Componente / Archivo | Configuración actual | Configuración objetivo (Fase 1 -> Fase 3) |
|---|---|---|
| `firewalld` (`public`) | `444/tcp 80/tcp` | **Fase 1:** Añadir `https` (`443/tcp`) manteniendo `444/tcp` y `80/tcp` |
| `/etc/nginx/conf.d/tienda.conf` (bloque HTTPS) | `listen 444 ssl; listen [::]:444 ssl;` | **Fase 1:** Añadir `listen 443 ssl; listen [::]:443 ssl;` en el mismo bloque `server` + directivas `set_real_ip_from 192.168.200.6; real_ip_header X-Forwarded-For; real_ip_recursive on;` |
| `/etc/nginx/conf.d/tienda.conf` (bloque HTTP `:80`) | `root /usr/share/nginx/html;` + `return 301 https://$host:444$request_uri;` | **Fase 1:** No tocar aún (dejar `:444`). **Fase 3:** Cambiar solo el redirect a `return 301 https://$host$request_uri;` (**conservar siempre `root /usr/share/nginx/html;`** para Certbot) |
| `/etc/tienda-virtual/backend.env` | `APP_CORS_ALLOWED_ORIGINS=https://tienda.gadmsigchos.gob.ec:444` <br> `PAYPHONE_RESPONSE_URL=...:444/...` <br> `PAYPHONE_CANCELLATION_URL=...:444/...` | **Fase 1:** `APP_CORS_ALLOWED_ORIGINS=https://tienda.gadmsigchos.gob.ec,https://tienda.gadmsigchos.gob.ec:444` <br> **Fase 3:** Quitar `:444` en `PAYPHONE_RESPONSE_URL` y `PAYPHONE_CANCELLATION_URL` (+ verificar `chmod 600` y `chcon -t etc_t`) |
| `/var/www/tienda-virtual-frontend/.env.production` | `NEXT_PUBLIC_GATEWAY_URL=https://tienda.gadmsigchos.gob.ec:444` | **Fase 3:** `NEXT_PUBLIC_GATEWAY_URL=https://tienda.gadmsigchos.gob.ec` y recompilar `npm run build` |
| `/etc/systemd/system/tienda-frontend.service` | `Environment="NEXT_PUBLIC_GATEWAY_URL=https://tienda.gadmsigchos.gob.ec:444"` | **Fase 3:** `Environment="NEXT_PUBLIC_GATEWAY_URL=https://tienda.gadmsigchos.gob.ec"` + `systemctl daemon-reload && systemctl restart tienda-frontend` |

> [!CAUTION]
> **Discrepancia detectada entre `gateway/tienda.prod.conf` y el servidor real:**  
> En el repo, `gateway/tienda.prod.conf` línea 49 dice `root /var/www/html;`, pero en el servidor de producción el bloque `:80` de `/etc/nginx/conf.d/tienda.conf` usa `root /usr/share/nginx/html;`, que es la ruta registrada en `/etc/letsencrypt/renewal/tienda.gadmsigchos.gob.ec.conf`. Al editar Nginx en producción, **mantener `root /usr/share/nginx/html;`**.

### 4.3 Servidor de Turismo (`192.168.200.6` — SSH `2244`)
| Componente / Archivo | Configuración actual | Configuración objetivo (Fase 1) |
|---|---|---|
| `firewalld` (`public`) | `448/tcp 80/tcp 8084/tcp` | Añadir `https` (`443/tcp`) |
| `/etc/nginx/ssl/tienda.gadmsigchos.gob.ec/` | No existe | Crear directorio y copiar `fullchain.pem` (`0644`) y `privkey.pem` (`0600`) desde Tienda (`restorecon -Rv /etc/nginx/ssl`) |
| `/etc/nginx/conf.d/proxy_tienda.conf` | Solo bloque `listen 80;` -> `http://192.168.200.5` | Conservar intacto el bloque `:80` y añadir bloque `listen 443 ssl; listen [::]:443 ssl; http2 on; server_name tienda.gadmsigchos.gob.ec;` -> `proxy_pass https://192.168.200.5:444;` |

---

## 5. Plan de Ejecución Seguro en 3 Fases (Cero Caídas)

### FASE 1 — Preparación de Servidores sin Cortar `:444` (Se puede ejecutar YA)
En esta fase `:444` sigue 100% operativo, pero ambos servidores quedan listos para recibir tráfico `:443`.

#### 1.1 Respaldos previos obligatorios
En **Tienda (`2226`)**:
```bash
mkdir -p /root/backup-migracion-443-$(date +%F)
cp -a /etc/nginx/conf.d/tienda.conf /root/backup-migracion-443-$(date +%F)/
cp -a /etc/tienda-virtual/backend.env /root/backup-migracion-443-$(date +%F)/
cp -a /etc/systemd/system/tienda-frontend.service /root/backup-migracion-443-$(date +%F)/
cp -a /var/www/tienda-virtual-frontend/.env.production /root/backup-migracion-443-$(date +%F)/
```
En **Turismo (`2244`)**:
```bash
mkdir -p /root/backup-migracion-443-$(date +%F)
cp -a /etc/nginx/conf.d/proxy_tienda.conf /root/backup-migracion-443-$(date +%F)/
```

#### 1.2 Configurar Servidor de Tienda (`2226`)
1. Abrir puerto `443/tcp` en `firewalld`:
   ```bash
   firewall-cmd --permanent --add-service=https
   firewall-cmd --permanent --add-port=443/tcp
   firewall-cmd --reload
   firewall-cmd --list-all
   ```
2. Editar `/etc/nginx/conf.d/tienda.conf`:
   - Dentro del `server` de HTTPS (que hoy tiene `listen 444 ssl;`), dejar ambos puertos y la configuración de Real IP para cuando el proxy venga desde Turismo (`192.168.200.6`):
     ```nginx
     server {
         listen 443 ssl;
         listen [::]:443 ssl;
         listen 444 ssl;
         listen [::]:444 ssl;
         http2 on;
         server_name tienda.gadmsigchos.gob.ec;
         server_tokens off;

         # Confianza en la IP interna del proxy de Turismo para conservar la IP real del cliente en rate-limiting
         set_real_ip_from 192.168.200.6;
         real_ip_header X-Forwarded-For;
         real_ip_recursive on;
     ```
   - Verificar sintaxis y recargar:
     ```bash
     nginx -t && systemctl reload nginx
     ss -tlnp | grep -E ':(80|443|444)'
     ```
3. Preparar CORS dual en `/etc/tienda-virtual/backend.env` (sin cambiar todavía PayPhone URL):
   - Cambiar `APP_CORS_ALLOWED_ORIGINS` a:
     ```env
     APP_CORS_ALLOWED_ORIGINS=https://tienda.gadmsigchos.gob.ec,https://tienda.gadmsigchos.gob.ec:444
     ```
   - Restaurar permisos y SELinux:
     ```bash
     chmod 600 /etc/tienda-virtual/backend.env
     chcon -t etc_t /etc/tienda-virtual/backend.env
     ls -lZ /etc/tienda-virtual/backend.env
     ```
   - Reiniciar en orden los 3 servicios que consumen `APP_CORS_ALLOWED_ORIGINS` (`auth-service`, `msvc-producto`, `msvc-ventas`) y comprobar `active` y registro en Eureka (`auth-service` y `msvc-producto`):
     ```bash
     systemctl restart auth-service msvc-producto msvc-ventas
     sleep 15
     systemctl is-active auth-service msvc-producto msvc-ventas
     curl -s http://127.0.0.1:8761/eureka/apps | grep -Eo '<name>[^<]+</name>'
     ```
4. Probar localmente en Tienda que `:443` responde TLS + Gateway:
   ```bash
   curl -skI --resolve tienda.gadmsigchos.gob.ec:443:127.0.0.1 https://tienda.gadmsigchos.gob.ec/api/health
   ```

#### 1.3 Configurar Servidor de Turismo (`2244`)
1. Abrir puerto `443/tcp` en `firewalld`:
   ```bash
   firewall-cmd --permanent --add-service=https
   firewall-cmd --permanent --add-port=443/tcp
   firewall-cmd --reload
   firewall-cmd --list-all
   ```
2. Copiar certificado de `tienda.gadmsigchos.gob.ec` desde Tienda (`192.168.200.5`) hacia `/etc/nginx/ssl/tienda.gadmsigchos.gob.ec/` en Turismo (por ejemplo vía `scp` desde tu PC puenteando Tienda -> local temporal -> Turismo, o directo por red LAN si se autoriza llave SSH, **borrando el temporal en PC si se pasa por ahí**):
   ```bash
   mkdir -p /etc/nginx/ssl/tienda.gadmsigchos.gob.ec
   chmod 700 /etc/nginx/ssl/tienda.gadmsigchos.gob.ec
   # Tras copiar fullchain.pem y privkey.pem:
   chmod 644 /etc/nginx/ssl/tienda.gadmsigchos.gob.ec/fullchain.pem
   chmod 600 /etc/nginx/ssl/tienda.gadmsigchos.gob.ec/privkey.pem
   restorecon -Rv /etc/nginx/ssl
   ```
3. Actualizar `/etc/nginx/conf.d/proxy_tienda.conf` en Turismo (conservando exactamente el bloque `listen 80;` actual):
   ```nginx
   server {
       listen 80;
       server_name tienda.gadmsigchos.gob.ec;

       location / {
           proxy_pass http://192.168.200.5;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }

   server {
       listen 443 ssl;
       listen [::]:443 ssl;
       http2 on;
       server_name tienda.gadmsigchos.gob.ec;
       server_tokens off;

       ssl_certificate     /etc/nginx/ssl/tienda.gadmsigchos.gob.ec/fullchain.pem;
       ssl_certificate_key /etc/nginx/ssl/tienda.gadmsigchos.gob.ec/privkey.pem;
       ssl_protocols       TLSv1.2 TLSv1.3;
       ssl_prefer_server_ciphers on;

       client_max_body_size 10M;
       proxy_connect_timeout 5s;
       proxy_send_timeout    60s;
       proxy_read_timeout    60s;

       access_log /var/log/nginx/proxy_tienda_ssl_access.log;
       error_log  /var/log/nginx/proxy_tienda_ssl_error.log;

       location / {
           proxy_pass https://192.168.200.5:444;
           proxy_ssl_server_name on;
           proxy_ssl_verify off;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection $http_connection;
           proxy_set_header Host $http_host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto https;
           proxy_cache_bypass $http_upgrade;
       }
   }
   ```
   *(Nota: `Connection $http_connection` en Nginx reenvía `Upgrade` cuando hay WebSocket y mantiene `keep-alive` o limpio en HTTP normal. En `adilcorp.conf` / `tienda.conf` ya se usa `Upgrade` para Next.js).*
4. Validar sintaxis, recargar y verificar desde Turismo:
   ```bash
   nginx -t && systemctl reload nginx
   ss -tlnp | grep -E ':(80|443|448|8084)'
   curl -skI --resolve tienda.gadmsigchos.gob.ec:443:127.0.0.1 https://tienda.gadmsigchos.gob.ec/api/health
   ```
   El `curl` debe devolver `HTTP/2 200` sin afectar a `:448` ni a `:8084` de Turismo.

---

### FASE 2 — Solicitud Puntual al Administrador del Firewall pfSense (Grupo Mundo Digital)
Hasta este punto **nada en `:444` se ha roto**. Para que el tráfico público de Internet por `:443` deje de caducar y deje de chocar con el WebGUI de pfSense (`69.10.49.106:443`), envía esta solicitud al **Ing. Miguel Palacios / soporte de Grupo Mundo Digital**:

> **Plantilla de mensaje para soporte (Grupo Mundo Digital):**  
> *"Estimado Ing. Miguel Palacios, para publicar la Tienda Virtual (`tienda.gadmsigchos.gob.ec`) en el puerto HTTPS estándar `443` sin usar `:444`, necesitamos habilitar en el firewall pfSense (`69.10.49.106`) el reenvío de puerto (NAT Port Forward) del tráfico entrante **WAN TCP `443`** hacia nuestra VM de Tienda **`192.168.200.5` puerto `443`** (o en su defecto hacia `192.168.200.6` puerto `443`, donde ya está configurado el proxy inverso).  
> Actualmente en la IP `69.10.49.106` el puerto `443` da timeout desde Internet y responde en LAN el WebGUI de pfSense; agradeceríamos mover el WebGUI de pfSense a otro puerto o restringirlo y mantener temporalmente activo también el reenvío actual de `444` mientras completamos la transición final. Quedamos atentos para validar en vivo."*

#### Cómo verificar apenas soporte confirme el NAT
Desde PowerShell en tu PC:
```powershell
(Test-NetConnection -ComputerName 69.10.49.106 -Port 443).TcpTestSucceeded
curl.exe -vkI --connect-timeout 5 https://tienda.gadmsigchos.gob.ec/api/health
curl.exe -vkI --connect-timeout 5 https://tienda.gadmsigchos.gob.ec/landing
```
**Criterio de paso a Fase 3:** Debe devolver `TcpTestSucceeded = True`, certificado emitido para `tienda.gadmsigchos.gob.ec` (NO `pfSense GUI`) y código `HTTP/2 200` en `/api/health`.

---

### FASE 3 — Corte Final de Variables, Frontend y Redirección (Solo tras pasar Fase 2)

1. **Actualizar PayPhone en `/etc/tienda-virtual/backend.env` (Servidor de Tienda `2226`):**
   ```env
   PAYPHONE_RESPONSE_URL=https://tienda.gadmsigchos.gob.ec/checkout/payphone-confirmacion
   PAYPHONE_CANCELLATION_URL=https://tienda.gadmsigchos.gob.ec/checkout
   ```
   Asegurar permisos y SELinux y reiniciar `auth-service`:
   ```bash
   chmod 600 /etc/tienda-virtual/backend.env
   chcon -t etc_t /etc/tienda-virtual/backend.env
   systemctl restart auth-service
   sleep 15
   systemctl is-active auth-service
   curl -s http://127.0.0.1:8761/eureka/apps | grep -Eo '<name>[^<]+</name>'
   ```

2. **Recompilar y desplegar Frontend (`avalon-react-10.1.0`) con la nueva URL pública:**
   En `/var/www/tienda-virtual-frontend/.env.production` (y antes en tu `.env.production` local usado para el build):
   ```env
   NEXT_PUBLIC_GATEWAY_URL=https://tienda.gadmsigchos.gob.ec
   GATEWAY_INTERNAL_URL=http://127.0.0.1:8080
   NEXT_PUBLIC_GOOGLE_CLIENT_ID=<MANTENER_EL_ACTUAL>
   NEXT_PUBLIC_ADMIN_EMAIL=sigchosecommers@gmail.com
   ```
   Compilar (`npm run build`), subir `.next` y `.env.production` a `/var/www/tienda-virtual-frontend/`, actualizar `/etc/systemd/system/tienda-frontend.service`:
   ```ini
   Environment="NEXT_PUBLIC_GATEWAY_URL=https://tienda.gadmsigchos.gob.ec"
   ```
   Y reiniciar:
   ```bash
   systemctl daemon-reload
   systemctl restart tienda-frontend
   systemctl is-active tienda-frontend
   ```
   *Verificación rápida:* Confirmar en el HTML/JS compilado o en DevTools que las llamadas `fetch` de red ya salen hacia `https://tienda.gadmsigchos.gob.ec/api/...` y **NO** hacia `:444`.

3. **Confirmar plataformas externas:**
   - **PayPhone Developer:** Confirmar `Url de respuesta = https://tienda.gadmsigchos.gob.ec/checkout/payphone-confirmacion`, `Dominio web` incluye `https://tienda.gadmsigchos.gob.ec`, y si existe campo Webhook / Notificación actualizarlo a `https://tienda.gadmsigchos.gob.ec/api/payphone/confirm`.
   - **Google Cloud Console:** Confirmar que `https://tienda.gadmsigchos.gob.ec` figura guardado en `Orígenes autorizados de JavaScript`.

4. **Actualizar redirección HTTP `:80` -> HTTPS sin `:444` en Tienda (`/etc/nginx/conf.d/tienda.conf`):**
   En el bloque `listen 80;`:
   ```nginx
   location /.well-known/acme-challenge/ {
       root /usr/share/nginx/html;
   }

   location / {
       return 301 https://$host$request_uri;
   }
   ```
   Recargar Nginx (`nginx -t && systemctl reload nginx`).  
   *(Recomendación prudente: dejar `listen 444 ssl;` activo unos días más en `tienda.conf` o redirigir `:444` -> `:443` una vez verificadas todas las pruebas, para no romper enlaces antiguos guardados por usuarios durante la semana de transición).*

5. **Checklist de Verificación Post-Migración (sobre `https://tienda.gadmsigchos.gob.ec` sin puerto):**
   - [ ] `https://tienda.gadmsigchos.gob.ec/landing` carga `200`, candado TLS válido (`Let's Encrypt` para `tienda.gadmsigchos.gob.ec`), cero peticiones a `:444` en consola/red del navegador.
   - [ ] `http://tienda.gadmsigchos.gob.ec/landing` redirige `301` a `https://tienda.gadmsigchos.gob.ec/landing` (sin `:444`).
   - [ ] `https://tienda.gadmsigchos.gob.ec/api/productos` y `/api/categorias` devuelven `200` y cachés en Valkey funcionan.
   - [ ] Bloqueos de seguridad intactos: `/actuator/health`, `/swagger-ui.html`, `/camunda` -> `403`.
   - [ ] Login tradicional y **Google OAuth** funcionan sin error de origen.
   - [ ] Imagen QR DeUna carga `200` por `/uploads/qr-deuna/...` sin referencia a `:444`.
   - [ ] **Compra E2E real con PayPhone** desde `https://tienda.gadmsigchos.gob.ec`: redirige a `/checkout/payphone-confirmacion` (sin `:444`), queda `COMPLETADA`, descuenta stock 1 vez y genera PDF descargable (`200`).
   - [ ] Portales de Turismo (`https://turismo.gadmsigchos.gob.ec:448/` y `:8084`) siguen operativos al 100%.
   - [ ] Prueba de renovación Certbot en Tienda: `certbot renew --dry-run` termina sin errores.

---

## 6. Matriz de Resolución de Problemas y Plan de Rollback

| Síntoma / Fallo | Causa probable | Diagnóstico y corrección exacta |
|---|---|---|
| `https://tienda.gadmsigchos.gob.ec` da *Connection timed out* o muestra certificado `pfSense GUI` | Falta el NAT Port Forward `WAN 443` en pfSense, o `443` sigue bloqueado en `firewalld` | Confirmar en el VPS si llegan paquetes con sniffer Python `AF_PACKET` en puerto `443`. Si llegan `0`, el bloqueo está en el pfSense de Grupo Mundo Digital. Si llegan paquetes pero no conecta, revisar `firewall-cmd --list-all` y `ss -tlnp \| grep :443`. |
| Error `502 Bad Gateway` al entrar vía Turismo (`192.168.200.6`) | `firewalld` en Tienda bloquea el puerto destino (`443`/`444`), `httpd_can_network_connect` apagado en Turismo, o certificado inválido | Revisar `tail -n 30 /var/log/nginx/proxy_tienda_ssl_error.log` en Turismo; verificar `getsebool httpd_can_network_connect` (`on`) y `curl -skI https://192.168.200.5:444/health` desde Turismo. |
| Error `403 Forbidden` / *Invalid CORS request* al llamar `/api/...` | `APP_CORS_ALLOWED_ORIGINS` no incluye `https://tienda.gadmsigchos.gob.ec`, o Turismo no envía `X-Forwarded-Proto https` / `Host` | Revisar `APP_CORS_ALLOWED_ORIGINS` en `/etc/tienda-virtual/backend.env`, confirmar `proxy_set_header Host $http_host;` y `X-Forwarded-Proto https;` en Turismo, y reiniciar `auth-service`, `msvc-producto`, `msvc-ventas`. |
| Microservicio Java no arranca (`status=209/STDOUT` o `Permission denied` en `backend.env`) | Contexto SELinux o permisos perdidos al editar `/etc/tienda-virtual/backend.env` | Ejecutar `chmod 600 /etc/tienda-virtual/backend.env && chcon -t etc_t /etc/tienda-virtual/backend.env && systemctl restart <servicio>`. |
| PayPhone falla al abrir cajita o redirige a URL rota | Diferencia entre `PAYPHONE_RESPONSE_URL` en `backend.env` y `Url de respuesta` / `Dominio web` en PayPhone Developer | Igualar exactamente `PAYPHONE_RESPONSE_URL` (`backend.env`), `Url de respuesta` (PayPhone Developer) y recompilar frontend con la misma URL base. |
| Google OAuth falla con error de origen/URI | Falta `https://tienda.gadmsigchos.gob.ec` o no se guardó en Google Cloud Console | Verificar en Google Cloud Console (`Tienda Virtual Web` -> *Orígenes autorizados de JavaScript*) que ambos URIs están guardados y que `GOOGLE_CLIENT_ID` y `NEXT_PUBLIC_GOOGLE_CLIENT_ID` coinciden. |
| `429 Too Many Requests` masivo si el tráfico pasa por Turismo | `limit_req_zone $binary_remote_addr` en Tienda está viendo `192.168.200.6` como única IP de todos los usuarios | Confirmar `set_real_ip_from 192.168.200.6; real_ip_header X-Forwarded-For; real_ip_recursive on;` en `/etc/nginx/conf.d/tienda.conf` y que `access.log` registra la IP pública real del cliente. |
| `certbot renew --dry-run` falla con `404` | El bloque `:80` de Turismo no reenvía a Tienda `:80`, o en Tienda se cambió `root /usr/share/nginx/html` por `/var/www/html` | Restaurar en Tienda `location /.well-known/acme-challenge/ { root /usr/share/nginx/html; }` y mantener el bloque `listen 80;` de `/etc/nginx/conf.d/proxy_tienda.conf` en Turismo. |
| Si se usa **Alternativa 2** y el certificado expira tras 90 días | Certbot renovó en Tienda (`192.168.200.5`) pero Turismo (`192.168.200.6`) sigue con la copia vieja en `/etc/nginx/ssl/` | Re-copiar `/etc/letsencrypt/live/tienda.gadmsigchos.gob.ec/{fullchain,privkey}.pem` a `/etc/nginx/ssl/tienda.gadmsigchos.gob.ec/` en Turismo y `systemctl reload nginx` (o migrar el NAT del pfSense a la Alternativa 1 directo a `192.168.200.5:443`). |

### Plan de Rollback (< 60 segundos)
Si ocurre cualquier fallo inesperado durante la Fase 3 y debes volver inmediatamente a `:444`:
1. **En Tienda (`2226`):**
   ```bash
   BK=$(ls -td /root/backup-migracion-443-* | head -n 1)
   cp -a "$BK/tienda.conf" /etc/nginx/conf.d/tienda.conf
   cp -a "$BK/backend.env" /etc/tienda-virtual/backend.env
   chmod 600 /etc/tienda-virtual/backend.env && chcon -t etc_t /etc/tienda-virtual/backend.env
   cp -a "$BK/tienda-frontend.service" /etc/systemd/system/tienda-frontend.service
   cp -a "$BK/.env.production" /var/www/tienda-virtual-frontend/.env.production
   nginx -t && systemctl reload nginx
   systemctl daemon-reload
   systemctl restart auth-service msvc-producto msvc-ventas
   # Recompilar o restaurar el build .next anterior si ya se sobrescribió y reiniciar tienda-frontend
   systemctl restart tienda-frontend
   ```
   *(Consejo adicional: antes de reemplazar `.next` en la Fase 3, guarda una copia `mv /var/www/tienda-virtual-frontend/.next /var/www/tienda-virtual-frontend/.next.bak-444` para revertir el frontend en 2 segundos sin recompilar).*
2. **En Turismo (`2244`):**
   ```bash
   BK=$(ls -td /root/backup-migracion-443-* | head -n 1)
   cp -a "$BK/proxy_tienda.conf" /etc/nginx/conf.d/proxy_tienda.conf
   nginx -t && systemctl reload nginx
   ```
3. **En PayPhone Developer:** regresar `Url de respuesta` a `https://tienda.gadmsigchos.gob.ec:444/checkout/payphone-confirmacion`.

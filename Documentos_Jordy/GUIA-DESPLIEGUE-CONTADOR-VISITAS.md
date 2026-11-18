# Guía de Despliegue a Producción: Contador Persistente de Visitas (`feature/contador-visitas`)

**Fecha de análisis y verificación en vivo:** 2026-11-17  
**Servidor destino:** `tienda.gadmsigchos.gob.ec` (SSH puerto `2226`, AlmaLinux 10.1)  
**URL pública vigente:** `https://tienda.gadmsigchos.gob.ec:444`  
**Rama / Commit analizado:** `origin/feature/contador-visitas` -> `daffd59` (*`feat: agregar contador persistente de visitas`*, padre `f8a2006`)

> [!NOTE]
> **Estado real verificado en Git y en el servidor (2026-11-17):**
> 1. Actualmente **`main` (`origin/main`) sigue en `f8a2006`**, que es el código ya desplegado en producción. El nuevo cambio (`daffd59`) está en `origin/feature/contador-visitas` y aún requiere integrarse a `main`.
> 2. En la base MySQL de producción (`productos`), la tabla `visitas_diarias` **ya existe** con el esquema idéntico al de `scripts/prod/migraciones/V12-visitas-diarias.sql` (`0` filas actuales). Aun así, en el Paso 3 se respalda `productos` y se valida idempotente antes de reiniciar `msvc-producto`.
> 3. Las credenciales SSH están en `documentos-trabajo/docs_produ/guia-wildcat-ssh-ssl.md`. **No** escribir contraseñas, tokens ni secretos en este documento.

---

## 1. Alcance Exacto del Despliegue (Qué SÍ y Qué NO se Toca)

### Componentes que NO hace falta tocar ni reiniciar
- `eureka-server`
- `auth-service`
- `msvc-inventario`
- `msvc-ventas` (conservar intacto `/var/www/tienda-virtual-backend/msvc-ventas/facturas`)
- `valkey` (caché Redis)
- `/etc/tienda-virtual/backend.env` (`0600 root:root`, contexto SELinux `etc_t`)
- `/var/www/tienda-virtual-backend/auth-service/uploads/qr-deuna`
- Servidor de Turismo (`2244`)

### Componentes que SÍ participan en este despliegue
1. **Git + plantilla Gateway:** fusionar `origin/feature/contador-visitas` a `main` y añadir `/api/visitas` en `gateway/tienda.prod.conf`.
2. **Nginx en el VPS (`/etc/nginx/conf.d/tienda.conf`):** añadir `|visitas` en las rutas de `prod_producto_service` tanto en el bloque público `:444` como en el bloque interno `127.0.0.1:8080`.
3. **MySQL (`productos`):** respaldo previo y validación/aplicación idempotente de `scripts/prod/migraciones/V12-visitas-diarias.sql`.
4. **Backend (`msvc-producto`):** compilar y subir únicamente `msvc-producto-0.0.1-SNAPSHOT.jar` a `/var/www/tienda-virtual-backend/msvc-producto/`.
5. **Frontend (`avalon-react-10.1.0`):** compilar `.next` con las variables de producción (`:444`), subir `.next` a `/var/www/tienda-virtual-frontend/` y reiniciar `tienda-frontend`.

---

## 2. Resumen de Archivos Cambiados y 3 Trampas Detectadas

### 2.1 Inventario de cambios del commit `daffd59`
| Área | Archivos en `daffd59` | Función |
|---|---|---|
| **Base de datos** | `scripts/prod/migraciones/V12-visitas-diarias.sql` | Crea `productos.visitas_diarias` (`fecha` PK, `cantidad`, `fecha_actualizacion`). |
| **Backend (`msvc-producto`)** | `VisitasEstadisticasDto.java`, `VisitaDiariaEntity.java`, `VisitaDiariaJpaRepository.java`, `VisitasService.java`, `VisitasController.java`, `VisitasServiceTest.java` | `POST /api/visitas` (público, `204`) e `GET /api/visitas/estadisticas` (`@PreAuthorize("hasAuthority('ROLE_ADMIN')")`). |
| **Frontend (`avalon-react-10.1.0`)** | `services/visitasService.ts`, `app/(main)/LandingPage.tsx`, `app/(full-page)/administrador/page.tsx` | Registra visita diaria (cookie `feriadigital_visit_day`) al cargar `/landing` o `/` y muestra la tarjeta *Visitas del sitio* en `/administrador`. |
| **Gateway Docker/dev** | `gateway/nginx.conf` | Añadió `location /api/visitas` únicamente en el gateway de Docker/local. |

### 2.2 Puntos críticos que DEBEN cuidarse para que no falle en producción
1. **Falta `/api/visitas` en `gateway/tienda.prod.conf` y `/etc/nginx/conf.d/tienda.conf`:**
   - `avalon-react-10.1.0/services/visitasService.ts` consume `${NEXT_PUBLIC_PRODUCTOS_API_URL || '/api/proxy/productos'}/visitas`.
   - En producción esa variable no está definida, por lo que el navegador pide `/api/proxy/productos/visitas` y `/api/proxy/productos/visitas/estadisticas`.
   - `avalon-react-10.1.0/next.config.js` reescribe `/api/proxy/productos/:path*` hacia `http://127.0.0.1:8080/api/:path*` (`http://127.0.0.1:8080/api/visitas...`).
   - En `gateway/tienda.prod.conf` (y en `/etc/nginx/conf.d/tienda.conf`), las líneas **246** (`listen 444 ssl`) y **376** (`listen 127.0.0.1:8080`) solo enrutan `^/api/(productos|categorias|empresas)(/.*)?$`. Sin agregar `|visitas` en ambas líneas, **el contador devolvería `404`** en producción.
2. **`spring.jpa.hibernate.ddl-auto=validate` en `msvc-producto`:**
   - Verificado en `Microservicios/msvc-producto/src/main/resources/application.properties` línea 22: si `visitas_diarias` no existiera en `productos`, `msvc-producto` fallaría al arrancar. Por eso siempre se verifica/aplica MySQL **antes** de reiniciar el JAR.
3. **Precedencia de `.env.local` en `npm run build` local:**
   - `avalon-react-10.1.0/.env.local` contiene `NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080`.
   - En Next.js 13, `.env.local` tiene prioridad sobre `.env.production` incluso durante `npm run build`, mientras que **`process.env` tiene la prioridad más alta**. Por ello, antes de compilar `.next` en tu PC debes exportar `NEXT_PUBLIC_*` en la sesión de PowerShell (y validar después que `.next` no contenga `localhost:8080`) usando los valores exactos de `/var/www/tienda-virtual-frontend/.env.production` del VPS.

---

## 3. Procedimiento Paso a Paso para Subir al Servidor

### PASO 1 — Integrar en Git y corregir Nginx de producción (`gateway/tienda.prod.conf`)
1. Confirmar estado limpio en `main` y fusionar la rama:
   ```powershell
   git checkout main
   git pull origin main
   git merge origin/feature/contador-visitas
   ```
2. Editar `gateway/tienda.prod.conf` en **las dos ubicaciones** de `prod_producto_service`:
   - **Bloque HTTPS `:444` (línea 246):**
     ```nginx
     location ~ ^/api/(productos|categorias|empresas|visitas)(/.*)?$ {
     ```
   - **Bloque Gateway interno `127.0.0.1:8080` (línea 376):**
     ```nginx
     location ~ ^/api/(productos|categorias|empresas|visitas)(/.*)?$ {
     ```
3. *(Recomendado tras probar)*: hacer `commit` de esa corrección y `git push origin main` para que el repositorio y el VPS queden sincronizados.

---

### PASO 2 — Compilar y probar en tu PC local

#### 2.1 Backend (`msvc-producto`)
En `Microservicios\msvc-producto`:
```powershell
cd Microservicios\msvc-producto
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests
```
- Verificar que todos los tests (incluyendo `VisitasServiceTest`) pasan en verde.
- Confirmar el artefacto generado: `Microservicios\msvc-producto\target\msvc-producto-0.0.1-SNAPSHOT.jar`.

#### 2.2 Frontend (`avalon-react-10.1.0`)
1. Consultar primero en el VPS cuáles son los valores vigentes en `/var/www/tienda-virtual-frontend/.env.production` (sin copiarlos al repo):
   ```powershell
   ssh -p 2226 root@tienda.gadmsigchos.gob.ec "cat /var/www/tienda-virtual-frontend/.env.production"
   ```
2. En tu PowerShell local, cargar esas variables en el entorno de proceso (`$env:...` pisa a `.env.local`), pasar lint y compilar:
   ```powershell
   cd avalon-react-10.1.0
   $env:NEXT_PUBLIC_GATEWAY_URL="https://tienda.gadmsigchos.gob.ec:444"
   $env:GATEWAY_INTERNAL_URL="http://127.0.0.1:8080"
   $env:NEXT_PUBLIC_GOOGLE_CLIENT_ID="<VALOR_DEL_VPS>"
   $env:NEXT_PUBLIC_ADMIN_EMAIL="<VALOR_DEL_VPS>"
   npm run lint
   npm run build
   ```
3. **Comprobación obligatoria post-build (evitar `localhost:8080` incrustado):**
   ```powershell
   Select-String -Path ".next\static\**\*.js" -Pattern "localhost:8080"
   Select-String -Path ".next\static\**\*.js" -Pattern "tienda\.gadmsigchos\.gob\.ec:444" | Select-Object -First 3
   ```
   - La primera búsqueda debe devolver **0 coincidencias**; la segunda debe encontrar la URL de producción.

---

### PASO 3 — Respaldo y migración SQL en el VPS (`2226`)
Conectado al servidor de Tienda (`ssh -p 2226 root@tienda.gadmsigchos.gob.ec`):

```bash
mkdir -p /root/backup-visitas-$(date +%F_%H%M)
BK=/root/backup-visitas-$(date +%F_%H%M)

# Cargar credenciales MySQL desde el archivo seguro sin imprimirlas
set -a; source /etc/tienda-virtual/backend.env; set +a

# 1. Respaldo rápido de la base productos
mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --single-transaction productos > "$BK/productos.sql"
ls -lh "$BK/productos.sql"

# 2. Verificar / aplicar tabla visitas_diarias (idempotente)
mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -D productos -e "
CREATE TABLE IF NOT EXISTS visitas_diarias (
    fecha DATE NOT NULL,
    cantidad BIGINT NOT NULL DEFAULT 0,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (fecha),
    CONSTRAINT chk_visitas_diarias_cantidad CHECK (cantidad >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SHOW CREATE TABLE visitas_diarias;
"
```

---

### PASO 4 — Actualizar Nginx en el VPS (`/etc/nginx/conf.d/tienda.conf`)
En el servidor de Tienda (`2226`), **sin alterar `root /usr/share/nginx/html;` del bloque `:80`**:

```bash
cp -a /etc/nginx/conf.d/tienda.conf "$BK/tienda.conf.bak"

# Agregar |visitas en ambas reglas de prod_producto_service (:444 y 127.0.0.1:8080)
sed -i 's|location ~ \^/api/(productos\|categorias\|empresas)(/.\*)?\$|location ~ ^/api/(productos|categorias|empresas|visitas)(/.*)?$|g' /etc/nginx/conf.d/tienda.conf

# Verificar que quedaron exactamente 2 coincidencias (bloque :444 y bloque 127.0.0.1:8080) y que acme-challenge sigue intacto
grep -n -E 'visitas|acme-challenge|root ' /etc/nginx/conf.d/tienda.conf

# Validar sintaxis y recargar sin caída
nginx -t && systemctl reload nginx
```

---

### PASO 5 — Desplegar únicamente `msvc-producto`
1. **Respaldar el JAR actual y detener solo `msvc-producto`** (en el VPS):
   ```bash
   cp -a /var/www/tienda-virtual-backend/msvc-producto/msvc-producto-0.0.1-SNAPSHOT.jar "$BK/"
   systemctl stop msvc-producto
   ```
2. **Subir el nuevo JAR** desde PowerShell local:
   ```powershell
   scp -P 2226 Microservicios\msvc-producto\target\msvc-producto-0.0.1-SNAPSHOT.jar root@tienda.gadmsigchos.gob.ec:/var/www/tienda-virtual-backend/msvc-producto/msvc-producto-0.0.1-SNAPSHOT.jar
   ```
3. **Arrancar y comprobar registro en Eureka** (en el VPS):
   ```bash
   systemctl start msvc-producto
   sleep 15
   systemctl is-active msvc-producto
   journalctl -u msvc-producto -n 30 --no-pager | grep -Ei 'error|exception|started|visitas' || true
   curl -s http://127.0.0.1:8761/eureka/apps | grep -Eo '<name>[^<]+</name>'
   ```
   - Debe aparecer `active` y `MSVC-PRODUCTO` listado en Eureka junto a `LOGIN` y `MSVC-INVENTARIO`.

---

### PASO 6 — Desplegar el Frontend (`.next`) sin tocar `.env.production` del VPS
1. **En el VPS**, respaldar `.next` y `.env.production` actuales y detener `tienda-frontend` brevemente mientras se reemplaza `.next`:
   ```bash
   cp -a /var/www/tienda-virtual-frontend/.env.production "$BK/"
   systemctl stop tienda-frontend
   mv /var/www/tienda-virtual-frontend/.next "$BK/.next.old"
   ```
2. **Desde PowerShell local**, subir únicamente la nueva carpeta `.next` compilada en el Paso 2.2 (sin tocar `/var/www/tienda-virtual-frontend/.env.production`):
   ```powershell
   scp -P 2226 -r avalon-react-10.1.0\.next root@tienda.gadmsigchos.gob.ec:/var/www/tienda-virtual-frontend/
   ```
3. **En el VPS**, arrancar y comprobar `tienda-frontend`:
   ```bash
   chown -R root:root /var/www/tienda-virtual-frontend/.next
   systemctl start tienda-frontend
   sleep 5
   systemctl is-active tienda-frontend
   curl -s -o /dev/null -w "LOCAL_FRONTEND:%{http_code}\n" http://127.0.0.1:3000/landing
   ```

---

### PASO 7 — Verificación Rápida en Producción (`:444`)

1. **Seguridad del endpoint de estadísticas (sin token debe rechazar):**
   ```powershell
   curl.exe -s -o $null -w "SIN_TOKEN:%{http_code}`n" https://tienda.gadmsigchos.gob.ec:444/api/visitas/estadisticas
   curl.exe -s -o $null -w "PROXY_SIN_TOKEN:%{http_code}`n" https://tienda.gadmsigchos.gob.ec:444/api/proxy/productos/visitas/estadisticas
   ```
   - Ambos deben responder **`401`** (no `404` ni `200`).

2. **Probar flujo real en navegador (ventana Incógnito para evitar caché/cookie previa):**
   - Abrir `https://tienda.gadmsigchos.gob.ec:444/landing`.
   - En DevTools -> Network confirmar `POST .../api/proxy/productos/visitas` -> **`204 No Content`** y creación de la cookie `feriadigital_visit_day=1`.
   - Recargar la página (`F5`): gracias a la cookie diaria, **no** debe volver a disparar `POST /visitas` en la misma sesión del día.

3. **Confirmar persistencia en MySQL (en el VPS):**
   ```bash
   set -a; source /etc/tienda-virtual/backend.env; set +a
   mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -D productos -e "SELECT * FROM visitas_diarias ORDER BY fecha DESC LIMIT 5;"
   ```
   - Debe existir la fila del día actual (zona `America/Guayaquil`) con `cantidad >= 1`.

4. **Confirmar vista del Administrador:**
   - Iniciar sesión como `ROLE_ADMIN` en `https://tienda.gadmsigchos.gob.ec:444/administrador`.
   - Verificar que `GET /api/proxy/productos/visitas/estadisticas` devuelve **`200`** JSON (`{"total":...,"hoy":...,"fecha":"..."}`) y se muestra la tarjeta **Visitas del sitio**.

5. **Regresión mínima de servicios existentes:**
   - `curl.exe -s https://tienda.gadmsigchos.gob.ec:444/api/health` -> `200`
   - `curl.exe -s -o $null -w "%{http_code}\n" https://tienda.gadmsigchos.gob.ec:444/api/productos` -> `200`

---

## 4. Plan de Rollback (< 60 segundos) y Diagnóstico Rápido

### Rollback inmediato en el VPS si algo falla
```bash
BK=$(ls -td /root/backup-visitas-* | head -n 1)

# 1. Revertir Nginx
cp -a "$BK/tienda.conf.bak" /etc/nginx/conf.d/tienda.conf
nginx -t && systemctl reload nginx

# 2. Revertir msvc-producto
systemctl stop msvc-producto
cp -a "$BK/msvc-producto-0.0.1-SNAPSHOT.jar" /var/www/tienda-virtual-backend/msvc-producto/msvc-producto-0.0.1-SNAPSHOT.jar
systemctl start msvc-producto

# 3. Revertir Frontend (.next)
systemctl stop tienda-frontend
rm -rf /var/www/tienda-virtual-frontend/.next
cp -a "$BK/.next.old" /var/www/tienda-virtual-frontend/.next
systemctl start tienda-frontend
```
*(La tabla `productos.visitas_diarias` es aditiva y no rompe a la versión anterior de `msvc-producto`; no hace falta borrarla en un rollback rápido).*

### Diagnóstico si algo falla
| Síntoma | Causa probable | Solución |
|---|---|---|
| `/api/proxy/productos/visitas` devuelve `404` | Faltó agregar `\|visitas` en el bloque `127.0.0.1:8080` de `/etc/nginx/conf.d/tienda.conf` | Revisar líneas `246` y `376` en `tienda.conf`, agregar `\|visitas` y hacer `nginx -t && systemctl reload nginx`. |
| `msvc-producto` falla al arrancar | Tabla `visitas_diarias` faltante o alterada (`ddl-auto=validate`) | Revisar `journalctl -u msvc-producto -n 50` y ejecutar `V12-visitas-diarias.sql`. |
| Frontend hace llamadas a `http://localhost:8080` | `npm run build` leyó `.env.local` local | Recompilar exportando `$env:NEXT_PUBLIC_*` antes de `npm run build` y verificar con `Select-String` en `.next\static\`. |
| Tarjeta *Visitas del sitio* no aparece en `/administrador` | El usuario logueado no tiene `ROLE_ADMIN`, o Nginx `127.0.0.1:8080` no reenvía `Authorization` | Confirmar `proxy_set_header Authorization $http_authorization;` en la ruta de `prod_producto_service` y revisar el estado en Network (`200` vs `403`). |

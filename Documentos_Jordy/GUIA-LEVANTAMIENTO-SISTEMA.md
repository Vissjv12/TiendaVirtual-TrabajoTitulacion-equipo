# 📖 Guía de Levantamiento Local — E-Commerce Sigchos (Feria Digital)

> **Destinatarios:** Equipo de desarrollo (titulación / vinculación).
>
> **Propósito:** Procedimiento paso a paso para levantar la base de datos, microservicios y frontend en un entorno Windows local.

---

## 🛠️ 1. Prerrequisitos del Sistema

| Herramienta | Versión Requerida | Estado / Ubicación recomendada |
|---|---|---|
| **Docker Desktop** | 4.x+ | Motor activo (ballena 🐳 en la barra de tareas) |
| **JDK (Java)** | **Java 17 Obligatorio** | Eclipse Temurin 17 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`) |
| **Node.js** | 18+ o 20+ (compatible con 24) | `node -v` |
| **npm** | 9+ u 11+ | `npm -v` |
| **Git** | 2.x+ | Instalado en el sistema |

### ⚠️ Configuración de Entorno en cada Terminal (Obligatorio)
Para evitar que el compilador Maven utilice versiones experimentales de Java (como Java 26), **siempre ejecuta estas dos líneas al abrir una nueva terminal de PowerShell**:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```
*(Verifica que responda versión 17 con `java -version`)*.

### 🔐 Cargar las variables del `.env` en cada terminal de backend (Obligatorio)
Los secretos (`MYSQL_PASSWORD`, `JWT_SECRET`, `STRIPE_*`, `CAMUNDA_*`) ya no están en `application.properties`. Se leen del entorno. Ejecuta esto en cada terminal antes de `mvnw.cmd` (no imprime valores):

```powershell
$root = "d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo"
Get-Content "$root\.env" | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim().Trim('"'), 'Process')
}
```

---

## 🗄️ 2. Paso 1: Levantar Base de Datos (MySQL 8.0) y API Gateway (NGINX) en Docker

El proyecto utiliza **Docker Compose** para dos contenedores ligeros e independientes:
1. **`mysql-feria-digital` (puerto `3306`):** Inicializa automáticamente los 4 esquemas (`auth_service`, `productos`, `inventario`, `ventas`) y sus datos semilla.
2. **`gateway-feria-digital` (puerto `8080`):** API Gateway NGINX que unifica el tráfico del frontend hacia los microservicios (`8081–8084`). **No requiere borrar ni recrear el contenedor de MySQL si ya lo tenías levantado.**

### 2.0 Preparar archivos `.env` (solo la primera vez si clonas desde cero)
Si acabas de clonar el repositorio y aún no tienes tus archivos `.env` y `avalon-react-10.1.0\.env.local`:
```powershell
Copy-Item .\.env.example .\.env
Copy-Item .\avalon-react-10.1.0\.env.local.example .\avalon-react-10.1.0\.env.local
```
*(En `.env` asegúrate de definir `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD`, `JWT_SECRET` y `CAMUNDA_BPM_ADMIN_USER_PASSWORD`).*

### 2.1 Iniciar los contenedores (MySQL + API Gateway)
Abre PowerShell en la raíz del proyecto y ejecuta:

```powershell
docker compose -f .\docker-compose.db.yml up -d
docker compose -f .\docker-compose.gateway.yml up -d
```

### 2.2 Verificar estado de los contenedores y del API Gateway
```powershell
docker compose -f .\docker-compose.db.yml ps
docker compose -f .\docker-compose.gateway.yml ps
curl.exe http://localhost:8080/health
```
*(El contenedor `mysql-feria-digital` debe mostrar `Up` en el puerto `3306`, `gateway-feria-digital` debe mostrar `Up` en el puerto `8080` y `/health` debe responder `{"status":"UP","service":"api-gateway","engine":"nginx"}`)*.

### 2.3 Verificar logs y bases de datos
```powershell
# Ver logs de inicialización
docker logs --tail 20 mysql-feria-digital

# Verificar las 4 bases de datos (usa las credenciales del contenedor, sin escribirlas en la terminal)
docker exec -it mysql-feria-digital sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SHOW DATABASES;"'
```

### 2.4 Contraseña del Administrador (`admin`)
**No se requiere ninguna acción.** El seed activo (`scripts/mysql-init/06-seed-auth-service.sql`) ya asigna a `admin` el mismo hash BCrypt que a los demás usuarios de prueba, por lo que `admin` inicia sesión con la contraseña de la sección 5.

> [!WARNING]
> Versiones anteriores de esta guía indicaban ejecutar un `UPDATE` sobre `auth_service.usuarios` para copiar el hash de `sigcholac` a `admin`. **No lo ejecutes.** Modificar hashes directamente en la base está fuera del procedimiento de A-10. Si un inicio de sesión falla, primero confirma qué base y qué seed están activos (ver la sección 7).

---

## ☕ 3. Paso 2: Levantar los Microservicios Backend (Spring Boot)

> [!IMPORTANT]
> **Orden de encendido:**
> 1. `msvc-eureka` (Discovery Server) **SIEMPRE PRIMERO**.
> 2. `demo` (Autenticación y Usuarios).
> 3. `msvc-producto` (Catálogo y Empresas - *en singular*).
> 4. `msvc-inventario` (Stock físico).
> 5. `msvc-ventas` (Órdenes y Pasarelas de Pago).

### ⚡ Opción A (Recomendada): Usar el script automático `start-all-services.ps1`
Abre PowerShell y ejecuta:
```powershell
cd .\Microservicios
.\start-all-services.ps1
```
*(El script carga automáticamente los secretos de tu `.env`, configura Java 17 y abre cada microservicio en una nueva ventana en el orden correcto: Eureka primero, Auth, Producto, Inventario y Ventas).*

### 🖥️ Opción B (Manual): Abrir 5 terminales separadas de PowerShell y ejecutar en cada una:

---

### Terminal 1 — Servidor Eureka (Descubrimiento)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

cd d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\Microservicios\eureka-server
.\mvnw.cmd spring-boot:run
```
* **Puerto:** `8761`
* **Verificación:** Abre en tu navegador [http://localhost:8761](http://localhost:8761).

---

### Terminal 2 — Servicio de Autenticación (`demo`)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Cargar secretos del .env (obligatorio; sin esto MySQL responde 'Access denied')
Get-Content "d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\.env" | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object { $k, $v = $_ -split '=', 2; [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim().Trim('"'), 'Process') }

cd d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\Microservicios\demo
.\mvnw.cmd spring-boot:run
```
* **Puerto:** `8084`
* Gestiona registro, login JWT, roles y perfiles de emprendedores.

---

### Terminal 3 — Servicio de Productos (`msvc-producto`)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Cargar secretos del .env (obligatorio; sin esto MySQL responde 'Access denied')
Get-Content "d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\.env" | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object { $k, $v = $_ -split '=', 2; [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim().Trim('"'), 'Process') }

cd d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\Microservicios\msvc-producto
.\mvnw.cmd spring-boot:run
```
* **Puerto:** `8081`
* Gestiona catálogo, categorías y empresas.

---

### Terminal 4 — Servicio de Inventario (`msvc-inventario`)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Cargar secretos del .env (obligatorio; sin esto MySQL responde 'Access denied')
Get-Content "d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\.env" | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object { $k, $v = $_ -split '=', 2; [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim().Trim('"'), 'Process') }

cd d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\Microservicios\msvc-inventario
.\mvnw.cmd spring-boot:run
```
* **Puerto:** `8082`
* Gestiona stock y disponibilidad de productos.

---

### Terminal 5 — Servicio de Ventas (`msvc-ventas`)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Cargar secretos del .env (obligatorio; sin esto MySQL responde 'Access denied')
Get-Content "d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\.env" | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object { $k, $v = $_ -split '=', 2; [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim().Trim('"'), 'Process') }

cd d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\Microservicios\msvc-ventas
.\mvnw.cmd spring-boot:run
```
* **Puerto:** `8083`
* Gestiona pedidos, facturación y pagos (Payphone y Stripe).
* *Nota:* Este servicio corre correctamente en el puerto `8083`, pero los desarrolladores anteriores deshabilitaron su registro en Eureka por un tema de dependencias.
* *Arranque "silencioso":* `application.properties` usa `logging.level.root=WARN`, por lo que **no se muestra** `Started MsvcVentasApplication` ni `Tomcat started on port 8083`. El servicio tarda unos 15–20 s después de compilar. Para confirmar que está activo:
  ```powershell
  Get-NetTCPConnection -LocalPort 8083 -State Listen   # debe mostrar el puerto en escucha
  ```
  Si quieres ver los mensajes de arranque sin modificar archivos:
  ```powershell
  .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--logging.level.root=INFO"
  ```
* *Antes de relanzar:* verifica que no quede otra copia ocupando el `8083` (comando anterior). Si aparece, cierra esa terminal en lugar de iniciar otra.
* *Camunda:* define `CAMUNDA_BPM_ADMIN_USER_PASSWORD` en `.env` antes de levantar sobre una base de datos limpia. Con el volumen actual, el usuario `admin` de Camunda ya existe y su creación se omite.

---

## 💻 4. Paso 3: Levantar el Frontend (Next.js 13 / React)

Abre una **6ta terminal** de PowerShell:

```powershell
cd d:\ESPE\Vinculacion\Trabajo_Vinculacion\TiendaVirtual-TrabajoTitulacion-equipo\avalon-react-10.1.0

# 1. Instalar dependencias (solo la primera vez)
npm install

# 2. Iniciar servidor de desarrollo
npm run dev
```

* **Acceso web:** [http://localhost:3000](http://localhost:3000)

---

## 👥 5. Credenciales de Prueba del Sistema

> [!IMPORTANT]
> Estas credenciales corresponden **únicamente** a los datos semilla del entorno local. No deben usarse en producción ni en el servidor municipal.

| Rol | Usuario | Contraseña | Empresa | Pantalla / Funcionalidad principal |
|---|---|---|---|---|
| **Administrador** | `admin` | `password123` | — | **Panel de Administración:** Gestión de usuarios, empresas, categorías, inventario global y reportes de ventas. |
| **Emprendedor** | `sigcholac` | `password123` | 2 | **Panel del Emprendedor:** Configurar métodos de pago (Payphone App ID, Token, QR Deuna, Cuenta bancaria, costo de envío) y gestionar catálogo de quesos de SIGCHOLAC. |
| **Emprendedor** | `ultimo_inca` | `password123` | 1 | Segundo emprendedor para pruebas de aislamiento entre empresas (A-05). |
| **Emprendedor** | `perla_andina` | `password123` | 3 | Tercer emprendedor para pruebas de aislamiento entre empresas (A-05). |
| **Cliente** | `cliente1` | `password123` (el valor `cliente123` de versiones anteriores ya no coincide con el seed activo) | — | **Tienda Virtual Pública:** Explorar catálogo, agregar productos de un emprendedor al carrito y realizar checkout. |

### Pruebas de seguridad (A-07) y validación del API Gateway (Etapa 4 / A-12 a A-14)
1. **Levantar el API Gateway (NGINX en puerto `8080`):**
   ```powershell
   docker compose -f .\docker-compose.gateway.yml up -d
   ```
   *(Si en tu equipo Windows el puerto `8080` está ocupado por VMware `vmnat.exe`, define `GATEWAY_PORT=8090` en `.env` y `NEXT_PUBLIC_GATEWAY_URL=http://localhost:8090` en `avalon-react-10.1.0\.env.local`).*
2. **Validar seguridad directa (`A-07`):**
   Con los servicios levantados, `Documentos_Jordy\pruebas-seguridad-a07.ps1` ejecuta la matriz de disponibilidad, cantidades y respuestas `200`/`401`/`403`.
3. **Validar el API Gateway de extremo a extremo (`A-14`):**
   ```powershell
   .\Documentos_Jordy\pruebas-gateway-a14.ps1
   ```
   Verifica salud (`/health`), preflight CORS (`OPTIONS` sin duplicación de cabeceras), bloqueo de rutas internas (`/sincronizar-stock` y `/actuator`), enrutamiento hacia los 4 microservicios y control de roles (`200`/`401`/`403`) a través del Gateway.

---

## 🛑 6. Comandos para Apagar el Sistema

### Apagar el Backend y Frontend:
En cada ventana de PowerShell abierta presiona:
`Ctrl + C` y confirma con `S` (o `Y`).

### Apagar la Base de Datos Docker:
```powershell
# Detener el contenedor manteniendo los datos
docker compose -f .\docker-compose.db.yml stop

# Detener y eliminar contenedor (sin borrar los datos)
docker compose -f .\docker-compose.db.yml down
```

### Reiniciar la base desde cero (destructivo)

> [!CAUTION]
> `down -v` **elimina el volumen de MySQL**: se pierden productos, inventario, ventas y usuarios creados, y se vuelven a ejecutar los seeds. No lo uses para resolver errores de arranque ni durante las pruebas de las etapas. Solo se permite en un entorno local desechable y después de generar un respaldo con los scripts de A-02.
>
> Antes de hacerlo, define `CAMUNDA_BPM_ADMIN_USER_PASSWORD` en `.env`; sobre una base limpia, Ventas crea el usuario administrador de Camunda con ese valor.

```powershell
docker compose -f .\docker-compose.db.yml down -v
docker compose -f .\docker-compose.db.yml up -d
```

---

## 🔧 7. Solución Rápida a Errores Comunes

| Error | Causa | Solución |
|---|---|---|
| `Fatal error compiling: TypeTag :: UNKNOWN` | Se está usando Java 26 en lugar de Java 17 | Ejecutar `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"` y `$env:Path = "$env:JAVA_HOME\bin;$env:Path"` en esa terminal. |
| `BUILD FAILURE ... Process terminated with exit code: 1` con traza en `JdbcEnvironmentInitiator...obtainConnection` (`Access denied for user 'admin'`) | La terminal no cargó el `.env`: `MYSQL_PASSWORD` no existe y Spring envía `${MYSQL_PASSWORD}` como contraseña | Ejecutar en esa terminal el bloque "Cargar secretos del .env" y volver a lanzar `.\mvnw.cmd spring-boot:run`. |
| `Could not resolve placeholder 'JWT_SECRET'` | Igual que el anterior (Auth y Productos necesitan `JWT_SECRET`) | Cargar el `.env` en esa terminal. |
| `ERROR 2002: Can't connect to MySQL server` | MySQL recién está inicializando los 9 scripts SQL | Esperar 20 segundos y revisar con `docker logs mysql-feria-digital` hasta ver `ready for connections`. |
| `401 Unauthorized` al iniciar sesión con un usuario de prueba | La contraseña usada no corresponde al seed activo, o la base no se creó con `06-seed-auth-service.sql` | Usar las credenciales de la sección 5. Si siguen fallando, confirmar qué seed se aplicó. **No** modificar hashes con SQL (ver 2.4). |
| Login con `http://localhost:8084/api/api/auth/login` o error de conexión al autenticarse | `NEXT_PUBLIC_AUTH_API_URL` mal definida en `.env.local` | Debe ser exactamente `http://localhost:8084` (sin `/api`). Reiniciar el frontend tras cambiarla. |
| `Blocked by CORS policy` o `401` en una solicitud `OPTIONS` | El preflight del navegador fue rechazado por la seguridad del microservicio | Inventario ya permite `OPTIONS`. Si aparece en otro servicio, revisar su `SecurityConfig` en lugar de desactivar la autenticación. Verificar también que `NEXT_PUBLIC_PRODUCTOS_API_URL` termine en `/api`. |
| El catálogo muestra todos los productos con stock `0` | Inventario no está levantado o la consulta batch falla | Comprobar `http://localhost:8082/api/inventarios` y, en la pestaña Network, que `POST /api/inventarios/cantidad/batch` responda `200`. |
| Ventas no muestra `Started ...` | `logging.level.root=WARN` oculta los mensajes de arranque | Ver la nota de la Terminal 5. |

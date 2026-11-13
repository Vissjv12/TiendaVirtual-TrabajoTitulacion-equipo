# P5 — Auditoría de endurecimiento y preparación productiva

## Alcance

Esta etapa revisa la exposición de los microservicios y del API Gateway antes de Redis. No modifica Redis, credenciales, SSH, Nginx del servidor productivo ni reglas de negocio. Las acciones productivas requieren respaldo, ventana de mantenimiento y autorización del responsable.

## Resultado ejecutivo

El Gateway ya bloquea `/actuator` desde el perímetro y responde errores JSON uniformes. Sin embargo, los microservicios mantienen documentación Swagger pública cuando se accede directamente, varios controladores conservan CORS abierto y no existe rate limiting configurado en Nginx. Estos puntos deben tratarse de forma gradual para no romper consumidores directos o pruebas existentes.

## Hallazgos

### P5-01 — SQL en logs

Los archivos `application.properties` de `demo`, `msvc-producto` y `msvc-inventario` tenían `spring.jpa.show-sql=true`. En producción esto puede registrar consultas y datos sensibles, además de aumentar ruido y costo de almacenamiento.

**Acción aplicada en esta etapa:** se desactiva `spring.jpa.show-sql` en las configuraciones principales. Las configuraciones de pruebas pueden conservar más verbosidad para diagnóstico local.

### P5-02 — Swagger/API Docs expuestos directamente

Los cuatro servicios permiten `/swagger-ui/**`, `/api-docs/**` y `/v3/api-docs/**` sin autenticación. El Gateway no publica una ruta general para Swagger, por lo que el riesgo depende de que los puertos 8081–8084 estén accesibles fuera del servidor.

**No se cambia todavía:** primero debe confirmarse si existe algún consumidor directo, herramienta de monitoreo o necesidad operativa de Swagger en producción. La decisión recomendada es permitirlo solo en perfiles de desarrollo o restringirlo por red/autenticación.

### P5-03 — Actuator

El Gateway bloquea `/actuator` con respuesta 403. No se encontró una política explícita de exposición de Actuator en las propiedades auditadas.

**Pendiente productivo:** comprobar desde el servidor que los puertos internos no sean accesibles desde Internet y, si Actuator se habilita, publicar únicamente `health`/`info` mediante una red administrativa o autenticada.

### P5-04 — CORS duplicado y amplio

El Gateway centraliza CORS, pero varios controladores usan `@CrossOrigin(originPatterns = "*")` y `msvc-ventas` tiene `allowedOrigins("*")`. Esto puede generar exposición directa y configuraciones inconsistentes si alguien accede a los microservicios sin pasar por el Gateway.

**No se cambia todavía:** cerrar CORS en los servicios requiere confirmar que ningún frontend, prueba o integración consuma 8081–8084 directamente. La corrección futura debe conservar los orígenes oficiales y las credenciales sin duplicar cabeceras.

### P5-05 — Rate limiting

No existe `limit_req_zone`/`limit_req` en `gateway/nginx.conf`. El Gateway sí limita el tamaño de carga a 10 MB y define timeouts, pero no limita frecuencia de solicitudes.

**Pendiente condicionado:** diseñar límites por ruta y origen, empezando por login, recuperación de contraseña y endpoints de escritura. Debe probarse con el frontend y con las pruebas A-14 antes de activarlo en producción.

### P5-06 — Respuestas de error y roles

La autorización principal se aplica por JWT y rol en los servicios; A-14 validó 401/403/404 a través del Gateway. `msvc-ventas` ya evita devolver al cliente el detalle de Feign. En `demo`, `msvc-producto` y `msvc-inventario` aún debe revisarse de forma individual que los manejadores genéricos no devuelvan mensajes internos.

**Acción aplicada:** `demo`, `msvc-producto` y `msvc-inventario` ahora registran el detalle técnico en el logger y devuelven mensajes genéricos para errores de autenticación, autorización, Feign y excepciones no controladas. Los mensajes de negocio de validación y recurso inexistente se conservaron para no cambiar el contrato funcional.

**Pendiente:** agregar pruebas de contrato para confirmar que los detalles internos se registran solo en servidor y que el cliente recibe mensajes genéricos.

### Validación adicional de P5-06

Los tres módulos modificados compilaron correctamente con Maven Wrapper usando `-DskipTests compile`. La ejecución completa de pruebas continúa condicionada a que el entorno local tenga las credenciales válidas de MySQL descritas anteriormente.

### Evidencia de validación manual

- La solicitud `GET /api/usuarios` sin token devolvió `401` con cuerpo vacío y sin URLs, puertos, JDBC, MySQL, Feign ni stack trace.
- La solicitud con el valor literal `TOKEN_CLIENTE` devolvió `401` con `invalid_token` y `Malformed token`. Esto es esperado porque el valor utilizado no es un JWT real; no representa un fallo de autorización ni una fuga interna.
- El filtro de búsqueda solo encontró `Access-Control-Allow-Origin: http://localhost:3000`, que corresponde a la política CORS esperada y no a una dirección interna de microservicio.
- Las demás verificaciones 401, 403 y 503 cumplieron el formato genérico esperado.

Con esta evidencia, P5-06 queda validado manualmente.

### Cierre de P5

Las pruebas `LoginApplicationTests` y `MsvcProductoApplicationTests` se repitieron con las credenciales locales corregidas y finalizaron sin errores. Por tanto, P5 queda cerrado para el entorno local. La aplicación de restricciones adicionales de Swagger, Actuator, CORS o rate limiting en producción permanece fuera de este commit y requiere validación operativa, respaldo y ventana de mantenimiento.

## Preparación para las siguientes tareas

### P6 — Línea base y medición sin Redis

- Usar el contrato actual y los datos locales existentes.
- Medir catálogo público, detalle de producto, inventario y una lectura de ventas.
- Registrar p50, p95, tasa de error, throughput y memoria.
- Guardar comandos, fecha, versión, número de productos y configuración del entorno.
- No introducir Redis ni modificar la fuente transaccional MySQL.

### P7 — Puerta de entrada para Redis

Antes de implementar Redis deben estar definidos por infraestructura:

- host, puerto y política de autenticación;
- secreto fuera de Git y fuera de capturas;
- namespace de claves, TTL e invalidación;
- comportamiento cuando Redis no esté disponible;
- métricas y logs sin valores sensibles;
- estrategia de rollback para desactivar la caché.

La primera implementación debe ser reversible, con MySQL como fuente de verdad y con pruebas de consistencia de stock, precio y disponibilidad. Redis no debe convertirse en requisito único de arranque hasta completar P6 y aprobar P7.

### P8 — Regresión y entrega

La siguiente entrega debe incluir revisión completa del diff, pruebas funcionales y de seguridad, validación de configuración local y comprobación de que no se versionaron secretos, PDFs, logs ni archivos generados.

## Separación de acciones

### Realizado localmente

- Desactivación de SQL visible en logs de configuración principal.
- Registro de los hallazgos y de sus riesgos.
- Conservación de reglas de negocio, roles, rutas públicas y CORS funcional del Gateway.

### Puede realizarse localmente después de validación

- Pruebas de exposición directa a 8081–8084.
- Revisión y normalización de `GlobalExceptionHandler`.
- Perfil de producción sin Swagger público.
- Pruebas de rate limiting en un entorno local.

### Requiere servidor, administrador o ventana de mantenimiento

- Verificar firewall y puertos públicos.
- Cambiar configuración Nginx instalada en producción.
- Restringir Swagger/Actuator por red.
- Activar rate limiting productivo.
- Rotar secretos o modificar credenciales.

## Validación P5

1. Ejecutar `mvn test` en cada microservicio.
2. Levantar el conjunto local y verificar health, Swagger esperado y rutas A-14.
3. Confirmar que una escritura sin token continúa en 401 y un rol insuficiente en 403.
4. Revisar que los logs ya no impriman SQL Hibernate en una lectura normal.
5. No ejecutar cambios sobre producción hasta contar con respaldo y rollback.

### Ejecución realizada

La ejecución automática disponible en VS Code reportó `49` pruebas exitosas y `2` fallidas. Las dos fallas corresponden a cargas de contexto de `msvc-auth` y `msvc-producto` que no pudieron conectarse a MySQL con el usuario local `admin` (`Access denied ... using password: YES`). No apuntan al cambio de `show-sql`; requieren corregir las credenciales/variables del entorno local o levantar la base de datos con la configuración esperada. No se alteraron credenciales ni la base de datos para forzar el resultado.

## Cómo corregir y repetir las dos pruebas fallidas

### 1. Confirmar que está levantado el MySQL local

Desde la raíz del repositorio:

```powershell
docker compose -f docker-compose.db.yml ps
```

El contenedor `mysql-feria-digital` debe aparecer como `running`. Si está detenido, levantarlo con:

```powershell
docker compose -f docker-compose.db.yml up -d mysql
```

No usar `down -v`, porque eliminaría el volumen de datos.

### 2. Cargar las variables sin imprimir contraseñas

En la misma sesión de PowerShell, cargar las variables del `.env` local:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$($name.Trim())" -Value $value
}
```

Comprobar solamente que existen, sin mostrar valores:

```powershell
@('MYSQL_USER','MYSQL_PASSWORD','JWT_SECRET') | ForEach-Object {
    "{0}: {1}" -f $_, [bool](Get-Item "Env:$_" -ErrorAction SilentlyContinue)
}
```

Si `MYSQL_PASSWORD` no corresponde a la contraseña con la que se creó el usuario dentro del contenedor, el archivo `.env` debe corregirse localmente o debe recrearse el entorno de pruebas de forma controlada. No cambiar la contraseña de producción desde esta prueba.

### 3. Verificar la conexión sin exponer la contraseña

Ejecutar el cliente dentro del contenedor y escribir la contraseña cuando la solicite:

```powershell
docker exec -it mysql-feria-digital mysql -h 127.0.0.1 -u admin -p -e "SELECT 1;"
```

Después confirmar las bases necesarias:

```powershell
docker exec -it mysql-feria-digital mysql -h 127.0.0.1 -u admin -p -e "SHOW DATABASES;"
```

Deben existir al menos `auth_service` y `productos`.

### 4. Repetir solo las pruebas que fallaron

Desde cada módulo, con las variables cargadas:

```powershell
Set-Location Microservicios/demo
.\mvnw.cmd -Dtest=LoginApplicationTests test

Set-Location ..\msvc-producto
.\mvnw.cmd -Dtest=MsvcProductoApplicationTests test
Set-Location ..\..
```

Si el proyecto no contiene `mvnw.cmd`, usar `mvn` instalado en el sistema. El resultado esperado es `Tests run: 1, Failures: 0, Errors: 0` para cada módulo.

### 5. Repetir la validación general de P5

Con los servicios levantados, comprobar que las lecturas y escrituras de A-14 mantienen sus respuestas, y revisar los logs de una lectura normal. No deben aparecer sentencias SQL Hibernate por causa de `show-sql`. Las pruebas bajo `src/test/resources` pueden seguir mostrando SQL si su perfil de pruebas lo solicita explícitamente.

## Rollback

El cambio local de esta subetapa se revierte restaurando `spring.jpa.show-sql=true` únicamente si una prueba de diagnóstico lo necesita. No se requiere migración de base de datos ni cambio de credenciales.

## Próximo paso recomendado

Validar los cuatro `GlobalExceptionHandler` y añadir pruebas que aseguren que errores Feign, excepciones genéricas y autenticación no filtran URLs internas, puertos, tokens ni mensajes de infraestructura.


# Registro de seguridad y configuración — A-07, A-08, A-09 y A-10

## 1. Alcance

Este registro consolida la validación directa de autorización contra la API y el trabajo preparatorio para retirar secretos versionados, permitir una rotación controlada de credenciales y revisar el tratamiento de contraseñas antes de incorporar nuevas dependencias de infraestructura.

Rama de trabajo: `feature/a07-validacion-api`, derivada de `etapa/seguridad-a07-a10`.

La rama base incorpora la integración validada de A-01 a A-06 y la preparación previa de A-08 a A-10.

## 1.1. A-07 — Validación directa contra API (subpaso 1)

Se agregaron pruebas HTTP con MockMvc sobre el controlador real de productos y la cadena de seguridad configurada. Las pruebas no utilizan la interfaz web ni modifican la base de datos.

### Casos cubiertos

- Una solicitud `POST /api/productos` sin token es rechazada con `401 Unauthorized`.
- Una solicitud de escritura con un rol sin permisos de gestión es rechazada con `403 Forbidden`.
- Un emprendedor autenticado que intenta operar sobre una empresa ajena es rechazado con `403 Forbidden`.
- La consulta pública `GET /api/productos` continúa disponible y responde `200 OK`.

### Resultado automatizado

- Prueba: `ProductoControllerApiAuthorizationTest`.
- Casos ejecutados: 4.
- Fallos: 0.
- Errores: 0.
- Resultado: `BUILD SUCCESS`.

### Hallazgo pendiente

El microservicio de inventario no cuenta todavía con una configuración equivalente de Resource Server y expone operaciones de escritura sin autenticación propia. No se aplicó una protección parcial porque productos y ventas lo invocan mediante Feign; primero debe definirse la propagación del Bearer token o una identidad de servicio controlada. Esta revisión queda como el siguiente subpaso de A-07 para evitar romper la creación de inventario y el descuento de stock durante una compra.

## 1.2. A-07 — Comparación y revisión de inventario (subpaso 2)

### Comparación de rama

Se comparó `feature/a07-validacion-api` contra `etapa/seguridad-a07-a10`. La diferencia contiene únicamente:

- Las pruebas HTTP de autorización de productos.
- La dependencia de pruebas de Spring Security.
- La actualización de este registro documental.

No se modificaron archivos de Docker, semillas SQL, credenciales locales, controladores de ventas ni configuraciones de producción.

### Rutas revisadas

En inventario se identificaron las siguientes operaciones de escritura que actualmente no tienen autenticación propia:

- `POST /api/inventarios`.
- `POST /api/inventarios/producto/{productoId}`.
- `PUT /api/inventarios/{id}`.
- `PUT /api/inventarios/producto/{productoId}/stock`.
- `DELETE /api/inventarios/{id}`.
- `POST /api/inventarios/salida-lote`.
- `POST /api/movimientos`.

Las consultas de inventario y cantidad se mantienen públicas porque son consumidas por catálogo y checkout; su exposición deberá validarse junto con A-23, A-24 y A-25.

### Dependencias internas identificadas

- Productos crea el inventario inicial mediante Feign.
- Ventas descuenta stock mediante `salida-lote` y actualización de stock.
- Inventario consulta productos y sincroniza stock mediante Feign.
- No se encontró un interceptor común que propague el encabezado `Authorization` entre esos clientes.
- El cliente Feign de productos utilizaba una ruta batch distinta a la expuesta por Inventario; el contrato se alineó posteriormente en el subpaso 1.5 con `/api/inventarios/cantidad/batch`.

### Decisión de seguridad

No se añadió una autenticación parcial al microservicio de inventario. Hacerlo sin propagar el token o definir una identidad de servicio provocaría fallos en la creación de productos y en el flujo de ventas. El siguiente subpaso deberá definir y probar esa comunicación interna antes de cerrar la protección directa de inventario.

## 1.3. A-07 — Protección coordinada de inventario (subpaso 3)

### Cambios implementados

- Se agregó validación JWT al microservicio de inventario utilizando `JWT_SECRET`, sin crear una segunda clave de firma.
- Las operaciones de escritura administrativa de inventario y movimientos requieren `ROLE_ADMIN` o `ROLE_EMP`.
- La salida de inventario y el ajuste de stock requieren autenticación para evitar llamadas anónimas directas.
- Las consultas de inventario y cantidad permanecen públicas para conservar el funcionamiento del catálogo y el checkout.
- Se agregó un interceptor Feign en productos, ventas e inventario para propagar el encabezado `Authorization` cuando existe una solicitud HTTP autenticada.
- Se añadió un perfil de pruebas con H2 para que las pruebas de contexto no dependan de credenciales ni de un contenedor MySQL local.

### Validación automatizada

- `InventarioControllerApiAuthorizationTest`: 4 casos, 0 fallos y 0 errores.
- Suite completa de inventario: 5 casos, 0 fallos y 0 errores.
- `ProductoControllerApiAuthorizationTest` y `ProductoAuthorizationServiceTest`: 11 casos, 0 fallos y 0 errores.
- Compilación de ventas después del interceptor Feign: correcta.

### Límites y seguimiento

- `POST /api/inventarios/salida-lote` y `PUT /api/inventarios/producto/{productoId}/stock` validan autenticación, pero su autorización de negocio debe endurecerse con A-24 para impedir que un usuario autenticado manipule productos ajenos.
- La sincronización asíncrona de stock no conserva automáticamente el contexto HTTP; por eso el endpoint receptor de sincronización requiere una revisión posterior con identidad de servicio o mecanismo equivalente.
- La validación manual con los cuatro microservicios levantados queda como evidencia final de integración. No se requiere cambiar Docker ni eliminar el volumen MySQL para ejecutar las pruebas automatizadas.

## 1.4. A-07/A-08 — Verificación de arranque de Ventas (2026-11-07)

### Síntoma reportado

Al ejecutar `.\mvnw.cmd spring-boot:run` en `msvc-ventas` con las variables del `.env` cargadas, Maven compilaba y Camunda comenzaba a iniciar, pero después de más de un minuto no aparecía el mensaje `Started ...` ni se confirmaba el puerto `8083`.

### Diagnóstico

- Antes de relanzar se comprobó que no quedaba ningún proceso Java de Ventas activo y que el puerto `8083` estaba libre. Solo seguían activos Eureka (`8761`) y MySQL (`3306`). El puerto `8080` lo ocupa `vmnat` (VMware) y no tiene relación con el sistema.
- Causa de la falta de mensajes: `msvc-ventas/src/main/resources/application.properties` define `logging.level.root=WARN`. Con ese nivel se suprimen los mensajes INFO `Tomcat started on port 8083` y `Started MsvcVentasApplication`, aunque el servicio haya arrancado. No se identificó ningún bloqueo real.
- Para tener evidencia, el servicio se relanzó sin modificar archivos, agregando solo un argumento de ejecución: `-Dspring-boot.run.arguments=--logging.level.root=INFO`.

### Resultado

- `Tomcat started on port 8083 (http)`.
- `Started MsvcVentasApplication in 15.959 seconds`.
- Hikari conectó con MySQL usando `MYSQL_USER`/`MYSQL_PASSWORD` del `.env`. Hibernate `ddl-auto=validate` no informó diferencias de esquema.
- Camunda 7.20.0: el motor `default` se creó, el JobExecutor quedó activo y se omitió la creación del usuario administrador y del filtro `All tasks` porque ya existían en la base de datos.
- `GET /api-docs` y `GET /api/ventas` respondieron `200`.
- No se observó ningún bloque `Caused by`. Las advertencias registradas (`MySQL8Dialect`, `open-in-view`, caché de LoadBalancer y barra final añadida a recursos estáticos de Camunda) no impiden el arranque.
- No se modificaron credenciales, `JWT_SECRET`, contenedores ni el volumen MySQL.

### Observaciones pendientes, sin corrección en este subpaso

- El `.env` local no define `CAMUNDA_BPM_ADMIN_USER_PASSWORD`, aunque `.env.example` sí la incluye. Hoy no afecta porque el usuario `admin` de Camunda ya existe. Sin embargo, sobre una base de datos limpia, Spring dejaría sin resolver el marcador `${CAMUNDA_BPM_ADMIN_USER_PASSWORD}` y lo usaría literalmente como contraseña inicial. Debe definirse antes de cualquier reinstalación desde cero (relacionado con A-09).
- `GET /api/ventas` responde `200` sin token. Debe revisarse dentro de A-07 si el listado de ventas debe ser público, porque es una decisión de autorización que no se cambió en este subpaso.

### Seguimiento — fallo de Productos e Inventario por terminal sin `.env`

- Síntoma: `BUILD FAILURE` con una traza en `JdbcEnvironmentInitiator$ConnectionProviderJdbcConnectionAccess.obtainConnection`. Ocurrió al arrancar desde una terminal nueva con los bloques de la guía, que solo configuraban `JAVA_HOME`.
- Causa: tras A-08, `spring.datasource.password=${MYSQL_PASSWORD}` no tiene valor por defecto. Si la terminal no carga el `.env`, el marcador queda sin resolver y MySQL rechaza la conexión. El `.env` se revisó (formato, comillas, espacios, valores de ejemplo) y es correcto.
- Verificación: con el `.env` cargado, `msvc-inventario` arrancó en `8082` (`Started MsvcInventarioApplication in 10.564 seconds`) y `msvc-producto` en `8081` (`Started MsvcProductoApplication in 10.719 seconds`).
- Corrección documental: cada bloque de terminal de backend de `GUIA-LEVANTAMIENTO-SISTEMA.md` incluye ahora la carga del `.env`, y la tabla de errores comunes registra este síntoma.
- `.env` local (no versionado): se agregaron `CAMUNDA_BPM_ADMIN_USER_ID` y `CAMUNDA_BPM_ADMIN_USER_PASSWORD` con el valor original que tenía `application.properties` antes del commit `81d7163`. Coincide con el usuario `admin` de Camunda ya existente en la base. No se rotó ni se modificó ninguna otra credencial.

## 1.5. A-07 — Integración de cantidades de inventario en Productos (2026-11-07)

### Hallazgo

La base de datos y el microservicio de Inventario contenían cantidades correctas. `GET /api/inventarios` devolvió 500 unidades para el producto 1 y 1000 para el producto 2. Sin embargo, `GET /api/productos` devolvía `inventario: null` y `GET /api/productos/listado` asignaba explícitamente `inventarioCantidad = 0`.

El frontend consumía primero el endpoint optimizado, por lo que mostraba inicialmente todos los productos con cero unidades y dependía de una actualización posterior en segundo plano.

### Cambio implementado

- Se alineó el cliente Feign de Productos con `POST /api/inventarios/cantidad/batch`.
- El listado optimizado obtiene las cantidades en una sola llamada para todos los productos.
- Se eliminó la asignación incondicional de cero en el backend.
- Se conserva cero como valor seguro para productos sin inventario o cuando Inventario no está disponible.
- No se modificaron datos, semillas SQL, credenciales ni el volumen MySQL.
- Se evitó realizar una llamada individual por producto para no introducir un problema N+1.

### Validación

- `ProductoServiceInventoryBatchTest`: 1 caso, 0 fallos y 0 errores.
- `ProductoAuthorizationServiceTest`: 7 casos, 0 fallos y 0 errores.
- `ProductoControllerApiAuthorizationTest`: 4 casos, 0 fallos y 0 errores.
- Resultado: `BUILD SUCCESS`.
- La validación HTTP posterior al reinicio de Productos debe confirmar que `/api/productos/listado` ya devuelve cantidades reales. Quedó confirmada en la sección 1.6.

## 1.6. A-07 — Verificación integrada de disponibilidad, cantidades y autorización (2026-11-07)

Pruebas HTTP ejecutadas contra los servicios locales en ejecución. Para que fueran no destructivas, las escrituras usaron IDs inexistentes (`999999`) o `categoriaId=999999`: si la autorización hubiera fallado, el resultado habría sido 404 y nada se habría guardado. No se modificaron datos, credenciales ni el volumen MySQL. El procedimiento quedó automatizado en `Documentos_Jordy/pruebas-seguridad-a07.ps1`, que pide las contraseñas de forma oculta y no imprime tokens.

### Disponibilidad

| Servicio | URL | Resultado |
|---|---|---|
| Eureka | `http://localhost:8761` | 200 |
| Productos | `/api-docs` (8081) | 200 |
| Inventario | `/api/inventarios` (8082) | 200 |
| Ventas | `/api-docs` (8083) | 200 |
| Auth | `/api-docs` (8084) | 200 |
| Frontend | `http://localhost:3000` | 200 |

### Cantidades

- `/api/productos/listado`: 39 productos, ninguno con `inventarioCantidad` nulo ni en cero.
- Comparación de los 39 productos contra `/api/inventarios` (registros activos por `producto.id`): 0 diferencias.
- Muestra: producto 1 → listado 500 / inventario 500; producto 39 → listado 300 / inventario 300.
- Observación sin corrección: los nombres con tildes o ñ llegan con doble codificación (`MortiÃÂ±o` en lugar de `Mortiño`). Afecta los datos semilla o la codificación de la conexión y no depende de A-07.

### Autorización

| Caso | Esperado | Resultado |
|---|---|---|
| Lectura pública de productos e inventario sin sesión | 200 | 200 |
| POST/PUT/DELETE de productos sin token | 401 | 401 |
| POST/PUT/DELETE de inventario, `stock` y `salida-lote` sin token | 401 | 401 |
| PUT de producto con token falso | 401 | 401 |
| `cliente1` (`ROLE_USER`): POST/PUT/DELETE de productos | 403 | 403 |
| `cliente1`: PUT/DELETE de inventario | 403 | 403 |
| `sigcholac` (empresa 2): POST/PUT/DELETE sobre la empresa 1 o el producto 1 | 403 | 403 |
| `admin`: `GET /api/usuarios/estadisticas` | 200 | 200 |
| `admin`: PUT sobre un producto inexistente | supera la seguridad | 404 (autorizado y rechazado por datos inexistentes) |
| `cliente1`: `GET /api/usuarios` en Auth (solo ADMIN) | 403 | **500** |

### Hallazgos

## 9. A-07 - Correccion de preflight CORS y conservacion de cantidades

La revision del HAR del frontend mostro que `GET /api/productos/listado` devolvia cantidades reales, pero la actualizacion batch fallaba antes de ejecutar el `POST`. El navegador enviaba `OPTIONS /api/inventarios/cantidad/batch` y Inventario respondia `401 Unauthorized`.

Se permitio `OPTIONS /**` en `SecurityConfig` de Inventario y se mantuvo publico `POST /api/inventarios/cantidad/batch`. Las operaciones de escritura continuan protegidas.

El frontend ya no reemplaza cantidades existentes por cero cuando el batch falla o no devuelve un producto. Esta regla se aplico en `productService.ts`, `emprendedorService.ts` e `inventarioService.ts`.

Validacion: `InventarioControllerApiAuthorizationTest` quedo con 8 casos exitosos y la comprobacion TypeScript del frontend termino sin errores. El commit publicado es `bdfdf1e fix(a07): completar preflight y conservar stock`.

Pendiente manual: reiniciar Inventario y frontend, comprobar `OPTIONS` y `POST /api/inventarios/cantidad/batch` en Network, confirmar cantidades reales en el catalogo y verificar que las operaciones protegidas sigan respondiendo `401` o `403`.

- **Auth devuelve 500 en lugar de 403 cuando el rol no alcanza.** El acceso se niega correctamente y no se devuelven datos. Sin embargo, `GlobalExceptionHandler` (`demo`) tiene un `@ExceptionHandler(Exception.class)` que captura la `AccessDeniedException` lanzada por `@PreAuthorize` y la transforma en 500 con el mensaje de la excepción. Corrección sugerida: agregar un manejador específico de `AccessDeniedException` que responda 403. No se corrigió en este subpaso.
- **Contraseña de prueba de `cliente1` desactualizada en la guía.** El inicio de sesión con `cliente123` devuelve 401. El seed activo (`scripts/mysql-init/06-seed-auth-service.sql`) asigna a `cliente1` el mismo hash BCrypt que a `admin`, y el inicio de sesión con esa contraseña funciona. El valor `cliente123` proviene de `scripts/nuevo-seed-auth-service.sql`, que no es el seed en uso. Se corrigió la guía.
- La revisión visual del catálogo y del panel administrativo en el navegador queda a cargo del equipo. Hay que revisar en la pestaña Network de DevTools que no aparezcan respuestas 401, 403, 404 o 500.

## 2. Cambios implementados — A-08

- Se sustituyeron credenciales de base de datos codificadas en archivos de configuración por variables de entorno.
- Se externalizó la clave JWT del servicio de autenticación y del servicio de productos.
- Se externalizaron las credenciales de Stripe y del usuario administrador de Camunda.
- Se agregaron variables documentadas a `.env.example` sin incluir valores reales.
- Se agregaron reglas de exclusión para archivos locales de secretos.
- Se modificó la configuración Docker para exigir las variables sensibles en lugar de utilizar contraseñas predeterminadas silenciosamente.
- Se eliminaron credenciales de pago que estaban presentes en documentación versionada.

## 3. A-09 — rotación de credenciales

No se rotaron credenciales reales durante este cambio porque todavía debe confirmarse la propiedad de las cuentas externas y coordinarse una ventana de cambio.

La rotación deberá realizarse posteriormente con este procedimiento:

1. Crear credenciales nuevas en cuentas institucionales o del proyecto.
2. Configurarlas en desarrollo y pruebas.
3. Ejecutar las pruebas de autenticación, pagos, correo y conexión a base de datos.
4. Aplicarlas en producción durante una ventana controlada.
5. Verificar logs y endpoints de salud.
6. Revocar las credenciales anteriores después de confirmar el funcionamiento.

No se deben enviar valores secretos al repositorio, a la documentación ni al chat.

## 4. A-10 — hashes de contraseñas

No se modificaron los hashes de usuarios existentes. Los archivos semilla contienen hashes BCrypt y el cambio indiscriminado de contraseñas podría impedir el acceso de usuarios legítimos.

Antes de actualizar hashes se debe confirmar:

- La política de contraseñas aprobada.
- La contraseña temporal o mecanismo de recuperación para cada usuario.
- El respaldo de la base de datos.
- El procedimiento de reversión.
- La necesidad real de actualizar el factor de costo de BCrypt.

La actualización de hashes debe realizarse mediante el servicio de autenticación o una migración controlada, nunca copiando contraseñas en texto plano a la base de datos.

## 5. Compatibilidad y acción manual requerida

Los archivos de configuración ahora requieren variables de entorno. Antes de levantar el sistema desde esta rama, cada integrante debe:

1. Copiar `.env.example` a `.env`.
2. Completar los valores correspondientes a su entorno.
3. No subir `.env` al repositorio.
4. Mantener separadas las credenciales de desarrollo, pruebas y producción.

No se debe ejecutar `docker compose down -v` para aplicar este cambio sobre la base activa, porque eliminaría el volumen de datos.

## 6. Riesgos pendientes

- Las credenciales históricas de algunos servicios pueden continuar activas en instalaciones ya desplegadas y deben rotarse de forma coordinada.
- La documentación histórica puede contener instrucciones con contraseñas antiguas; debe utilizarse el `.env` del entorno como fuente de configuración.
- La integración futura de Redis debe reutilizar este esquema de variables y no introducir secretos en `docker-compose` ni en los microservicios.

## 7. Resultado esperado

El sistema conserva sus datos y su modelo actual, pero deja de depender de secretos escritos directamente en los archivos versionados. La siguiente validación debe comprobar el arranque local con `.env`, autenticación, conexión de los cuatro microservicios y ejecución de las pruebas existentes.

## 8. Verificación realizada

- Compilación del servicio de autenticación: correcta.
- Compilación del servicio de inventario: correcta.
- Compilación del servicio de ventas: correcta.
- Prueba `ProductoAuthorizationServiceTest`: 7 pruebas ejecutadas, 0 fallos y 0 errores.
- `git diff --check`: sin errores de formato.
- No se reinició Docker ni se modificó el volumen MySQL durante esta validación.

La validación de Docker queda como paso manual porque el entorno del agente no dispone del comando `docker`. Debe ejecutarse con un `.env` local configurado y sin utilizar `down -v` sobre la instalación que contiene datos.

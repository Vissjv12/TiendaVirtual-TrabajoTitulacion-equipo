# A-01 — Inventario de recursos, permisos y superficie del sistema

## Propósito

Línea base técnica y de seguridad elaborada a partir de `docs/ManualUsuario.md`, la configuración del repositorio, los microservicios, el frontend y la validación funcional del panel. No modifica datos, contratos de API ni esquemas.

## Componentes

| Componente | Tecnología | Puerto | Responsabilidad |
|---|---|---:|---|
| Frontend | Next.js, React, TypeScript | 3000 | Sitio público y panel administrativo |
| Autenticación | Spring Boot | 8080 | Usuarios, roles, sesión y tokens |
| Eureka | Spring Cloud Netflix | 8761 | Registro y descubrimiento |
| Productos | Spring Boot | 8081 | Productos, categorías y empresas |
| Inventario | Spring Boot | 8082 | Stock, movimientos y ajustes |
| Ventas | Spring Boot | 8083 | Ventas, pagos, clientes y logística |
| MySQL | MySQL 8 en Docker | 3306 | Persistencia |

## Recursos administrativos

La documentación funcional identifica Dashboard, Usuarios, Productos, Categorías, Empresas, Inventario y Ventas. Según el módulo se realizan consultas, creaciones, actualizaciones, activaciones/desactivaciones y eliminaciones. La autorización debe aplicarse en el backend y no depender solo de ocultar botones.

## Superficie de API

| Recurso | Prefijo | Operaciones observadas |
|---|---|---|
| Categorías | `/api/categorias` | GET listado, GET por id, POST, PUT, DELETE |
| Empresas | `/api/empresas` | GET listado, GET por id, POST, PUT, DELETE |
| Productos | `/api/productos` | Gestión y consultas del catálogo |
| Inventario | `/api/inventarios` | Consulta, movimientos y ajustes |
| Ventas | Rutas del servicio de ventas | Ventas, clientes, pagos y logística |

El frontend usa `NEXT_PUBLIC_PRODUCTOS_API_URL=http://localhost:8081/api`, necesario para que categorías y empresas conserven el prefijo `/api` del backend.

## CIA

| Información | Confidencialidad | Integridad | Disponibilidad |
|---|---|---|---|
| Usuarios, contraseñas, roles y tokens | Alta | Alta | Alta |
| Clientes, ventas y pagos | Alta | Muy alta | Alta |
| Catálogo | Media | Alta | Alta |
| Inventario y movimientos | Media/alta | Muy alta | Alta |
| Credenciales y claves externas | Muy alta | Alta | Alta |

Controles base: no versionar secretos, no registrar tokens, validar autenticación y autorización en backend, conservar trazabilidad, realizar cambios reversibles y no recrear/eliminar el MySQL activo durante las pruebas.

## Riesgos iniciales

| Riesgo | Impacto | Control o tarea |
|---|---|---|
| Endpoints administrativos sin autorización suficiente | Cambios o exposición no autorizada | A-03 y A-06 |
| Rutas frontend/API inconsistentes | Fallos funcionales | Corrección de configuración realizada |
| Credenciales en configuración | Compromiso de servicios | Etapa posterior |
| Cambios directos sobre producción | Pérdida o indisponibilidad | Ramas, revisión y pruebas |
| Codificación inconsistente | Caracteres españoles ilegibles | Incidencia separada |

## Convenciones

1. Usar una rama de etapa y una rama `feature` por tarea.
2. No subir `.env.local`, contraseñas, tokens ni datos reales.
3. No cambiar contratos de API sin actualizar pruebas y documentación.
4. Validar compilación y pruebas funcionales antes de integrar.
5. Registrar actividad, resultado, evidencia y limitaciones.
6. Revisar confidencialidad, integridad y disponibilidad antes de integrar.
7. Solicitar revisión de otro integrante antes de fusionar.

## Resultado

**A-01 iniciado y documentado.** El sistema cargó correctamente el dashboard, 6 categorías y 4 empresas. No se ejecutaron operaciones de escritura ni se alteró la base de datos. El siguiente paso es revisar la autorización efectiva de los endpoints administrativos para preparar A-03 y A-06.

## Registro A-01 — Matriz inicial de endpoints y permisos

### Criterio de lectura

La columna **situación actual** refleja la línea base observada en la revisión. La columna **control esperado** representa el objetivo de seguridad para las tareas posteriores; no constituye todavía un cambio aplicado.

| Dominio / endpoint | Método | Recurso | Rol esperado | Sensibilidad | Situación actual | Control esperado |
|---|---|---|---|---|---|---|
| `/api/auth/login` | POST | Inicio de sesión | Público | Alta | Público por diseño | Validar credenciales, límites de intento y respuesta sin revelar información sensible |
| `/api/auth/registro` | POST | Registro | Público | Alta | Público por diseño | Validar datos, contraseña segura y prevención de duplicados |
| `/api/categorias` | GET | Listado de categorías | Público/administrador según caso de uso | Media | Se consume desde frontend y panel | Definir explícitamente si la consulta pública es necesaria |
| `/api/categorias` | POST | Crear categoría | Administrador | Alta | Operación administrativa visible en panel | Requerir autenticación y rol administrador en backend |
| `/api/categorias/{id}` | PUT | Actualizar categoría | Administrador | Alta | Operación administrativa visible en panel | Requerir autenticación, rol y validación de entidad |
| `/api/categorias/{id}` | DELETE | Eliminar categoría | Administrador | Muy alta | Operación administrativa visible en panel | Requerir rol administrador, confirmar dependencias y registrar operación |
| `/api/empresas` | GET | Listado de empresas | Público/administrador según caso de uso | Media | Se consume desde frontend y panel | Definir explícitamente si la consulta pública es necesaria |
| `/api/empresas` | POST | Crear empresa | Administrador | Alta | Operación administrativa visible en panel | Requerir autenticación y rol administrador en backend |
| `/api/empresas/{id}` | PUT | Actualizar empresa | Administrador | Alta | Operación administrativa visible en panel | Requerir autenticación, rol y validación de entidad |
| `/api/empresas/{id}` | DELETE | Eliminar empresa | Administrador | Muy alta | Operación administrativa visible en panel | Requerir rol administrador, confirmar dependencias y registrar operación |
| `/api/productos` | POST/PUT/DELETE | Catálogo | Administrador / rol de gestión definido por negocio | Alta | Gestionado desde panel | Autorización backend, validación de relaciones y trazabilidad |
| `/api/inventarios` | POST/PUT | Stock y ajustes | Administrador / responsable autorizado | Muy alta | Módulo operativo | Control de rol, validación de cantidades y auditoría de movimientos |
| Ventas, pagos y logística | Varios | Operación comercial | Administrador / cliente según operación | Muy alta | Módulo operativo | Separar permisos de consulta, operación y administración |

### Roles considerados

| Rol | Alcance funcional base | Acceso que debe comprobarse |
|---|---|---|
| Administrador | Gestionar y supervisar el sistema completo | Dashboard, usuarios, catálogo, inventario y ventas |
| Emprendedor | Gestionar recursos propios según las reglas del negocio | Solo sus productos, inventario y operaciones autorizadas |
| Cliente | Consultar catálogo y operar compras propias | Catálogo público y datos de sus propias operaciones |
| No autenticado | Acceso público limitado | Login, registro y recursos explícitamente públicos |

### Hallazgos de autorización

1. El servicio de autenticación tiene una cadena Spring Security con JWT y `@EnableMethodSecurity`.
2. La configuración revisada protege por autenticación las rutas no declaradas públicas.
3. Los controladores de categorías y empresas revisados no muestran anotaciones de autorización por método.
4. A-03 debe comprobar el aislamiento por empresa/emprendedor en backend; ocultar datos o botones en el frontend no es suficiente.
5. A-06 debe verificar que el acceso al panel administrativo y sus rutas no pueda evadirse modificando la URL o realizando solicitudes directas a la API.

### Evidencia y actividad registrada

- Se accedió al panel con una cuenta administradora existente.
- Se consultó el dashboard sin modificar información.
- Se cargó el listado de categorías: 6 registros visibles.
- Se cargó el listado de empresas: 4 registros visibles.
- No se pulsaron acciones de crear, editar, eliminar o activar/desactivar.
- No se ejecutaron migraciones ni cambios en MySQL.
- No se requiere acción manual adicional en este punto.

## Registro A-03 — Diseño incremental de autorización por empresa

### Objetivo

Evitar que un usuario con rol de emprendedor pueda crear, modificar o eliminar productos asociados a una empresa distinta de la que figura en su identidad autenticada.

### Evidencia técnica de la línea base

- El servicio de autenticación emite en el JWT los claims `userId` y `empresaId`.
- Los emprendedores se identifican actualmente con el rol `ROLE_EMP`.
- La entidad de producto conserva `empresa_id` y `emprendedor_id`.
- `ProductoRequestDto` recibe `empresaId` y `emprendedorId` desde el cliente, por lo que esos valores no deben considerarse confiables sin comparación contra el JWT.
- El microservicio de productos no contiene actualmente la dependencia ni la configuración de Resource Server para validar el JWT.
- Los controladores de productos, categorías y empresas no contienen autorización por método.

### Diseño aprobado para revisión

1. Incorporar validación JWT en el microservicio de productos usando la misma clave configurada por el servicio de autenticación, externalizable mediante variable de entorno.
2. Mantener públicas únicamente las consultas que el negocio necesita para el catálogo.
3. Exigir `ROLE_ADMIN` para administrar categorías y empresas.
4. Permitir operaciones de productos a `ROLE_ADMIN` y `ROLE_EMP`, pero verificar en backend la empresa del token.
5. Para un emprendedor, tomar `empresaId` y `userId` del token; nunca confiar únicamente en los valores enviados por el frontend.
6. Devolver `403 Forbidden` cuando el usuario autenticado no tenga relación con la empresa solicitada.
7. Mantener intactos los datos existentes, los contratos de consulta y el esquema de MySQL.

### Secuencia de implementación

| Incremento | Alcance | Verificación antes de continuar |
|---|---|---|
| A-03.1 | Dependencias y configuración JWT del servicio de productos | Compilación y arranque sin cambios de datos |
| A-03.2 | Servicio reutilizable para extraer identidad y verificar empresa | Pruebas unitarias de administrador, emprendedor y empresa distinta |
| A-03.3 | Protección de operaciones de escritura | Pruebas con solicitudes autorizadas y no autorizadas |
| A-03.4 | Documentación y regresión funcional | Dashboard, catálogo, categorías y empresas continúan cargando |

### Decisiones de seguridad y calidad

- No se agregará lógica de autorización en el frontend como sustituto de la validación backend.
- No se permitirá que el cliente elija libremente el `empresaId` efectivo de una operación de emprendedor.
- No se cambiarán contraseñas, semillas, migraciones, Redis, Gateway ni puertos como parte de A-03.
- Se conservará la disponibilidad del catálogo público y se limitarán los cambios a operaciones sensibles.
- Cada incremento se compilará y revisará antes de aplicar el siguiente.

### Acción manual requerida

No se requiere acción manual para A-03. Después del primer incremento será necesario reiniciar el microservicio de productos para cargar la nueva configuración. Para las pruebas de autorización se solicitará posteriormente una cuenta de prueba con rol `ROLE_EMP`; la contraseña deberá ingresarse manualmente y no compartirse en el chat.

### Estado

### Incidencia de configuración detectada durante A-03.3

Después del reinicio se observó un error de conexión al autenticarse y respuestas 401 en módulos protegidos. La causa fue que el `.env.local` del frontend apuntaba a `http://localhost:8080/api`, mientras el servicio de autenticación del repositorio está configurado en `http://localhost:8084/api`.

Se corrigió `NEXT_PUBLIC_AUTH_API_URL` en `.env.local` y `.env.local.example`. Es necesario reiniciar únicamente el frontend para que Next.js cargue la variable corregida y volver a iniciar sesión para obtener un token nuevo.

### VerificaciÃ³n del servicio de autenticaciÃ³n

Se probÃ³ directamente `POST /api/auth/login` en `http://localhost:8084` con las cuentas documentadas `admin/password123` y `sigcholac/password123`; ambas respondieron HTTP 200 y devolvieron los roles esperados. Esto confirma que las cuentas y contraseÃ±as permanecen en la base actual.

La respuesta de `sigcholac` no incluyÃ³ `empresaId`, aunque la tabla `auth_service.emprendedores` sÃ­ contiene la relaciÃ³n usuario 3 con empresa 2. Esto indica que el proceso de autenticaciÃ³n activo debe reiniciarse desde el cÃ³digo actual antes de probar A-03, para que el JWT incluya el claim de empresa.

### CorrecciÃ³n de URL duplicada en autenticaciÃ³n

La consola del frontend mostrÃ³ `http://localhost:8084/api/api/auth/login`. La causa fue que `authService.ts` agrega `/api/auth` internamente, por lo que `NEXT_PUBLIC_AUTH_API_URL` debe contener solo `http://localhost:8084`.

Se corrigiÃ³ `.env.local` y `.env.local.example`. La aplicaciÃ³n debe reiniciarse para que Next.js cargue la variable y la URL final sea `http://localhost:8084/api/auth/login`.

### Resultado de la correcciÃ³n de autenticaciÃ³n

- Se confirmÃ³ que el frontend vuelve a solicitar `POST http://localhost:8084/api/auth/login` sin duplicar el prefijo `/api`.
- El inicio de sesiÃ³n volviÃ³ a responder correctamente y los mÃ³dulos protegidos recuperaron el token esperado.
- La advertencia de precarga del CSS se considerÃ³ informativa y no relacionada con el fallo de autenticaciÃ³n.
- No fue necesario modificar contraseÃ±as, homologar hashes ni alterar la base de datos.

### Resultado A-03.1

- Se agregaron las dependencias de Spring Security y OAuth2 Resource Server al microservicio de productos.
- Se agregÃ³ la configuraciÃ³n JWT compatible con el claim `scope` y las autoridades `ROLE_*` del servicio de autenticaciÃ³n.
- La clave se puede proporcionar mediante `JWT_SECRET`; se conserva un valor local de compatibilidad para no romper el entorno actual.
- Las reglas siguen temporalmente en `permitAll` para no alterar el comportamiento antes de implementar A-03.2 y A-03.3.
- CompilaciÃ³n ejecutada: `mvnw.cmd -DskipTests compile`.
- Resultado: **BUILD SUCCESS**.
- Se implementÃ³ `ProductoAuthorizationService` para validar `empresaId` desde el JWT y consultar la empresa del producto.
- Se agregaron 5 pruebas unitarias para administrador, emprendedor de la empresa correcta, empresa distinta, rol sin permisos y autenticaciÃ³n no JWT.
- Prueba ejecutada: `mvnw.cmd -Dtest=ProductoAuthorizationServiceTest test`.
- Resultado A-03.2: **5 pruebas ejecutadas, 0 fallos, 0 errores**.
- A-03.3 conectÃ³ la autorizaciÃ³n con crear, actualizar y eliminar productos.
- Los administradores conservan alcance global; `ROLE_EMP` queda limitado a la empresa del JWT.
- En operaciones de emprendedor, `emprendedorId` se toma del claim `userId` y no del valor enviado por el cliente.
- Se exige `userId` en el JWT para evitar continuar con una identidad incompleta.
- CategorÃ­as, empresas, consultas pÃºblicas y sincronizaciÃ³n interna de stock no fueron modificadas en este incremento.
- Prueba de regresiÃ³n ejecutada: `mvnw.cmd -Dtest=ProductoAuthorizationServiceTest test`.
- Resultado A-03.3: **5 pruebas ejecutadas, 0 fallos, 0 errores; BUILD SUCCESS**.
- Se retirÃ³ la capacidad de emitir JWT del microservicio de productos; conserva Ãºnicamente la validaciÃ³n de tokens, aplicando mÃ­nimo privilegio.
- Se repitiÃ³ la compilaciÃ³n despuÃ©s del ajuste: **BUILD SUCCESS**.
- No se ejecutaron migraciones, operaciones sobre datos ni reinicios automÃ¡ticos de servicios.

**Siguiente paso:** A-03.2, crear el componente de autorizaciÃ³n que lea `userId` y `empresaId` del JWT y compare la empresa solicitada con la empresa del recurso.

**A-03 en diseño incremental.** El repositorio se encuentra en `feature/a03-autorizacion-empresa`. El siguiente cambio será únicamente A-03.1: dependencias y configuración JWT del microservicio de productos.

### Resultado A-03.4

- Se protegieron en el filtro HTTP las operaciones `POST /api/productos`, `PUT /api/productos/{id}` y `DELETE /api/productos/{id}` para exigir autenticaciÃ³n.
- Se agregÃ³ el manejo explÃ­cito de denegaciones de acceso en el microservicio de productos.
- Una solicitud de escritura sin token responde **401 Unauthorized**.
- Una solicitud de un emprendedor sobre una empresa diferente responde **403 Forbidden** con cÃ³digo `FORBIDDEN`.
- Las consultas de productos, categorÃ­as y empresas continÃºan respondiendo **200 OK**.
- ValidaciÃ³n de autenticaciÃ³n: administrador **200**, `ROLE_ADMIN`; emprendedor **200**, `ROLE_EMP`, `userId=3`, `empresaId=2`.
- RegresiÃ³n funcional: productos **39**, categorÃ­as **6** y empresas **4** registros consultados correctamente.
- Prueba unitaria ejecutada: `mvnw.cmd -Dtest=ProductoAuthorizationServiceTest test`.
- Resultado: **5 pruebas ejecutadas, 0 fallos, 0 errores; BUILD SUCCESS**.
- Durante la primera comprobaciÃ³n se detectÃ³ que las denegaciones eran clasificadas como **500** por el manejador genÃ©rico; se corrigiÃ³ la clasificaciÃ³n y se repitiÃ³ la validaciÃ³n con **401/403**.
- Las solicitudes negativas fueron rechazadas antes de persistir; no se crearon, editaron ni eliminaron registros.
- Se reiniciÃ³ manualmente Ãºnicamente `msvc-producto`; no se ejecutaron migraciones ni se modificÃ³ MySQL, Redis, Gateway o contraseÃ±as.

**Estado A-03:** completado en la rama `feature/a03-autorizacion-empresa`, pendiente de revisiÃ³n remota del commit de A-03.4.

### Estado de la matriz

**Estado:** línea base preparada para revisión de A-03 y A-06.

**Pendiente:** validar con una cuenta no administradora las respuestas de acceso a rutas administrativas. Esa prueba deberá realizarse sin compartir credenciales en el chat y sin ejecutar operaciones de escritura.

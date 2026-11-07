# Resumen de estado del proyecto y plan de continuación

- Fecha de revisión: 2026-11-07
- Rama revisada: `feature/a07-validacion-api` (derivada de `etapa/seguridad-a07-a10`)
- Base funcional: `integration/validacion-a01-a06`
- Último commit funcional de A-07: `bdfdf1e fix(a07): completar preflight y conservar stock`

> **Documento vigente.** Este resumen prevalece sobre `Cronograma_mejoras_sistema.xlsx`, `PLAN-IMPLEMENTACION-SISTEMA-V2 (1).docx` y `Plan_de_trabajo_etapas_y_ramas.docx`, que son anteriores a lo implementado. El cronograma se usa como guía de alcance y orden, no como calendario obligatorio; las desviaciones aceptadas se registran en la sección 8.

## 1. Propósito

Este documento consolida el estado técnico de las tareas del cronograma, los cambios integrados hasta el momento, las pruebas realizadas, los pendientes y las condiciones necesarias para continuar sin deteriorar el sistema desplegado.

El alcance actual mantiene MySQL como fuente transaccional principal. Redis, el Gateway, la paginación y el catálogo público se implementarán en etapas posteriores y no deben mezclarse con la corrección actual de autorización e inventario.

## 2. Estado general

## 2.1 Estado de los paquetes de trabajo del equipo

La integracion actual debe mantenerse como base comun antes de que los otros integrantes continÃºen. La rama que contiene las correcciones de A-07 es `feature/a07-validacion-api` y tiene publicados los commits `bdfdf1e` y `691c53f`. Los compañeros deben partir de la rama de etapa o de una rama de integracion aprobada, nunca de esta rama feature directamente.

### Integrante 1 - Backend y seguridad

Pendientes principales:

- A-11: definir la estrategia del Gateway, tabla de rutas, puertos, CORS, manejo de errores y responsabilidades.
- A-12: implementar el Gateway en pruebas, sin migrar todavia todo el frontend.
- A-19: publicar el contrato comun de paginacion.
- A-20: implementar paginacion backend con `page`, `size`, `sort`, limites y metadatos.
- A-24: proteger checkout y creacion de ventas con JWT y validar el alcance de empresa.
- Preparar la integracion de Redis solo despues de que exista un contrato aprobado para catalogo y stock.

No debe modificar A-07, `SecurityConfig` de Inventario, los servicios frontend de cantidades ni las semillas SQL sin abrir una tarea de integracion relacionada y coordinar el cambio. Tampoco debe crear rutas del Gateway que contradigan el contrato publicado.

### Integrante 2 - Frontend e integracion

Pendientes principales:

- A-13: migrar las URLs del frontend al Gateway cuando A-11 y A-12 tengan rutas estables.
- A-21: conectar DataTable y Paginator con el contrato de A-19 y A-20.
- A-23: mostrar catalogo, precios e informacion comercial publica sin iniciar sesion, sin exponer costos ni datos privados.
- A-24: implementar carrito, checkout, login requerido y mensajes de autenticacion.

No debe cambiar las rutas de API de forma unilateral ni eliminar la propagacion de `Authorization`. Debe conservar el comportamiento corregido del stock: una falla temporal del batch no puede convertirse en cero. Los cambios de Gateway deben hacerse despues de recibir la tabla de rutas del Integrante 1.

### Integrante 3 - Infraestructura, datos y pruebas

Trabajo actual y pendientes:

- Mantener respaldos y procedimiento de rollback de A-02.
- Coordinar A-09 y A-10 cuando exista autorizacion para credenciales y hashes.
- Ejecutar pruebas de seguridad, regresion, disponibilidad y rendimiento.
- Validar A-14 cuando exista Gateway.
- Preparar la configuracion de Redis, limites de memoria, puertos, salud y recuperacion, sin activar aun la migracion funcional.
- Ejecutar A-22 y A-25 cuando existan paginacion, catalogo publico y compra autenticada.

La responsabilidad de infraestructura no implica cambiar contraseñas, destruir volumenes, modificar semillas o rotar claves reales sin respaldo, autorizacion y ventana controlada.

### Orden de integracion entre paquetes

1. Integrar y validar la correccion actual de A-07.
2. Integrar A-11 y A-12 con una tabla de rutas estable.
3. Publicar el contrato de A-19 antes de implementar A-20 y A-21.
4. Validar A-14 y A-22 antes de iniciar el piloto funcional de Redis.
5. Integrar A-13 despues de probar el Gateway.
6. Implementar A-23 y A-24 sin hacer depender el catalogo publico de Redis.
7. Ejecutar A-25 y la regresion integral.

## 2.2 Redis antes del piloto completo

Redis puede prepararse de forma independiente, pero no debe incorporarse todavia como dependencia obligatoria de los microservicios ni como fuente principal de datos.

### Trabajo permitido antes de la implementacion funcional

- A-15: documentar la evaluacion de Redis frente a MySQL y justificar el alcance.
- Revisar el consumo disponible del servidor y establecer un limite de memoria para Redis.
- Definir el nombre del servicio, puerto interno, puerto local de pruebas y healthcheck.
- Definir variables de entorno como `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` y limites operativos, sin publicar valores reales.
- Definir las claves, estructura, serializacion y TTL de un solo caso piloto.
- Definir la politica de invalidacion cuando se cree, actualice o elimine un producto.
- Definir metricas: cache hit, cache miss, latencia, memoria, errores y fallback.
- Crear un plan de rollback que quite Redis sin borrar MySQL ni los datos transaccionales.
- Preparar una rama propia de evaluacion, por ejemplo `feature/a15-evaluacion-redis`, sin mezclarla con Gateway o paginacion.

### Trabajo que debe esperar

- A-16 debe esperar la decision de A-15 y el formato final de respuesta del catalogo.
- A-17 debe esperar el contrato del catalogo y una implementacion de fallback a MySQL.
- A-18 debe esperar las mediciones del piloto.
- La integracion de stock atomico en Redis debe esperar una definicion clara de consistencia, transacciones y reconciliacion con MySQL.
- Los carritos en Redis deben esperar la definicion del flujo de compra y su expiracion.
- La activacion en produccion debe esperar mediciones de memoria, pruebas de carga, respaldo y rollback.

### Lo que no debe tocarse antes del piloto

- No reemplazar MySQL como fuente de verdad.
- No guardar ventas, pagos, facturas, usuarios o roles unicamente en Redis.
- No modificar el esquema de MySQL solo para introducir Redis.
- No convertir Redis en requisito de arranque para el catalogo antes de probar el fallback.
- No agregar `FLUSHALL` a scripts normales de despliegue.
- No almacenar tokens, contraseñas, claves de pago ni datos sensibles en claves de cache.
- No cambiar el contrato REST del catalogo sin coordinar frontend y pruebas.
- No aplicar decrementos de stock en Redis sin una estrategia de confirmacion o compensacion en MySQL.
- No agregar `redis-feria-digital` al ambiente compartido hasta contar con variables, limites y procedimiento de apagado.

### Condiciones para iniciar el piloto A-17

El piloto puede iniciar cuando se cumpla todo lo siguiente:

- A-11 y A-12 tienen rutas estables o se definio que el piloto funcionara temporalmente sobre el endpoint actual.
- A-19 y A-20 tienen un contrato y respuesta paginada estable para el catalogo.
- El endpoint de catalogo conserva respuesta correcta cuando Redis esta apagado.
- Se conoce el limite de memoria del servidor de produccion.
- Existe respaldo de MySQL y rollback probado.
- Se definieron TTL, claves e invalidacion.
- Existen mediciones de linea base sin Redis.
- El equipo sabe quien puede modificar Docker y los archivos de entorno.

### Resultado esperado de Redis

Redis debe ser una capa Cache-Aside complementaria. Ante un cache hit, el catalogo puede responder mas rapido. Ante un cache miss, una clave expirada o una caida de Redis, el sistema debe consultar MySQL y continuar funcionando. La perdida de Redis no debe causar perdida de ventas, usuarios, inventario ni pagos.

| Área | Estado | Observación |
|---|---|---|
| Preparación y línea base | Integrada | A-01 y A-02 forman la base de trabajo y respaldo. |
| Aislamiento entre emprendedores | Integrado con validación automatizada | A-03, A-04 y A-05 tienen cambios y pruebas. Falta la validación HTTP de A-05; el seed activo ya contiene emprendedores de empresas distintas, por lo que no requiere modificar la base (ver 3.3). |
| Rutas administrativas y API | Implementada y validada por HTTP | A-06 y A-07 cuentan con protección, pruebas automatizadas y matriz HTTP ejecutada. Falta la comprobación visual en navegador tras la corrección del preflight y corregir el `500` de Auth (ver 7). |
| Secretos de configuración | Implementada | A-08 externalizó la configuración sensible. El `.env` continúa siendo local y no debe versionarse. |
| Rotación de credenciales | Pendiente controlado | A-09 tiene procedimiento, pero aún no se ejecutó una rotación real. |
| Actualización de hashes | Pendiente controlado | A-10 no debe modificar usuarios hasta confirmar la política y el mecanismo de recuperación. |
| Gateway | Pendiente | A-11 a A-14 todavía no están integradas en esta rama. |
| Redis | Pospuesto | A-15 a A-18 permanecen pendientes por decisión de secuencia y validación de recursos. |
| Paginación | Pendiente | A-19 a A-22 permanecen pendientes. |
| Catálogo y compra | Pendiente | A-23 a A-25 permanecen pendientes. |

## 3. Cambios realizados hasta ahora

### 3.1 A-01 y A-02

Se conservaron el inventario de servicios, puertos, roles y configuraciones existentes, además de los scripts de respaldo y el procedimiento de ambiente de pruebas entregado por el equipo.

No se eliminó el volumen MySQL activo ni se utilizó `docker compose down -v`, porque esa operación borraría los datos persistidos.

### 3.2 A-03 y A-04

Se integró la autorización por empresa en backend y el ajuste del frontend para utilizar el contexto de autenticación y manejar respuestas `401` y `403`.

Durante la revisión se identificó una inconsistencia de navegación en la vista administrativa de empresas, que reutiliza componentes o rutas del módulo emprendedor. No se mezcló ese hallazgo con las pruebas de aislamiento para evitar introducir cambios no relacionados.

### 3.3 A-05

Se ampliaron las pruebas de aislamiento horizontal con dos empresas y usuarios emprendedores distintos. También se cubrió el caso de un emprendedor sin `empresaId`.

Resultado automatizado registrado:

- 7 casos ejecutados.
- 0 fallos.
- 0 errores.

Pendiente: ejecutar la misma matriz mediante HTTP y confirmar que una operación rechazada no modifique datos.

No es necesario crear ni modificar usuarios para esta prueba. `scripts/mysql-init/06-seed-auth-service.sql` ya define emprendedores `ROLE_EMP` asociados a empresas distintas, todos con la misma contraseña de prueba que `admin`:

| Usuario | Empresa |
|---|---|
| `ultimo_inca` | 1 |
| `sigcholac` | 2 |
| `perla_andina` | 3 |

Antes de ejecutar la prueba se debe confirmar, con una consulta de solo lectura a `auth_service.emprendedores`, que la base activa conserva esas asociaciones.

### 3.4 A-06

Se protegieron las rutas del panel administrativo y se validó el acceso con la matriz de roles definida para el sistema.

### 3.5 A-07

Se agregaron pruebas directas contra la API, sin depender de la interfaz:

- Escritura de productos sin token: `401 Unauthorized`.
- Escritura con rol insuficiente: `403 Forbidden`.
- Emprendedor operando sobre empresa ajena: `403 Forbidden`.
- Consulta pública de productos: `200 OK`.

También se protegió Inventario con JWT y se agregó propagación del encabezado `Authorization` en los clientes Feign de Productos, Ventas e Inventario. Las consultas públicas de cantidades se conservaron para no romper el catálogo ni el flujo de compra.

Se corrigió el contrato de integración de cantidades: Productos utilizaba una ruta batch distinta a la expuesta por Inventario. El listado optimizado ahora consulta las cantidades en una sola llamada y deja de asignar cero incondicionalmente.

Commit enviado:

`125e081 fix(productos): integrar cantidades de inventario por lote`

Validación automatizada posterior:

- `ProductoAuthorizationServiceTest`: 7 casos.
- `ProductoControllerApiAuthorizationTest`: 4 casos.
- `ProductoServiceInventoryBatchTest`: 1 caso.
- Prueba de contexto de Productos: 1 caso.
- Total: 13 casos, 0 fallos y 0 errores.

Se ejecutó además la matriz HTTP contra los servicios levantados (`Documentos_Jordy/pruebas-seguridad-a07.ps1`): disponibilidad de los 5 servicios y del frontend, 39 productos con cantidades iguales a Inventario y respuestas `200`, `401` y `403` según lo esperado. El detalle está en la sección 1.6 de `REGISTRO-SEGURIDAD-A08-A10.md`.

#### Corrección posterior: preflight CORS y conservación de cantidades

La revisión del HAR detectó una regresión de integración: `GET /api/productos/listado` devolvía cantidades correctas, pero el preflight `OPTIONS /api/inventarios/cantidad/batch` respondía `401`. El navegador no enviaba el `POST` batch y el frontend convertía el resultado vacío en cantidad `0` para todos los productos.

La protección JWT era correcta; faltaba una prueba de integración frontend–Inventario. Una modificación de seguridad en un servicio debe probarse contra todos sus consumidores: frontend directo, preflight CORS, clientes Feign, consultas públicas y operaciones protegidas.

- Inventario permite `OPTIONS /**` (el preflight no lleva token ni ejecuta lógica de negocio).
- `productService.ts`, `emprendedorService.ts` e `inventarioService.ts` conservan la cantidad ya recibida cuando el batch falla o no incluye un producto; ya no la reemplazan por `0`.
- Las escrituras siguen protegidas: crear o modificar inventario sin token `401`, rol insuficiente `403`.
- `InventarioControllerApiAuthorizationTest`: 8 casos, 0 fallos. Comprobación TypeScript del frontend sin errores.
- Commits: `bdfdf1e fix(a07): completar preflight y conservar stock` y `691c53f docs(a07): registrar correccion de preflight y stock`.

Pendiente manual: reiniciar Inventario y frontend, comprobar en la pestaña Network que `OPTIONS` y `POST /api/inventarios/cantidad/batch` respondan `200`, y confirmar visualmente que el catálogo no vuelva a mostrar cero.

### 3.6 A-08

Se externalizaron credenciales de base de datos, JWT, Stripe y Camunda mediante variables de entorno. Se actualizaron las configuraciones para exigir variables sensibles en lugar de utilizar valores predeterminados silenciosos.

El archivo `.env` local está excluido del repositorio. El archivo `.env.example` contiene nombres y valores de referencia, pero no debe contener secretos operativos reales.

Importante: al ejecutar Spring Boot directamente desde una terminal, esa terminal debe cargar el `.env`. Docker Compose y el arranque directo no necesariamente comparten el mismo mecanismo de carga de variables.

### 3.7 A-09 y A-10

A-09 y A-10 quedaron preparados documentalmente, pero no se ejecutó una modificación irreversible:

- No se rotaron credenciales externas.
- No se cambiaron contraseñas de usuarios existentes.
- No se reemplazaron hashes BCrypt de la base de datos.
- No se alteraron semillas ni el volumen MySQL.

## 4. Comprobación inmediata recomendada

La comprobación debe realizarse después de reiniciar `msvc-producto`, `msvc-inventario` y el frontend, porque los últimos cambios de A-07 afectan a esos tres componentes. No es necesario recrear MySQL, borrar volúmenes ni reiniciar Eureka, Auth o Ventas.

### 4.1 Comprobación de disponibilidad

Confirmar que los siguientes servicios respondan:

| Servicio | URL de comprobación | Resultado esperado |
|---|---|---|
| Eureka | `http://localhost:8761` | HTTP 200 |
| Productos | `http://localhost:8081/api-docs` | HTTP 200 |
| Inventario | `http://localhost:8082/api/inventarios` | HTTP 200 |
| Ventas | `http://localhost:8083/api-docs` | HTTP 200 |
| Auth | `http://localhost:8084/api-docs` | HTTP 200 |

### 4.2 Comprobación del inventario corregido

1. Abrir `http://localhost:8081/api/productos/listado`.
2. Localizar los campos `inventarioCantidad`.
3. Confirmar que productos con existencias conocidas ya no aparezcan con cero.
4. Comparar dos o tres productos contra `http://localhost:8082/api/inventarios`.
5. Abrir el catálogo y el panel administrativo del frontend.
6. Recargar la pantalla y confirmar que la cantidad mostrada coincida con la API.
7. Revisar la consola del navegador para confirmar que no existan errores `401`, `403`, `404` o `500` provocados por esta integración.

Resultado esperado: la cantidad se obtiene mediante la consulta batch y no mediante una llamada individual por cada producto. En la pestaña Network, `OPTIONS` y `POST /api/inventarios/cantidad/batch` deben responder `200`.

Comportamiento ante fallos:

- **Backend (`/api/productos/listado`):** si Inventario no está disponible, Productos devuelve `0` como valor seguro. Ese cero significa indisponibilidad, no un dato real, y debe registrarse así.
- **Frontend (actualización batch en segundo plano):** si el batch falla o no incluye un producto, el frontend conserva la cantidad que ya entregó el backend y no la sobrescribe con `0`.

### 4.3 Comprobación de autorización

1. Sin iniciar sesión, ejecutar una consulta pública de productos. Debe responder `200`.
2. Sin token, intentar crear o modificar un producto. Debe responder `401`.
3. Sin token, intentar crear o modificar inventario. Debe responder `401`.
4. Con un usuario autenticado sin rol suficiente, repetir una operación administrativa. Debe responder `403`.
5. Con un emprendedor, intentar operar sobre un recurso de otra empresa. Debe responder `403` y no modificar la base.
6. Con administrador, validar únicamente una operación de prueba autorizada.

Las operaciones de escritura deben ejecutarse con datos de prueba y respaldo disponible. No se deben usar endpoints destructivos sobre datos reales solo para obtener evidencia.

### 4.4 Criterios para cerrar esta revisión

La revisión puede declararse correcta cuando:

- Todos los servicios respondan.
- El listado de productos muestre cantidades reales.
- Las pruebas de autorización con y sin token mantengan `200`, `401` y `403` según corresponda.
- No haya cambios inesperados en productos, inventario, ventas o usuarios.
- La evidencia quede registrada en el registro de la etapa.
- El árbol de Git no contenga cambios no relacionados ni secretos.

## 5. A-09 Rotación real de credenciales

### 5.1 Qué significa rotar

Rotar una credencial significa generar una nueva clave o contraseña, actualizar todos los consumidores autorizados, validar que el sistema funcione y revocar la credencial anterior. No significa simplemente cambiar el nombre de una variable ni volver a guardar el mismo valor en otro archivo.

### 5.2 Qué se necesita solicitar

No se necesitan contraseñas pegadas en el chat. Se necesita confirmar, por canal institucional seguro:

1. Quién es propietario o administrador de cada cuenta externa.
2. Qué credenciales están activas actualmente en producción.
3. Qué servicios están realmente contratados y habilitados: Stripe, PayPhone, correo, Google OAuth u otros.
4. Quién autoriza la ventana de cambio y la revocación de las credenciales antiguas.
5. Qué dominio, callback, webhook y ambiente corresponden a producción y cuáles a pruebas.
6. Qué procedimiento de recuperación se utilizará si una credencial nueva falla.
7. Qué restricciones de facturación, límites o costos tiene cada proveedor.

Para MySQL se necesita conocer las credenciales del entorno que se va a modificar y tener un respaldo restaurable. Para Camunda se necesita confirmar el usuario administrador y su contraseña de ambiente, sin publicar sus valores.

### 5.3 Procedimiento seguro de A-09

1. Inventariar cada secreto y su consumidor.
2. Crear nuevas credenciales en la cuenta institucional o autorizada.
3. Guardarlas en el gestor o mecanismo seguro definido para el ambiente.
4. Actualizar `.env` local, secretos de Docker o configuración de despliegue.
5. Ejecutar pruebas de autenticación, pagos, webhooks, correo y conexión a base.
6. Desplegar durante una ventana controlada.
7. Revisar logs, endpoints de salud y transacciones de prueba.
8. Revocar las credenciales antiguas solo después de confirmar el funcionamiento.
9. Registrar fecha, responsable, alcance y resultado sin escribir los valores secretos.

### 5.4 Por qué requiere autorización

Una rotación real puede cerrar el acceso del sistema a pagos, correo, OAuth, Camunda o MySQL. Si se revoca una clave sin conocer todos sus consumidores, se puede interrumpir producción, invalidar webhooks, perder acceso administrativo o bloquear recuperaciones de cuenta. La autorización define quién puede cambiar la cuenta, cuándo hacerlo, cómo revertirlo y quién responde ante una interrupción.

## 6. A-10 Actualización controlada de hashes

### 6.1 Qué se debe confirmar

Antes de cambiar hashes se necesita:

- Política de contraseñas aprobada.
- Algoritmo requerido y factor de costo BCrypt.
- Lista de usuarios afectados.
- Confirmación de que los hashes actuales son inválidos, inseguros o incompatibles.
- Flujo oficial de restablecimiento o contraseña temporal.
- Respaldo y procedimiento de reversión.
- Prueba en una copia o ambiente aislado.

### 6.2 Qué no se debe hacer

No se deben escribir contraseñas en texto plano en SQL, no se deben copiar hashes inventados a usuarios reales y no se deben cambiar todos los usuarios solo porque las contraseñas documentadas no coinciden con los seeds.

Los seeds contienen hashes BCrypt. Un hash no permite recuperar la contraseña original. Si las credenciales documentadas no funcionan, primero se debe determinar qué base se está ejecutando, qué seed fue aplicado y cuál es el procedimiento autorizado de recuperación.

### 6.3 Resultado esperado

Después de A-10, los usuarios afectados deben autenticarse mediante hashes BCrypt válidos, el login debe seguir funcionando y debe existir una forma documentada de recuperación. El cambio no debe eliminar usuarios, empresas, roles ni relaciones existentes.

## 7. Tareas pendientes por etapa

### Etapa 0 a 3

- Cerrar la evidencia HTTP de A-05 con `ultimo_inca`, `sigcholac` y `perla_andina` (ver 3.3).
- Completar la comprobación visual de A-07 en navegador después de reiniciar Productos, Inventario y frontend.
- Corregir en Auth (`demo`) el `500` que se devuelve en lugar de `403` cuando el rol no alcanza: `GlobalExceptionHandler` captura `AccessDeniedException` con el manejador genérico de `Exception`. Se resuelve agregando un manejador específico que responda `403`.
- Registrar como riesgo abierto que `msvc-ventas` no tiene Spring Security: todos sus endpoints, incluidos creación de ventas, pagos y configuración de pagos, responden sin token. La corrección corresponde a A-24, pero debe priorizarse (ver 8).
- Corregir posteriormente la codificación de caracteres españoles, como tarea separada.
- Ejecutar A-09 únicamente con autorización de cuentas y ventana de cambio.
- Ejecutar A-10 únicamente si la revisión de hashes demuestra una necesidad real.

### Etapa 4 Gateway

Integrante 1 debe diseñar e implementar las rutas del Gateway, CORS, errores y comunicación interna en A-11 y A-12. Integrante 2 debe migrar el frontend en A-13. Integrante 3 debe validar puertos, salud, disponibilidad y fallos en A-14.

No se debe migrar el frontend a rutas del Gateway antes de tener una tabla de rutas estable y pruebas de seguridad.

El plan de implementación V2 recomienda NGINX como gateway de producción por la restricción de memoria del servidor (unos 1.4 GB libres); Spring Cloud Gateway queda como componente académico con memoria acotada. Al centralizar CORS en el gateway se deben retirar o alinear las anotaciones `@CrossOrigin` locales, y la regla `OPTIONS` de Inventario debe mantenerse hasta que el frontend deje de llamar directamente a los puertos internos.

### Etapa 5 Redis

A-15 debe evaluar alternativas y restricciones de memoria. A-16 debe definir claves, TTL, serialización e invalidación. A-17 debe implementar un piloto Cache-Aside. A-18 debe decidir si una migración amplia es conveniente.

Redis no reemplaza MySQL. Las ventas, pagos, usuarios e inventario transaccional deben continuar en MySQL. El piloto debe incluir fallback a MySQL, límites de memoria, métricas y rollback.

### Etapa 6 Paginación

A-19 debe publicar el contrato común antes de que se modifique el frontend. A-20 implementa `Pageable`, límites y metadatos en backend. A-21 conecta DataTable/Paginator. A-22 comprueba rendimiento y evita colecciones ilimitadas.

### Etapa 7 Catálogo y compra

A-23 debe permitir consultar precios públicos sin iniciar sesión, filtrando costos y datos privados. A-24 debe exigir JWT para checkout y creación de ventas. A-25 debe probar la separación entre lectura pública y operaciones privadas.

A-24 parte de cero en `msvc-ventas`: el servicio no tiene dependencia ni configuración de Spring Security. Debe incorporar el mismo esquema de Resource Server de Productos e Inventario, y endurecer la autorización de negocio de `salida-lote` y del ajuste de stock en Inventario, que hoy solo exigen autenticación.

Este trabajo no depende de Redis para comenzar, aunque debe coordinarse posteriormente con Gateway y paginación para evitar retrabajo.

## 8. Desviaciones aceptadas respecto a la planificación original

La planificación (`Cronograma_mejoras_sistema.xlsx` y `Plan_de_trabajo_etapas_y_ramas.docx`) se elaboró antes de la implementación. Se registran aquí las diferencias para que no se interpreten como errores:

| Tema | Planificación original | Situación real | Decisión |
|---|---|---|---|
| Calendario | 7 semanas; A-07 y A-10 en semana 3 | Las etapas 0 a 3 se extendieron por los ajustes de integración | El cronograma es una guía de orden y dependencias; las fechas se replanifican por etapa |
| Rama de etapas 2 y 3 | `etapa/2-rutas-administrativas` y `etapa/3-secretos-credenciales` | A-07 a A-10 se trabajan en `etapa/seguridad-a07-a10` | Se acepta una rama conjunta porque A-07 y A-08 comparten configuración JWT y variables de entorno |
| Rama de A-07 | `feature/a07-pruebas-roles-api` | `feature/a07-validacion-api` | Se conserva el nombre publicado |
| Integración en `develop` | `feature → etapa → develop → release/corte-1-seguridad` | `develop` sigue igual que `main`; el trabajo está en `integration/validacion-a01-a06` y `etapa/seguridad-a07-a10` | Integrar ambas en `develop` al cerrar la revisión actual y abrir `release/corte-1-seguridad` |
| A-09 y A-10 | Ejecutadas en las semanas 2 y 3 | Preparadas documentalmente, sin ejecución irreversible | Pendientes aceptados hasta tener autorización y política aprobada |
| Seguridad de Ventas | Incluida implícitamente en A-24 | `msvc-ventas` no tiene ninguna seguridad | Priorizar dentro de A-24 o como tarea adicional antes del corte 3 |
| Ventas en Eureka (O-12) | Reintegrar el cliente Eureka | `eureka.client.enabled=false` | Sin tarea A-XX asignada; registrar como tarea adicional |

Observaciones del plan V2 que no tienen una actividad A-XX en el cronograma y deben asignarse como tareas adicionales: O-03 (cifrado de credenciales de pago), O-05 (tokens en logs de Payphone), O-06 (ruta de Deuna QR), O-07 (pago simulado en producción), O-08 (dashboards con datos estáticos), O-09 (claves de sesión), O-10 (`npm run build`), O-12 (Ventas en Eureka), O-14 (rutas duplicadas en Next.js), O-17 (`console.log` en producción) y O-18 (inicialización múltiple del SDK de Google).

## 9. Reglas para continuar con los compañeros

- Cada integrante trabaja en una rama `feature/aXX-*` creada desde la rama de etapa correspondiente.
- Nadie debe crear su rama a partir de la rama feature de otro integrante.
- Cada rama debe contener una tarea delimitada y archivos relacionados con esa tarea.
- Antes de abrir un Pull Request se deben ejecutar pruebas, revisar `git diff --check` y confirmar que no se agregaron `.env`, contraseñas, tokens, logs ni archivos temporales.
- El Pull Request debe indicar tarea, archivos, pruebas, evidencia, riesgos y pendientes.
- Los cambios se integran primero a la rama de etapa y luego a `develop`.
- Una rama de corte no debe recibir funcionalidades nuevas mientras está en validación.
- Antes de iniciar una tarea que modifique Docker, semillas o base de datos debe existir respaldo y rollback probado.
- Los contratos compartidos, como rutas del Gateway y paginación, deben publicarse antes de que otro integrante los consuma.

## 10. Próximo entregable

El próximo entregable debe ser el cierre verificable de la etapa actual:

1. Evidencia de disponibilidad de servicios (registrada en 1.6 del registro de seguridad).
2. Evidencia de cantidades de inventario corregidas, incluida la captura de Network con `OPTIONS` y `POST` batch en `200`.
3. Evidencia de respuestas `200`, `401` y `403` (registrada en 1.6; falta repetir el caso de Auth después de corregir el `500`).
4. Registro de pruebas y resultado.
5. Confirmación de que `feature/a07-validacion-api` está publicada con `bdfdf1e` y la documentación actualizada.
6. Lista de pendientes aceptados: A-05 por HTTP, A-09, A-10 y seguridad de Ventas.
7. Integración de `integration/validacion-a01-a06` y `etapa/seguridad-a07-a10` en `develop`.

Después de ese cierre, el equipo puede organizar el trabajo de Gateway y paginación sin depender de Redis ni mezclar cambios con la corrección de inventario.

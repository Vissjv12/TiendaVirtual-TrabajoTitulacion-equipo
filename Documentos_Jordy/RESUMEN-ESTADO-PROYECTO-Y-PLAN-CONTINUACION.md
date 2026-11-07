# Resumen de estado del proyecto y plan de continuación

Fecha de revisión: 2026-11-07  
Rama revisada: `feature/a07-validacion-api`  
Base funcional: `integration/validacion-a01-a06`

## 1. Propósito

Este documento consolida el estado técnico de las tareas del cronograma, los cambios integrados hasta el momento, las pruebas realizadas, los pendientes y las condiciones necesarias para continuar sin deteriorar el sistema desplegado.

El alcance actual mantiene MySQL como fuente transaccional principal. Redis, el Gateway, la paginación y el catálogo público se implementarán en etapas posteriores y no deben mezclarse con la corrección actual de autorización e inventario.

## 2. Estado general

| Área | Estado | Observación |
|---|---|---|
| Preparación y línea base | Integrada | A-01 y A-02 forman la base de trabajo y respaldo. |
| Aislamiento entre emprendedores | Integrado con validación automatizada | A-03, A-04 y A-05 tienen cambios y pruebas; la validación HTTP con dos emprendedores reales aún requiere credenciales/datos de prueba. |
| Rutas administrativas y API | Implementada en código | A-06 y A-07 cuentan con protección y pruebas automatizadas. Falta cerrar la evidencia manual de integración. |
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

Pendiente: ejecutar la misma matriz mediante HTTP usando usuarios de prueba realmente asociados a empresas distintas y confirmar que una operación rechazada no modifique datos.

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

La comprobación debe realizarse después de reiniciar únicamente `msvc-producto`, porque el cambio de cantidades está en ese microservicio. No es necesario recrear MySQL, borrar volúmenes ni reiniciar todo el sistema.

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

Resultado esperado: la cantidad se obtiene mediante la consulta batch y no mediante una llamada individual por cada producto. Si Inventario no estuviera disponible, el fallback conserva cero como valor seguro, pero eso debe registrarse como indisponibilidad y no como dato real.

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

- Cerrar la evidencia manual de A-05 con usuarios y empresas de prueba.
- Completar la evidencia manual de A-07 después del reinicio de Productos.
- Revisar la exposición pública de `GET /api/ventas`.
- Corregir posteriormente la codificación de caracteres españoles, como tarea separada.
- Ejecutar A-09 únicamente con autorización de cuentas y ventana de cambio.
- Ejecutar A-10 únicamente si la revisión de hashes demuestra una necesidad real.

### Etapa 4 Gateway

Integrante 1 debe diseñar e implementar las rutas del Gateway, CORS, errores y comunicación interna en A-11 y A-12. Integrante 2 debe migrar el frontend en A-13. Integrante 3 debe validar puertos, salud, disponibilidad y fallos en A-14.

No se debe migrar el frontend a rutas del Gateway antes de tener una tabla de rutas estable y pruebas de seguridad.

### Etapa 5 Redis

A-15 debe evaluar alternativas y restricciones de memoria. A-16 debe definir claves, TTL, serialización e invalidación. A-17 debe implementar un piloto Cache-Aside. A-18 debe decidir si una migración amplia es conveniente.

Redis no reemplaza MySQL. Las ventas, pagos, usuarios e inventario transaccional deben continuar en MySQL. El piloto debe incluir fallback a MySQL, límites de memoria, métricas y rollback.

### Etapa 6 Paginación

A-19 debe publicar el contrato común antes de que se modifique el frontend. A-20 implementa `Pageable`, límites y metadatos en backend. A-21 conecta DataTable/Paginator. A-22 comprueba rendimiento y evita colecciones ilimitadas.

### Etapa 7 Catálogo y compra

A-23 debe permitir consultar precios públicos sin iniciar sesión, filtrando costos y datos privados. A-24 debe exigir JWT para checkout y creación de ventas. A-25 debe probar la separación entre lectura pública y operaciones privadas.

Este trabajo no depende de Redis para comenzar, aunque debe coordinarse posteriormente con Gateway y paginación para evitar retrabajo.

## 8. Reglas para continuar con los compañeros

- Cada integrante trabaja en una rama `feature/aXX-*` creada desde la rama de etapa correspondiente.
- Nadie debe crear su rama a partir de la rama feature de otro integrante.
- Cada rama debe contener una tarea delimitada y archivos relacionados con esa tarea.
- Antes de abrir un Pull Request se deben ejecutar pruebas, revisar `git diff --check` y confirmar que no se agregaron `.env`, contraseñas, tokens, logs ni archivos temporales.
- El Pull Request debe indicar tarea, archivos, pruebas, evidencia, riesgos y pendientes.
- Los cambios se integran primero a la rama de etapa y luego a `develop`.
- Una rama de corte no debe recibir funcionalidades nuevas mientras está en validación.
- Antes de iniciar una tarea que modifique Docker, semillas o base de datos debe existir respaldo y rollback probado.
- Los contratos compartidos, como rutas del Gateway y paginación, deben publicarse antes de que otro integrante los consuma.

## 9. Próximo entregable

El próximo entregable debe ser el cierre verificable de la etapa actual:

1. Evidencia de disponibilidad de servicios.
2. Evidencia de cantidades de inventario corregidas.
3. Evidencia de respuestas `200`, `401` y `403`.
4. Registro de pruebas y resultado.
5. Confirmación de que el commit `125e081` está publicado.
6. Lista de pendientes aceptados, especialmente A-05 manual, A-09 y A-10.

Después de ese cierre, el equipo puede organizar el trabajo de Gateway y paginación sin depender de Redis ni mezclar cambios con la corrección de inventario.

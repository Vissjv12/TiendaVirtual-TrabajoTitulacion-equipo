# Registro de avances — Etapa 1

## Alcance

Este documento consolida los avances de integración y validación de las tareas A-01 a A-06. Se mantiene separado por secciones para evitar crear un documento independiente por cada tarea.

## Integración de ramas

Se integraron en la rama `integration/validacion-a01-a06` los cambios disponibles de A-02, A-03, A-04 y A-06, manteniendo los commits de integración y resolviendo únicamente los conflictos identificados en `.gitignore` y `categoriasEmpresasService.ts`.

La rama quedó publicada en el repositorio remoto para revisión. Los archivos originales de planificación se conservaron sin modificarse.

## A-01 — Inventario

Se mantuvo el inventario de recursos, permisos y rutas existente. El inventario sirve como línea base para las pruebas de autorización y para identificar las operaciones públicas y protegidas.

## A-02 — Respaldo y ambiente

Se conservaron los scripts y archivos de respaldo entregados por la rama A-02. Las pruebas de A-05 no dependen de recrear el contenedor ni de eliminar volúmenes.

## A-03 y A-04 — Autorización e integración frontend

La autorización de operaciones de productos utiliza los roles y el `empresaId` del JWT validado. El frontend agrega los encabezados de autenticación y maneja respuestas `401` y `403`.

Se identificó una inconsistencia de navegación: la vista administrativa de empresas reutiliza componentes del módulo emprendedor y algunas acciones redirigen a rutas `/emprendedor`. El comportamiento debe revisarse como una mejora independiente para no mezclarlo con A-05.

## A-05 — Pruebas de aislamiento horizontal

### Trabajo realizado

Se ampliaron las pruebas unitarias de autorización para representar dos emprendedores asociados a empresas diferentes y verificar que no compartan alcance. También se verificó que un emprendedor sin `empresaId` no pueda gestionar recursos.

### Resultado automatizado

- Prueba ejecutada: `ProductoAuthorizationServiceTest`.
- Casos ejecutados: 7.
- Fallos: 0.
- Errores: 0.
- Resultado: `BUILD SUCCESS`.

Los casos cubren administrador con alcance global, emprendedor con acceso a su empresa, rechazo de empresa ajena, rechazo de producto ajeno, ausencia de rol de gestión, autenticación no JWT, aislamiento entre dos empresas y emprendedor sin empresa asociada.

### Pendiente manual

La prueba de integración contra la base de datos queda pendiente porque el entorno Docker requiere credenciales de MySQL. Para cerrar A-05 se necesitan dos usuarios `ROLE_EMP` asociados a empresas diferentes y comprobar el comportamiento mediante HTTP, incluyendo que una operación rechazada no modifique datos.

No se modificaron contraseñas, hashes, usuarios existentes ni volúmenes Docker.

**Actualización (2026-11-07):** no es necesario asociar usuarios manualmente. El seed activo (`scripts/mysql-init/06-seed-auth-service.sql`) ya define `ultimo_inca` (empresa 1), `sigcholac` (empresa 2) y `perla_andina` (empresa 3) con rol `ROLE_EMP` y la misma contraseña de prueba que `admin`. Los pasos 2 y 3 de "Próximos pasos" se reemplazan por una consulta de solo lectura a `auth_service.emprendedores`, para confirmar que la base activa conserva esas asociaciones.

## Hallazgos y riesgos

1. La interfaz y el API administrativo actuales no permiten asignar una empresa a un usuario emprendedor nuevo.
2. La guía y los archivos seed contienen credenciales documentadas diferentes; los seeds almacenan hashes BCrypt y no permiten recuperar contraseñas en texto plano.
3. La asignación manual de empresas debe realizarse únicamente en el entorno local o de pruebas, con respaldo previo y registro de la operación.
4. El endpoint de sincronización de stock requiere revisión independiente antes de incluirlo en pruebas de aislamiento, porque puede ser utilizado por comunicación interna entre microservicios.

## Próximos pasos

1. Obtener las credenciales de MySQL del entorno local.
2. Consultar el usuario temporal y las empresas sin exponer hashes ni contraseñas.
3. Asociar dos usuarios de prueba a empresas diferentes mediante una operación controlada.
4. Ejecutar la matriz manual de A-05 y registrar códigos HTTP, resultado y evidencia.
5. Crear el commit de A-05 y actualizar este mismo registro.

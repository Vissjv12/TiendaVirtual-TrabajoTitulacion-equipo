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

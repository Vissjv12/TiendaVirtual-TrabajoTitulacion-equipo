# Resumen del análisis de documentación de producción

> **Clasificación:** interno y confidencial. Este documento no contiene contraseñas, tokens, claves privadas ni valores de `.env`.
>
> **Fecha del análisis:** 2026-11-10.
>
> **Alcance:** revisión documental, sin conexión SSH, sin ejecución de comandos en servidores y sin cambios en producción.

## 1. Fuentes revisadas

Se contrastaron los documentos de `Documentos_Jordy/docs/`, el resumen existente en `documentos-trabajo/RESUMEN-DOCUMENTACION-PRODUCCION.md` y la configuración actual del repositorio.

Fuentes principales:

- `despliegue-exitoso-sesion-2026-07-04.md`
- `guia-despliegue-mi-repositorio.md`
- `guia-wildcat-ssh-ssl.md`
- `guia_despliegue_ssh.md`
- `README-SSL-TIENDA.md`
- `configuracionssl.md`
- `IMPLEMENTACION-PAYPHONE.md`
- `optimizaciones-y-seguridad.md`
- `preubas.md`
- `RESUMEN-DOCUMENTACION-PRODUCCION.md`
- `docker-compose.db.yml`, `docker-compose.gateway.yml`, `docker-compose.test.yml`
- archivos `application.properties`, `.env.example`, configuración Nginx y scripts del repositorio.

## 2. Conclusión ejecutiva

La producción documentada no utiliza el mismo modelo que el entorno local actual.

### Producción documentada

- AlmaLinux, con versión exacta pendiente de verificación.
- MySQL 8.4 instalado de forma nativa.
- Eureka, Auth, Productos, Inventario y Ventas ejecutados como servicios `systemd`.
- Frontend Next.js ejecutado como servicio del sistema en el puerto 3000.
- Nginx del sistema termina SSL y publica la aplicación por el puerto HTTPS 444.
- Los microservicios Java escuchan localmente en los puertos 8081–8084.
- Ventas incorpora Camunda y necesita más memoria que los demás servicios.
- El certificado Let's Encrypt depende parcialmente del servidor de Turismo para la validación del puerto 80.

### Entorno local actual

- MySQL y Gateway se ejecutan con Docker Compose.
- Los microservicios se ejecutan desde Maven en el host.
- El frontend usa el Gateway NGINX en `127.0.0.1:8090`.
- A-11–A-14 están integradas y validadas localmente.

No se debe desplegar literalmente `docker-compose.gateway.yml` en producción: sus upstreams están pensados para el entorno local. En producción se debe adaptar el Gateway al Nginx nativo o aprobar formalmente una migración a Docker.

## 3. Arquitectura real documentada

```text
Internet
   |
Firewall / proveedor de infraestructura
   |
Nginx del servidor de Tienda :444 con SSL
   |
Next.js :3000
   |  rewrites /api/proxy/*
   +--> Auth :8084
   +--> Productos :8081
   +--> Inventario :8082
   +--> Ventas/Camunda :8083
                         |
                       MySQL :3306
```

El servidor de Turismo participa en el flujo del puerto 80 y en la validación/renovación de certificados, pero no es parte del sistema de comercio. No debe accederse ni modificarse sin autorización expresa.

| Componente | Producción documentada | Observación |
|---|---|---|
| Nginx | Servicio del sistema, SSL en 444 | Verificar el archivo y los `location` activos |
| Frontend | Next.js con `npm start`, servicio `systemd` | `NEXT_PUBLIC_*` se fija durante el build |
| Eureka | `eureka-server.service`, 8761 | Inicia antes de los demás servicios |
| Auth | `auth-service.service`, 8084 | En el repositorio la carpeta se llama `demo` |
| Productos | `msvc-producto.service`, 8081 | Actualmente no tiene Redis integrado |
| Inventario | `msvc-inventario.service`, 8082 | Debe conservar seguridad y Bearer de A-07 |
| Ventas | `msvc-ventas.service`, 8083 | Incluye Camunda y requiere mayor memoria |
| MySQL | `mysqld.service`, 3306 | Instalación nativa según el registro |

## 4. Flujo de despliegue documentado

1. Conectarse al servidor autorizado.
2. Identificar rutas y servicios existentes.
3. Respaldar base de datos, facturas, uploads, Nginx, unidades `systemd`, `.env` y artefactos.
4. Detener servicios de forma controlada.
5. Actualizar o clonar el repositorio.
6. Aplicar migraciones SQL solo si están aprobadas y respaldadas.
7. Compilar microservicios con Java 17 y Maven.
8. Copiar los JAR a las rutas que realmente usan las unidades `systemd`.
9. Compilar el frontend con variables de producción.
10. Reiniciar en orden: MySQL, Eureka, Auth, Productos, Inventario, Ventas, Nginx/Gateway y frontend.
11. Validar salud, logs, puertos, SSL, login, catálogo, inventario y compra.
12. Mantener una ventana y un procedimiento de rollback.

Este procedimiento es válido como modelo, pero no debe ejecutarse sin completar el reconocimiento T00. Las guías antiguas incluyen comandos destructivos como `rm -rf`, `pkill`, `kill -9` y `docker compose down`; no deben ejecutarse en producción sin respaldo y aprobación.

## 5. Contradicciones encontradas

### 5.1 Docker frente a despliegue nativo

La guía del repositorio propone MySQL y, opcionalmente, todos los microservicios en Docker. El registro de despliegue y el resumen nuevo indican que producción usa MySQL nativo y servicios `systemd`.

**Decisión recomendada:** usar como fuente operativa el modelo nativo documentado en el despliegue real. Docker debe conservarse para desarrollo, pruebas y una eventual migración aprobada.

### 5.2 Rutas de JAR posiblemente inconsistentes

Una guía copia los JAR directamente a la carpeta del servicio, mientras que otra indica que `ExecStart` ejecuta archivos dentro de `target/`. Si ambas instrucciones no coinciden, el servicio puede continuar ejecutando un JAR anterior.

**Verificación requerida:** revisar `systemctl cat <servicio>` y comparar fecha, checksum y commit del JAR ejecutado.

### 5.3 Configuración Nginx contradictoria

Los documentos muestran configuraciones distintas: unas enrutan directamente a 8081–8084 y otras envían todo a Next.js para que actúen sus rewrites.

**Riesgo:** podrían quedar expuestos Swagger, Camunda, engine-rest o endpoints no previstos. Se debe revisar únicamente el archivo Nginx activo y ejecutar `nginx -t` antes de cualquier cambio.

### 5.4 Memoria disponible

La documentación antigua usa cifras menores que el resumen nuevo. Este último estima 5.6 GB totales y aproximadamente 2.8 GB libres, pero debe confirmarse con `free -h`.

No se debe dimensionar Redis ni cambiar límites JVM con base únicamente en cifras documentales.

### 5.5 SSL y renovación

La renovación del certificado de Tienda depende del proxy o configuración del servidor de Turismo. Deben verificarse `certbot certificates`, la fecha real de expiración y una renovación de prueba, sin modificar certificados durante el reconocimiento.

## 6. Credenciales y secretos

La documentación de producción contiene referencias a credenciales SSH, MySQL, JWT, Camunda, PayPhone y pasarelas. No se copian aquí.

Riesgos principales:

- Credenciales SSH de root documentadas y posible uso de contraseña sin evidencia de rotación.
- Usuario MySQL administrativo reutilizado y posible acceso desde `%`.
- Root de MySQL sin contraseña o con autenticación por socket, pendiente de verificar.
- JWT escrito directamente en código o configuración del `main` anterior.
- Credenciales Camunda débiles o predecibles.
- Claves Stripe de prueba y credenciales PayPhone en varias ubicaciones.
- Servicios ejecutándose como `root`.

Antes de entrar al servidor se debe confirmar con el responsable qué credencial está vigente, quién autoriza el acceso, qué ambiente se inspecciona, dónde se almacenan los secretos y cómo se rotarán. No se deben hacer reintentos repetidos de SSH por el riesgo de Fail2Ban.

## 7. Riesgos prioritarios

| Prioridad | Riesgo | Acción previa |
|---|---|---|
| Crítica | Proxy público puede exponer Camunda, engine-rest o Swagger | Verificar con GET de solo lectura y bloquear con respaldo si procede |
| Crítica | MySQL puede aceptar conexiones desde cualquier host | Verificar listeners/firewall y restringir a red interna |
| Alta | SSH como root con contraseña | Usar acceso nominal con llave y rotar la contraseña |
| Alta | Todos los servicios corren como root | Migrar progresivamente a usuarios de servicio |
| Alta | No hay respaldo operativo comprobado | Respaldar BD, facturas, uploads, configuración y artefactos |
| Alta | Producción no contiene A-01–A-14 | Definir adaptación nativa, ventana y rollback |
| Media | Build con servicios apagados | Preparar artefactos fuera del servidor o despliegue atómico |
| Media | Renovación SSL depende de otro servidor | Coordinar responsable y probar renovación |
| Media | Pago aprobado puede quedar sin venta completada | Auditar estados pendientes y revisar flujo PayPhone |

## 8. Reconocimiento T00 antes de cualquier cambio

La primera conexión debe ser solo de lectura. No se debe modificar Nginx, systemd, MySQL, firewall, certificados ni archivos de entorno.

### Datos del servidor

- Sistema operativo y versión.
- Memoria, CPU, disco y uptime.
- Estado de SELinux.
- Servicios activos.
- Puertos y direcciones de escucha.
- Estado del firewall.

### Estado de aplicación

- Commit exacto desplegado, remoto y rama.
- Estado local del repositorio.
- JAR realmente ejecutado por cada servicio.
- Nombres de variables de cada unidad `systemd`, ocultando valores.
- Logs recientes.
- Archivo Nginx activo y resultado de `nginx -t`.

### Estado de datos y respaldos

- Bases existentes y tamaños.
- Conteos de usuarios, productos, inventario y ventas.
- Ventas y pagos pendientes.
- Facturas y uploads.
- Respaldos existentes, fecha y posibilidad de restauración.

### Pruebas externas de bajo riesgo

- Dominio HTTPS y certificado.
- Redirección HTTP a HTTPS.
- Página principal, login, catálogo e inventario.
- Exposición accidental de Swagger, Camunda y engine-rest.
- Puertos externos inesperados, sin ejecutar escrituras.

## 9. Adaptación de nuestro plan

### A-01 a A-06

Comparar esquema y datos de producción con las migraciones. No ejecutar semillas sobre la base real. Las migraciones necesitan respaldo y rollback.

### A-07 a A-10

La externalización de secretos y JWT requiere un archivo como `/etc/tienda-virtual/backend.env`, con permisos restringidos, referenciado mediante `EnvironmentFile=` en las unidades `systemd`. No copiar el `.env` local a producción.

La rotación de JWT debe coordinar todos los servicios que validan tokens. Las credenciales de base y pasarelas deben rotarse con sus propietarios.

### A-11 a A-14

El Gateway validado localmente es NGINX en Docker, mientras que producción usa Nginx nativo. La adaptación recomendada es:

- Gateway interno en `127.0.0.1:8080`.
- Nginx SSL en 444 como único punto público.
- CORS para el origen HTTPS real.
- Bloqueo de `/actuator`, Swagger no autorizado, Camunda y rutas internas.
- Sin exposición pública de 8081–8084.

No se debe copiar literalmente la configuración local: utiliza `host.docker.internal` y el puerto local 8090.

### A-15 a A-18 Redis

Redis no debe implementarse durante la primera entrada al servidor. Primero hay que confirmar la arquitectura, crear respaldos y decidir si se usará una instancia nativa, contenedor aislado o servicio administrado.

El piloto debe comenzar en pruebas con una lectura pública acotada, fallback a MySQL, TTL, invalidación y bandera de desactivación. No cachear usuarios, JWT, ventas, pagos, carrito ni inventario transaccional.

### A-19 a A-25

- Definir paginación antes de cachear listados.
- Separar precio público de checkout autenticado.
- Reforzar seguridad de Ventas.
- Validar precio y stock contra MySQL al comprar.
- Ejecutar pruebas primero en local y luego en un entorno controlado.

## 10. Procedimiento seguro para el siguiente paso

1. Confirmar autorización escrita para entrar al servidor de Tienda.
2. Confirmar que no se debe acceder al servidor de Turismo.
3. Usar una sesión SSH autorizada y evitar reintentos de contraseña.
4. Ejecutar únicamente el reconocimiento T00 de lectura.
5. Guardar la salida redactada, sin contraseñas, tokens, correos privados ni claves.
6. Comparar commit y configuración desplegados con nuestra rama actual.
7. Generar y validar respaldos antes de cualquier cambio.
8. Presentar al responsable los riesgos identificados y solicitar aprobación específica para cada mitigación.
9. Definir una ventana de mantenimiento y rollback antes de modificar producción.

## 11. Resultado del análisis

La documentación permite entender la arquitectura general y preparar un reconocimiento controlado, pero no debe ejecutarse como procedimiento automático: contiene instrucciones de distintas épocas y modelos de despliegue.

La prioridad inmediata no es Redis. Es confirmar el estado real de producción, proteger accesos y secretos, generar respaldos verificables, cerrar posibles proxies públicos y definir cómo desplegar nuestra rama en el esquema nativo actual.

Redis puede planificarse después de esa línea base. Su implementación será viable, pero necesitará autorización de infraestructura, credenciales gestionadas, límites de memoria, fallback y rollback.

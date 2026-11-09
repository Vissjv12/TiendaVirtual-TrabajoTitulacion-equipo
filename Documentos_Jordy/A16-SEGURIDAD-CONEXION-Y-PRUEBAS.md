# A16 seguridad conexión salud y pruebas del piloto Redis

> **Estado:** borrador contractual para que A17 implemente sin inventar decisiones. No cambia `pom.xml`, `.env.example`, Compose, Spring ni el servidor.
> **Base técnica revisada:** `msvc-producto` usa Spring Boot `3.4.5` y Java `17`; su `pom.xml` actual no incluye el starter de Redis.

## 1. Variables candidatas

Confirmar esta tabla con el equipo antes de A17. Los nombres `SPRING_DATA_REDIS_*` reflejan propiedades estándar de Spring Boot; `CACHE_*` son opciones propias de la aplicación y deberán declararse explícitamente en código cuando llegue A17. No se añade ninguna variable ahora.

| Variable | Origen/nombre Spring Boot | Valor local inicial propuesto | Secreto | Decisión/nota |
|---|---|---|---|---|
| `CACHE_REDIS_ENABLED` | Propia de la aplicación | `false` | No | Interruptor general y mecanismo de rollback; si falta o es `false`, no se intenta usar caché |
| `SPRING_DATA_REDIS_HOST` | `spring.data.redis.host` | `127.0.0.1` para proceso local; `redis` si el servicio está en la misma red Compose | No | Elegir según dónde corre el microservicio |
| `SPRING_DATA_REDIS_PORT` | `spring.data.redis.port` | `6379` | No | Puerto interno estándar; mapear solo a loopback en desarrollo si hace falta acceso desde host |
| `SPRING_DATA_REDIS_USERNAME` | `spring.data.redis.username` | Sin valor en el modo local sin ACL | No | Usuario ACL de privilegio mínimo en entornos que autentican |
| `SPRING_DATA_REDIS_PASSWORD` | `spring.data.redis.password` | No guardar valor en el repositorio | **Sí** | Secreto inyectado desde mecanismo autorizado; nunca en código, PR, logs o documentos compartidos |
| `SPRING_DATA_REDIS_CONNECTTIMEOUT` | `spring.data.redis.connect-timeout` | `500ms` como hipótesis de prueba | No | Spring Boot convierte puntos a `_`, elimina guiones y convierte a mayúsculas; ajustar con medición |
| `SPRING_DATA_REDIS_TIMEOUT` | `spring.data.redis.timeout` | `500ms` como hipótesis de prueba | No | Límite candidato de operación; acordar si aplica igual a lectura y escritura |
| `SPRING_DATA_REDIS_LETTUCE_POOL_MAXACTIVE` | `spring.data.redis.lettuce.pool.max-active` | `8` como hipótesis | No | Spring Boot elimina guiones al convertir; solo si se añade y configura `commons-pool2` |
| `SPRING_DATA_REDIS_LETTUCE_POOL_MAXWAIT` | `spring.data.redis.lettuce.pool.max-wait` | `100ms` como hipótesis | No | Spring Boot elimina guiones al convertir; verificar soporte y carga en A17 |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `spring.data.redis.ssl.enabled` | `false` en local aislado | No | Habilitar TLS si el tráfico cruza hosts o redes no confiables |
| `CACHE_PUBLIC_PRODUCTS_TTL` | Propia de la aplicación | `300s` propuesto | No | Acorde al borrador A16: 5 min |
| `CACHE_PUBLIC_CATEGORIES_TTL` | Propia de la aplicación | `900s` propuesto | No | Acorde al borrador A16: 15 min |
| `CACHE_PUBLIC_COMPANIES_TTL` | Propia de la aplicación | `900s` propuesto | No | Acorde al borrador A16: 15 min; solo para campos aprobados |

Spring Boot documenta `spring.data.redis.host`, `port`, `username`, `password`, `connect-timeout`, `timeout` y parámetros Lettuce. Para convertir una propiedad a variable de entorno se reemplazan puntos por `_`, se eliminan guiones y se convierte a mayúsculas; por eso `spring.data.redis.connect-timeout` corresponde a `SPRING_DATA_REDIS_CONNECTTIMEOUT`. Si se usa `spring.data.redis.url`, host/puerto/usuario/contraseña individuales se ignoran; A17 debe elegir un solo estilo, preferiblemente propiedades separadas para no poner secreto dentro de una URL. Véase [Binding From Environment Variables de Spring Boot](https://docs.spring.io/spring-boot/3.4/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables).

## 2. Revisión de seguridad

### Reglas obligatorias del piloto

- Redis no publica puerto en interfaces públicas. En local, cualquier mapeo del host debe enlazarse a `127.0.0.1`; en contenedores, usar la red interna y el nombre de servicio.
- ACL con usuario dedicado para el microservicio; solo comandos necesarios y patrón de claves `~feriadigital:*`. No reutilizar la contraseña de MySQL.
- La contraseña va en almacén de secretos o mecanismo aprobado del entorno; `.env.example` puede nombrar la variable, pero no llevar valor real.
- Nunca cachear JWT, contraseñas, usuarios, datos de pago, carrito/checkout, ventas ni respuestas administrativas.
- Ejecutar autorización antes de consultar caché. Una respuesta de un emprendedor no puede compartirse con otro emprendedor ni con cliente/admin por una clave genérica.
- Los valores se limitan a la proyección pública aprobada en A16; campos `ruc`, email, teléfono, dirección, `emprendedorId` y stock quedan fuera por defecto.
- Si Redis va entre hosts, usar TLS y validar identidad del servidor conforme al entorno. No exponer Redis como endpoint público para el navegador.
- No escribir contraseñas, valores de caché, JWT ni datos personales en logs o etiquetas de métricas. Registrar solo recurso, hit/miss/fallo, latencia y resultado no sensible.
- Prohibido usar `FLUSHALL` o `FLUSHDB` para invalidación normal.

### Puertas de seguridad antes de activar el piloto

1. A14 demuestra autorización efectiva en Gateway y directamente en API, incluidas escrituras de categoría/empresa y acceso por empresa.
2. A23 resuelve la visibilidad de precio sin eliminar las protecciones de carrito y compra.
3. Dos empresas sintéticas producen datos distinguibles y los casos cruzados fallan con el código HTTP esperado, sin mutar datos.
4. Revisar que los logs y las claves no incorporen claims del JWT ni datos del usuario.
5. Documentar quién administra ACL/secretos y cómo se rota la credencial antes de cualquier despliegue compartido.

## 3. Healthchecks y disponibilidad

| Comprobación | Qué demuestra | Qué no demuestra | Comportamiento requerido |
|---|---|---|---|
| Compose `redis-cli PING` | Proceso Redis responde | No demuestra que Spring conecte, autentique, use la red correcta ni que una clave sea válida | Contenedor local muestra `healthy` |
| Health indicator Redis en Spring Actuator, si se habilita | El cliente de la aplicación logra comprobar Redis | No demuestra que MySQL o el endpoint estén correctos | Debe ser observable y no convertir una caché opcional en dependencia obligatoria |
| Health/readiness de aplicación | Servicio puede atender su función principal | No debe reportar indisponibilidad total solo porque Redis esté caído si MySQL puede responder | Redis caído -> salud de caché degradada; lectura por origen sigue disponible |
| Métricas funcionales | Hit, miss, errores Redis, latencia, memoria y lecturas a MySQL | No deben incluir valores ni etiquetas de alta cardinalidad sensible | Revisar tendencias para el informe A18 |

Liveness no debe depender de Redis. Readiness puede marcar Redis como degradado, pero debe seguir `UP` para lecturas que tienen fallback correcto a MySQL. No exponer detalles internos de la conexión Redis en un endpoint público.

## 4. Conexión, timeouts y fallback

### Comportamiento propuesto

1. La aplicación inicia aunque Redis no esté instalado, configurado o disponible. `CACHE_REDIS_ENABLED=false` es el valor inicial y también el rollback inmediato.
2. No bloquear el inicio esperando Redis y no realizar reintentos infinitos.
3. Para una lectura aprobada: intentar Redis con timeout finito; ante miss consultar MySQL; ante error de conexión o comando omitir Redis y consultar MySQL.
4. Si MySQL falla, devolver el error original del origen. El fallback de Redis no oculta fallos de negocio o de MySQL.
5. Si una escritura MySQL tuvo éxito pero falla el incremento de generación Redis, no reportar como fallida la escritura ya confirmada. Registrar el fallo; una clave pública anterior puede seguir sirviéndose hasta vencer su TTL. Esto no es un bypass automático a MySQL. El TTL máximo propuesto es 5 minutos desde cada escritura de caché y no se renueva en hits, pero la carrera de lecturas que comenzaron antes del commit debe probarse aparte; A17 no activa el recurso hasta acotar esa ventana y obtener su aceptación. El precio y stock que se validan para una operación de compra siempre se leen del origen y no de esta caché de presentación.
6. Reintentos limitados y backoff corto, o circuito abierto temporal ante una caída persistente. A17 debe elegir la librería/patrón y fijar el límite con pruebas; no duplicar retries de cliente, framework y aplicación.
7. No cachear respuestas 4xx/5xx, errores de autorización, errores de base de datos ni respuestas incompletas.
8. Hit, miss y fallback deben retornar el mismo DTO permitido, código HTTP, orden y filtros.

### Conexión local frente a Docker

- Java ejecutándose en el host: host Redis `127.0.0.1` y puerto loopback documentado.
- Java dentro de la misma red Compose: nombre de servicio, por ejemplo `redis`, y puerto interno `6379`; no usar `localhost` desde otro contenedor.
- Entorno institucional: valores reales y ACL solo mediante la persona autorizada. A15/A16 no acceden ni despliegan allí.

## 5. Matriz de pruebas A17

Todas las pruebas funcionales usarán MySQL local con fixtures sintéticos de, al menos, dos empresas y usuarios/roles separados. El caso de una ruta por Gateway se ejecutará tras validar A14.

| ID | Caso | Resultado esperado | Evidencia a conservar |
|---|---|---|---|
| R01 | Redis desactivado | Respuesta original por MySQL; no intento de conexión Redis | Código HTTP, cuerpo resumido, logs sin errores Redis |
| R02 | Miss | Consulta al origen; solo proyección autorizada se guarda con TTL | Hit/miss metric, consulta a origen y clave no sensible |
| R03 | Hit | Igual contrato/campos/orden que R02; no repetir consulta de origen | Comparación de respuestas y contador de consultas |
| R04 | Redis apagado antes del arranque | Microservicio inicia y endpoint funcional puede responder por MySQL | Estado de health y respuesta local |
| R05 | Redis se apaga durante lectura | Fallback dentro del timeout; sin 5xx atribuible a Redis | Tiempo, HTTP, log de fallback |
| R06 | Redis no responde o excede timeout | La petición termina dentro del presupuesto acordado y usa MySQL | Medición de timeout, HTTP y logs |
| R07 | Vence TTL | La siguiente lectura hace miss y consulta origen; no reutiliza valor vencido | Tiempo de expiración y contador miss |
| R08 | Crear/editar/desactivar/eliminar producto con invalidación exitosa | Después del commit y de incrementar generación, listado y variantes relevantes dejan de devolver dato anterior | Antes/después, versión de clave y consulta de origen |
| R09 | Cambio de categoría/empresa pública con invalidación exitosa | Respuestas agregadas se invalidan según matriz A16 | Antes/después y claves/grupos invalidados |
| R10 | Empresas distintas y acceso cruzado | No se comparte respuesta privada; 401/403 y no mutación donde corresponda | Respuestas directas API + Gateway y estado de datos |
| R11 | Intento de cachear datos sensibles | `ruc`, contacto no aprobado, JWT, stock privado y admin no se escriben a Redis | Inspección de proyección sintética, nunca valores productivos |
| R12 | Respuesta 4xx/5xx o fallo de MySQL | No se crea valor cacheado; se conserva el comportamiento HTTP del origen | Clave ausente y respuesta HTTP |
| R13 | Reinicio de Redis / claves antiguas | No se corrompe el contrato; la aplicación recupera desde MySQL | Logs y respuesta después del reinicio |
| R14 | Ruta final por Gateway | Misma autorización/contrato en URL pública y directa | Tabla A14, prueba API y prueba de extremo a extremo |
| R15 | Falla el incremento de generación después del commit MySQL, sin lectura concurrente | La escritura conserva éxito; una entrada previa solo vive hasta su expiración, con TTL máximo propuesto de 5 min desde el último SET y sin renovación en hits; el checkout valida precio/stock en el origen. Si el equipo no acepta la ventana, el recurso no se habilita | Inyección del fallo, hora de creación/vencimiento de clave, respuesta de escritura/lectura y lectura fuente al validar compra |
| R16 | Carrera: lectura fría empieza antes de una escritura y trata de guardar después del commit/invalidez | La implementación impide o mide y acota la publicación de una respuesta vieja; si no se puede demostrar un máximo aceptable, el recurso queda fuera de A17 | Tiempos de consulta, commit, invalidación y SET; generación/claves; comparación posterior contra MySQL |

Cada caso debe incluir prerequisitos, llamada reproducible, resultado esperado/observado, evidencia redactada y cleanup de datos locales. No usar credenciales ni datos institucionales.

## 6. Medición y decisión A18

Medir la misma consulta y dataset local en tres modos: caché desactivada, miss (frío) y hit (caliente). Registrar p50/p95, solicitudes por segundo, consultas a MySQL, tasa de hit, latencia/errores Redis, memoria, respuestas incorrectas y códigos 4xx/5xx.

Umbral inicial propuesto para aprobación antes de correr la medición:

- Cero fugas entre empresas/roles y cero diferencias de contenido o autorización.
- Cero errores funcionales atribuibles a Redis; degradación con Redis apagado sigue sirviendo desde MySQL.
- Para justificar expansión, al menos 20 % de mejora de p95 **o** 20 % menos consultas de origen en el caso repetido, sin empeorar errores ni frescura acordada.

Si no se alcanza el umbral, A18 recomienda mantener el piloto, corregir el cuello de botella medido o retirar la caché. No se agregan endpoints automáticamente.

## 7. Handoff para A17/A18

Antes de programar, A17 debe tener aprobados: endpoint y DTO público, ruta Gateway, clasificación de campos, formato JSON/versionado, nombres finales de variables, ACL/secret source, límites de conexión, salud opcional, TTL, tolerancia a datos obsoletos, invalidaciones, fallback, datos sintéticos, matriz R01–R16 y procedimiento de rollback. La ventana por fallo de invalidación y la carrera de lecturas son decisiones pendientes, no aceptaciones del equipo.

A18 debe publicar resultados medidos y una recomendación de ampliar/mantener/corregir/retirar. No se considera éxito solo porque el contenedor Redis esté `healthy`.

## Referencias técnicas y del repositorio

- `msvc-producto` declara Spring Boot `3.4.5` en su [pom.xml](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/etapa5-redis/Microservicios/msvc-producto/pom.xml).
- [Spring Boot 3.4: Redis y propiedades `spring.data.redis.*`](https://docs.spring.io/spring-boot/3.4/reference/data/nosql.html) y [propiedades comunes](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/).
- [Docker Compose healthcheck](https://docs.docker.com/reference/compose-file/services/#healthcheck).
- [SecurityConfig actual de Producto](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/etapa5-redis/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/config/SecurityConfig.java) y [tabla de rutas A11 no integrada](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/feature/a11-diseno-gateway/Documentos_Jordy/A11-ESTRATEGIA-Y-DISENO-GATEWAY.md).

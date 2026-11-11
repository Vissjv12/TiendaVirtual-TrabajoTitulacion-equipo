# A15 evaluación y decisión de alcance Redis

> **Estado:** documentación A15/A16 para revisión del equipo. No implementa ni configura Redis.
> **Rama documental actual:** `feature/a15-a18-redis-gateway`, creada desde `integration/estabilizacion-produccion@aca30ab`. La rama Redis anterior `feature/a15-a18-redis` se retira. A17 se abrirá desde el release desplegado según D-06.
> **Base técnica consultada en esta revisión:** `integration/estabilizacion-produccion` en `aca30ab648f968651d773d7bfb778f29c9002950` (2026-11-10), donde A11–A14 ya están integradas.
> **Evidencia A14:** el registro del repositorio informa 43/43 pruebas locales OK. No las repetí en esta revisión; no equivalen a validación en producción ni cierran los hallazgos posteriores de seguridad.
> **Secuencia:** A17 se implementará en una rama nueva desde el release desplegado, después de la estabilización indicada en D-06. A15/A16 no acceden a producción ni usan datos o credenciales institucionales.
> **Aprobación de producción:** no solicitada ni concedida.

## Decisión propuesta

Adoptar Redis únicamente para un **piloto Cache-Aside de una lectura pública, acotada y repetida**, si A16 confirma una proyección pública segura, se completa la estabilización D-06, se aprueba el contrato de Gateway y MySQL puede atender la lectura cuando Redis no esté disponible. El candidato actual es una proyección de catálogo de productos; la respuesta completa de `/api/productos/listado` no se debe cachear porque también incorpora stock consultado a Inventario.

Redis sería un acelerador opcional de lecturas. MySQL mantiene la fuente de verdad y las operaciones de escritura/transacción siguen ocurriendo en los microservicios y MySQL. No se propone copiar tablas completas a Redis ni hacer una migración general de la aplicación.

Si el análisis A17 previo al piloto demuestra que las esperas se deben a pagos, inventario, generación de documentos, comunicación entre servicios u otra causa que no mejora con una caché de lectura, la decisión recomendada es aplazar Redis y corregir primero esa causa.

## Contexto observado

- A11–A14 están integradas en `integration/estabilizacion-produccion`. NGINX enruta `/api/productos` a `msvc-producto`, centraliza CORS y normaliza errores; no contiene `proxy_cache`. Si se aprueba Redis, el cliente seguirá usando el Gateway y el patrón Cache-Aside vivirá dentro de `msvc-producto`, no en NGINX.
- El `/health` actual del Gateway devuelve una respuesta estática de NGINX. El healthcheck de `docker-compose.gateway.yml` comprueba ese endpoint y no demuestra disponibilidad de los microservicios, MySQL o Redis.
- El registro A14 informa 43/43 pruebas locales OK, incluidos GET de catálogo y algunas comprobaciones JWT/roles. Es evidencia reportada por el proyecto; no se repitió aquí y no prueba el estado de producción ni cierra los hallazgos de la revisión de seguridad posterior.
- `GET /api/productos/listado` ejecuta una consulta optimizada en la base de Productos y luego llama a Inventario por lote para llenar `inventarioCantidad`. El `ProductoListadoDto` también contiene `empresaId`. Cachear esa respuesta completa compartiría stock posiblemente obsoleto y campos que todavía requieren clasificación.
- El candidato A17 es una **proyección de producto explícita y sanitizada**. El stock debe seguir consultándose al servicio Inventario en cada respuesta si continúa dentro del contrato; si esa separación no da una mejora medible, se descarta o aplaza Redis. Precio público depende de T12/A23.
- La configuración actual de Producto acaba en `anyRequest().permitAll()`; además, la revisión vigente identifica tareas de seguridad pendientes para Productos, Inventario y datos públicos (por ejemplo T07, T12/T13 y H-15). Las pruebas A14 no sustituyen su resolución antes de activar caché.
- El trabajo A15/A16 y las pruebas de A17 deben usar un entorno local con fixtures sintéticos de varias empresas. No se incluirán datos, resultados de consultas ni secretos de producción en documentos o repositorios.

## Alternativas evaluadas

| Alternativa | Ventajas | Costos y riesgos | Ajuste al problema |
|---|---|---|---|
| No añadir caché todavía; medir y optimizar consulta/índices | No duplica datos; no agrega un servicio; menor riesgo de obsolescencia | No reduce lecturas repetidas si el origen sí es el cuello de botella | Debe elegirse si A22/local perf indica que la causa no es una lectura repetida de MySQL |
| Caché en memoria del proceso, por ejemplo Caffeine | Sencilla y rápida; poca infraestructura | Cada instancia conserva una copia distinta; invalidación y consistencia se complican al escalar | Puede servir para una sola instancia local, pero no es la opción preferida si varias instancias deben compartir caché |
| Redis compartido con Cache-Aside | Claves y TTL compartibles; se puede invalidar; proceso y memoria separados de la JVM | Agrega conexión, credenciales, límites de memoria, operación y un nuevo modo de fallo; datos pueden quedar obsoletos | Mejor candidato para un piloto si hay lecturas repetidas, estables y públicas |
| Caché HTTP/CDN para respuestas públicas | Puede evitar llegar al backend para contenido realmente público | Requiere definir `Cache-Control`, invalidación HTTP, reglas de Gateway/CDN y límites de privacidad | NGINX ya está integrado como Gateway, pero no tiene directivas `proxy_cache`; el piloto Redis se ubicaría en `msvc-producto`. Evaluar caché de borde como decisión separada |

No se propone una puntuación numérica porque aún faltan mediciones comparables, una política aprobada de frescura y la validación final de rutas/seguridad.

## Alcance de datos recomendado

Redis debe guardar **valores por clave**, como una proyección JSON de una consulta de lectura. Las tablas relacionales se documentan para conocer el origen, propietario e invalidaciones; no se exportan completas al caché.

| Candidato | Tabla o servicio de origen | Decisión A15 | Condición para A17 |
|---|---|---|---|
| Catálogo público de productos | `productos`, relacionadas con `categorias` y `empresas` en la base `productos` | Candidato prioritario, sujeto a filtrado y autorización | Resolver T07/T12/T13 y H-15; aprobar la proyección pública y el precio vía T12/A23. El 200 registrado en A14 no basta para habilitar caché |
| Categorías visibles | `categorias` | Candidato alternativo de menor sensibilidad | Verificar que la ruta de lectura sea pública y excluir categorías inactivas |
| Empresas visibles | `empresas` | Candidato solo como proyección pública | Aprobar campos públicos; el esquema también contiene RUC, email, teléfono y dirección |
| Inventario/stock | `inventarios` del microservicio de inventario | Excluido del piloto inicial | Reevaluar solo con prueba de que el stock es público, su frescura está definida y su invalidación puede seguir a cada cambio |
| Usuarios, sesiones, JWT, ventas y administración | Autenticación, ventas y operaciones privadas | Excluidos | No forman parte del piloto Redis de A15–A18 |

## Alcance que no se aprueba

- No cachear respuestas administrativas, datos dependientes de un JWT, datos personales ni respuestas que cambien según rol sin clave y pruebas de aislamiento explícitas.
- No cachear toda la entidad o DTO solo porque la ruta GET sea accesible sin sesión.
- No cachear operaciones `POST`, `PUT`, `PATCH` o `DELETE`, errores del origen, ni carrito/checkout/pagos.
- No usar Redis como fuente transaccional, almacenamiento de sesión, sistema de respaldo de MySQL o corrección de datos obsoletos.
- No activar una instancia Redis en el servidor institucional como parte de A15 o A16.

## Condiciones para avanzar

1. Usar como referencia de código `integration/estabilizacion-produccion@aca30ab`, no la base antigua de la rama Redis; conservar separado el estado real de esa rama.
2. Cumplir la secuencia D-06 de `REVISION-FLUJO-SEGURIDAD-Y-TAREAS.md`: estabilizar las tareas T01–T13, desplegar el release aprobado y medir la línea base antes del piloto. A17 sale en una rama nueva del release desplegado; T14/T15 se ejecutan según el orden aprobado en ese documento.
3. Resolver las autorizaciones y la exposición de campos señaladas por T07/T12/T13 y H-15. A23/T12 debe confirmar el precio público; el checkout siempre consulta y valida el precio y el stock en origen.
4. A16 debe fijar la proyección y la ruta final. No cachear el DTO actual completo de `/api/productos/listado`; separar la lectura de producto del stock en vivo antes de implementar.
5. Una medición local reproducible con datos sintéticos demuestra lecturas repetidas y establece una línea base. Si la latencia dominante sigue siendo Inventario u otra llamada, reconsiderar el caso de Redis.
6. A16 define TTL, invalidación, fallback, configuración, seguridad, conexión y pruebas. A17 debe demostrar la obsolescencia máxima y la carrera de lectura/escritura; si no se acota o no se acepta, no habilitar ese recurso.

## Registro de decisión

| Decisión | Propuesta | Estado |
|---|---|---|
| Función de Redis | Caché compartida y opcional de lecturas | Recomendada; requiere aprobación del equipo |
| Fuente de verdad | MySQL | Acordada por el plan de trabajo y la guía Redis |
| Patrón inicial | Cache-Aside | Propuesto para el piloto A17 |
| Alcance inicial | Proyección pública de producto derivada de `GET /api/productos/listado` | Condicionada a T07/T12/T13, H-15, separación del stock vivo y aprobación de A16 |
| Persistir datos de negocio en Redis | No | Rechazado para este alcance |
| Despliegue productivo | No en A15/A16 | Requiere autorización, propietario institucional y seguridad aprobada |

## Referencias revisadas

- [Plan de trabajo por etapas y ramas](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/Documentos_Jordy/Plan_de_trabajo_etapas_y_ramas.docx): definición A15–A18.
- [Diseño A11 de Gateway integrado](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/Documentos_Jordy/A11-ESTRATEGIA-Y-DISENO-GATEWAY.md), [Gateway NGINX](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/gateway/nginx.conf) y [Compose del Gateway](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/docker-compose.gateway.yml).
- [Registro A14](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/Documentos_Jordy/REGISTRO-ETAPA-4-GATEWAY-A11-A14.md): evidencia local reportada; no sustituye pruebas de producción o tareas de estabilización.
- [Revisión de seguridad y orden D-06](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/Documentos_Jordy/REVISION-FLUJO-SEGURIDAD-Y-TAREAS.md).
- [ProductoController](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/presentation/controller/ProductoController.java), [ProductoServiceImpl](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/service/ProductoServiceImpl.java), [ProductoListadoDto](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/application/dto/ProductoListadoDto.java) y [SecurityConfig](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/config/SecurityConfig.java).
- [Esquema de productos](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/db-03-schema-productos.sql) y [esquema de inventario](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/estabilizacion-produccion/db-04-schema-inventario.sql).

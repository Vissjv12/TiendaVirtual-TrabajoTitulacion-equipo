# A15 evaluación y decisión de alcance Redis

> **Estado:** borrador de decisión para revisión del equipo, publicado en `feature/a15-a18-redis`. **Tipo de cambio:** solo documentación. No crea ni configura Redis.
> **Base consultada:** `integration/etapa5-redis` en `208fd6db97f3163a6a185359f4b3c6ae207722c6`, revisada el 2026-11-08.
> **Dependencia:** A11–A14 de Gateway aún deben integrarse y validar las rutas; las candidatas de este documento siguen provisionales.
> **Aprobación de producción:** no solicitada ni concedida.

## Decisión propuesta

Adoptar Redis únicamente para un **piloto Cache-Aside de una lectura pública, acotada y repetida**, si A16 confirma que la respuesta no contiene información privada, el contrato del Gateway está aprobado, la autorización se verifica directamente y MySQL puede atender la lectura cuando Redis no esté disponible.

Redis sería un acelerador opcional de lecturas. MySQL mantiene la fuente de verdad y las operaciones de escritura/transacción siguen ocurriendo en los microservicios y MySQL. No se propone copiar tablas completas a Redis ni hacer una migración general de la aplicación.

Si el análisis A17 previo al piloto demuestra que las esperas se deben a pagos, inventario, generación de documentos, comunicación entre servicios u otra causa que no mejora con una caché de lectura, la decisión recomendada es aplazar Redis y corregir primero esa causa.

## Contexto observado

- La actividad A15 del plan de etapas es evaluar alternativas y decidir el alcance. A16 diseña el modelo; A17 implementa un piloto; A18 evalúa una migración más amplia con evidencia.
- El documento de estrategia A11 de Gateway existe en `feature/a11-diseno-gateway`, pero aún no forma parte de `integration/etapa5-redis`. Describe NGINX como Gateway, las rutas y una intención de centralizar CORS y errores.
- En el código de Producto de la rama de integración hay lecturas GET para productos, categorías y empresas. A11 propone que varias sean públicas; A14 todavía debe demostrar que el comportamiento real y el contrato coinciden con ese diseño.
- La configuración actual de seguridad de Producto termina en `anyRequest().permitAll()` tras reglas específicas para escrituras de productos; la tabla A11, en cambio, describe escrituras de categorías y empresas solo para administrador. Esta diferencia debe resolverse y probarse en A12–A14 antes de declarar que esas respuestas o mutaciones son seguras para cachear.
- `ProductoResponseDto` incluye inventario y datos anidados de la empresa; `ProductoListadoDto` incluye `empresaId` e `inventarioCantidad`. No deben cachearse sin una proyección y clasificación de campos aprobadas.
- La guía de Redis recomienda empezar por datos públicos de lectura y no cachear JWT, usuarios, ventas, inventario privado ni administración. El equipo no tiene credenciales del servidor o de la base institucional, por lo que las pruebas futuras deben usar datos locales sintéticos.

## Alternativas evaluadas

| Alternativa | Ventajas | Costos y riesgos | Ajuste al problema |
|---|---|---|---|
| No añadir caché todavía; medir y optimizar consulta/índices | No duplica datos; no agrega un servicio; menor riesgo de obsolescencia | No reduce lecturas repetidas si el origen sí es el cuello de botella | Debe elegirse si A22/local perf indica que la causa no es una lectura repetida de MySQL |
| Caché en memoria del proceso, por ejemplo Caffeine | Sencilla y rápida; poca infraestructura | Cada instancia conserva una copia distinta; invalidación y consistencia se complican al escalar | Puede servir para una sola instancia local, pero no es la opción preferida si varias instancias deben compartir caché |
| Redis compartido con Cache-Aside | Claves y TTL compartibles; se puede invalidar; proceso y memoria separados de la JVM | Agrega conexión, credenciales, límites de memoria, operación y un nuevo modo de fallo; datos pueden quedar obsoletos | Mejor candidato para un piloto si hay lecturas repetidas, estables y públicas |
| Caché HTTP/CDN para respuestas públicas | Puede evitar llegar al backend para contenido realmente público | Requiere definir `Cache-Control`, invalidación HTTP, reglas de Gateway/CDN y límites de privacidad | Reconsiderar si el objetivo principal es una respuesta pública cacheable en el borde; Gateway aún se está integrando |

No se propone una puntuación numérica porque aún faltan mediciones comparables, una política aprobada de frescura y la validación final de rutas/seguridad.

## Alcance de datos recomendado

Redis debe guardar **valores por clave**, como una proyección JSON de una consulta de lectura. Las tablas relacionales se documentan para conocer el origen, propietario e invalidaciones; no se exportan completas al caché.

| Candidato | Tabla o servicio de origen | Decisión A15 | Condición para A17 |
|---|---|---|---|
| Catálogo público de productos | `productos`, relacionadas con `categorias` y `empresas` en la base `productos` | Candidato prioritario, sujeto a filtrado y autorización | Confirmar ruta pública en A14, precios con A23, campos de salida y empresa/estado activos |
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

1. A11–A14 integrados: tabla de rutas, CORS, exposición y códigos HTTP validados de extremo a extremo.
2. A03/A05/A07 y configuración A08 revisados en el HEAD de integración; las pruebas directas a API confirman que un usuario no obtiene o modifica datos de otra empresa.
3. Para una respuesta de catálogo con precio, A23 confirma el precio público sin login y mantiene protegido carrito/compra.
4. Si la respuesta elegida es paginada, A19/A20 publican el contrato primero. Las claves deberán distinguir `page`, `size`, filtros y orden.
5. Una medición local reproducible demuestra una lectura repetida y establece una línea base antes del piloto.
6. A16 define TTL, invalidación, fallback, configuración, seguridad, conexión y pruebas; A17 demuestra la ventana real de obsolescencia incluso ante una lectura concurrente y fallo de invalidación. Si el equipo no acepta el máximo medido, el recurso no entra en A17 hasta diseñar invalidación durable.

## Registro de decisión

| Decisión | Propuesta | Estado |
|---|---|---|
| Función de Redis | Caché compartida y opcional de lecturas | Recomendada; requiere aprobación del equipo |
| Fuente de verdad | MySQL | Acordada por el plan de trabajo y la guía Redis |
| Patrón inicial | Cache-Aside | Propuesto para el piloto A17 |
| Alcance inicial | Una lectura pública, con proyección explícita | Pendiente de A16 y A14 |
| Persistir datos de negocio en Redis | No | Rechazado para este alcance |
| Despliegue productivo | No en A15/A16 | Requiere autorización, propietario institucional y seguridad aprobada |

## Referencias revisadas

- [Plan de trabajo por etapas y ramas](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/etapa5-redis/Documentos_Jordy/Plan_de_trabajo_etapas_y_ramas.docx): definición A15–A18.
- Documento `Sí.docx` proporcionado por el equipo: criterios de datos públicos, exclusiones, fallback y riesgos; deberá estar disponible al revisar la PR documental.
- [Diseño A11 de Gateway](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/feature/a11-diseno-gateway/Documentos_Jordy/A11-ESTRATEGIA-Y-DISENO-GATEWAY.md) (todavía no integrado).
- [SecurityConfig de Producto en la integración revisada](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/etapa5-redis/Microservicios/msvc-producto/src/main/java/com/example/msvc_producto/infrastructure/config/SecurityConfig.java).
- [Esquema de productos](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/etapa5-redis/db-03-schema-productos.sql) y [esquema de inventario](https://github.com/Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo/blob/integration/etapa5-redis/db-04-schema-inventario.sql).

# Plan de cierre de Redis A17–A18

> **Para quien ejecute el plan:** usar la guía `superpowers:executing-plans`; marcar cada casilla con evidencia verificable.

**Objetivo:** completar y evaluar el piloto Redis de catálogo en local, documentar los resultados y preparar una prueba controlada en servidor.

**Arquitectura:** Cache-Aside en `msvc-producto`, con MySQL como fuente de verdad y stock consultado a Inventario en cada solicitud. Las claves versionadas y la generación compartida invalidan las proyecciones después de los commits; Redis sigue siendo opcional.

**Tecnología:** Java 17, Spring Boot, MySQL y Redis locales en Docker, Gateway NGINX y pruebas Maven/JUnit.

**Referencias:** `Documentos_Jordy/P7-PUERTA-ENTRADA-REDIS.md`, `A16-MODELO-DE-CACHE.md`, `A16-SEGURIDAD-CONEXION-Y-PRUEBAS.md` y `REDIS-VERIFICACION-CACHE-LOCAL-A17-A18.md`.

**Base revisada:** `integration/avance-hasta-redis` en `1f4aece`; antes de ejecutar, comprobar que la rama y su HEAD sigan siendo los previstos.

**Estado de ejecución (2026-11-14):** el cierre local A17 está implementado y verificado. A18 incluye una comparación mixta exploratoria y una segunda medición dedicada al listado, con tres corridas de 30 s por modo y lectura del contador global `Com_select`; esta última supera el criterio alternativo de reducción de consultas observadas, pero el p95 no mejora. Sigue pendiente validar las consultas con instrumentación por sesión, acordar el contrato público y ejecutar la prueba institucional. El detalle está en `REDIS-RESULTADOS-A18.md` y `REDIS-VERIFICACION-CACHE-LOCAL-A17-A18.md`.

## Restricciones y decisiones

- Usar inicialmente MySQL, Redis, Inventario y datos sintéticos locales; no modificar datos institucionales durante las pruebas locales.
- La bandera general y las banderas por ruta permanecen desactivadas por defecto. No cachear inventario transaccional, autenticación, usuarios, ventas, carrito ni pagos.
- Las rutas propuestas para el piloto son `GET /api/productos/listado`, `GET /api/categorias` y `GET /api/categorias/{id}`. Las dos rutas de categorías amplían la única lectura de productos descrita en P7: registrar la decisión posterior del equipo y validar cada contrato.
- Los TTL actuales son propuestas locales: productos `PT5M`, categorías `PT15M`. La tolerancia a datos obsoletos y la publicación de campos requieren decisión del responsable.
- Una autorización para continuar el piloto local no equivale a aprobación de despliegue institucional. Host, ACL, TLS, secretos, respaldo y ventana de cambios del servidor deben acordarse con sus responsables.
- Conservar evidencia sin claves, tokens, contraseñas, datos personales ni cuerpos reales del servidor. Identificar cada prueba como local, simulada o institucional.

## Revisión prioritaria

1. Si falla Redis después de confirmar una escritura, no prometer que se invalida la caché ni que la obsolescencia desde el commit dura como máximo un TTL sin probar la carrera.
2. Si cambian una categoría o empresa visible, comprobar que también se invalida el listado de productos y que un rollback no cambia la generación.
3. Si Redis se detiene, reinicia o responde lentamente, comprobar el arranque, el fallback, la salud y el tiempo de respuesta con datos sintéticos.
4. Comparar los contratos HTTP y la autorización de dos empresas y roles, tanto directamente como por el Gateway de esta rama.
5. Comprobar productos y categorías inactivos y los campos `precio`, `empresaId`, `activo` y fechas antes de aprobar su almacenamiento público.

## 1. Fijar el contrato y el alcance

**Archivos:** `Documentos_Jordy/P7-PUERTA-ENTRADA-REDIS.md`, `A16-MODELO-DE-CACHE.md`, `A16-SEGURIDAD-CONEXION-Y-PRUEBAS.md`, `REDIS-VERIFICACION-CACHE-LOCAL-A17-A18.md`; revisar `ProductoCatalogoPublicoDto.java`, `CategoriaCatalogoQueryService.java`, los controladores y repositorios de Producto y Categoría.

- [x] Registrar que el piloto local se inició después de P7 y que se propusieron las tres rutas; mantener la fecha y el alcance histórico de P7 identificables. Evidencia: nota de actualización en P7 y el informe de resultados.
- [ ] Inventariar la respuesta real y su consumidor: el inventario está documentado, pero falta que el responsable apruebe `precio`, `empresaId`, `activo`, `fechaCreacion` y `fechaActualizacion` para exposición pública.
- [ ] Comparar en forma explícita los mismos casos de productos, empresas y categorías activos/inactivos con caché apagada y encendida. Hay pruebas de invalidación al desactivar producto, categoría y empresa, pero falta el visto bueno de visibilidad del propietario del requisito.
- [x] Registrar ruta, DTO, TTL, flags, invalidaciones y permisos en A16 y en la verificación. Las tres familias mantienen flags apagados por defecto en `application.properties`.
- [x] Actualizar A16 con las variables que realmente enlaza la aplicación y separar configuración local de variables institucionales aún desconocidas.

**Aceptación:** tabla de decisiones por ruta y campo, con responsable y estado; ninguna ruta se considera aprobada para un entorno compartido mientras su contrato o visibilidad siga pendiente.

## 2. Completar invalidación y frescura

**Archivos:** `ProductoCatalogoInvalidacionService.java`, `ProductoCatalogoCacheService.java`, `RedisProductoCatalogoStore.java`, `ProductoServiceImpl.java`, `CategoriaServiceImpl.java`, `EmpresaServiceImpl.java`; ampliar `ProductoCatalogoHttpIT.java` y `RedisProductoCatalogoStoreIT.java` o crear pruebas específicas en la misma carpeta.

- [x] Probar con transacciones locales cambios de producto, categoría y empresa, desactivaciones disponibles y rollback; comprobar invalidación posterior al commit. El endpoint de “eliminación” de producto es baja lógica. Evidencia: `ProductoCatalogoHttpIT`.
- [x] Ejecutar R16 con coordinación determinista: la lectura fría no puede publicar el valor antiguo después de que el commit avance la generación. Evidencia: `inFlightOldCatalogReadCannotPublishAfterCommittedUpdateAdvancesGeneration` y prueba del almacén.
- [x] Ejecutar R15 con fallo inyectado después del commit: se conserva la escritura, la entrada vieja vive hasta su TTL y un hit no renueva el vencimiento. La prueba usa TTL corto sintético; no mide una espera de cinco minutos reales.
- [ ] Con los resultados, acordar la ventana máxima de obsolescencia. Si no es aceptable o no puede acotarse, mantener la bandera apagada en entornos compartidos y diseñar recuperación durable; evaluar outbox u otra alternativa con su impacto en esquema, migración, recuperación y rollback antes de aplicarla.

**Aceptación:** R08, R09, R15 y R16 con resultados observados y reproducibles; ventana documentada y aceptada, o ruta desactivada y alternativa durable definida como pendiente.

## 3. Completar fallback, TTL y salud

**Archivos:** `RedisCacheConfig.java`, `RedisProductoCatalogoStore.java`, `ProductoCatalogoCacheService.java`, `application.properties`, `docker-compose.test.yml`; pruebas `RedisCacheConfigTest.java`, `ProductoCatalogoFallbackIT.java` y pruebas de integración adicionales cuando hagan falta.

- [x] Ejecutar R01–R05, R07 y R13 en pruebas y runtime local: caché apagada, Redis ausente antes del arranque y durante lectura, miss/hit, expiración y reinicio. R06 (latencia controlada/timeout) queda pendiente; no se inventa una medición.
- [ ] Definir un presupuesto aceptable de fallback y medir timeout/lectura por separado. La configuración local es `500ms` para conexión/comando; falta el ensayo de latencia forzada que valide el presupuesto.
- [x] Verificar health seguro: con Redis caído, liveness/readiness y catálogo siguen 200 y `/actuator/health/cache` da 503; sin Redis habilitado el indicador no conecta.
- [x] Ensayar rollback con `CACHE_REDIS_ENABLED=false` mediante la carga local y revisar el camino por origen. No se usó `FLUSHALL` ni `FLUSHDB`.

**Aceptación:** cada caso indica prerequisitos, comando, resultado HTTP, duración, evidencia y limpieza; Redis indisponible no impide servir la lectura cuando los orígenes están disponibles.

## 4. Aislamiento, seguridad y Gateway

**Archivos:** `ProductoCatalogoHttpIT.java`, `ProductoCatalogoFallbackIT.java`, configuración de seguridad de Producto y Gateway, fixtures de `docker-compose.test.yml` y documentación de pruebas.

- [x] Preparar dos empresas sintéticas y roles distinguibles en pruebas; las filas creadas por los casos HTTP se eliminan o restauran en los callbacks de limpieza.
- [x] Probar aislamiento entre empresas/roles, 401/403, ausencia de mutación, rechazo de publicación incompleta y autorización de métricas. Los fallos de MySQL/Inventario se prueban con fixtures/inyección; la matriz precisa sus límites.
- [ ] Obtener aprobación del propietario para `empresaId` y categoría. La proyección excluye stock y `emprendedorId`; aún no se aprueba que todo campo conservado sea público.
- [x] Ejecutar lectura, miss/hit, fallback y salud por un Gateway NGINX aislado de este checkout. La autorización de escritura y métricas se cubre en pruebas HTTP directas; no se afirma una auditoría completa de todas las rutas del Gateway.
- [ ] Revisar que compra, precio y stock transaccional continúen consultando sus fuentes; comprobar que ninguna ruta privada o transaccional usa las claves públicas.

**Aceptación:** matriz por ruta, actor y empresa con resultados directos y por Gateway, cero fugas o mutaciones no autorizadas y ninguna respuesta de error publicada como valor exitoso.

## 5. Observabilidad y evaluación A18

**Archivos:** `ProductoCatalogoCacheService.java`, configuración de métricas/Actuator si existe, `Documentos_Jordy/P6-LINEA-BASE-SIN-REDIS.md`, `REDIS-VERIFICACION-CACHE-LOCAL-A17-A18.md` y un informe nuevo `Documentos_Jordy/REDIS-RESULTADOS-A18.md`.

- [x] Verificar métricas de hit, miss, fallback, fallos y duración; `/actuator/metrics/**` requiere `ROLE_ADMIN`, health no revela detalles y Redis local reporta memoria usada. El origen se midió con el contador global MySQL `Com_select`, no con un contador por sesión.
- [x] Ejecutar tres corridas de 30 s por modo, 5 VUs, mismo dataset de 39 productos, stub de Inventario y Gateway, usando el script dedicado `scripts/redis-a18-listado.js`.
- [x] Registrar p50/p95, rendimiento, llamadas a Inventario, hits/misses, respuestas HTTP y `Com_select` global en `REDIS-RESULTADOS-A18.md`; salud y memoria se verificaron en la prueba runtime separada.
- [x] Comparar umbrales: el p95 de hit no mejora (mediana 54,69 ms frente a 49,45 ms desactivado), mientras que `Com_select` observado por solicitud cae cerca de 99,8 % en hit. Al superar el umbral alternativo de 20 %, mantener el piloto solo en el listado local; no ampliar ni activar en infraestructura compartida hasta confirmar con instrumentación controlada y resolver las aprobaciones pendientes.

**Aceptación:** informe reproducible con comparaciones y limitaciones; no afirmar mejoras basándose solo en un `PING`, en la existencia de claves o en la línea base anterior.

## 6. Prueba institucional posterior

**Archivos:** nueva guía `Documentos_Jordy/REDIS-PRUEBA-INSTITUCIONAL-Y-ROLLBACK.md` y actualización del informe de resultados.

- [ ] Identificar con el responsable la versión y servicios desplegados; no hay acceso institucional disponible para esta ejecución.
- [ ] Acordar ventana, responsables y respaldo/restauración institucional; falta autorización y acceso.
- [ ] Obtener por canal seguro host, puerto, ACL, TLS, secretos, límites, rotación y observabilidad; estos datos no están disponibles al equipo universitario.
- [ ] Obtener aprobación institucional y preparar plan de despliegue/rollback; no se accedió ni se modificó el servidor.
- [ ] Ejecutar pruebas institucionales únicamente en una ventana y con autorización del responsable.

**Aceptación:** evidencia institucional separada de la local y decisión del responsable sobre activación; si faltan permisos, valores o ventana, registrar el bloqueo y mantener la prueba institucional pendiente.

## Comprobaciones y entrega

- [x] Ejecutar `mvn -B clean test` en Maven/JDK 17 con MySQL/Redis sintéticos: 26/26 pasaron. Suite focalizada incluyendo casos Redis/HTTP: 46/46 pasaron. Entorno y comandos se anotan en el informe A18.
- [x] Con JDK 25 en el host, ejecutar suites en contenedor Maven/JDK 17 conectado a los servicios locales de prueba.
- [x] Completar la matriz R01–R16 en `REDIS-RESULTADOS-A18.md` y actualizar la verificación sin borrar la evidencia histórica.
- [x] Revisar el diff y comprobar que `.env`, contraseñas, tokens y datos personales no forman parte de los cambios documentados.

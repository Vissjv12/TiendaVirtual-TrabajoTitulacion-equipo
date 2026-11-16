# Ejecución del plan de cierre Redis A17–A18

**Fecha:** 2026-11-14  
**Rama de trabajo:** `integration/avance-hasta-redis`  
**HEAD base revisado:** `1f4aeceef6bcfdf90d1aeb430dfbde4b2fc9317d`  
**Alcance de esta ejecución:** cerrar los pendientes locales de A17, comprobarlos y medir A18 con una carga dedicada al listado en tres modos, con tres repeticiones por modo. El cierre institucional está pendiente porque el equipo universitario no tiene credenciales ni autorización para el servidor y su base de datos.

> **La evidencia y las cifras de A17/A18 en este informe corresponden al entorno local aislado con MySQL/Redis sintéticos, Gateway local e Inventario simulado. No son mediciones ni validación del servidor institucional.**

## Estado

- **Implementación local A17:** terminada para el alcance de catálogo ya integrado: producto, categorías y salud/observabilidad de caché.
- **Pruebas automáticas:** suite completa `26/26` y focalizada `46/46`, Maven/JDK 17, MySQL/Redis sintéticos.
- **A18:** comparación mixta inicial y nueve corridas dedicadas al listado terminadas. En hit se observaron cerca de 99,8 % menos `Com_select` por solicitud que con caché desactivada, pero el p95 mediano subió 10,6 %. `Com_select` es global del contenedor, no una medición por sesión; falta confirmación con instrumentación más precisa, además de R06 y aprobación del contrato de datos.
- **Prueba institucional:** no ejecutada. No se consultó ni modificó el servidor o base de datos institucional.

## Decisiones conservadas

1. `CACHE_REDIS_ENABLED` y los flags por ruta siguen apagados por defecto. Categorías y productos solo se activan en el entorno local aislado.
2. Se mantienen fuera de Redis inventario/stock, usuarios, identidad, ventas, pagos, carrito, logística y datos empresariales privados.
3. No se añadió outbox ni migración: la prueba local evidencia que, si falla la invalidación después del commit, una entrada antigua puede seguir viva hasta su TTL. El responsable aún debe aceptar esa ventana o solicitar recuperación durable.
4. No se aprobaron `precio`, `empresaId` ni los campos `activo`/fechas de categoría como datos públicos. Mientras falte la decisión, los flags permanecen desactivados en entornos compartidos.
5. A18 justifica conservar el piloto local solo para el listado: el criterio alternativo de lecturas observadas al origen supera 20 %, aunque el p95 no mejora. El contador MySQL usado es global y no sustituye instrumentación por sesión; no se amplía el caché mientras falten esa confirmación y las aprobaciones de datos.

## Cambios de implementación local

- Añadida salud de caché independiente de liveness/readiness. Con caché desactivada informa `UP` sin conexión; con Redis caído informa `DOWN` sin exponer detalles. Los probes de aplicación siguen disponibles.
- Añadido Actuator para salud y métricas; salud mínima es pública, métricas requieren `ROLE_ADMIN` y otros endpoints Actuator quedan denegados.
- Protegidas escrituras de categoría y empresa para que solo `ROLE_ADMIN` pueda ejecutarlas, de acuerdo con el control previsto en A11.
- Una excepción al incrementar generación después de commit se cuenta como fallo y se propaga al invalidator transaccional, que conserva el éxito de MySQL e informa el fallo.
- Tests Redis pueden apuntar a `TEST_REDIS_HOST`/`TEST_REDIS_PORT`; pruebas HTTP se aíslan de la configuración institucional mediante un perfil de test con MySQL sintético.
- Ampliadas pruebas para invalidación de producto/categoría/empresa, lectura concurrente obsoleta, TTL no deslizante, error post-commit, autorización de empresa, categoría/empresa, salud y métricas.

## Verificación

Resultados medidos y la matriz R01–R16: `Documentos_Jordy/REDIS-RESULTADOS-A18.md`. Evidencia histórica anterior conservada en `Documentos_Jordy/REDIS-VERIFICACION-CACHE-LOCAL-A17-A18.md`.

Comandos ejecutados en imagen Maven con JDK 17 y dependencias de prueba locales:

```text
mvn -B clean test
mvn -B -Dtest=RedisGenerationIT,RedisProductoCatalogoStoreIT,ProductoCatalogoHttpIT,ProductoCatalogoFallbackIT,ProductoCatalogoCacheServiceTest,RedisCacheConfigTest,ProductoControllerApiAuthorizationTest test
```

Resultado: **BUILD SUCCESS, 26/26** en suite completa y **BUILD SUCCESS, 46/46** en suite focalizada. La ejecución de `clean test` incluye pruebas IT configuradas en el proyecto; la segunda orden enfoca las clases que ejercitan Redis, HTTP y autorización.

Para carga k6, salud, Gateway y pruebas manuales se usaron únicamente Producto `8281`, Gateway aislado `8290`, Inventario simulado `8282`, MySQL sintético `3307` y Redis `6380`. No se copió `.env`, token, clave, contraseña ni dato personal a la documentación.

## Riesgos y asuntos pendientes

- Aprobar qué campos son públicos y validar activos/inactivos con el propietario del requisito.
- Terminar prueba de timeout controlado R06 y fallo real de MySQL en la capa HTTP R12.
- Confirmar la reducción con un contador SQL por sesión o trazas controladas; las tres repeticiones de listado ya están documentadas, pero `Com_select` incluye actividad global de fondo.
- Completar la matriz de autorización y contenido vía Gateway (R14) y la revisión de ruta transaccional/precio/stock.
- Diseñar recuperación durable si no se acepta R15; no desplegar con la invalidación de mejor esfuerzo en un entorno compartido.
- Obtener por canal institucional la versión desplegada, ventana, respaldo, host Redis, ACL/TLS, límites, secretos y rollback. No guardar esos valores en Git.

## Documentación nueva/actualizada

- `Documentos_Jordy/PLAN-CIERRE-REDIS-A17-A18.md`
- `Documentos_Jordy/REDIS-RESULTADOS-A18.md`
- `Documentos_Jordy/REDIS-VERIFICACION-CACHE-LOCAL-A17-A18.md`
- `Documentos_Jordy/A16-MODELO-DE-CACHE.md`
- `Documentos_Jordy/A16-SEGURIDAD-CONEXION-Y-PRUEBAS.md`
- `Documentos_Jordy/REDIS-PRUEBA-INSTITUCIONAL-Y-ROLLBACK.md`

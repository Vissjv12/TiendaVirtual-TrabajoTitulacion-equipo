# P7 — Puerta de entrada para Redis

## Estado y alcance

**Estado:** revisión de preparación, sin implementación de Redis.

Este documento revisa los documentos A15–A18 aportados por el equipo, el estado integrado de A14, P4, P5 y P6, y las guías de pruebas existentes. Su objetivo es decidir si el sistema está listo para comenzar A17 (piloto Cache-Aside) sin afectar las reglas actuales ni introducir un nuevo punto de fallo en producción.

Durante P7 no se agregan dependencias Maven, contenedores, variables reales, credenciales, claves Redis, cambios de esquema ni configuración de producción.

## Fuentes revisadas

- `Documentos_Jordy/A15-EVALUACION-Y-DECISION.md`.
- `Documentos_Jordy/A16-MODELO-DE-CACHE.md`.
- `Documentos_Jordy/A16-SEGURIDAD-CONEXION-Y-PRUEBAS.md`.
- `Documentos_Jordy/REVISION-RAMA-A15-A18-REDIS-GATEWAY.md`.
- `Documentos_Jordy/PLAN-PAQUETE-PRE-REDIS-Y-MEJORAS.md`.
- `Documentos_Jordy/P5-AUDITORIA-ENDURECIMIENTO.md`.
- `Documentos_Jordy/P6-LINEA-BASE-SIN-REDIS.md`.
- Resultados de A14: Gateway, CORS, JWT, aislamiento por empresa y rutas públicas validadas.
- `info/RESULTADOS-PRUEBAS-ESTRES.md` y `info/GUIA-EJECUTAR-PRUEBAS-AHORA.md`, tratados como antecedentes y no como evidencia equivalente a P6.

## Decisión de P7

**No se recomienda iniciar todavía la implementación A17 ni desplegar Redis.**

El repositorio está preparado para diseñar un piloto local controlado, pero todavía no existe evidencia suficiente para habilitarlo. Primero deben cerrarse el contrato exacto de la proyección pública, la estrategia de invalidación, el fallback a MySQL, las pruebas de aislamiento y los parámetros operativos proporcionados por infraestructura.

La opción aprobada como candidata futura es una única lectura pública y repetida del catálogo de productos, mediante Cache-Aside. MySQL seguirá siendo la fuente de verdad. Redis no debe participar en autenticación, usuarios, JWT, ventas, inventario/stock, administración, carrito, checkout o pagos.

## Matriz de preparación

| Condición | Estado | Evidencia o pendiente |
|---|---|---|
| Catálogo funcional con Redis apagado | **Cumplido** | A14 y P6 validan lectura pública, disponibilidad y consistencia de 39 productos/inventarios con 0 diferencias. |
| Línea base reproducible antes de Redis | **Cumplido** | P6 registró 2.919 solicitudes, 0% de errores, p50 38,50 ms y p95 135,54 ms en la prueba local documentada. |
| MySQL como fuente de verdad | **Cumplido como decisión** | Está establecido en A15/A16 y se conserva P4: precios, configuración de compra y stock de compra no deben depender de una caché de presentación. |
| Respaldo y restauración de MySQL demostrados | **Pendiente** | Hay referencias operativas, pero no se conserva en P7 una evidencia de restauración válida y reciente. Debe probarse en entorno local o de recuperación antes del despliegue. |
| Proyección pública y campos aprobados | **Pendiente** | Los DTO actuales contienen campos que requieren revisión, como `empresaId`, información de empresa e inventario. No se debe cachear el DTO completo. |
| Claves, versión, serialización y alcance | **Propuesto, no aprobado** | A16 propone namespace/versionado y hash de consulta. Falta aprobar formato final, filtros, orden, paginación y separación de ámbitos. |
| TTL e invalidación | **Propuesto, no demostrado** | Se propone 300 s para productos y generación de claves. Falta probar cambios, eliminaciones, desactivaciones y carreras de lecturas. |
| Fallback Redis → MySQL | **Pendiente** | A16 define el comportamiento esperado, pero todavía no existe implementación ni prueba de Redis apagado o con timeout. |
| Contrato HTTP hit/miss/fallback | **Pendiente** | Debe ser idéntico en código HTTP, campos, orden, filtros y errores. |
| Aislamiento entre empresas y roles | **Parcial** | A14 demuestra autorización actual; falta demostrar que las claves y proyecciones Redis no cruzan empresas, roles ni contextos. |
| Secretos, ACL, red y límites | **Bloqueado por infraestructura** | Faltan host, puerto, TLS, usuario ACL, secreto, memoria, política de eviction y límites aprobados. No se deben inventar valores productivos. |
| Observabilidad Redis | **Pendiente** | P6 aporta línea base general; faltan métricas de hit, miss, latencia, errores, memoria y lecturas a MySQL. |
| Rollback operativo | **Parcial** | El interruptor `CACHE_REDIS_ENABLED=false` está propuesto; falta incorporarlo y ensayar el rollback cuando exista implementación. |
| Aprobación de producción | **Pendiente** | Requiere revisión del responsable técnico y del administrador del servidor. |

## Diseño que queda aprobado como restricción

### Datos permitidos para el futuro piloto

- Una proyección explícita del catálogo público.
- Solo los campos necesarios para mostrar el catálogo.
- Consultas distinguidas por paginación, filtros, orden y cualquier otro parámetro que cambie la respuesta.
- Datos sintéticos para las pruebas de dos empresas diferentes.

### Datos excluidos

- JWT, sesiones, contraseñas y usuarios.
- Ventas, facturas, pagos, carrito y checkout.
- Stock o inventario usado para validar una compra.
- RUC, correo, teléfono, dirección, identificadores privados y datos administrativos, salvo aprobación explícita como campo público.
- Respuestas 4xx, 5xx, incompletas o producidas por errores de autorización/base de datos.

### Reglas que no deben cambiar

- MySQL conserva la fuente de verdad.
- El precio y el stock de una compra se validan contra el origen, nunca contra la caché de presentación.
- La autorización ocurre antes de consultar una caché cuando la ruta dependa del usuario, rol o empresa.
- Redis caído no debe impedir que el microservicio arranque ni que una lectura pueda continuar por MySQL.
- Redis no se publica hacia Internet; en local solo se permite un mapeo a loopback y en Compose se usa la red interna.
- No se utiliza `FLUSHALL` ni `FLUSHDB` como invalidación normal.
- No se registran secretos, JWT, valores completos de caché ni datos personales en logs o métricas.

## Trabajo que debe realizarse antes de A17

1. **Aprobar el contrato de salida:** seleccionar el endpoint público, definir el JSON mínimo, excluir campos sensibles y acordar filtros, orden y paginación.
2. **Definir las claves:** cerrar namespace, versión, alcance, hash de consulta y estrategia de generación/incremento.
3. **Definir frescura:** aprobar TTL, tolerancia a datos obsoletos e invalidación para crear, editar, desactivar y eliminar.
4. **Diseñar el fallback:** especificar timeout, ausencia de reintentos infinitos, comportamiento con Redis apagado y preservación de los errores reales de MySQL.
5. **Preparar fixtures sintéticos:** dos empresas, usuarios/roles separados y datos distinguibles; sin credenciales ni información institucional.
6. **Definir observabilidad:** métricas p50/p95, hit/miss, lecturas a MySQL, errores Redis, memoria y respuestas incorrectas.
7. **Solicitar valores de infraestructura:** host, puerto, TLS, ACL, secreto, memoria, eviction, red y procedimiento de rotación.
8. **Aprobar rollback:** confirmar que el interruptor de caché desactivada devuelve el comportamiento actual y documentar la reversión.
9. **Repetir la línea base si cambia el entorno:** Redis no debe compararse contra las antiguas pruebas JMeter directas sin controlar endpoint, dataset y carga.

## Pruebas mínimas para A17 cuando se autorice

- Redis desactivado y Redis apagado antes del arranque.
- Miss, hit y comparación exacta de contrato.
- Redis apagado durante una lectura y timeout controlado.
- Expiración de TTL.
- Invalidación después de crear, editar, desactivar y eliminar.
- Carrera de lectura iniciada antes de un commit y publicación posterior de un valor viejo.
- Dos empresas y roles cruzados, verificando ausencia de fuga y ausencia de mutaciones.
- Respuestas 4xx/5xx sin escritura en caché.
- Reinicio de Redis y recuperación desde MySQL.
- Misma autorización y contrato a través del Gateway.
- Verificación de que precio, stock y checkout siguen consultando el origen.

Cada prueba debe conservar prerequisitos, comando o llamada reproducible, resultado esperado, resultado observado, evidencia resumida y limpieza de datos locales.

## Conclusión

P7 queda **cerrado como evaluación documental**, pero la puerta de implementación A17 queda **cerrada temporalmente**. Se puede avanzar con el contrato, fixtures y diseño de pruebas en local; no se debe agregar Redis al Compose, introducir credenciales ni desplegarlo en el servidor hasta resolver los pendientes de la matriz.

La recomendación actual es: **preparar y aprobar primero; implementar después; medir antes de decidir A18**.


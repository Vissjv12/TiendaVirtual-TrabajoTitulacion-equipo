# Guía para una futura prueba institucional de Redis

**Estado:** preparación documental; no ejecutada.  
**Motivo:** el equipo universitario no dispone de credenciales autorizadas para el servidor ni para la base de datos.  
**Regla:** este documento no autoriza acceso, despliegue, consulta de datos ni cambio de configuración.

## 1. Aprobaciones previas

Antes de usar el servidor, el responsable institucional y el director del trabajo deben acordar por escrito:

- versión exacta desplegada, servicios relacionados, Gateway y base de datos usados;
- ambiente autorizado, responsables con acceso, alcance de lectura y ventana de trabajo;
- conjunto de datos de prueba permitido y procedimiento de limpieza;
- respaldo y restauración comprobados por el responsable de la base;
- límite de carga y señales para detener la prueba;
- autorización explícita para crear/usar Redis, ACL, secretos y configuración de red.

Sin cualquiera de estas aprobaciones, detenerse en local. Una conexión SSH por sí sola no concede autorización para consultar la base ni desplegar cambios.

## 2. Valores de infraestructura que debe entregar el responsable

Solicitar por el canal institucional aprobado; no copiar secretos en Git, issues, mensajes de equipo ni esta guía.

| Dato | Requisito |
|---|---|
| Host y puerto Redis | Dirección privada alcanzable desde `msvc-producto`; no publicar Redis en Internet |
| ACL | Usuario dedicado al microservicio, comandos mínimos para lectura/escritura/TTL y patrón de claves aprobado |
| Secreto | Entrega por almacén de secretos o canal autorizado; rotación y responsable definidos |
| TLS/red | Confirmar cifrado, validación de certificado, firewall, segmento de red y origen permitido |
| Memoria | Límite, política de eviction, persistencia/recuperación y alertas acordados |
| Observabilidad | Métricas autorizadas, retención de logs, responsables y umbrales de alerta |
| MySQL y servicios | Confirmar conectividad existente, versión desplegada y comportamiento de Inventario/Gateway |
| Rollback | Quién puede cambiar flags/reiniciar, tiempo esperado y validación posterior |

No usar credenciales de MySQL para Redis. No solicitar ni almacenar contraseñas compartidas de root cuando el responsable pueda crear una identidad de mínimo privilegio.

## 3. Revisión técnica antes de la ventana

1. Comparar el artefacto a probar con la versión desplegada y revisar el diff de la rama aprobada.
2. Confirmar contrato y campos del listado y categorías: `precio`, `empresaId`, estado y fechas requieren aprobación del propietario del dato.
3. Resolver la ventana R15 ante fallo post-commit; si no se acepta, no habilitar Redis compartido hasta diseñar recuperación durable.
4. Completar R06 (timeout controlado), fallo real de MySQL y la matriz de autorización a través del Gateway.
5. Repetir A18 con carga fija y métricas del origen; la primera corrida local no alcanzó la mejora p95 propuesta y no midió sentencias SQL.
6. Acordar valores de flags y configuración en una revisión separada. Mantenerlos apagados durante despliegue inicial.
7. Preparar checklist de verificación y rollback con el operador que tiene permisos sobre la infraestructura.

## 4. Secuencia de una prueba controlada, si se autoriza

1. Verificar la versión, la base, el Gateway y salud de los servicios antes del cambio.
2. Desplegar únicamente el artefacto aprobado con `CACHE_REDIS_ENABLED=false` y flags de rutas apagados.
3. Verificar liveness/readiness, catálogo por Gateway, Inventario, logs y métricas antes de habilitar caché.
4. Habilitar una sola ruta autorizada con un dataset acordado; empezar con concurrencia baja y ventana breve.
5. Observar hit/miss/fallback, errores, latencia p50/p95, memoria, respuesta, Inventario y métricas SQL disponibles.
6. Ejecutar un fallback Redis caído solo si se autorizó y existe impacto aceptable; verificar que el origen sigue respondiendo.
7. Detener la prueba ante 5xx atribuibles, fuga/diferencia de datos, degradación fuera del umbral o crecimiento de memoria; avisar al operador.
8. Registrar resultados sin cuerpos privados, tokens, secretos ni datos personales; dejar la decisión de ampliar al responsable técnico.

## 5. Rollback

1. El operador autorizado establece `CACHE_REDIS_ENABLED=false` y reinicia o recarga el servicio según el mecanismo aprobado.
2. Verifica que las lecturas vuelvan al origen y que liveness/readiness y Gateway estén saludables.
3. Verifica el estado de Redis y detiene la observación de carga. No borrar datos de otros servicios ni usar `FLUSHALL`/`FLUSHDB`.
4. Si hay claves residuales, el operador elimina solo el namespace aprobado y documentado, con revisión previa.
5. Confirma con el dueño del servicio que errores y latencias volvieron al nivel esperado, y registra hora, versión, responsable y evidencia redactada.

## 6. Criterio de cierre institucional

La prueba institucional solo se considera finalizada con: aprobación del responsable, evidencia de respaldo/rollback, matriz de resultados, comparación con umbral acordado y decisión firmada de activar, mantener apagado, corregir o retirar. La verificación local de `REDIS-RESULTADOS-A18.md` no sustituye ese cierre.

# Plan para cerrar A15–A18 de Redis y completar respaldos

## Objetivo y alcance

Completar la documentación, implementación pendiente y validación local de Redis partiendo de `integration/version-estable-deuna-pulido`. La rama ya contiene una implementación y pruebas previas; este plan cierra los pendientes que quedaron identificados, sin asumir que esos resultados históricos se repitieron en esta revisión.

El trabajo se mantiene primero en local con datos sintéticos y servicios aislados. No se habilita el caché en producción hasta cerrar los criterios de salida y revisar el resultado con el equipo.

## Decisiones y límites confirmados

- Los campos del caché están aprobados: producto público (`id`, `nombre`, `descripcion`, `precio`, `imagen`, `categoriaNombre`, `empresaNombre`, `empresaId`) y categoría (`id`, `nombre`, `descripcion`, `activo`, `fechaCreacion`, `fechaActualizacion`).
- No incluir inventario, cantidades, datos de clientes, credenciales ni información de pago en esos objetos cacheados.
- Mantener desactivado el caché en producción mientras falten pruebas o una decisión explícita sobre expiración, invalidación y ventana de datos obsoletos de R15.
- No depender de credenciales del servidor ni de la base institucional para las pruebas locales.
- Los respaldos reales y secretos no se versionan en GitHub. Los respaldos son manuales; no configurar cron ni timers.
- No modificar la tabla de particiones mientras siga sin resolverse el aviso GPT/PMBR y no exista acceso al panel VMware o una vía de recuperación de la VM.

## Secuencia de trabajo

### 1. Fijar la base y reconciliar la documentación

1. Confirmar que se trabaja en `integration/version-estable-deuna-pulido` y registrar el commit inicial.
2. Revisar los cambios locales/remotos y los documentos A15, A16, A18 y los resultados de pruebas para separar: implementado, probado anteriormente, pendiente y no verificado.
3. Actualizar la especificación A16 con los campos aprobados y comprobar que coincidan con los DTO reales. Añadir una revisión para impedir que campos de inventario o datos privados entren al caché.
4. Mantener la matriz R01–R16 con evidencia, responsable, estado y enlace a prueba o decisión; no marcar como cerrado algo que solo esté descrito.

**Verificación:** diff revisado; documento y DTO coinciden; no hay secretos ni datos reales en fixtures o reportes.

### 2. Cerrar R06: latencia, timeout y fallback de Redis

1. En un entorno local aislado, introducir latencia mayor al timeout Redis configurado (por ejemplo, 800 ms frente a un límite de 500 ms) usando Toxiproxy o un mecanismo equivalente.
2. Probar el endpoint HTTP de listado con caché habilitado, tanto con Redis disponible como con Redis lento/timeout.
3. Comprobar que el endpoint cae a la base de datos dentro del presupuesto de respuesta acordado, responde con datos correctos y no deja hilos/recursos bloqueados.
4. Comprobar logs/métricas del fallo sin imprimir credenciales y que la recuperación de Redis permita volver a usar el caché.
5. Probar además Redis detenido y conexión rechazada; registrar status HTTP, latencia y resultado funcional para cada caso.

**Verificación:** pruebas automatizadas reproducibles de timeout, Redis caído, fallback y recuperación; los límites observados quedan escritos en el informe.

### 3. Completar R12: fallo real de MySQL visto por HTTP

1. Con Redis habilitado en la red sintética, llenar el caché y probar lecturas hit/miss.
2. Detener o aislar MySQL en el entorno de prueba y llamar las rutas HTTP correspondientes, incluyendo una clave no cacheada.
3. Validar el comportamiento definido por requisitos ante error de base: no convertir fallos en respuestas exitosas ni devolver datos parciales o inventados; documentar por separado el caso de un hit válido ya cacheado.
4. Restaurar MySQL y verificar que las lecturas vuelven a funcionar y que no queda un estado de error pegado.

**Verificación:** evidencia HTTP antes, durante y después de la falla, con códigos y cuerpo sin datos personales.

### 4. Completar R14: matriz de autorización pasando por Gateway

1. Probar por Gateway rutas públicas y protegidas con token ausente, inválido, vencido y válido.
2. Probar administrador y usuarios de dos empresas/clientes sintéticos: cada usuario solo puede leer o modificar sus propios recursos; el administrador conserva el alcance acordado.
3. Incluir lecturas con hit y miss de Redis y confirmar que la caché no cruza tenants ni evita la autorización.
4. Cubrir rutas de producto/categoría y las rutas de ventas/inventario relacionadas; separar los hallazgos de seguridad existentes que no sean parte de Redis.
5. Confirmar que las rutas de administración/health de Actuator no quedan expuestas por Gateway.

**Verificación:** matriz de casos con ruta, actor, token, resultado esperado/real y evidencia. No usar mocks como sustituto de estas pruebas HTTP.

### 5. Cerrar R15: expiración, invalidación y concurrencia

1. Probar TTL real: hit antes del vencimiento, miss y recarga tras vencer.
2. Probar invalidación después de altas, cambios y eliminaciones que afecten producto o categoría; comprobar que el siguiente GET obtiene el dato actualizado.
3. Probar escrituras concurrentes con lecturas y generación/versionado de claves para detectar repoblación con datos antiguos.
4. Simular fallo de invalidación posterior a una escritura y definir/medir qué garantía ofrece el sistema.
5. No servir datos stale durante una ventana ampliada hasta que su duración y riesgo estén aprobados y exista una estrategia segura. Si no se puede garantizar consistencia, mantener caché desactivado para esa ruta.

**Verificación:** pruebas de TTL, invalidación, concurrencia y fallo; decisión documentada para cada mutación y política stale.

### 6. Repetir y cerrar A18: rendimiento y consumo de consultas

1. Ejecutar k6 contra la misma versión de datos sintéticos y topología en modo caché apagado y encendido.
2. Fijar duración, VUs/tasa, calentamiento, número de repeticiones y digest del script; hacer al menos tres corridas por modo.
3. Registrar p50/p95/p99, tasa de error, throughput y consultas MySQL por conexión o desde métricas aisladas. No atribuir `Com_select` global a k6 si puede incluir tráfico ajeno.
4. Incluir fallos de Redis y MySQL por separado y comprobar que no se comparen ejecuciones con condiciones distintas.
5. Publicar resultados como **locales y sintéticos**, con limitaciones; no extrapolarlos al servidor ni afirmar mejora si el p95 empeora.

**Verificación:** scripts reproducibles, parser que falla ante salida inválida y reporte que enlaza cada cifra con sus artefactos.

### 7. Regresión y trazabilidad final

1. Ejecutar el conjunto de pruebas de los servicios y las pruebas Redis/HTTP/autorización que cubren los criterios anteriores.
2. Corregir fallos antes de actualizar el reporte; guardar comando, commit, fecha y resultado.
3. Actualizar R01–R16 y A15–A18: cada requisito debe quedar implementado y probado, pendiente con razón, o explícitamente diferido.

**Verificación:** suite reproducible sin depender de servicios institucionales y documentación sin resultados presentados como actuales cuando no se repitieron.

### 8. Terminar la preparación de respaldos

1. Conservar los respaldos existentes fuera del repositorio; mantener permisos restrictivos y verificar SHA-256/lectura.
2. Repetir un dump manual de las cuatro bases antes de una ventana de producción y hacer una restauración aislada sobre MySQL compatible.
3. Documentar que el dump actual restaurado no cubre usuarios/grants globales; preparar, revisar y probar su recuperación sin guardar contraseñas en el repo.
4. Hacer ensayo de restauración de JARs, frontend, unidades systemd, configuración Nginx y material de Certbot desde las copias existentes; no basta con validar que el tar se pueda listar.
5. Mantener una copia fuera del servidor y del mismo disco. La carpeta de respaldos en el disco del servidor no protege frente a pérdida de esa VM/disco.
6. No crear una partición de respaldo en el servidor. El usuario decidió conservar copias en la laptop; la advertencia GPT/PMBR y la falta de consola VMware además hacen insegura cualquier escritura de tabla. No ejecutar `sfdisk`, `parted`, `growpart` ni comandos equivalentes.
7. No automatizar respaldos: dejar instructivo manual, ubicación, permisos, retención acordada y comando de verificación.

**Verificación:** acta local de restauración con alcance/limitaciones, huellas SHA-256 y ACL privada confirmadas.

### 9. Preparación del runtime del servidor (sin activar caché)

1. Elegir Redis nativo con systemd o Redis en contenedor; Docker no es requisito de Redis. El Compose actual del Gateway es el uso de Docker identificado en esta rama.
2. Si se adopta contenedor, decidir entre Docker y Podman según soporte operativo del servidor y probar networking/arranque en staging; no instalar Docker solo para satisfacer Redis.
3. Definir binding privado/localhost, ACL y secreto fuera de Git, límites de memoria/política de expulsión, timeout, health check, logs y reinicio systemd/Quadlet.
4. Preparar variables de ejecución con permisos mínimos y el secreto JWT compatible para servicios que validan tokens; verificar que Gateway solo publique el puerto previsto y que el proxy Nginx apunte correctamente.
5. Levantar Redis y Gateway primero con flags de caché apagados; verificar salud y rutas sin afectar datos productivos.

**Verificación:** procedimiento de instalación/reversión probado en staging, secretos fuera del repo y Redis inaccesible desde Internet.

### 10. Criterios para habilitación controlada

Solo proponer activar Redis después de: R06/R12/R14/R15 cerrados; pruebas de regresión aprobadas; comparación k6 revisada; respaldos restaurables; variables y secretos preparados; health check/rollback ensayados; y visto bueno del equipo responsable.

En la ventana: dejar el caché apagado al desplegar, validar Gateway, login, productos, categorías y flujos relacionados; activar primero de forma limitada, observar errores/latencias/consultas; desactivar el flag si hay inconsistencia, 5xx o regresión. Redis no debe ser fuente de verdad. No realizar cambios de partición durante esta ventana.

## Estado de respaldos verificado el 15 de noviembre de 2026

El dump de las cuatro bases se restauró en MySQL aislado y verificó 64 tablas. Las copias locales de JAR, frontend, systemd, Nginx, certificados y tabla de particiones se verificaron por SHA-256 y estructura; el dump del 15 de noviembre coincide con la huella recibida del servidor. Se decidió no crear una partición y conservar los respaldos en la laptop, fuera de GitHub. La reconsulta SSH posterior falló por DNS, así que no se confirmó ni retiró una copia temporal que pudiera seguir en el servidor. Antes de una ventana se hará un dump manual nuevo, se copiará y verificará en local y luego se retirará la copia temporal remota. El fixture de SQL trackeado por el repositorio no es un respaldo de producción.

## Criterio de cierre

El trabajo queda listo para revisión cuando las pruebas pendientes tengan evidencia reproducible, la matriz R01–R16 esté actualizada, las limitaciones del benchmark sean claras, los respaldos tengan restauración ensayada y el caché permanezca apagado hasta la aprobación de la ventana de producción.


## Avance y hallazgos verificados el 15 de noviembre de 2026

- Se hizo git pull --ff-only de integration/version-estable-deuna-pulido; quedó al día en bdb79ab799aaf82305bd1fd3a698410da9bf2636.
- En una red local sintética, la suite de Producto terminó 33/33 y la suite Redis/HTTP/autorización 49/49; el empaquetado Maven también pasó. Estas cifras no acreditan comportamiento en el servidor.
- El contrato de caché aprobado por el equipo ahora coincide con ProductoCatalogoPublicoDto y CategoriaResponseDto. La aprobación de campos no amplía por sí misma la visibilidad HTTP. La proyección de producto excluye inventarioCantidad.
- R06 sigue pendiente: no se inyectó una demora Redis superior a 500 ms. R12 requiere fallo real de MySQL observado por HTTP. R14 necesita una matriz completa de actores y permisos por Gateway. R15 tiene pruebas automatizadas de TTL/invalidez/carrera, pero la aceptación de su ventana de datos obsoletos aún debe quedar registrada.
- El Compose del Gateway pasó validación y /health respondió 200 en una instancia local temporal. El servidor sigue sin Docker/Podman y su NGINX aún no envía /api/ al Gateway. La configuración predeterminada de puerto 8080 puede chocar con Auth.
- El servidor reportó 1.7 GiB de memoria disponible de 5.5 GiB totales. No es una medición de capacidad para carga.
- Las copias locales suman 27 archivos y 1,328,027,567 bytes. Se recalculó SHA-256 y pasó la lectura de 12 archivos comprimidos/JAR; el dump reciente coincide con la huella recibida por SSH. La restauración aislada de base ya verificó 64 tablas. Falta ensayar extracción/validación de todos los artefactos y una recuperación Linux integral.
- Se reorganizó la preparación local en backups servidor tienda sigchos y programa tienda sigchos. Los residuos .tmp-jdk17 y el worktree Redis están bloqueados por Windows; se conservan intactos hasta que el proceso que los usa libere sus archivos.
- Se detuvieron seis contenedores locales identificados como parte de la prueba. En la comprobación final el motor Docker no respondió; no se eliminaron contenedores ni volúmenes.
- Se decidió conservar respaldos únicamente en la laptop. La copia local está verificada, pero la reconsulta SSH falló por DNS; no se confirmó ni borró la copia temporal remota.

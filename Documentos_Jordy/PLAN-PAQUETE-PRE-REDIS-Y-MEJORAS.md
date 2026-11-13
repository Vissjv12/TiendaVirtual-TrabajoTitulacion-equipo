# Plan del paquete propio antes de Redis

Fecha de revisión: 2026-11-11  
Rama local de referencia: `integration/estabilizacion-produccion`  
Rama documental revisada: `feature/a15-a18-redis-gateway`

## 1. Propósito y alcance

Este documento ordena el trabajo que podemos realizar sobre la base actual antes de activar Redis. Se consideran los hallazgos de las guías del sistema, el resumen del proyecto, las pruebas de Gateway, la revisión del servidor productivo y la rama `feature/a15-a18-redis-gateway`.

La rama A15–A18 revisada contiene principalmente evaluación, modelo de caché y seguridad. No incorpora todavía el starter de Redis en Productos, cambios funcionales de Spring, un servicio Redis obligatorio ni cambios de Docker. Por tanto, se utilizará como referencia de diseño, pero no se integrará Redis en esta etapa.

El objetivo de esta etapa es dejar la aplicación coherente, segura y medible para que la futura implementación de Redis sea una capa complementaria y reversible.

## 2. Alcance que asumimos como propio

Para evitar volver a modificar el trabajo de los compañeros, este plan considera como paquete propio:

- estabilización, infraestructura y pruebas de A01–A10;
- validación de Gateway y configuración de A14;
- preparación documental y criterios de A15–A18;
- pruebas de integración, regresión, rendimiento y despliegue;
- correcciones transversales que no cambien el contrato de los módulos asignados a otros integrantes.

No se deben duplicar ni reimplementar en este paquete A11–A13, A19–A21 o A24 si el cambio modifica los archivos que ya mantiene otro integrante. A23, precios públicos, es un requisito transversal: puede adelantarse, pero debe coordinarse con quien mantiene el frontend para no producir dos implementaciones sobre las mismas rutas.

## 3. Decisiones conocidas

1. Redis queda pospuesto como implementación funcional. No debe convertirse en requisito de arranque ni sustituir a MySQL.
2. Stripe no se utilizará. No se deben solicitar ni incorporar claves de Stripe; se debe retirar o aislar su código solo después de confirmar todas sus referencias.
3. Las claves de PayPhone, OAuth, JWT, Camunda y Redis que correspondan al ambiente real serán entregadas por los responsables. No se copian credenciales productivas al repositorio ni a esta documentación.
4. La producción funciona con servicios nativos administrados por `systemd` y Nginx; no se debe forzar una migración a Docker sin una decisión explícita del responsable de infraestructura.
5. El MySQL productivo es la fuente transaccional. No se ejecutan `DROP`, `FLUSHALL`, `FLUSHDB`, recreación de volúmenes ni cambios de semillas en producción como parte de estas tareas.

## 4. Hallazgos que guían el orden

### 4.1 Catálogo y precios

El backend ya dispone de consultas públicas de productos. El problema de precio oculto se encuentra principalmente en la experiencia del frontend: una vista o componente condiciona la información comercial al estado de autenticación. El precio público no debe depender de login; el login debe conservarse para carrito, checkout y operaciones privadas.

La respuesta pública tampoco debe exponer datos privados innecesarios de emprendedores o empresas. Antes de cambiar la interfaz se debe verificar el DTO entregado por el backend y mantener solo nombre comercial, imagen, categoría, descripción, precio y stock que el negocio haya decidido publicar.

### 4.2 Caracteres especiales

Los textos mostrados con secuencias como `Ã³` o `Ã±` no prueban por sí mismos que el sistema esté almacenando mal los datos: también pueden ser un problema de decodificación de PowerShell o del terminal al leer UTF-8. La corrección debe empezar por reproducirlo en navegador y revisar, en este orden:

1. bytes y `Content-Type` de la respuesta HTTP;
2. serialización JSON y encabezado `charset=UTF-8`;
3. decodificación de la aplicación Next.js;
4. conexión JDBC y configuración de codificación;
5. juego de caracteres y colación de las tablas MySQL;
6. representación del terminal utilizado para las pruebas.

No se deben regrabar masivamente archivos, semillas o tablas hasta identificar el punto exacto de la conversión incorrecta.

### 4.3 Seguridad y flujo de compra

Los hallazgos anteriores incluyen confianza excesiva en precio y costo enviados por el cliente, endpoints de ventas o movimientos que requieren revisión de autorización, documentación/actuator expuestos, puertos directos visibles, CORS distribuido, logs sensibles y flujo de pago simulado. Estos puntos son más importantes que añadir Redis y deben resolverse o quedar explícitamente aceptados antes del piloto.

### 4.4 Producción

En el servidor se observaron servicios activos de Eureka, Auth, Productos, Inventario, Ventas, MySQL y Nginx. El certificado administrado por Certbot tiene una discrepancia entre la fecha del certificado renovado y la fecha observada en el endpoint; antes de modificar Nginx se debe confirmar qué terminación TLS sirve realmente el dominio y coordinar una ventana de cambio.

También se documentaron `PasswordAuthentication yes`, `PermitRootLogin yes`, ausencia de Fail2ban y numerosos intentos fallidos de SSH. Son hallazgos operativos que requieren autorización del responsable del servidor; no deben cambiarse durante una corrección funcional local.

## 5. Orden de trabajo recomendado

### Paso P0 — Congelar la base y establecer línea base

**Objetivo:** que cada cambio posterior pueda compararse contra una versión conocida.

**Se puede hacer ahora:** sí, sin nuevas credenciales.

**Acciones:**

- registrar rama, commit, servicios, puertos, variables requeridas y estado de la base;
- ejecutar `git diff --check` y guardar el resultado;
- ejecutar pruebas existentes de A07 y A14;
- registrar cantidad de productos, inventario, categorías y respuestas públicas;
- guardar un reporte de tiempos sin Redis para compararlo después.

**Criterio de salida:** una línea base reproducible y sin cambios funcionales.

**Prompt de continuación:**

> Trabaja únicamente en P0. Revisa la rama y la documentación vigente, ejecuta las pruebas de seguridad A07 y Gateway A14, registra versiones, endpoints, conteos, tiempos y resultados sin modificar código, base de datos, Docker ni producción. Si una prueba falla, identifica primero si es un problema de ambiente o de código. Actualiza solo el registro documental.

### Paso P1 — Confirmar configuración y eliminar referencias obsoletas a Stripe

**Objetivo:** dejar claro que Stripe no forma parte del despliegue previsto, sin tocar PayPhone ni romper el flujo de compra.

**Se puede adelantar:** sí, en una rama aislada.

**Acciones:**

- localizar referencias de Stripe en backend, frontend, Docker, `.env.example`, documentación y scripts;
- clasificar cada referencia como código ejecutable, configuración, documentación o ejemplo;
- retirar solo la integración que ya no se utilizará, o marcarla como inactiva si se necesita conservar compatibilidad temporal;
- eliminar valores ficticios de documentación que puedan confundirse con secretos;
- verificar que el arranque no exija variables de Stripe si el flujo vigente no las usa.

**No hacer:** borrar código de pago sin revisar el flujo de ventas, modificar datos de producción o sustituir PayPhone por otra pasarela.

**Criterio de salida:** el sistema arranca sin Stripe, el flujo de pago vigente queda identificado y las pruebas de catálogo/ventas no se rompen.

**Prompt de continuación:**

> Ejecuta P1 sobre una rama propia. Busca todas las referencias a Stripe y clasifícalas antes de editar. Como Stripe no se utilizará, elimina o desactiva únicamente sus referencias verificadas, sin tocar PayPhone, Camunda, JWT, usuarios ni MySQL. Ejecuta compilación y pruebas del flujo afectado, revisa el diff y documenta cualquier referencia que deba conservarse por compatibilidad.

### Paso P2 — Diagnosticar y corregir caracteres especiales

**Objetivo:** resolver la causa real de la visualización incorrecta de `á`, `é`, `í`, `ó`, `ú`, `ñ` y caracteres comerciales.

**Se puede adelantar:** sí, sin credenciales productivas.

**Acciones:**

- reproducir con productos, categorías, empresas y ventas que contengan caracteres no ASCII;
- comparar la respuesta directa, la respuesta por Gateway y el texto renderizado en Next.js;
- inspeccionar encabezados HTTP y payload JSON;
- comprobar que archivos fuente estén en UTF-8 sin BOM inesperado ni doble conversión;
- comprobar JDBC/MySQL (`utf8mb4`) solo con consultas de lectura;
- añadir una prueba de regresión con `ñ`, tildes y símbolos monetarios;
- aplicar el cambio mínimo en el punto que realmente produzca la corrupción.

**Criterio de salida:** el mismo texto se conserva desde MySQL hasta API, Gateway y navegador, y existe una prueba que evita la regresión.

**Prompt de continuación:**

> Ejecuta P2 como diagnóstico primero. Reproduce caracteres españoles en API directa, Gateway y frontend. Inspecciona Content-Type, JSON, fuente Next.js, configuración JDBC y colación MySQL antes de cambiar nada. No reescribas semillas ni archivos en masa. Corrige solo la capa que demuestre la conversión incorrecta y añade una prueba de regresión.

### Paso P3 — Hacer públicos los precios del catálogo

**Objetivo:** permitir que una persona vea productos y precios sin iniciar sesión, manteniendo autenticados carrito, checkout y operaciones privadas.

**Dependencia:** no depende de Redis. Requiere coordinar con el responsable de frontend si mantiene los mismos componentes.

**Acciones:**

- identificar todos los recorridos públicos y duplicados de catálogo;
- retirar únicamente el guard visual que oculta precio;
- conservar redirección/login para carrito, compra y cuenta;
- verificar que el backend no acepte precio o costo de envío como autoridad del cliente;
- revisar que el DTO público no exponga PII ni datos de empresa innecesarios;
- probar usuario anónimo, cliente autenticado, emprendedor y administrador.

**Criterio de salida:** visitante anónimo ve catálogo, precio e información comercial autorizada; no puede crear compras ni alterar datos.

**Prompt de continuación:**

> Ejecuta P3 sin introducir Redis. Revisa todas las rutas públicas del catálogo y elimina solo la condición que oculta el precio al visitante. Conserva autenticación para carrito, checkout y operaciones privadas. Verifica el DTO público, no expongas PII y prueba anónimo, CLIENTE, EMP y ADMIN mediante navegador y API. Si el frontend pertenece a otro paquete, prepara el cambio como propuesta coordinada en lugar de duplicarlo.

### Paso P4 — Corregir la confianza en datos de precio, envío y compra

**Objetivo:** que el servidor calcule o valide los importes y que el cliente no pueda alterar el total enviando valores arbitrarios.

**Dependencia:** requiere coordinar A19/A20 y A24 cuando las rutas de compra se modifiquen.

**Acciones:**

- identificar dónde se reciben `precio`, subtotal, impuesto, costo de envío y total;
- recalcular en backend con datos actuales de MySQL;
- validar stock y empresa en el servidor;
- conservar trazabilidad de la orden y estado de pago;
- no integrar todavía una pasarela no autorizada ni guardar pagos en Redis.

**Criterio de salida:** modificar el payload desde el navegador no cambia el total autorizado ni permite comprar stock inexistente.

**Prompt de continuación:**

> Revisa P4 en backend y pruebas de integración. Rastrea precio, envío, subtotal, impuesto y total desde la petición hasta la venta. Haz que el servidor sea la autoridad, valida stock, empresa, usuario y estado de pago, y añade pruebas con payload manipulado. No cambies el proveedor de pago ni introduzcas Redis.

### Paso P5 — Endurecimiento aislado y preparación productiva

**Objetivo:** reducir exposición sin romper consumidores existentes.

**Se puede adelantar parcialmente:** sí, en local y con cambios separados.

**Acciones locales:**

- revisar exposición de Swagger y Actuator;
- reducir `show-sql` y logs sensibles;
- revisar roles inconsistentes y respuestas `401/403/500`;
- consolidar CORS solo cuando todos los consumidores estén definidos;
- preparar límites de rate limiting en Gateway.

**Acciones que requieren autorización productiva:** cerrar puertos directos, cambiar SSH, activar Fail2ban, recargar Nginx/certificados y cambiar secretos reales.

**Criterio de salida:** las medidas locales tienen pruebas y las acciones productivas están convertidas en una lista aprobable con rollback.

**Prompt de continuación:**

> Ejecuta P5 separando cambios locales de operaciones productivas. Revisa Actuator, Swagger, logs, roles, CORS y rate limiting sin romper el frontend. Para producción solo genera diagnóstico, comandos reversibles, respaldo y plan de rollback; no cierres puertos, cambies SSH, recargues Nginx ni rotes secretos sin autorización y ventana de mantenimiento.

### Paso P6 — Pruebas y medición sin Redis

**Objetivo:** medir el sistema actual para demostrar si Redis aporta una mejora real.

**Acciones:**

- medir latencia, errores, throughput y uso de memoria en catálogo;
- probar carga con catálogo público y consultas de inventario;
- comprobar que una falla de Inventario no convierte cantidades válidas en cero;
- conservar el resultado como línea base;
- repetir después del piloto con el mismo escenario.

**Criterio de salida:** existe una comparación cuantitativa sin Redis y no solo una percepción visual de rapidez.

**Prompt de continuación:**

> Ejecuta P6 usando el contrato actual, sin agregar Redis. Define un escenario reproducible de catálogo, registra p50/p95, errores, throughput, memoria y consistencia de stock. Usa datos no sensibles, conserva los resultados y documenta el comando exacto y el ambiente de ejecución.

### Paso P7 — Puerta de entrada para Redis

**Objetivo:** decidir si existe base suficiente para implementar A17.

Redis solo puede iniciar cuando se cumplan simultáneamente:

- contrato estable de catálogo y paginación;
- catálogo correcto con Redis apagado;
- respaldo y rollback MySQL verificados;
- límite de memoria del servidor conocido;
- claves, serialización, TTL e invalidación definidos;
- fallback a MySQL probado;
- métricas de hit, miss, latencia, memoria y errores definidas;
- propietario de Docker, `.env` y despliegue identificado;
- claves reales entregadas mediante un canal seguro, sin subirlas al repositorio.

**Criterio de salida:** aprobación explícita del diseño y una rama de implementación derivada de la versión funcional aprobada, no de una rama documental sin verificar.

**Prompt de continuación:**

> Ejecuta P7 como revisión de preparación, no como implementación. Compara la lista de condiciones A15–A18 con evidencia del repositorio, pruebas y servidor. Marca cada condición como cumplida, pendiente o bloqueada. No añadas dependencia Redis, no cambies MySQL y no crees claves reales. Entrega una recomendación de iniciar, posponer o corregir primero.

### Paso P8 — Regresión y entrega de etapa

**Objetivo:** cerrar la etapa con evidencia y una versión recuperable.

**Acciones:**

- ejecutar pruebas unitarias, integración, A07 y A14;
- probar catálogo público, caracteres, autenticación, autorización, inventario y compra;
- verificar `git diff --check`, compilación y configuración de arranque;
- actualizar el registro de cambios y la guía de levantamiento;
- crear commit y rama de entrega solo después de revisar el diff;
- no incluir `.env`, tokens, certificados privados, logs con datos personales ni archivos temporales.

**Criterio de salida:** informe de pruebas, documentación actualizada, commit identificable y rollback claro.

**Prompt de continuación:**

> Ejecuta P8 sobre la rama candidata. Revisa el diff completo, ejecuta las pruebas funcionales y de seguridad, valida configuración local y confirma que ningún secreto o archivo generado será versionado. Actualiza la documentación con cambios, resultados y pendientes. Solo después crea el commit de entrega y reporta su hash.

## 6. Trabajo que corresponde a otros paquetes

Estos puntos deben mantenerse coordinados, pero no deben duplicarse en nuestra rama:

- A11–A12: Gateway y rutas base, ya revisados en la integración existente.
- A13: migración definitiva del frontend al Gateway.
- A19–A20: contrato e implementación de paginación backend.
- A21: paginación frontend.
- A24: compra autenticada y flujo de checkout.
- A23: comparte objetivo con P3; acordar un único responsable del frontend.

La regla para evitar conflictos es que cada integrante modifique su módulo y publique una rama derivada de la base acordada. No se deben editar simultáneamente las mismas rutas, servicios, configuraciones o DTO. Toda modificación transversal debe acompañarse de una prueba de contrato y una nota de integración.

## 7. Variables, credenciales y producción

No necesitamos nuevas credenciales para P0–P3 ni para el diagnóstico de caracteres. Para P4 se necesitan datos de prueba y usuarios con roles, no credenciales productivas. Para P5 productivo se requiere autorización de infraestructura y acceso controlado. Para P7/A17 se necesitarán, cuando corresponda, host, puerto, política de autenticación y límites de Redis entregados por el responsable; no deben inventarse valores de producción.

Las claves de pago, OAuth, JWT, Camunda y Redis deben mantenerse fuera de Git, `.env.example`, capturas y documentos compartidos. El hecho de tener acceso root al servidor no autoriza por sí solo a rotar claves ni a modificar servicios: primero se respalda, se define ventana, se prueba rollback y se confirma el impacto.

## 8. Resultado recomendado

El siguiente paso práctico es P0 y, después, P1. En paralelo se puede ejecutar el diagnóstico P2. P3 puede implementarse antes de Redis porque solo afecta la presentación y el contrato público del catálogo, pero debe coordinarse con el responsable del frontend. P4 y P5 son requisitos de calidad y seguridad que deben quedar encaminados antes del piloto. Redis se mantiene como una etapa posterior, condicionada por P6 y P7.

No se recomienda comenzar por Redis mientras existan dudas sobre el contrato del catálogo, precios confiables, codificación, rollback, exposición productiva o propietario de la configuración.

## 9. Referencias internas

- `RESUMEN-ESTADO-PROYECTO-Y-PLAN-CONTINUACION.md`
- `REVISION-FLUJO-SEGURIDAD-Y-TAREAS.md`
- `REVISION-RAMA-A15-A18-REDIS-GATEWAY.md`
- `HALLAZGOS-REVISION-SERVIDOR-PRODUCCION.md`
- `GUIA-LEVANTAMIENTO-SISTEMA.md`
- `A15-EVALUACION-Y-DECISION.md`
- `A16-MODELO-DE-CACHE.md`
- `A16-SEGURIDAD-CONEXION-Y-PRUEBAS.md`

## 10. Registro de ejecución de P0

### 10.1 Estado de la base revisada

- Rama local: `integration/estabilizacion-produccion`.
- Commit actual: `aca30ab docs(revision): registrar hallazgos de flujo, seguridad, respaldo y tareas T00-T15`.
- `git diff --check`: sin errores de espacios o formato.
- No se modificó código, configuración, base de datos, Docker ni producción.
- Permanecen archivos no rastreados previamente en `Documentos_Jordy` y `.claude`; no se eliminaron ni se incluyeron automáticamente.

### 10.2 Disponibilidad al momento de la revisión

Los puertos locales `3000`, `8761`, `8081`, `8082`, `8083`, `8084` y `8090` no estaban escuchando durante la ejecución de P0. En consecuencia, las matrices A07 y A14 no se ejecutaron como pruebas funcionales: hacerlo habría registrado fallos de disponibilidad y no permitiría evaluar el código.

Antes de cerrar P0 se debe levantar el ambiente siguiendo `GUIA-LEVANTAMIENTO-SISTEMA.md` y repetir las pruebas con la configuración correcta. Para el Gateway local se debe conservar la configuración ya documentada con `GATEWAY_PORT=8090` y `NEXT_PUBLIC_GATEWAY_URL=http://127.0.0.1:8090`, cuando ese sea el puerto disponible del equipo.

### 10.3 Requisito manual para completar la matriz

Cuando los servicios estén activos, se necesitarán las contraseñas locales de `admin`, `sigcholac` y `cliente1` para que los scripts soliciten los valores de forma oculta. No se deben pegar contraseñas, tokens ni respuestas completas en la documentación.

Comandos previstos, desde la raíz del repositorio:

```powershell
.\Documentos_Jordy\pruebas-seguridad-a07.ps1
.\Documentos_Jordy\pruebas-gateway-a14.ps1 -Gateway http://127.0.0.1:8090
```

### 10.4 Resultado de la matriz A07

Con el ambiente levantado se ejecutó la matriz de seguridad A07. Los resultados recibidos fueron:

- Disponibilidad: 5 de 5 servicios respondieron `200`.
- Consistencia: 39 productos y 39 inventarios, 0 nulos y 0 diferencias.
- Lectura pública sin sesión: 3 de 3 casos respondieron `200`.
- Escrituras sin token: 8 de 8 casos respondieron `401`.
- Token falso en modificación: respondió `401`.
- Fallos observados en esta ejecución: 0.

La evidencia confirma que la lectura pública del catálogo y las cantidades funciona sin autenticación, mientras que las escrituras de Productos e Inventario permanecen protegidas. No se modificaron datos porque los casos de escritura usan identificadores inexistentes o peticiones de autorización negativa.

### 10.5 Pendiente para cerrar P0

La matriz completa A14 fue ejecutada mediante el Gateway `http://127.0.0.1:8090` y quedó aprobada:

- Salud y disponibilidad del Gateway: correcta.
- CORS y preflight: correctos, sin duplicación de `Access-Control-Allow-Origin`.
- Bloqueo de rutas internas y errores perimetrales: correcto.
- Enrutamiento público hacia los cuatro microservicios: correcto.
- Stock por Gateway: 39 productos, 39 inventarios y 0 diferencias.
- Escrituras sin token y token inválido: `401` correcto.
- Rol CLIENTE intentando operaciones restringidas: `403` correcto.
- Aislamiento de `sigcholac` frente a una empresa ajena: `403` correcto.
- Rol ADMIN y respuestas controladas: correctas.
- Resultado final: **43 éxitos, 0 fallos**.

P0 queda **cerrado funcionalmente con A07 y A14 aprobados**. La matriz demuestra que el Gateway y los servicios responden correctamente bajo ese ambiente de ejecución. Si el sistema falla al reiniciarse después de la prueba, debe registrarse como un problema independiente de arranque o configuración; no debe reinterpretarse como un fallo de la matriz A14 sin revisar el mensaje exacto.
### 10.7 Diagnóstico P2: codificación y navegador

La evidencia confirma que hay causas independientes:

- `roboto-v20-latin-ext_latin-700.woff` responde `404`; es un recurso visual faltante y no explica la corrupción de textos.
- Las advertencias de `label for`, `id` y `name` son de accesibilidad/formularios; deben corregirse aparte y no explican el problema de UTF-8.
- La semilla `scripts/mysql-init/07-seed-productos.sql` contiene `Cárnicos`, `Mortiño` y demás textos correctamente en UTF-8.
- Las tablas de Productos están definidas con `utf8mb4` y `utf8mb4_unicode_ci`.
- La respuesta HTTP directa de Productos y la respuesta por Gateway ya contienen `CÃÂ±o`/`CÃ¡rnicos` antes de renderizarse en Next.js.
- `msvc-producto` configura JDBC con `characterEncoding=utf8` y `characterSetResults=utf8`, en lugar de `utf8mb4`.

Diagnóstico P2: la corrupción se produce en la comunicación con MySQL o en su decodificación dentro de Productos, no en el Gateway ni exclusivamente en el navegador. El cambio candidato es armonizar la conexión JDBC con `utf8mb4`, reiniciar solo el servicio afectado y comprobar API directa, Gateway y frontend con una prueba de regresión. No se deben regrabar tablas ni semillas antes de esa prueba.

P2 sigue en estado **parcialmente corregido: el servicio inicia, pero la respuesta aún requiere diagnóstico de datos activos**.

La comprobación en el servidor productivo confirmó que la base `productos` conserva los caracteres correctamente. `Cárnicos` aparece con bytes UTF-8 válidos (`43 C3 A1...`) y los nombres con `ñ` también presentan secuencias UTF-8 correctas. No se debe modificar ni recargar la base productiva por este hallazgo. El siguiente contraste debe hacerse sobre el volumen Docker local, usando las mismas consultas `HEX(nombre)`.

### 10.8 Experimento P2 revertido

Se probó únicamente en local cambiar la URL JDBC de `msvc-producto`:

- `characterEncoding=utf8` pasó temporalmente a `characterEncoding=UTF-8`.
- `characterSetResults=utf8` pasó temporalmente a `characterSetResults=UTF-8`.

El primer intento con `utf8mb4` no fue válido como nombre de charset Java para Connector/J 9.1 y provocó `UnsupportedEncodingException`. Aunque `UTF-8` permitió iniciar el servicio, las respuestas locales todavía mostraban secuencias mojibake y no se demostró una mejora funcional.

El administrador confirmó que la versión productiva actual muestra correctamente los productos y caracteres especiales. Por seguridad y para mantener paridad con la versión estable, el cambio se **revirtió**: `application.properties` conserva nuevamente `characterEncoding=utf8` y `characterSetResults=utf8`.

La base productiva y el volumen Docker local fueron comprobados con `HEX(nombre)` y contienen bytes UTF-8 válidos para `Cárnicos`, `Mortiño` y otros textos. No se modificaron semillas, tablas, datos, contratos REST, frontend, Docker ni producción. P2 queda **pendiente de diagnóstico**, sin una corrección activa aprobada; no debe retomarse con cambios JDBC hasta reproducir el problema en la versión vigente y localizar la capa exacta de conversión.

### 10.9 Implementación P3: precios públicos del catálogo

Se implementó P3 únicamente en el frontend local, sin Redis ni cambios de backend:

- `app/(main)/products/page.tsx`: el listado y el modal de detalle muestran el precio a visitantes anónimos.
- `app/(main)/components/FeaturedProducts.tsx`: los productos destacados también muestran el precio sin sesión.
- El botón de un visitante anónimo abre el flujo de inicio de sesión; no agrega productos al carrito ni ejecuta una escritura.
- Los usuarios autenticados conservan sus acciones actuales: cliente agrega al carrito según stock y empleados gestionan productos.
- No se modificaron rutas públicas del backend, DTOs, precios almacenados, inventario, checkout, pagos ni Redis.

Validación realizada: `npx tsc --noEmit` finalizó correctamente. Falta validación manual en navegador para visitante anónimo, cliente autenticado, empleado y administrador, además de comprobar que carrito y checkout sigan exigiendo autenticación.

# Cierre de etapa P4–P7 y entrega de base de trabajo

## 1. Propósito

Este documento consolida los cambios realizados en la etapa propia P4–P7 y define el estado de la base entregada a los compañeros. Sirve como registro de cierre antes de continuar con nuevas tareas y evita confundir la preparación documental de Redis con una implementación de Redis.

## 2. Base entregada

- **Rama de trabajo:** `integration/base-pre-redis`
- **Rama de integración de origen:** `integration/estabilizacion-produccion`
- **Commit de entrega:** `4a99df4 docs(p7): evaluar preparacion para redis`
- **Repositorio remoto:** `Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo`
- **Estado:** rama publicada en GitHub y lista para derivar ramas de trabajo.

La rama contiene los commits de la etapa P4, P5, P6 y P7, además de los cambios previos integrados en la base A14. No contiene una implementación funcional de Redis ni nuevas credenciales productivas.

## 3. Cambios registrados por etapa

### P4 — Autoridad del servidor en el flujo de compra

Commit: `137bccc security(ventas): hacer autoritativos precios y configuracion de compra`

- El servidor valida y obtiene los datos comerciales relevantes desde el origen.
- Se redujo la confianza en valores enviados por el cliente.
- Se mantuvieron las reglas de autenticación, empresa, stock y compra.
- Se documentó que Redis no debe intervenir en el cálculo o validación transaccional de precio, stock, envío o pago.

Documento: [P4-AUDITORIA-AUTORIDAD-COMPRA.md](./P4-AUDITORIA-AUTORIDAD-COMPRA.md)

### P5 — Endurecimiento de errores y logs

Commit: `f1ac8eb security(p5): endurecer errores y reducir logs sensibles`

- Se desactivó `spring.jpa.show-sql` en los servicios revisados.
- Se redujo la exposición de mensajes internos de Feign, URLs, puertos y detalles de infraestructura.
- Los detalles técnicos se conservan para logs internos, mientras que el cliente recibe mensajes genéricos.
- Se mantuvieron los códigos HTTP de seguridad esperados: `401`, `403` y `503`.

Documento: [P5-AUDITORIA-ENDURECIMIENTO.md](./P5-AUDITORIA-ENDURECIMIENTO.md)

### P6 — Línea base sin Redis

Commit: `d3baf2b perf(p6): establecer linea base sin redis`

- Se midieron únicamente lecturas seguras a través del Gateway.
- Se verificaron 39 productos, 39 inventarios y 0 diferencias.
- Se ejecutaron 2.919 solicitudes con k6.
- Resultado: 0% de errores, p50 de 38,50 ms y p95 de 135,54 ms.
- Se registró el consumo de memoria observado en Docker.
- Se creó un script de carga exclusivamente de lectura para evitar ventas, pagos, facturas o escrituras durante la medición.

Documento: [P6-LINEA-BASE-SIN-REDIS.md](./P6-LINEA-BASE-SIN-REDIS.md)

### P7 — Puerta de entrada para Redis

Commit: `4a99df4 docs(p7): evaluar preparacion para redis`

- Se revisaron los documentos A15–A18 y la rama documental de Redis.
- Se confirmó que Redis todavía no debe implementarse ni desplegarse.
- Se definió como candidato futuro una única lectura pública y acotada del catálogo.
- Se excluyeron usuarios, JWT, ventas, pagos, carrito, checkout, inventario transaccional y datos privados.
- Se dejaron pendientes contrato de salida, claves, TTL, invalidación, fallback, métricas, aislamiento, ACL y parámetros de infraestructura.

Documento: [P7-PUERTA-ENTRADA-REDIS.md](./P7-PUERTA-ENTRADA-REDIS.md)

## 4. Validaciones realizadas

### Gateway y autorización

- A14: `43/43 OK`.
- Salud, CORS, rutas públicas y errores perimetrales verificados.
- Escrituras sin token: `401`.
- Roles insuficientes: `403`.
- Acceso por empresa validado.
- No se observaron URLs internas, puertos ni detalles Feign en las respuestas revisadas.

### Pruebas Java

Las dos pruebas de contexto se ejecutaron nuevamente con MySQL local activo y las credenciales correctas:

- `LoginApplicationTests`: `Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS`.
- `MsvcProductoApplicationTests`: `Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS`.

Los avisos de Hibernate, Caffeine y OpenJDK observados son advertencias de ejecución y no produjeron fallos.

### Frontend

La compilación completa del frontend se ejecutó como comprobación adicional. Presenta errores ESLint preexistentes en pantallas administrativas y checkout, principalmente por comillas sin escapar, además de advertencias de dependencias de `useEffect`.

Estos errores no fueron introducidos por P4–P7 ni corresponden a los archivos modificados en esta etapa. El frontend de producción no muestra actualmente el mensaje observado en el entorno local. Por decisión de alcance, no se modificó esta parte y queda como revisión independiente del equipo frontend.

### Archivos sensibles y cambios ajenos

- `.env` no está rastreado por Git.
- No se agregaron contraseñas, tokens, certificados privados ni claves de Redis.
- No se agregaron PDFs, documentos de servidor ni archivos temporales al commit de entrega.
- Permanecen archivos locales ajenos sin comprometer; no forman parte de `integration/base-pre-redis`.

## 5. Qué no se modificó

- No se agregó Redis a Maven, Docker Compose ni Spring.
- No se modificó MySQL productivo.
- No se rotaron credenciales.
- No se cambiaron certificados, Nginx, SSH o firewall del servidor.
- No se alteraron semillas ni volúmenes Docker.
- No se sustituyó PayPhone ni se incorporaron nuevas pasarelas.
- No se modificaron las reglas de negocio para permitir compras sin autenticación.

## 6. Pendientes posteriores

### Para el equipo antes de Redis

1. Aprobar el contrato público del catálogo.
2. Definir la proyección mínima y excluir campos privados.
3. Definir claves, versionado, filtros, orden, paginación y TTL.
4. Diseñar invalidación después de cada escritura relevante.
5. Implementar y probar fallback Redis → MySQL.
6. Crear fixtures sintéticos para dos empresas.
7. Medir aislamiento, stale reads, reinicio y timeout.
8. Definir métricas de hit, miss, latencia, errores y memoria.
9. Solicitar a infraestructura host, puerto, TLS, ACL, secreto, memoria y eviction.
10. Aprobar rollback y ventana de despliegue.

### Para el equipo frontend

- Revisar independientemente los errores ESLint de la compilación completa.
- Verificar los mensajes de caracteres especiales directamente en el entorno de producción y local.
- Confirmar que los cambios de precio público no se dupliquen con A13, A21 o A23.

### Para producción

- Usar la guía de levantamiento y despliegue existente.
- Respaldar antes de cualquier cambio.
- Confirmar el procedimiento de rollback con el responsable del servidor.
- No copiar `.env`, claves ni certificados privados al repositorio.
- No realizar cambios operativos sobre SSH, Nginx, firewall o Certbot sin autorización y ventana de mantenimiento.

## 7. Próximos pasos recomendados

1. Informar al equipo que `integration/base-pre-redis` es la rama base oficial de esta etapa.
2. Crear ramas individuales desde esa base para las tareas asignadas.
3. Mantener Redis fuera del arranque hasta cerrar P7/A17.
4. Tratar la corrección ESLint del frontend como tarea independiente, no como parte de P4–P7.
5. Para Redis, comenzar únicamente con la aprobación del contrato y las pruebas sintéticas.
6. Ejecutar P8 como cierre de regresión cuando los compañeros terminen sus cambios.

## 8. Estado final

La etapa P4–P7 queda documentada y entregada. La base es funcional sin Redis, conserva MySQL como fuente de verdad y tiene evidencia de Gateway, autorización, endurecimiento, rendimiento y preparación documental.

La siguiente implementación debe derivarse de `integration/base-pre-redis`, mantenerse aislada por tarea y acompañarse de pruebas y documentación. Redis permanece como trabajo posterior condicionado a la aprobación técnica y operativa.


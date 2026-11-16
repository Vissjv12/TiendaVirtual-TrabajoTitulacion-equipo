# Revisión de la rama `feature/a15-a18-redis-gateway`

**Fecha de revisión:** 11 de noviembre de 2026  
**Repositorio revisado:** `Vissjv12/TiendaVirtual-TrabajoTitulacion-equipo`  
**Rama revisada:** `feature/a15-a18-redis-gateway`  
**Commit evaluado:** `eea48657a534ad8ff79e26ed183774080c989ffe`  
**Base declarada por la rama:** `integration/estabilizacion-produccion@aca30ab648f968651d773d7bfb778f29c9002950`

## 1. Alcance de la revisión

Se revisó la rama remotamente sin descargarla ni integrarla en el repositorio local. Se analizaron:

- Historial reciente de commits.
- Archivos modificados después de `aca30ab`.
- Documentos A15 y A16.
- `pom.xml` de `msvc-producto`.
- `docker-compose.gateway.yml`.
- Relación con A11–A14 y el flujo previsto para A17–A18.

No se ejecutó código de la rama ni se realizaron cambios en el repositorio local.

## 2. Resultado ejecutivo

La rama **no implementa Redis**. Es una rama de diseño y preparación documental.

Los commits posteriores a la base revisada son:

| Commit | Contenido | Evaluación |
|---|---|---|
| `830ecb4d85fd8df13911a37b0ea95029d5ef845b` | Actualización de la decisión A15 | Solo documentación |
| `251bb5615e488b8b3f321e727b09fafb72dc1f4e` | Actualización del modelo A16 | Solo documentación |
| `eea48657a534ad8ff79e26ed183774080c989ffe` | Ajustes de seguridad y pruebas A16 | Solo documentación |

No se identificaron cambios posteriores a `aca30ab` en:

- Código Java.
- Dependencias Maven.
- `application.properties` o configuración Spring.
- Variables `.env`.
- Docker Compose.
- Nginx o Gateway.
- Esquemas SQL.
- Frontend.
- Pruebas automatizadas de Redis.

## 3. Decisión técnica documentada

La rama propone, pero no activa, un piloto Redis con patrón **Cache-Aside** para una lectura pública y acotada del catálogo.

La decisión documentada es técnicamente prudente:

- MySQL permanece como fuente de verdad.
- Redis funcionaría como acelerador opcional de lecturas.
- El fallback debe continuar consultando MySQL si Redis no está disponible.
- No se propone almacenar tablas completas en Redis.
- No se propone utilizar Redis para sesiones, JWT, ventas, pagos, administración o inventario.
- El Gateway Nginx seguiría enroutando; Redis viviría dentro de `msvc-producto`.

La rama también reconoce que la respuesta actual de `/api/productos/listado` combina datos de Producto con `inventarioCantidad`, por lo que no recomienda cachear el DTO completo.

## 4. Aspectos correctos y aprovechables

### 4.1 Separación entre diseño e implementación

Los documentos dejan explícito que A15/A16 no deben modificar código, Docker, variables de entorno ni la base de datos. Esto evita activar Redis sin contrato, pruebas ni autorización.

### 4.2 Protección de datos

Se excluyen correctamente del piloto:

- Usuarios.
- Sesiones y JWT.
- Ventas.
- Pagos.
- Administración.
- Datos personales.
- Inventario y stock, hasta definir su frescura y autorización.

También se advierte que `EmpresaResponseDto` contiene RUC, teléfono, correo y dirección, por lo que no debe reutilizarse completo como proyección pública.

### 4.3 Manejo de consistencia

La documentación identifica correctamente dos riesgos:

- Una escritura en MySQL puede confirmarse y no lograr invalidar Redis.
- Una lectura iniciada antes de una escritura puede guardar después un valor antiguo.

El diseño propone TTL y generaciones de clave, pero no presenta esto como garantía suficiente. Exige probar y aceptar la ventana real de obsolescencia en A17.

### 4.4 Separación del stock

La propuesta de no cachear inicialmente `inventarioCantidad` es adecuada. El stock tiene impacto comercial y debe mantenerse consultable desde Inventario o contar con un contrato de frescura e invalidación aprobado.

## 5. Riesgos y puntos que deben corregirse o confirmarse

### 5.1 El nombre de la rama puede inducir a error

`feature/a15-a18-redis-gateway` parece indicar que Redis y Gateway ya fueron implementados, pero el contenido posterior a `aca30ab` es documental.

Recomendación: tratarla como rama de diseño, o cambiar posteriormente su nombre/documentación para diferenciarla de una rama de implementación A17.

### 5.2 A17 no debe salir automáticamente de esta rama

La propia documentación indica que A17 debe abrirse desde el release desplegado después de completar la estabilización y la línea base.

Antes de crear A17 se debe confirmar que `integration/estabilizacion-produccion@aca30ab` representa exactamente el release aprobado y desplegado. Si producción tiene otro commit, se debe usar el commit real de producción como base.

### 5.3 El candidato actual exige contrato nuevo o una separación de respuesta

`GET /api/productos/listado` actualmente incluye datos de Inventario y campos de empresa. Para cachear de forma segura será necesario:

1. Definir una proyección pública mínima.
2. Confirmar si el precio será público mediante A23/T12.
3. Excluir campos internos como `emprendedorId` y `empresaId` cuando no sean necesarios.
4. Decidir si el stock se consulta siempre en vivo.
5. Verificar que el frontend no dependa de campos que se retiren del DTO.

No se debe agregar Redis antes de cerrar estas decisiones.

### 5.4 Falta de implementación y pruebas ejecutables

La rama no permite comprobar todavía:

- Compatibilidad de la dependencia Redis con Spring Boot.
- Conexión autenticada o mediante ACL.
- Comportamiento cuando Redis está caído.
- TTL real.
- Invalidación después de escrituras.
- Carreras entre lectura y escritura.
- Aislamiento entre empresas.
- Ganancia real de latencia.
- Consumo de memoria.

Por tanto, los resultados documentales no deben presentarse como una integración funcional terminada.

### 5.5 Healthcheck insuficiente para la futura arquitectura

El healthcheck actual del Gateway comprueba un endpoint estático de Nginx. No demuestra la disponibilidad de Producto, MySQL ni Redis.

Cuando Redis se implemente, se debe decidir si habrá un healthcheck separado para el microservicio o una comprobación controlada de dependencia. No se debe hacer que una caída opcional de Redis vuelva indisponible todo el catálogo si el fallback a MySQL sigue funcionando.

### 5.6 Seguridad de la conexión Redis pendiente

Antes de implementar deben definirse y aprobarse:

- Host y puerto por ambiente.
- Contraseña o ACL.
- Fuente segura de secretos.
- TLS, si aplica según la red de despliegue.
- Límites de conexión y timeouts.
- Política de memoria y eviction.
- Restricción de exposición de Redis a redes internas.
- Logs sin credenciales ni datos cacheados sensibles.

No se deben inventar valores productivos ni incluir secretos en Git.

## 6. Recomendación sobre el siguiente paso

No integrar esta rama directamente como si fuera una implementación Redis.

El siguiente paso recomendado es usarla como referencia documental y completar, antes de A17:

1. Confirmación del release real desplegado.
2. Línea base de latencia y volumen de lecturas sin Redis.
3. Cierre de las tareas de seguridad y catálogo indicadas por D-06.
4. Definición del DTO público y de la visibilidad del precio.
5. Decisión explícita sobre stock en vivo.
6. Aprobación del contrato de claves, TTL e invalidación.
7. Definición de secretos, ACL, red y límites operativos.
8. Fixtures sintéticos para pruebas de varias empresas.
9. Procedimiento de fallback y rollback.
10. Creación de una rama A17 desde el release aprobado.

## 7. Criterio para aceptar A17

A17 solo debería considerarse lista cuando demuestre, mediante pruebas reproducibles:

- Hit y miss con el mismo contrato HTTP.
- Fallback correcto cuando Redis no está disponible.
- No exposición de datos de otra empresa.
- No cacheo de JWT, sesiones, pagos o datos personales.
- Invalidación posterior a cambios de producto.
- TTL y obsolescencia máxima medidos.
- No afectación del checkout, precio final ni stock transaccional.
- Mejora medible frente a la línea base.
- Logs, métricas y documentación suficientes para operar y revertir.

## 8. Conclusión

La rama es útil como diseño y está alineada con el principio de introducir Redis de forma gradual y reversible. No obstante, no debe integrarse todavía como cambio funcional ni desplegarse.

La principal decisión pendiente no es “cómo levantar Redis”, sino **qué lectura pública se puede cachear sin exponer datos, romper el contrato actual ni presentar stock o precio obsoletos**. Esa decisión debe cerrarse antes de escribir código.


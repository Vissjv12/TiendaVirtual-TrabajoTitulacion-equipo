# Revisión de flujo, seguridad, respaldo y tareas previas a producción y Redis

- **Fecha:** 2026-11-10
- **Base revisada:** `feature/a15-a18-redis-docs-local` (`411ab5c`). En código es igual al cierre de A-14 (`1308429`).
- **Alcance:** revisión del código fuente y de la documentación. Todavía **no** se revisó el servidor desplegado. Cada hallazgo marcado *(verificar en servidor)* puede variar si producción corre otra versión.
- **Este documento no contiene secretos.** Los valores reales de las credenciales se guardan fuera del repositorio.

---

## 1. Decisiones registradas

| # | Decisión | Origen | Efecto |
|---|---|---|---|
| D-01 | **Stripe no se usará.** El único pago en línea es PayPhone; se mantienen Deuna QR y transferencia. | Equipo | Retirar Stripe del frontend, del backend, de las variables y de la documentación (T06) |
| D-02 | Los precios del catálogo deben verse **sin iniciar sesión**. El login solo se exige para agregar al carrito y para el checkout. | Observación del equipo; plan V2 §6.1 (O-16) | T12 (A-23) |
| D-03 | **El administrador no debe modificar productos.** Cada empresa o emprendedor administra los suyos por seguridad y privacidad. | Líder del equipo (*pendiente de confirmar el alcance exacto*, ver T13) | T13 |
| D-04 | Las credenciales de PayPhone en texto plano se registran para un **cambio futuro** (cifrado). No bloquean el despliegue, pero sí lo bloquea la **exposición pública** del token (ver H-01). | Equipo | T02 (ahora) y T14 (después) |
| D-05 | No se despliega nada sin un **respaldo verificado** del servidor real. | Equipo | T01 |
| D-06 | Redis se trabaja en una rama nueva **después** de estabilizar y desplegar las correcciones de este documento. | Equipo | A-17 en adelante |

---

## 2. Hallazgos de seguridad

La severidad se mide por el impacto si el servicio es alcanzable desde internet.

| ID | Severidad | Hallazgo | Evidencia en el código |
|---|---|---|---|
| H-01 | **Crítica** | Cualquiera, sin sesión, puede obtener el **token y el App ID de PayPhone** de cualquier emprendedor recorriendo `GET /api/emprendedor/configuracion-pagos/payphone/{empresaId}` con 1, 2, 3… | `demo/.../ConfiguracionPagosController.java:148` devuelve `PayphoneDto{payphoneAppId, payphoneToken}`; `SecurityConfig` aplica `permitAll` en la línea 48; el gateway lo enruta |
| H-02 | **Crítica** | **El precio y el costo de envío los envía el cliente.** Se puede crear una venta de cualquier producto a $0.01 y sin costo de envío. | `VentaItemRequestDto.precioUnitario` y `VentaRequestDto.costoEnvio`; `VentaApplicationService.java:65` y `VentaMapper.java:60` usan esos valores sin recalcularlos |
| H-03 | **Crítica** | **El frontend marca la venta como completada** con `PUT /api/ventas/{id}/completar`, que es público. También son públicos `POST /api/pagos/{id}/aprobar` y `/rechazar`. Cualquiera puede aprobar un pago que no hizo. | `cartService.ts:541`, `ventasService.ts:187`, `pagoService.ts:68`; `msvc-ventas` no tiene Spring Security |
| H-04 | **Crítica** | `msvc-ventas` **no tiene autenticación**. Ventas, clientes (nombre, email, teléfono, dirección), pagos, comprobantes, logística y BPM quedan abiertos, y se pueden listar o borrar sin token. | Sin dependencia de seguridad; `WebConfig` con `allowedOrigins("*")` |
| H-05 | **Crítica** *(verificar en servidor)* | El **API REST de Camunda** (`/engine-rest`) y el webapp (`/camunda`) quedan accesibles en el puerto 8083. Si el puerto está abierto, permiten iniciar o eliminar procesos y leer variables. El webapp usa `admin/admin`. | `msvc-ventas/pom.xml:72-78` (`starter-rest` y `starter-webapp`) |
| H-06 | Alta *(verificar en servidor)* | **Puertos internos expuestos:** 3306 (MySQL con `admin/admin` probable), 8761 (Eureka), 8081–8084 y 8083. Un atacante puede saltarse el gateway y llegar directo a los servicios. | `docker-compose*.yml` publican `0.0.0.0:puerto`; A-14 dejó este punto pendiente |
| H-07 | Alta | Cualquier usuario autenticado puede crear, editar o eliminar **categorías y empresas**. | `msvc-producto/SecurityConfig`: solo protege `/api/productos`; termina en `anyRequest().permitAll()` |
| H-08 | Alta | `salida-lote` y `PUT /inventarios/producto/{id}/stock` solo exigen **estar autenticado**: un cliente puede descontar o alterar el stock de cualquier empresa. | `msvc-inventario/SecurityConfig:47-48` |
| H-09 | Media | `GET /api/movimientos/**` es público y expone la operación interna del inventario. | `msvc-inventario/SecurityConfig:45` |
| H-10 | Media | **Swagger y `/api-docs` son públicos** en los cuatro servicios, lo que entrega un mapa de la API a un atacante. El gateway no los enruta, pero quedan expuestos si los puertos están abiertos. | `permitAll` para `/swagger-ui/**` y `/api-docs/**` |
| H-11 | Media | Sin **rate limiting** en login, registro y recuperación de contraseña. Permite fuerza bruta y abuso del correo. | `gateway/nginx.conf` define `@error_429` pero no hay `limit_req` |
| H-12 | Media | **Logs sensibles:** el frontend registra parte del token de PayPhone y el backend tiene `spring.jpa.show-sql=true` en producción. | `route.ts:99` (primeros 10 caracteres del token), `PayphoneForm.tsx:57` |
| H-13 | Media | **Pago simulado** en el código de producción (`procesarPagoSimulado`, O-07). | `services/pagoService.ts:148` |
| H-14 | Media | `@CrossOrigin(originPatterns="*")` en casi todos los controladores. Solo queda neutralizado si nadie llega a los servicios sin pasar por el gateway. | Auth, Productos, Inventario y Ventas |
| H-15 | Baja | `GET /api/productos/{id}` devuelve la entidad `Empresa` anidada (RUC, email, teléfono, dirección) a anónimos. Hay que decidir qué datos de la empresa son públicos. | `Producto.empresa`; ver A15 |
| H-16 | Baja | La estadística `emprendedores` cuenta `ROLE_EMPRENDEDOR`, pero el rol real es `ROLE_EMP`, así que siempre da 0. | `UsuarioServiceImpl.java:149` |
| H-17 | Registrar para el futuro | Token y App ID de PayPhone en **texto plano** en `auth_service.configuracion_metodos_pago` (O-03). *Verificar en el servidor.* | Esquema `payphone_token TEXT` |

> **Nota sobre H-01:** la Cajita de PayPhone se inicializa en el navegador con el token, así que el comprador termina viendo el token del emprendedor; es una limitación del diseño de PayPhone. Lo que sí se debe corregir es que cualquiera pueda **listar los tokens de todos los emprendedores sin sesión ni compra**. La mitigación consiste en entregar la configuración solo al cliente autenticado que es dueño de una venta pendiente de esa empresa y en hacer la confirmación del pago en el servidor. Conviene preguntar a PayPhone si existe un flujo con un token de un solo uso.

---

## 3. Observaciones del flujo del sistema

### F-01 No existe forma de vincular un emprendedor a una empresa

- La relación usuario–empresa vive en `auth_service.emprendedores` (`usuario_id`, `empresa_id`). **Ningún código inserta en esa tabla**: no hay `emprendedorRepository.save` ni un endpoint para hacerlo. Solo la llenan las semillas SQL.
- El administrador puede crear un usuario con `ROLE_EMP` (`POST /api/usuarios` con `roles`), pero queda **sin empresa**. Su JWT no lleva `empresaId` y A-03 le rechaza la creación de productos (`403`).
- Las empresas se crean en otra base (`productos.empresas`, `POST /api/empresas`) sin relación con ningún usuario.
- `findByEmpresaId` devuelve un `Optional`. El código supone **un emprendedor por empresa**; hay que decidir si una empresa puede tener varios usuarios.
- **Conclusión:** en el sistema actual, un emprendedor nuevo no puede operar sin intervención manual en la base. Se resuelve en T11.

### F-02 Para ver los precios hay que iniciar sesión

- El backend **ya expone el precio de forma pública** (`GET /api/productos/listado` es `permitAll`). Ocultarlo es solo una decisión del frontend: los textos "Inicia sesión para ver precios" están en `FeaturedProducts.tsx` y `products/page.tsx`, en los grupos `(landing)` y `(main)`, que están duplicados (O-14).
- Ocultarlo en el frontend no protege nada, porque cualquiera ve el precio en la red. Solo empeora la experiencia y reduce las ventas.
- El modelo `Producto` no tiene costo ni margen, así que hacer público el precio no expone datos privados. Sí hay que revisar H-15.
- Se resuelve en T12 (A-23).

### F-03 El administrador puede modificar productos de cualquier empresa

- `ProductoAuthorizationService.canManageCompany` y `canManageProduct` devuelven `true` para `ROLE_ADMIN`.
- Existen las pantallas `administrador/productos` y `administrador/productos/[id]/editar`.
- Según la líder, el administrador **no** debería editar productos. Opciones que debe elegir el equipo antes de T13:
  - **(a)** Administrador solo lectura sobre productos e inventario.
  - **(b)** Solo lectura más **activar o desactivar** un producto por moderación (contenido indebido), con registro de quién y cuándo. *Opción recomendada:* protege la privacidad comercial y deja al municipio una herramienta de moderación.
  - **(c)** Sin acceso alguno a productos.
- Hay que aplicar el mismo criterio a **inventario** (hoy el administrador puede ajustar el stock) y a las **ventas y la configuración de pagos** de las empresas.

### F-04 Otras observaciones del flujo

- La venta confía en el cliente para el precio, el envío y el estado final (H-02 y H-03). Un pago con PayPhone debe completarse **únicamente** después de que el servidor confirme la transacción con PayPhone.
- El carrito puede mezclar productos de varias empresas. Hay que confirmar cómo se dividen las ventas y los pagos por emprendedor, porque cada uno tiene su propio PayPhone.
- Las páginas del grupo `(landing)` y del grupo `(main)` están duplicadas (O-14). Cada corrección del frontend debe aplicarse en ambas o consolidarse primero.

---

## 4. Respaldo: requisitos mínimos

El respaldo es condición previa a **cualquier** cambio en el servidor.

1. **Qué respaldar:**
   - Las 4 bases (`auth_service`, `productos`, `inventario`, `ventas`, con las tablas de Camunda).
   - Las facturas PDF (`facturas/`).
   - Los QR de Deuna subidos (`/uploads/qr-deuna`).
   - El `.env` y la configuración en ejecución (compose, nginx, unidades systemd).
   - Las imágenes o JARs desplegados actualmente.
2. **Cómo:** `mysqldump --single-transaction --routines --triggers --databases ...`, comprimido, con suma SHA-256, nombre con fecha y hora y permisos `600`.
3. **Dónde:** en el servidor, en un directorio fuera del proyecto, y **una copia fuera del servidor** (equipo autorizado o almacenamiento institucional).
4. **Cuándo:** antes de cada despliegue (manual) y todos los días por `cron`, con retención (por ejemplo 7 diarios y 4 semanales).
5. **Verificación:** restaurar el respaldo en `docker-compose.test.yml` (MySQL 3307) y comparar los conteos por tabla. Un respaldo que no se probó no cuenta como respaldo.
6. **Prohibido en producción:**
   - `scripts/restore-db.ps1` con `backup_pre_migracion.sql`, porque hace `DROP DATABASE` y carga las semillas.
   - `db-update-passwords-emergency.sql`, porque cambia la contraseña de todos los usuarios.
   - `docker compose down -v`.
   - `FLUSHALL` sobre Redis compartido.

---

## 5. Orden de trabajo y ramas

```
main (aa50092, original)
 └─ integration/estabilizacion-produccion   ← crear desde 411ab5c y publicar
     ├─ T01 feature/t01-respaldo-produccion
     ├─ T02 fix/t02-payphone-config-privada
     ├─ T03 fix/t03-precios-envio-servidor
     ├─ T04 feature/t04-seguridad-ventas            (A-24, depende de T03)
     ├─ T05 fix/t05-confirmacion-pago-servidor      (depende de T02 y T04)
     ├─ T06 chore/t06-retirar-stripe-y-simulado
     ├─ T07 fix/t07-autorizacion-producto-inventario
     ├─ T08 fix/t08-superficie-y-logs
     ├─ T09 feature/t09-gateway-produccion-puertos
     ├─ T10 feature/t10-despliegue-docker-produccion
     ├─ T11 feature/t11-vincular-emprendedor-empresa
     ├─ T12 feature/t12-precios-publicos             (A-23)
     └─ T13 feature/t13-admin-sin-edicion-productos  (requiere decisión F-03)
 → release/1.0-produccion → despliegue → tag v1.0.0
 → después: T14 cifrado PayPhone · T15 rotación (A-09) · A-17 Redis (rama nueva desde el release)
```

**Reglas:**
- Cada rama sale de `integration/estabilizacion-produccion` y contiene **una sola tarea**.
- Antes de integrar una rama: ejecutar sus pruebas y repetir `pruebas-gateway-a14.ps1`.
- Si la tarea modifica la base de datos, debe incluir su script de migración y uno de reversión.

| Tarea | Requiere el servidor | Prioridad | Antes del primer despliegue |
|---|---|---|---|
| T00 Reconocimiento del servidor | Sí (solo lectura) | Bloqueante | Sí |
| T01 Respaldo | Sí | Bloqueante | Sí |
| T02 a T05 | No | Crítica | Sí |
| T06 a T09 | No | Alta | Sí |
| T10 | No (se ensaya en local) | Bloqueante para desplegar | Sí |
| T11 a T13 | No | Alta (flujo de negocio) | Recomendado |
| T14, T15 | Sí | Media | No |

---

## 6. Tareas en formato de prompt

> Cada prompt se puede pegar tal cual para iniciar la tarea en su rama. Todos comparten estas **reglas comunes**:
> - No modificar el volumen de MySQL ni ejecutar `down -v`.
> - No imprimir ni versionar secretos.
> - No mezclar cambios de otras tareas.
> - Agregar pruebas automatizadas.
> - Actualizar `pruebas-gateway-a14.ps1` si cambian los códigos HTTP esperados.
> - Registrar la evidencia en `Documentos_Jordy/` sin valores sensibles.

### T00: Reconocimiento del servidor de producción (sin rama; solo lectura)

> Con el acceso SSH entregado al equipo, ejecutar en el servidor de producción **solo comandos de lectura** para documentar cómo corre hoy el sistema. Registrar:
> - Sistema operativo, RAM y disco libres, CPU, estado de SELinux y reglas de `firewalld`.
> - Contenedores, imágenes, volúmenes y redes de Docker; procesos `java`, `node`, `nginx` y `mysql`, con su memoria; servicios systemd, pm2 y crontab.
> - Puertos en escucha (`ss -tlnp`) y cuáles responden **desde fuera** del servidor (probar desde otro equipo: 3306, 8761, 8081–8084, `8083/engine-rest`, `8083/camunda`, `/swagger-ui.html`).
> - Ruta del proyecto, rama y commit desplegados, y si tiene cambios locales.
> - Dominio, proxy y certificados.
> - Nombre de las variables de entorno en uso, **sin sus valores**. Comparar los valores con los conocidos (`admin/admin`, el JWT de `main`, Camunda `admin`) solo para marcarlos como "coincide" o "difiere".
> - Si `configuracion_metodos_pago.payphone_token` está en texto plano. Contar las filas con `LENGTH`, sin mostrar valores.
>
> **No reiniciar ni modificar nada.** Guardar los valores secretos únicamente en el gestor de contraseñas del equipo. Entregable: `documentos-trabajo/RECONOCIMIENTO-SERVIDOR.md`, que no se versiona, con un resumen sin secretos para el equipo. Actualizar los hallazgos marcados *(verificar en servidor)* en este documento.

### T01: Respaldo verificable de producción · `feature/t01-respaldo-produccion`

> Crear `scripts/prod/backup.sh` para Linux (AlmaLinux):
> - Toma `mysqldump --single-transaction --routines --triggers --databases auth_service productos inventario ventas` desde el contenedor o la instancia MySQL configurada. Lee las credenciales del `.env` del servidor o de `~/.my.cnf`, sin pasarlas en la línea de comandos visible.
> - Comprime el volcado; incluye las facturas PDF, los QR de Deuna y una copia del `.env` con `chmod 600`.
> - Genera la suma SHA-256 y aplica la retención (7 diarios y 4 semanales).
> - Falla con un código distinto de 0 y un mensaje claro si algún paso falla.
>
> Además:
> - Crear `scripts/prod/restore-verify.sh`, que restaura un respaldo **solo** en el contenedor de pruebas (`mysql-feria-digital-test`, puerto 3307) y compara los conteos por tabla contra el respaldo.
> - Agregar una protección a `scripts/restore-db.ps1` para que se niegue a ejecutarse si el contenedor destino no termina en `-test`, salvo con una bandera explícita `-IUnderstandThisDropsProduction`.
> - Documentar en `GUIA-RESPALDO-PRODUCCION.md`: la entrada de cron, la copia fuera del servidor y el procedimiento de restauración real (manual, con aprobación).
>
> Pruebas:
> - Ejecutar el respaldo y la restauración contra `docker-compose.test.yml` en local.
> - Comprobar que la protección bloquea el contenedor `mysql-feria-digital`.

### T02: La configuración de PayPhone deja de ser pública · `fix/t02-payphone-config-privada`

> Corregir H-01:
> - Eliminar `permitAll` de `/api/emprendedor/configuracion-pagos/payphone/*`.
> - Crear un flujo en el que el frontend obtiene la configuración de la Cajita **solo** si un cliente autenticado es dueño de una venta en estado pendiente de esa empresa. Por ejemplo, `GET /api/ventas/{ventaId}/payphone-config` en Ventas, que valida la propiedad y pide la configuración a Auth por una llamada interna autenticada. Se puede usar un endpoint interno de Auth no enrutado por el gateway o protegido con un rol de servicio.
> - La ruta `app/api/payphone/confirm` debe obtener el token por esa vía interna, nunca desde un endpoint público.
> - `GET /payphone/{empresaId}` sin token debe responder `401`, y un cliente que no es dueño de la venta debe recibir `403`.
> - Mantener públicos los datos bancarios y el QR de Deuna solo si el negocio lo requiere. Documentar la decisión y devolver únicamente los campos necesarios para pagar.
> - Bloquear en el gateway la ruta pública anterior.
>
> Pruebas: MockMvc para 401, 403 y 200, y actualizar la matriz A-14.

### T03: Precio y envío calculados en el servidor · `fix/t03-precios-envio-servidor`

> Corregir H-02 en `msvc-ventas`:
> - Al crear una venta, ignorar `precioUnitario`, `nombreProducto` y `costoEnvio` del cliente.
> - Obtener el precio vigente y la empresa de cada producto desde Productos (Feign, `ProductoClient`) y el costo de envío desde Auth (`/costo-envio/{empresaId}`).
> - Rechazar con `400` un producto inactivo, inexistente, con cantidad menor o igual a 0 o con stock insuficiente.
> - Validar que todos los ítems pertenecen a la `empresaId` declarada; si el carrito mezcla empresas, definir la división de la venta y documentarla.
> - Mantener los campos en el DTO por compatibilidad, pero sin usarlos. Registrar un aviso si el precio del cliente difiere del real.
>
> Pruebas unitarias: precio manipulado, envío negativo o cero, producto inactivo, empresa mezclada y Productos caído (debe rechazar la venta, no usar el precio del cliente).

### T04: Spring Security en Ventas (A-24) · `feature/t04-seguridad-ventas`

> Agregar a `msvc-ventas` un Resource Server JWT con el mismo esquema que Productos e Inventario (`JWT_SECRET`, claims `userId`, `empresaId` y `scope`). Matriz mínima:
> - `POST /api/ventas`: `ROLE_USER` o superior. El `clienteId` se toma del token, no del cuerpo.
> - `GET /api/ventas/{id}`, `/cliente/{id}` y `/factura/**`: el dueño de la venta, el emprendedor de esa empresa o el administrador.
> - `/emprendedor/{id}`: solo ese emprendedor (validado contra el token) o el administrador.
> - `GET /api/ventas` (todas): administrador.
> - `PUT /completar` y `/cancelar`, y `/api/pagos/{id}/aprobar` y `/rechazar`: emprendedor dueño o administrador. **Nunca el cliente.** Ver T05 para PayPhone.
> - `/api/clientes/**`: el propio cliente o el administrador. `DELETE` solo el administrador.
> - `/api/seguimiento-logistica`: escritura por el emprendedor dueño; lectura por el dueño de la venta o el emprendedor.
> - `/api/bpm/ventas/**`: solo el administrador o uso interno.
> - Deshabilitar `camunda-bpm-spring-boot-starter-rest` (`/engine-rest`) o protegerlo con autenticación. Proteger el webapp `/camunda` y sacarlo de la exposición pública (H-05).
> - Mantener la propagación del Bearer por Feign hacia Inventario.
> - Retirar `allowedOrigins("*")` de `WebConfig`; el CORS lo resuelve el gateway.
>
> Pruebas: matriz 401/403/200 por endpoint con `admin`, `sigcholac`, `ultimo_inca` y `cliente1`, más un flujo de compra completo a través del gateway.

### T05: Confirmación del pago solo desde el servidor · `fix/t05-confirmacion-pago-servidor`

> Corregir H-03:
> - El paso de una venta pagada con PayPhone a `COMPLETADA` debe hacerlo el backend, después de verificar con PayPhone (`/api/confirm`) que `transactionStatus` es aprobado, que el monto coincide con el total calculado en T03 y que `clientTransactionId` corresponde a esa venta.
> - Mover la llamada de confirmación a Ventas o hacer que la ruta Next.js la delegue a un endpoint protegido de Ventas.
> - Quitar del frontend las llamadas directas a `/completar` (`cartService.ts:541`, `ventasService.ts:187`).
> - Que la confirmación sea idempotente (si se repite el mismo `id`, no duplica el descuento de stock) y respete la ventana de 5 minutos de PayPhone.
> - La transferencia y Deuna siguen con aprobación manual del emprendedor (T04).
>
> Pruebas: con PayPhone simulado en el test, cubrir aprobado, rechazado, monto distinto y confirmación repetida.

### T06: Retirar Stripe y el pago simulado · `chore/t06-retirar-stripe-y-simulado`

> Aplicar D-01:
> - Eliminar `StripeController`, el servicio, la dependencia Maven y las propiedades `stripe.*` de Ventas.
> - Eliminar `stripeService.ts`, `StripeCheckoutForm.tsx` (en `(landing)` y `(main)`), la opción en `PaymentMethodSelector`, la ruta `/api/stripe` del gateway y las variables `STRIPE_*` y `NEXT_PUBLIC_STRIPE_*` de los `.env.example` y los compose.
> - Eliminar `procesarPagoSimulado` (O-07) y cualquier camino que confirme ventas sin un pago real.
> - Actualizar la documentación de pagos.
>
> Verificar que `npm run build` y la compilación de Ventas pasan y que el checkout muestra solo PayPhone, Deuna y transferencia.

### T07: Autorización de categorías, empresas e inventario · `fix/t07-autorizacion-producto-inventario`

> Corregir H-07, H-08 y H-09:
> - En Productos: `POST`, `PUT` y `DELETE` de `/api/categorias/**` y `/api/empresas/**` solo para `ROLE_ADMIN`; los `GET` siguen públicos. Cambiar `anyRequest().permitAll()` por una lista explícita de rutas públicas.
> - En Inventario: `salida-lote` y `PUT /producto/{id}/stock` deben validar que el producto pertenece a la empresa del token (`ROLE_EMP`) o que la llamada es interna de Ventas durante una compra confirmada. Hay que definir una identidad de servicio o propagar el token del cliente con validación de la venta.
> - `GET /api/movimientos/**`: solo el emprendedor dueño o el administrador.
> - No romper el catálogo ni `cantidad/batch`.
>
> Pruebas: MockMvc y la matriz A-14 ampliada.

### T08: Reducir la superficie expuesta y los logs sensibles · `fix/t08-superficie-y-logs`

> Corregir H-10, H-12, H-14 y H-16:
> - Desactivar Swagger y `/api-docs` en producción (`springdoc.api-docs.enabled=${API_DOCS_ENABLED:false}`), manteniéndolos activos en local mediante el `.env`.
> - Poner `spring.jpa.show-sql=false` por defecto.
> - Eliminar los `console.log` que muestran tokens o parte de ellos (`route.ts`, `PayphoneForm.tsx`) y suprimir los `console.log` en el build de producción (O-17).
> - Retirar los `@CrossOrigin(originPatterns="*")` de los controladores, porque el CORS se centraliza en el gateway. Hacerlo solo después de confirmar que el frontend no llama directo a 808x (A-14 lo confirmó).
> - Corregir `ROLE_EMPRENDEDOR` → `ROLE_EMP` en las estadísticas.
> - Ejecutar la matriz A-14 completa: el CORS debe seguir funcionando a través del gateway.

### T09: Gateway de producción, puertos y rate limiting · `feature/t09-gateway-produccion-puertos`

> Corregir H-06 y H-11:
> - En `nginx.conf` de producción: agregar `limit_req_zone` para `/api/auth/login`, `/registro`, `/google` y `/recuperar-password/**` (por ejemplo 5 r/min por IP, con ráfaga de 10), con una respuesta `429` en JSON.
> - Agregar cabeceras de seguridad: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy: strict-origin-when-cross-origin` (requisito de PayPhone) y HSTS cuando haya TLS.
> - Bloquear explícitamente `/engine-rest`, `/camunda`, `/swagger-ui` y `/api-docs`.
> - Ajustar el `map $http_origin` al dominio real.
> - En los compose de producción: publicar MySQL, Eureka y los microservicios **solo en la red interna de Docker o en `127.0.0.1`**. Solo el gateway y el frontend quedan expuestos (80/443).
> - Documentar los comandos de `firewall-cmd` para AlmaLinux (en lugar de `ufw`) y el impacto de SELinux.
>
> Pruebas: en local, desde otro equipo de la red, comprobar que 3306 y 8081–8084 no responden y que el gateway sí; verificar el `429` tras superar el límite.

### T10: Despliegue Docker de producción · `feature/t10-despliegue-docker-produccion`

> Resolver B1–B7 del análisis de despliegue:
> - Crear el `Dockerfile` de Auth (`demo`, puerto 8084).
> - Agregar `spring-boot-starter-actuator` (solo `health` expuesto) o cambiar los healthchecks a un endpoint existente.
> - Crear `docker-compose.prod.yml` con MySQL (volumen existente, `external: true` si ya existe), Eureka, Auth, Productos, Inventario, Ventas, el gateway y el frontend, con:
>   - Límites de memoria tomados de `info/docker-compose.light.yml` y `JAVA_OPTS` acotados.
>   - Variables obligatorias (`JWT_SECRET`, `MYSQL_*`, `CAMUNDA_*`, `MAIL_*`), sin Stripe.
>   - URLs internas por nombre de servicio.
>   - Volúmenes para facturas y QR.
> - Frontend con `output: 'standalone'` y Node en ejecución; `NEXT_PUBLIC_GATEWAY_URL` como argumento de build con la URL pública; `GATEWAY_INTERNAL_URL` para las rutas API del servidor.
> - Upstreams del gateway por nombre de servicio.
> - Compilar las imágenes fuera del servidor (memoria limitada) y documentar `docker save`/`docker load` o el registro de imágenes.
> - Escribir `GUIA-DESPLIEGUE-PRODUCCION.md` con el orden de arranque, la verificación y el rollback.
>
> Ensayar el compose completo en local, medir la RAM total con `docker stats` y ejecutar la matriz A-14 contra él.

### T11: Vincular un emprendedor a una empresa · `feature/t11-vincular-emprendedor-empresa`

> Resolver F-01. Antes de programar, decidir con el equipo si una empresa admite **uno o varios** emprendedores (hoy el código supone uno). Luego:
> - En Auth: endpoints para el administrador, por ejemplo `POST /api/emprendedores` (`usuarioId`, `empresaId`), `GET /api/emprendedores`, `DELETE /api/emprendedores/{id}` y `PUT` para cambiar la empresa. Deben validar que el usuario existe y tiene `ROLE_EMP`, que la empresa existe y está activa (consultar Productos) y que no hay duplicados.
> - Al crear un usuario con `ROLE_EMP` desde el panel, exigir que se elija una empresa en la misma operación, o crear la empresa y el vínculo juntos.
> - En el panel del administrador: pantalla para asignar o reasignar emprendedores en la vista de Empresas.
> - Documentar que el emprendedor debe volver a iniciar sesión para recibir el `empresaId` en el JWT.
> - Sin cambios en el esquema si la tabla actual alcanza; si no, incluir el script de migración y el de reversión.
>
> Pruebas:
> - Crear un emprendedor de punta a punta, iniciar sesión y verificar que puede crear un producto solo en su empresa.
> - Intentar el vínculo con una empresa inexistente (400) y como no administrador (403).

### T12: Catálogo con precios públicos (A-23) · `feature/t12-precios-publicos`

> Resolver F-02 y D-02:
> - Mostrar el precio en el catálogo, en el detalle del producto y en los destacados sin sesión, en `(landing)` y en `(main)`, o consolidar antes las páginas duplicadas (O-14).
> - Exigir el login solo al agregar al carrito o al iniciar el checkout, con un mensaje claro y la vuelta al producto después del login.
> - Revisar H-15: crear un DTO público de producto sin RUC, email ni teléfono de la empresa (solo `empresaNombre` y `empresaId`) y usarlo en los `GET` públicos.
> - Verificar en Network que ninguna solicitud del catálogo anónimo devuelve `401`.

### T13: El administrador no modifica productos · `feature/t13-admin-sin-edicion-productos`

> Aplicar D-03 con la opción elegida en F-03 (recomendada: **b**, solo lectura más activar o desactivar con auditoría):
> - En `ProductoAuthorizationService`, `ROLE_ADMIN` deja de tener permiso de escritura. Si se elige la opción (b), crear `PATCH /api/productos/{id}/estado` solo para el administrador, que registre usuario, fecha y motivo.
> - Aplicar el mismo criterio al inventario: el administrador no ajusta stock.
> - En el frontend: quitar crear, editar y eliminar de `administrador/productos` y dejar la vista de consulta.
> - Actualizar `ProductoAuthorizationServiceTest` y la matriz A-14 (el administrador ahora recibe `403` en `PUT` y `DELETE` de productos).
> - Revisar si los datos semilla o los procesos existentes dependen de que el administrador cree productos (por ejemplo, empresas sin emprendedor). Si dependen, resolverlo primero con T11.

### T14: Cifrado de credenciales de PayPhone (O-03), futuro · `feature/t14-cifrado-credenciales-pago`

> Después de T00, que confirma el estado en el servidor:
> - Cifrar `payphone_token` (y el App ID si se decide) con AES-256-GCM mediante un `AttributeConverter` de JPA, con la clave en la variable `PAYMENT_CREDENTIALS_KEY`, fuera de la base y del repositorio.
> - Crear un script de migración idempotente que cifra las filas existentes (detecta si ya están cifradas) y un script de reversión.
> - Ninguna respuesta de API vuelve a mostrar el token completo al emprendedor; solo los últimos 4 caracteres.
> - Respaldo (T01) obligatorio antes de migrar.
> - Pruebas: cifrado y descifrado, filas antiguas en texto plano y clave incorrecta (fallo controlado).

### T15: Rotación de credenciales (A-09), después del despliegue

> Con la autorización del propietario y en la ventana acordada, rotar en este orden:
> 1. `JWT_SECRET`, que está expuesto en el historial de Git.
> 2. El usuario de aplicación de MySQL (nueva contraseña robusta y retiro de `admin/admin`).
> 3. La contraseña del administrador de Camunda.
> 4. Las credenciales SMTP.
> 5. Revocar la clave de Stripe expuesta en el historial (aunque no se use, D-01).
>
> Registrar fecha, responsable y resultado sin los valores.

---

## 7. Después de estas tareas

1. Integrar T01–T13 en `integration/estabilizacion-produccion` y repetir la regresión completa (A-14 ampliada más la compra con PayPhone en modo de pruebas).
2. Crear `release/1.0-produccion`, desplegar con T01 y T10 en la ventana autorizada y crear el tag `v1.0.0`.
3. Medir la **línea base de JMeter en producción** antes de Redis. Es requisito de la tesis (plan V2 §7).
4. Crear la rama de Redis **desde el release desplegado** (`feature/a17-piloto-redis`), siguiendo A15 y A16.

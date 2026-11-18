# Informe consolidado de pruebas en producción

## 1. Identificación

- **Fecha de ejecución:** 17 de noviembre de 2026
- **Sistema:** Tienda Virtual
- **URL validada:** `https://tienda.gadmsigchos.gob.ec:444`
- **Versión base desplegada:** `main` `b60b307`
- **Correcciones posteriores verificadas:** `eac5ec7` y `f8a2006`
- **Ambiente:** servidor AlmaLinux, NGINX, microservicios Spring Boot, MySQL y Valkey.
- **Credenciales y JWT:** no incluidos en este informe.

## 2. Resultado ejecutivo

Las pruebas confirmadas demuestran que el sistema se encuentra operativo en producción para el alcance validado:

- Gateway HTTPS y enrutamiento operativo.
- Autenticación y autorización por roles funcionando.
- Aislamiento de datos entre empresas funcionando.
- Vinculación emprendedor-empresa T11 protegida por validaciones e índice único.
- Flujo completo de compra con PayPhone Sandbox completado correctamente.
- Descuento de inventario ejecutado una sola vez.
- Factura PDF generada y descargable.
- Token de PayPhone cifrado en la base de datos.
- Valkey activo y utilizado para las consultas cacheadas del catálogo.
- Reducción comprobada de consultas a MySQL y mejora de latencias.

## 3. Validación del Gateway y perímetro

### 3.1 Disponibilidad y TLS

| Prueba | Resultado |
|---|---:|
| `nginx -t` | Correcto |
| Servicio NGINX | `active` |
| TLS | TLS 1.3 válido |
| HTTP `:80` hacia HTTPS `:444` | `301` |
| `GET /landing` | `200` |
| `GET /api/health` | `200` |
| `GET /api/productos` | `200` |
| `GET /api/categorias` | `200` |
| `GET /api/empresas` | `200` |

### 3.2 Bloqueo de rutas internas

Las siguientes rutas respondieron correctamente:

```text
/actuator/health       -> 403
/swagger-ui.html       -> 403
/api-docs              -> 403
/v3/api-docs           -> 403
/camunda               -> 403
/engine-rest           -> 403
/api/productos/1/sincronizar-stock -> 403
/api/ventas/internal/test           -> 404
```

### 3.3 Seguridad HTTP

Se verificaron las cabeceras de seguridad, la redirección HTTPS, el control CORS y el rate limiting de NGINX.

Un origen CORS no autorizado recibió:

```text
HTTP 403 - Invalid CORS request
```

Las rutas protegidas sin JWT respondieron `401` y las rutas internas bloqueadas respondieron `403`.

## 4. Autenticación y regresión por roles

Se validaron cuatro niveles de acceso:

- usuario anónimo;
- `ROLE_USER`;
- `ROLE_EMP`;
- `ROLE_ADMIN`.

### 4.1 Resultados principales

| Escenario | Resultado |
|---|---:|
| Rutas públicas sin sesión | `200` |
| Rutas privadas sin JWT | `401` |
| `ROLE_USER` accediendo al panel ADMIN | Bloqueado |
| `ROLE_USER` accediendo al panel EMP | Bloqueado |
| `ROLE_EMP` accediendo al panel ADMIN | `403` |
| `ROLE_ADMIN` accediendo a usuarios | `200` |
| `ROLE_ADMIN` accediendo a ventas | `200` |
| `ROLE_ADMIN` accediendo a inventario | `200` |
| `ROLE_EMP` consultando sus ventas | `200` |
| `ROLE_EMP` consultando movimientos propios | `200` |
| `ROLE_EMP` consultando ventas de otra empresa | `403` |

El emprendedor de `empresaId=2` visualizó únicamente sus productos, movimientos y ventas. El administrador conservó la visibilidad global del sistema.

## 5. Verificación de T11

### 5.1 Integridad de datos

Se confirmó:

- índice único `uk_emprendedor_empresa` activo;
- `Non_unique = 0`;
- cero empresas duplicadas;
- `MSVC_PRODUCTO_URL=http://127.0.0.1:8081` cargado en el proceso vivo de `auth-service`;
- usuarios `2`, `3`, `4` y `5` con `ROLE_EMP`;
- usuario `1` con `ROLE_ADMIN`.

Relaciones verificadas:

```text
usuario 2 -> empresa 1
usuario 3 -> empresa 2
usuario 4 -> empresa 3
usuario 5 -> empresa 4
```

### 5.2 Validación de endpoints T11

| Caso | Resultado | Evaluación |
|---|---:|---|
| ADMIN intenta vincular usuario que no es EMP | `400` | Correcto |
| EMP intenta tomar empresa ocupada | `409` | Correcto |
| Repetir vínculo de usuario 3 con empresa 2 | `200 SIN_CAMBIOS` | Correcto |
| Repetir vínculo de usuario 5 con empresa 4 | `200 SIN_CAMBIOS` | Correcto |
| `empresaId=0` | `400` | Correcto |
| `empresaId=-1` | `400` | Correcto |
| Empresa inexistente `999999` | `404` | Correcto |
| Consulta sin JWT | `401` | Correcto |
| Consulta T11 con `ROLE_EMP` | `403` | Correcto |
| `PUT` T11 con `ROLE_EMP` | `403` | Correcto |

### 5.3 Aislamiento directo de productos

El emprendedor de `empresaId=2` intentó operar sobre el producto `id=1`, perteneciente a otra empresa:

```text
DELETE producto ajeno -> 403
GET producto después del intento -> 200
PUT producto ajeno -> 403
GET usuarios administrativos -> 403
```

El producto permaneció disponible después del intento de eliminación, confirmando que no fue alterado.

El `PUT` inicialmente había respondido `400` porque el cuerpo usaba campos planos inexistentes (`empresaId` y `categoriaId`) en la respuesta del producto. Al construir el cuerpo con los IDs anidados reales de empresa y categoría, la autorización respondió correctamente `403`.

## 6. Flujo completo de compra con PayPhone Sandbox

Se validó una compra nueva desde cero con:

```text
ventaId: 9
Factura: FACT-1794933559721
Total: $7.76
Transacción PayPhone: 93755596
```

Resultados:

```text
POST /api/ventas                         -> 201
Preparación de PayPhone                  -> 200
Transacción PayPhone                     -> Approved
Confirmación de pago                     -> 200
resultadoVenta                           -> COMPLETADA
Stock                                    -> 296 a 295
Movimiento de inventario                 -> SALIDA única
Generación automática de factura         -> Correcta
Descarga de factura PDF                  -> 200
Errores recientes en generación PDF      -> 0
```

También se verificó la idempotencia con `ventaId=7`: dos llamadas de confirmación no generaron un doble descuento de inventario.

## 7. Cifrado y configuración de PayPhone

Se confirmó en runtime y en la base de datos:

- `PAYPHONE_TOKEN_ENC_KEY` presente y válida;
- clave equivalente a 32 bytes AES-256;
- token almacenado con prefijo `enc:v1:`;
- cero tokens pendientes en texto plano;
- token no expuesto por la API ni por la interfaz;
- Stripe deshabilitado;
- preparación y confirmación PayPhone funcionando.

La interfaz muestra únicamente que el token está configurado y no permite visualizar su contenido.

## 8. Valkey y rendimiento

Valkey quedó operativo como servicio persistente:

```text
Servicio: active
Puerto: 127.0.0.1:6379
Respuesta: PONG
Memoria utilizada: aproximadamente 1.17 MB
DBSIZE: 3
```

Se verificó que el catálogo utilice caché para:

- `GET /api/productos/listado`;
- `GET /api/categorias`;
- `GET /api/categorias/{id}`.

### 8.1 Resultados comparativos

| Endpoint | Resultado comprobado |
|---|---|
| `/api/categorias` | `Com_select` de 31 a 1; latencia media de 19.45 ms a 15.45 ms |
| `/api/productos/listado` | `Com_select` de 61 a 31; reducción de 49.2% |
| `/api/productos/listado` | `p95` de 35.20 ms a 26.15 ms |
| Caché Valkey | `100%` de aciertos durante la medición |
| Salud de caché | `UP` |

El stock continúa consultándose en vivo, por lo que no se almacena información de inventario obsoleta en Valkey.

## 9. Conclusión

Las pruebas consolidadas quedan aprobadas para el alcance ejecutado en producción.

El sistema demostró:

- disponibilidad del Gateway;
- protección de rutas internas;
- autenticación y autorización por roles;
- aislamiento multiempresa;
- integridad de la vinculación T11;
- procesamiento completo de una compra;
- descuento único de inventario;
- generación de factura;
- protección y cifrado de credenciales PayPhone;
- operación efectiva de Valkey y mejora medible del catálogo.

Los hallazgos visuales menores detectados durante las pruebas no impidieron el funcionamiento del sistema ni alteraron los resultados de seguridad, compras o integridad de datos.

Con base en estas evidencias, el sistema se encuentra validado para la presentación del flujo principal de operación.

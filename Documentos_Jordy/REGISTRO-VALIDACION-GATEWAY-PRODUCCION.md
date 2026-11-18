# Registro de validación del Gateway en producción

## 1. Identificación

- **Fecha:** 17 de noviembre de 2026
- **Sistema:** Tienda Virtual
- **Destino:** `https://tienda.gadmsigchos.gob.ec:444`
- **Alcance:** validación del Gateway, autenticación, autorización, CORS y aislamiento entre empresas.
- **Datos modificados:** ninguno.
- **JWT y contraseñas compartidos:** no.

## 2. Evidencias utilizadas

Se consolidaron las siguientes evidencias:

1. Resultados de validación del servidor y del Gateway incluidos en `PRUEBAS_GATEWAY.pdf`.
2. Pruebas autenticadas registradas en `EVIDENCIA-PRUEBAS-AUTENTICADAS-PRODUCCION.md`.
3. Verificación de la versión desplegada mediante el commit correspondiente, cuando el repositorio se encuentra disponible en el servidor.

## 3. Validación de infraestructura y Gateway

| Verificación | Resultado | Evaluación |
|---|---:|---|
| `nginx -t` | Correcto | Configuración válida |
| Estado de NGINX | `active` | Servicio operativo |
| `GET /health` | `200` | Gateway disponible |
| Página principal | `200` | Frontend disponible |
| Catálogo de productos | `200` | Ruta pública operativa |
| Categorías | `200` | Ruta pública operativa |
| `GET /actuator/health` | `403` | Ruta interna bloqueada |
| Swagger/API Docs | `403` | Documentación interna bloqueada |
| Camunda | `403` | Ruta interna bloqueada |
| Ruta interna de ventas | `404` | No expone un recurso válido |
| Sincronización de stock | `403` | Operación interna bloqueada |
| Peticiones inválidas de login | `429` después del límite | Rate limiting operativo |

## 4. Validación de CORS

Se ejecutó una solicitud `OPTIONS` utilizando un origen no autorizado:

```text
Origin: https://origen-no-autorizado.example
Ruta: /api/productos
Resultado: HTTP 403 - Invalid CORS request
```

El Gateway rechazó correctamente el origen no permitido y no devolvió una cabecera `Access-Control-Allow-Origin` válida para ese origen.

## 5. Validación de exposición de puertos

Los resultados obtenidos fueron:

- Valkey escucha en `127.0.0.1:6379` y `[::1]:6379`; no está expuesto externamente.
- Los servicios Java aparecen escuchando en `0.0.0.0` en los puertos `8081–8084`.
- Desde el exterior, los puertos `8081–8083` no respondieron.
- El puerto `8084` respondió a una conexión HTTP con `400 Bad Request` porque recibió HTTP en un listener HTTPS.

Este último resultado queda como observación operativa para revisar posteriormente el cierre externo de los puertos internos. No impidió la validación funcional del Gateway público.

## 6. Pruebas autenticadas

### 6.1 Inicio de sesión

| Perfil | Código | Resultado |
|---|---:|---|
| Administrador | `200` | `ROLE_ADMIN`, sin `empresaId` |
| Emprendedor | `200` | `ROLE_EMP`, `empresaId=2` |
| Cliente | `200` | `ROLE_USER`, sin `empresaId` |

### 6.2 Control de acceso

| Actor | Solicitud | Código esperado | Código obtenido | Resultado |
|---|---|---:|---:|---|
| Cliente | `GET /api/usuarios/paginado` | `403` | `403` | Correcto |
| Cliente | `GET /api/ventas/paginado` | `403` | `403` | Correcto |
| Emprendedor | `GET /api/usuarios/paginado` | `403` | `403` | Correcto |
| Emprendedor | `GET /api/movimientos` | `403` | `403` | Correcto |
| Administrador | `GET /api/usuarios/estadisticas` | `200` | `200` | Correcto |
| Administrador | `GET /api/usuarios/paginado` | `200` | `200` | Correcto |
| Administrador | `GET /api/ventas/paginado` | `200` | `200` | Correcto |
| Administrador | `GET /api/movimientos/paginado` | `200` | `200` | Correcto |
| Emprendedor | `GET /api/movimientos/paginado` propios | `200` | `200` | Correcto |
| Emprendedor | Ventas de su empresa | `200` | `200` | Correcto |
| Emprendedor | Ventas de empresa ajena | `403` | `403` | Correcto |

**Resultado:** 11 de 11 pruebas autenticadas correctas.

### 6.3 Rutas sin token

| Solicitud | Código | Resultado |
|---|---:|---|
| `GET /api/usuarios/paginado` | `401` | Correcto |
| `GET /api/usuarios/estadisticas` | `401` | Correcto |
| `GET /api/ventas/paginado` | `401` | Correcto |
| `GET /api/movimientos/paginado` | `401` | Correcto |
| `GET /api/ventas/emprendedor/1/paginado` | `401` | Correcto |
| `GET /api/productos/listado/paginado` | `200` | Ruta pública correcta |
| `GET /api/empresas` | `200` | Ruta pública correcta |

## 7. Interpretación de los resultados

Los resultados demuestran que:

- Las rutas protegidas rechazan solicitudes sin autenticación con `401`.
- Los usuarios autenticados sin permisos reciben `403`.
- El administrador conserva el acceso a sus operaciones permitidas.
- El emprendedor puede consultar sus propios movimientos y ventas.
- El emprendedor no puede consultar las ventas de otra empresa.
- El `empresaId` asociado al JWT está siendo utilizado correctamente para aplicar el aislamiento empresarial.
- Las rutas públicas continúan disponibles sin token.
- El Gateway aplica el límite de intentos de autenticación.
- Los endpoints internos y la documentación sensible están bloqueados desde el perímetro.

No se utilizó `GET /api/productos/{id}` para validar permisos porque es una ruta pública del catálogo. Tampoco se ejecutó un `PUT` sobre un producto ajeno, porque podría modificar datos de producción y la validación del cuerpo ocurre antes de la autorización.

## 8. Resultado de la etapa

### Estado: APROBADA

La validación del Gateway en producción queda cerrada para el alcance definido. No se identificaron fallos funcionales en autenticación, autorización, CORS, rate limiting o aislamiento entre empresas.

## 9. Pendientes fuera de esta etapa

Estos puntos no impiden cerrar la validación del Gateway, pero deben mantenerse registrados:

1. Ejecutar el flujo completo de compra, incluyendo creación del pedido, pago, confirmación y actualización de inventario.
2. Revisar el motivo por el que el puerto `8084` respondió externamente y confirmar el cierre de los puertos internos.
3. Continuar con la verificación específica de T11: vinculación, consulta, reasignación y desvinculación de emprendedores y empresas.
4. Mantener las pruebas de producción sin compartir JWT, contraseñas ni valores de `.env`.

La siguiente etapa de trabajo será la verificación de T11. El flujo completo de compra se realizará al final, como prueba integral del sistema.

# Evidencia T11: pruebas de vinculación en producción

- **Fecha:** 17 de noviembre de 2026
- **Destino:** `https://tienda.gadmsigchos.gob.ec:444`
- **Alcance:** pruebas no destructivas de vinculación emprendedor-empresa.
- **JWT y contraseñas compartidos:** no.

## Estado de datos

La relación actual verificada en MySQL es:

```text
usuario 2 -> empresa 1
usuario 3 -> empresa 2
usuario 4 -> empresa 3
usuario 5 -> empresa 4
```

La tabla de roles confirmó:

```text
usuario 1 -> ROLE_ADMIN
usuarios 2, 3, 4 y 5 -> ROLE_EMP
```

El índice único `uk_emprendedor_empresa` está activo y no existen empresas duplicadas.

## Resultados HTTP

| Caso | Resultado | Evaluación |
|---|---:|---|
| ADMIN intenta vincular usuario 1 (`ROLE_ADMIN`) | `400` | Correcto: el objetivo debe tener `ROLE_EMP` |
| Usuario 2 intenta tomar empresa 2, ya ocupada | `409` | Correcto: conflicto de empresa única |
| Usuario 3 repite su vínculo con empresa 2 | `200`, `SIN_CAMBIOS` | Correcto: operación idempotente |
| Usuario 5 repite su vínculo con empresa 4 | `200`, `SIN_CAMBIOS` | Correcto: operación idempotente |
| `empresaId = 0` | `400` | Correcto: valor inválido |
| `empresaId = -1` | `400` | Correcto: valor inválido |
| `empresaId = 999999` | `404` | Correcto: empresa inexistente |
| Consulta sin JWT | `401` | Correcto: autenticación obligatoria |
| EMP consulta endpoint administrativo | `403` | Correcto: rol insuficiente |
| EMP intenta ejecutar `PUT` administrativo | `403` | Correcto: rol insuficiente |

## Conclusión

La sección 1 de T11 queda validada en producción para los casos no destructivos:

- autenticación y autorización del endpoint;
- rechazo de usuarios que no son emprendedores;
- rechazo de empresas ocupadas;
- idempotencia de una vinculación existente;
- validación de IDs inválidos o inexistentes;
- protección contra consultas sin token;
- protección contra acceso de `ROLE_EMP` a operaciones administrativas.

No se ejecutaron `DELETE` ni reasignaciones reales para no modificar las cuatro relaciones productivas existentes. Esos casos requieren datos temporales o un entorno de prueba.

## Pruebas directas de aislamiento por `ROLE_EMP`

Se utilizó el emprendedor `sigcholac`, vinculado a `empresaId=2`, sin compartir el JWT.

| Solicitud | Resultado | Evaluación |
|---|---:|---|
| Ventas propias: `GET /api/ventas/emprendedor/2/paginado` | `200` | Correcto |
| Movimientos propios: `GET /api/movimientos/paginado` | `200` | Correcto |
| Ventas ajenas: `GET /api/ventas/emprendedor/1/paginado` | `403` | Correcto |
| Eliminar producto ajeno `id=1` | `403` | Correcto |
| Consultar producto `id=1` después del intento | `200` | El producto permaneció intacto |
| Actualizar producto ajeno `id=1` | `400` | Prueba inconclusa; el cuerpo no coincidió con el DTO |
| Consultar usuarios administrativos | `403` | Correcto |

### Diagnóstico del `400` en el `PUT`

El `PUT /api/productos/{id}` requiere un `ProductoRequestDto` válido, incluyendo:

```text
nombre, precio, empresaId y categoriaId
```

La respuesta de `GET /api/productos/{id}` devuelve `empresa` y `categoria` como objetos anidados, no como campos planos `empresaId` y `categoriaId`. Por ello, construir el cuerpo con `$detalleAjeno.empresaId` o `$detalleAjeno.categoriaId` produce valores nulos y la validación responde `400` antes de evaluar la autorización.

La prueba de `DELETE` sí fue concluyente: respondió `403` y el producto continuó disponible con `GET 200`. Por tanto, el aislamiento de escritura quedó demostrado; el `PUT` debe repetirse únicamente con un cuerpo válido si se requiere completar esa variante.

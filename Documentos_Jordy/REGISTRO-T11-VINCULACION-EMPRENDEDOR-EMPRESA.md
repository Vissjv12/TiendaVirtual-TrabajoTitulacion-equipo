# Registro T11: vinculación emprendedor-empresa

Fecha: 2026-11-16. Sin commit. T11 queda **cerrado en código**. Falta su validación operativa con datos reales.

## Qué cambia

Antes, la vinculación solo se hacía por SQL. Ahora el administrador la gestiona desde la pantalla de edición del usuario.

```
ADMIN -> PUT /api/usuarios/{id}/empresa {"empresaId": 10}
      -> auth-service: usuario existe y tiene ROLE_EMP
      -> msvc-producto GET /api/empresas/10: existe y está activa
      -> la empresa no está vinculada a otro emprendedor
      -> crea o actualiza emprendedores.empresa_id (índice único como garantía final)
      -> siguiente login: el JWT lleva empresaId
      -> producto, inventario y pagos aíslan por esa empresa
```

| Endpoint (solo ROLE_ADMIN) | Función |
|---|---|
| `GET /api/usuarios/{id}/empresa` | Consulta la empresa vinculada. |
| `PUT /api/usuarios/{id}/empresa` con `{"empresaId": N}` | Vincula o reasigna. `empresaId` nulo, 0 o negativo responde 400: para desvincular se usa DELETE. |
| `DELETE /api/usuarios/{id}/empresa` | Desvincula. |

La respuesta incluye `resultado` (`VINCULADA`, `REASIGNADA`, `SIN_CAMBIOS`, `DESVINCULADA`) y `requiereNuevoLogin`.

## Decisiones respecto a la propuesta

| Propuesta | Aplicado | Motivo |
|---|---|---|
| PUT y DELETE separados | Sí, y además un GET de consulta | `empresaId: null` en el PUT responde 400 en vez de desvincular por error. |
| Validar la empresa con Feign | Con `RestClient` (`EmpresaProductoClient`) | Es el patrón que auth-service ya usa con msvc-ventas, y auth-service no tiene Feign. Llama directo a msvc-producto, sin pasar por el Gateway. Si la empresa no existe responde 404; si producto no responde, 503 y no vincula (fail-closed). |
| Empresa inactiva: 409 o 400 | 409 | Es un conflicto con el estado actual del dato, no una petición mal formada. |
| Auditoría en tabla persistente | Log estructurado (`auditoria.emprendedores`) | Una tabla nueva exige otra migración en producción, donde `ddl-auto=validate` impide arrancar si falta. Sigue el mismo criterio que la auditoría de A-02 (`auditoria.empresas`). Con systemd queda en journald. Se registran el administrador (`userId` del JWT), el usuario, la empresa anterior y la nueva, el resultado (incluidos los rechazos) y el motivo. Fecha y hora las pone el log. |
| Unicidad de `empresa_id` | Índice único `uk_emprendedor_empresa` + comprobación previa | La comprobación da un mensaje claro. El índice impide el duplicado aunque lleguen dos solicitudes a la vez: el `saveAndFlush` falla y la API responde 409. |
| Reasignación | Permitida | Se registra la empresa anterior y la respuesta pide un nuevo login. |
| Desvincular un usuario sin `ROLE_EMP` | Permitido | Si a un emprendedor se le quitó el rol, el administrador debe poder limpiar su vinculación. Para **vincular** sí se exige `ROLE_EMP`. |

## Correcciones adicionales

- **`empresaId` en las respuestas administrativas.** `UsuarioMapper` nunca lo llenaba. Ahora el listado paginado, la lista completa, la búsqueda, el filtro por rol, la consulta por id, la edición y el cambio de estado lo completan con **una sola consulta** por página (`findByUsuarioIdIn`).
- **Login.** `AuthController.login` sobrescribía el usuario que ya traía `empresaId` y la respuesta lo devolvía en `null`. El frontend lo recuperaba leyendo el JWT, pero la respuesta era incorrecta. Ahora se conserva.

## Limitación conocida

El access token dura **15 minutos por defecto**, configurable mediante `JWT_ACCESS_TOKEN_TTL_SECONDS`. Tras reasignar o desvincular, el token **ya emitido** conserva la empresa anterior hasta que expire o hasta que el emprendedor vuelva a iniciar sesión. Por eso la respuesta indica `requiereNuevoLogin` y la pantalla lo avisa. Revocar tokens inmediatamente exigiría una lista de revocación o consultar la base en cada petición. Esa revocación inmediata queda fuera del alcance de T11.

## Despliegue

1. Respaldar `auth_service`.
2. Ejecutar `scripts/prod/migraciones/T11-emprendedores-empresa-unica.sql`:
   - **Paso 1 (solo lectura):** si encuentra una empresa vinculada a más de un emprendedor, **detenerse** y resolver qué vinculación se conserva.
   - **Paso 2:** comprobar si el índice ya existe.
   - **Paso 3:** crear el índice.
3. Definir `MSVC_PRODUCTO_URL=http://127.0.0.1:8081` en el entorno de auth-service. Si no se define, el valor por defecto es `http://localhost:8081`.
4. El Gateway no requiere cambios: `/api/usuarios` ya se enruta a auth-service en `nginx.conf` y en `tienda.prod.conf`.

`scripts/mysql-init/02-schema-auth-service.sql` ya crea el índice único en las instalaciones locales nuevas.

## Pruebas

| Suite | Casos | Verifica |
|---|---|---|
| `EmprendedorEmpresaServiceTest` | 14 | Crear la fila si no existe; reasignar conservando la fila y la empresa anterior; idempotencia sin llamar a productos ni guardar; usuario inexistente 404; usuario que no es EMP rechazado; empresa inexistente 404; empresa inactiva 409; empresa ya vinculada a otro emprendedor 409; productos caído sin vincular; choque con el índice único (simultaneidad) 409; desvincular; desvincular sin empresa es idempotente; usuario inexistente 404; `completarEmpresa` con una sola consulta. |
| `EmprendedorEmpresaControllerTest` | 5 | Sin token 401; ROLE_USER y ROLE_EMP 403 en PUT, DELETE y GET sin llamar al servicio; ADMIN vincula y desvincula; `empresaId` nulo, ausente, 0 o negativo 400; mapeo 404, 400, 409 y 503. |
| `EmpresaProductoClientTest` | 4 | Lee id, nombre y estado; `activo` nulo se trata como inactiva; 404 de productos; 500 o conexión rechazada se tratan como no disponible. |
| `AuthServiceEmpresaJwtTest` | 3 | Sin vinculación no hay claim `empresaId`; tras vincular, el login lo incluye (y también la respuesta); una fila desvinculada no lo emite. |
| Frontend `empresaVinculacion.test.mjs` | 8 | Solo empresas activas, ordenadas; la empresa actual inactiva se ve pero no se puede elegir; textos; solo ROLE_EMP; PUT para vincular y DELETE para desvincular; la tarjeta usa los roles **guardados**. |

**Resultados:**
- **demo (auth-service): 99/100.** La prueba que falla es `LoginApplicationTests`, que requiere MySQL y ya fallaba antes.
- **Frontend: 49/49.**
- **`tsc --noEmit`:** sin errores.
- **`next lint` sobre los archivos tocados:** sin errores. Queda una advertencia anterior en la página de edición (dependencias de `useEffect` en `cargarUsuario`).

**No probado contra MySQL real:** la simultaneidad se verificó simulando la violación del índice único. La garantía real depende de aplicar la migración.

## Pantalla

En **Administrador > Usuarios > Editar**, debajo del formulario aparece la tarjeta "Empresa vinculada" (`EmpresaVinculadaCard.tsx`). Muestra la empresa actual, un selector con búsqueda que solo ofrece empresas activas y los botones **Vincular** y **Desvincular** (este último pide confirmación). Usa los roles **guardados** del usuario: si se le asigna `ROLE_EMP` en el formulario, primero hay que guardar.

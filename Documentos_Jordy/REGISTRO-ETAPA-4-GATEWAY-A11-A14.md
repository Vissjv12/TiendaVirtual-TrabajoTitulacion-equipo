# Registro Técnico y Evidencias — Etapa 4: API Gateway (A-11 a A-14)

- **Etapa del cronograma:** `Etapa 4 — API Gateway`
- **Actividades cubiertas:** `A-11`, `A-12`, `A-13` y `A-14`
- **Ramas asociadas:**
  - `feature/a11-diseno-gateway`
  - `feature/a12-implementacion-gateway`
  - `feature/a13-frontend-gateway`
  - `feature/a14-validacion-gateway`
- **Observaciones de auditoría cerradas:** `O-11` (Ausencia de API Gateway centralizado), `O-13` (Políticas CORS dispersas/duplicadas) y `O-06` (Alineación de ruta Deuna QR)
- **Riesgos mitigados:** `R-01` (Prevención de agotamiento de memoria RAM en servidor municipal de 3.6 GB mediante contenedor NGINX ultraligero de `~20 MB` RAM)

---

## 1. Resumen de Implementación por Actividad

| Actividad | Rama | Archivos Principales | Estado |
|---|---|---|---|
| **A-11: Diseñar estrategia del Gateway** | `feature/a11-diseno-gateway` | `Documentos_Jordy/A11-ESTRATEGIA-Y-DISENO-GATEWAY.md` | Completada |
| **A-12: Implementar Gateway en pruebas** | `feature/a12-implementacion-gateway` | `gateway/nginx.conf`<br>`gateway/snippets/proxy-params.conf`<br>`gateway/snippets/cors-headers.conf`<br>`docker-compose.gateway.yml`<br>`.env.example` | Completada |
| **A-13: Migrar frontend al Gateway** | `feature/a13-frontend-gateway` | `avalon-react-10.1.0/services/apiConfig.ts`<br>12 servicios en `avalon-react-10.1.0/services/*.ts`<br>`next.config.js`, `app/api/payphone/confirm/route.ts`<br>`configuracion-pagos/page.tsx`, `PayphoneForm.tsx`, `DeunaForm.tsx`, `historial-compras/page.tsx` | Completada |
| **A-14: Validar seguridad, CORS y disponibilidad** | `feature/a14-validacion-gateway` | `Documentos_Jordy/pruebas-gateway-a14.ps1`<br>`Documentos_Jordy/REGISTRO-ETAPA-4-GATEWAY-A11-A14.md`<br>`Documentos_Jordy/GUIA-LEVANTAMIENTO-SISTEMA.md`<br>`Microservicios/demo/.../GlobalExceptionHandler.java` | Completada (`43/43 OK`) |

---

## 2. Hallazgos y Correcciones Aplicadas durante la Validación (A-14)

1. **Mapeo de `AccessDeniedException` a `403 Forbidden` en `auth-service` (`demo`):**
   - Durante la corrida de la matriz de roles a través del Gateway, se detectó que cuando un usuario autenticado con rol `ROLE_CLI` invocaba `GET /api/usuarios` (protegido con `@PreAuthorize("hasRole('ADMIN')")`), `GlobalExceptionHandler` en `demo` capturaba `AccessDeniedException` dentro del manejador genérico `Exception.class` y devolvía `500 Internal Server Error` en lugar de `403 Forbidden`.
   - Se añadió `@ExceptionHandler(AccessDeniedException.class)` en `Microservicios/demo/src/main/java/com/example/demo/presentation/controller/GlobalExceptionHandler.java` homologándolo con `msvc-producto` y `msvc-inventario` (`A-06`), devolviendo `403 Forbidden` con cuerpo JSON uniforme.
2. **Deduplicación de Cabeceras CORS (`O-13`):**
   - Se comprobó que al ocultar las cabeceras `Access-Control-Allow-*` provenientes de los microservicios (`proxy_hide_header` en `gateway/snippets/proxy-params.conf`) e inyectar la política única en NGINX, las respuestas `GET`, `POST`, `PUT`, `DELETE` y `OPTIONS` incluyen una única cabecera `Access-Control-Allow-Origin: http://localhost:3000`.
3. **Comportamiento ante Caída de Microservicios (`502 Bad Gateway` en JSON):**
   - Con los microservicios detenidos, el Gateway responde `502 Bad Gateway` con payload JSON `{"code":"SERVICE_UNAVAILABLE","message":"El servicio solicitado no se encuentra disponible temporalmente (Bad Gateway)","status":502}` y cabeceras CORS activas, evitando páginas HTML por defecto de NGINX.

---

## 3. Evidencia de Ejecución Automatizada (`pruebas-gateway-a14.ps1`)

Comando ejecutado:

```powershell
.\Documentos_Jordy\pruebas-gateway-a14.ps1
```
Resultado obtenido (`43` éxitos, `0` fallos):

```text
============================================================================
 VALIDACION DE API GATEWAY (A-14) -> http://localhost:8080
============================================================================

== 1. Salud y Disponibilidad del API Gateway ==
[OK]    GET /health (Gateway NGINX)                                      esperado=200      obtenido=200
[OK]    GET /api/health (Gateway NGINX)                                  esperado=200      obtenido=200
[OK]    Payload JSON de /health valido (status=UP, engine=nginx)

== 2. Politica CORS Centralizada y Preflight OPTIONS (O-13) ==
[OK]    OPTIONS /api/productos (Preflight CORS)                          esperado=204      obtenido=204
[OK]    Cabeceras CORS en OPTIONS correctas (Origin=http://localhost:3000, Credentials=true)
[OK]    GET /api/categorias con Origin (Deduplicacion CORS)              esperado=200      obtenido=200
[OK]    Sin duplicacion de Access-Control-Allow-Origin (http://localhost:3000)

== 3. Bloqueo de Rutas Internas y Errores Perimetrales JSON ==
[OK]    PUT /api/productos/1/sincronizar-stock (Bloqueado 403)           esperado=403      obtenido=403
[OK]    GET /actuator/health (Bloqueado 403)                             esperado=403      obtenido=403
[OK]    GET /api/ruta-inexistente-a14 (No existe 404 JSON)               esperado=404      obtenido=404

== 4. Enrutamiento Norte-Sur Publico hacia los 4 Microservicios ==
[OK]    GET /api/productos/listado (msvc-producto)                       esperado=200      obtenido=200
[OK]    GET /api/productos/1 (msvc-producto)                             esperado=200      obtenido=200
[OK]    GET /api/categorias (msvc-producto)                              esperado=200      obtenido=200
[OK]    GET /api/empresas (msvc-producto)                                esperado=200      obtenido=200
[OK]    GET /api/inventarios/producto/1 (msvc-inventario)                esperado=200      obtenido=200
[OK]    POST /api/inventarios/cantidad/batch (msvc-inventario)           esperado=200      obtenido=200
[OK]    GET /api/ventas (msvc-ventas)                                    esperado=200      obtenido=200
[OK]    GET /api/emprendedor/configuracion-pagos/costo-envio/1 (auth)    esperado=200      obtenido=200
[OK]    GET /api/emprendedor/deuna-qr/1 (alias O-06 -> auth)             esperado=200/404  obtenido=404

== 5. Consistencia de Stock via Gateway (Productos vs Inventario) ==
[OK]    Stock consistente via Gateway: productos=78 inventarios=39 diferencias=0

== 6. Rechazo de Escrituras y Rutas Protegidas sin Token (401) ==
[OK]    POST /api/productos sin token                                    esperado=401      obtenido=401
[OK]    PUT /api/productos/999999 sin token                              esperado=401      obtenido=401
[OK]    DELETE /api/productos/999999 sin token                           esperado=401      obtenido=401
[OK]    POST /api/inventarios sin token                                  esperado=401      obtenido=401
[OK]    PUT /api/inventarios/999999 sin token                            esperado=401      obtenido=401
[OK]    PUT /api/inventarios/producto/999999/stock sin token             esperado=401      obtenido=401
[OK]    DELETE /api/inventarios/999999 sin token                         esperado=401      obtenido=401
[OK]    GET /api/usuarios sin token (auth-service)                       esperado=401      obtenido=401
[OK]    GET /api/emprendedor/configuracion-pagos sin token (auth)        esperado=401      obtenido=401
[OK]    POST /api/inventarios/salida-lote sin token (inventario)         esperado=401      obtenido=401
[OK]    PUT /api/productos/999999 con token invalido                     esperado=401      obtenido=401

== 7. Autenticacion y Autorizacion por Rol via Gateway (JWT Propagation) ==

-- 7.1 Rol insuficiente: cliente1 (403) --
[OK]    POST /api/productos con rol CLIENTE                              esperado=403      obtenido=403
[OK]    PUT /api/productos/1 con rol CLIENTE                             esperado=403      obtenido=403
[OK]    DELETE /api/productos/999999 con rol CLIENTE                     esperado=403      obtenido=403
[OK]    PUT /api/inventarios/999999 con rol CLIENTE                      esperado=403      obtenido=403
[OK]    DELETE /api/inventarios/999999 con rol CLIENTE                   esperado=403      obtenido=403
[OK]    GET /api/usuarios con rol CLIENTE                                esperado=403      obtenido=403

-- 7.2 Aislamiento multi-empresa: sigcholac (empresa 2) sobre empresa 1 (403) --
[OK]    POST producto en empresa ajena 1                                 esperado=403      obtenido=403
[OK]    PUT producto ajeno 1                                             esperado=403      obtenido=403
[OK]    DELETE producto ajeno 1                                          esperado=403      obtenido=403

-- 7.3 Rol ADMIN via Gateway (200 / 404 controlado) --
[OK]    GET /api/usuarios/estadisticas con ADMIN                         esperado=200      obtenido=200
[OK]    GET /api/ventas con ADMIN                                        esperado=200      obtenido=200
[OK]    PUT /api/productos/999999 (inexistente -> 404)                   esperado=404      obtenido=404

============================================================================
 RESUMEN A-14: Exitos=43 | Fallos=0
============================================================================
```


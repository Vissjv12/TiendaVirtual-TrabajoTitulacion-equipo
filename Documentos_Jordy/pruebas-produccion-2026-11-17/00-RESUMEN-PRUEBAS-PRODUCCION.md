# Resumen Ejecutivo de Pruebas en Producción

- **Fecha de ejecución:** 17 de noviembre de 2026
- **URL probada:** `https://tienda.gadmsigchos.gob.ec:444/landing`
- **Commit desplegado en `main`:** `b60b307`
- **Carpeta de evidencias:** `documentos-trabajo/pruebas-produccion-2026-11-17/`

---

## 1. Semáforo general de las 7 pruebas solicitadas

| # | Prueba | Estado | Resultado resumido | Documento |
|:-:|:---|:---:|---|---|
| **1** | Validar el Gateway real | **Aprobada** *(TLS/HTTP + Chrome)* | TLS 1.3 válido, redirección `301` `:80 -> :444`, rutas internas `:8080` y externas `:444` operativas, `limit_req` activo, `403`/`404`/`401` correctos. Verificada en Chrome sobre `/landing`, `/products`, `/checkout` y `/auth/login2` (incluyendo Google OAuth `200`); solo hallazgos menores de fuentes Roboto 700 (`404`) y 2 imágenes `i.imgur.com/default.png`. | [01-validar-gateway-real.md](./01-validar-gateway-real.md) |
| **2** | Ejecutar flujo completo de compra | **Aprobada al 100% en vivo** *(E2E en 1 intento + idempotencia)* | Validado con **`ventaId = 7`** (reconfirmación webhook `200`, descuento único `297 -> 296`) y confirmado con compra **nueva desde cero `ventaId = 9`** (`FACT-1794933559721`, `$7.76`): `POST /api/ventas` (`201`) -> `.../preparar` (`200`) -> PayPhone (`93755596` `Approved`) -> `/checkout/payphone-confirmacion` con **`resultadoVenta = "COMPLETADA"` directo al primer intento**, stock `296 -> 295`, **PDF pre-generado en segundo plano** (`FACTURAS_TOTAL = 20`, `0` errores) y descarga `200` en `/profile/historial-compras`. | [02-flujo-completo-compra.md](./02-flujo-completo-compra.md) |
| **3** | Verificar T11 con cuentas reales de prueba | **Verificada (solo lectura)** | Índice `uk_emprendedor_empresa` (`Non_unique=0`), `0` duplicados, `MSVC_PRODUCTO_URL=http://127.0.0.1:8081` en `auth-service`, scoping `ROLE_EMP` (`empresaId=2` Sigcholac) e interfaz admin T11 (`ROLE_ADMIN` en `/administrador/usuarios/5/editar` mostrando `"Empresa vinculada"` y `"empresaId":4`) verificados en vivo. Mutaciones `409`/desvincular omitidas bajo Opción A porque las `4/4` empresas están asignadas. | [03-verificacion-t11-cuentas-prueba.md](./03-verificacion-t11-cuentas-prueba.md) |
| **4** | Confirmar PayPhone y cifrado desplegado | **Aprobada al 100% en vivo** *(E2E en 1 intento)* | `PAYPHONE_TOKEN_ENC_KEY` (`32` bytes AES-256) descifró el token `enc:v1:` en vivo; sandbox `(TEST) $7.76` probado con `venta 7` y compra nueva `venta 9` (`COMPLETADA` directa en `confirmar` + token oculto en `ROLE_EMP` + webhook `/api/payphone/confirm` `200`). | [04-confirmar-payphone-y-cifrado.md](./04-confirmar-payphone-y-cifrado.md) |
| **5** | Ejecutar pruebas de regresión por roles | **Aprobada (navegador + perímetro)** | Validados los 4 niveles en vivo (`ANÓNIMO`, `ROLE_USER`, `ROLE_EMP`, `ROLE_ADMIN`): bloqueos `401`/`403`/`404`, guardas UI de rutas restringidas, aislamiento de `ROLE_EMP` a `1` empresa (`10` productos) vs visibilidad global de `ROLE_ADMIN` (`14` usuarios, `4` empresas, `6` categorías, `39` productos, `6` ventas). Solo mutaciones cruzadas omitidas por Opción A. | [05-pruebas-regresion-por-roles.md](./05-pruebas-regresion-por-roles.md) |
| **6** | Medición comparativa de rendimiento | **Aprobada** *(benchmark en vivo)* | Comparativa real Redis OFF vs. ON en producción: `/api/categorias` bajó `MySQL Com_select` de `31` a **`1`** y latencia media de `19.45 ms` a `15.45 ms`; `/api/productos/listado` redujo `Com_select` de `61` a **`31`** (`-49.2%`) y `p95` de `35.20 ms` a `26.15 ms`, manteniendo stock en vivo. | [06-medicion-comparativa-rendimiento.md](./06-medicion-comparativa-rendimiento.md) |
| **7** | Registrar alcance y exclusiones de Redis | **Completada** *(auditoría formal)* | Documentado exactamente qué endpoints cachea Redis (`GET /api/productos/listado`, `GET /api/categorias`, `GET /api/categorias/{id}`) y todo lo que queda fuera de alcance (stock vivo, paginados, `auth-service`, `msvc-ventas`, rate limit, sesiones, HA/límite de memoria). | [07-alcance-redis-fuera-de-alcance.md](./07-alcance-redis-fuera-de-alcance.md) |

---

## 2. Datos técnicos más importantes verificados hoy

- **Estado de servicios (`9/9` `active`):** `mysqld`, `valkey`, `nginx`, `eureka-server`, `auth-service`, `msvc-producto`, `msvc-inventario`, `msvc-ventas`, `tienda-frontend`.
- **Eureka (`127.0.0.1:8761`):** `MSVC-INVENTARIO`, `LOGIN` y `MSVC-PRODUCTO` en `UP` (`msvc-ventas` corre con `eureka.client.enabled=false`).
- **Actuator `msvc-producto` (`127.0.0.1:8081`):** `{"status":"UP","groups":["cache","liveness","readiness"]}` y cache health `{"status":"UP"}`.
- **Valkey en vivo:** `PONG`, `used_memory_human=1.17M`, `DBSIZE=3` (`feriadigital:v1:{catalogo}:epoch` sin expiración, `productos:listado` `TTL ~5m`, `categorias:listado` `TTL ~15m`).
- **Exposición de red desde Internet:** solo `80` y `444` abiertos hacia la aplicación (`8080`, `8081`, `8082`, `8083`, `8761`, `3000`, `3306`, `6379` cerrados/filtrados; `firewalld` del VPS permite únicamente `80/tcp` y `444/tcp`). *Nota:* el puerto `8084` en la IP pública compartida responde `HTTP/1.1 400 Bad Request` desde un `nginx/1.26.3` institucional externo en esa IP compartida, **no** desde `auth-service` (cerrado por `firewalld` en este VPS).
- **Seguridad HTTP:** CORS bloquea orígenes no autorizados (`https://evil.example` -> `403`); Swagger, Actuator, Camunda, `engine-rest` y `sincronizar-stock` bloqueados en `403`; rutas internas de ventas devuelven `404`; rutas privadas devuelven `401` sin JWT.

---

## 3. Conclusión final y hallazgos menores observados

1. **Validación E2E definitiva de PayPhone (`ventaId = 9`, `FACT-1794933559721`, `main` `f8a2006`):** compra nueva desde cero completada en **1 solo paso** (`resultadoVenta = "COMPLETADA"` al retornar de PayPhone, descuento automático `296 -> 295`, **PDF pre-generado automáticamente en segundo plano** `FACTURAS_TOTAL = 20` con `0` errores en logs, y descarga `200` en Chrome).
2. **Idempotencia también demostrada (`ventaId = 7`):** doble llamada a `/api/payphone/confirm` (`200`) descontó el stock una sola vez (`297 -> 296`).
3. **Alcance omitido voluntariamente (Opción A):** subida real de comprobante DeUna/Transferencia, mutaciones T11 (`409`/desvincular con las `4/4` empresas asignadas) y `PUT`/`DELETE` cruzados entre empresas desde navegador.
3. **Detalles menores de frontend detectados en Chrome (no bloqueantes, útiles para pulido posterior):**
   - `/landing`: faltan 2 archivos locales de fuente Roboto 700 (`404`).
   - `/products` y `/emprendedor/productos`: `Queso tipo finca | 1000g` (ID `18`) y `500g` (ID `17`) usan `https://i.imgur.com/default.png` (placeholder roto de Imgur).
   - `/emprendedor`: las tarjetas resumen del dashboard inicial muestran datos de plantilla, aunque sus submódulos (`/productos`, `/inventario`, `/ventas`) leen datos reales.
   - `/emprendedor/inventario` y `/administrador/inventario`: la columna `"Ubicación/Estado"` muestra el badge `"Sin stock"` aunque `"Stock Actual"` muestra existencias reales.
   - `/administrador/usuarios/[id]/editar`: el botón `"Cancelar"` dispara `PUT` sin cambios (falta `type="button"`).

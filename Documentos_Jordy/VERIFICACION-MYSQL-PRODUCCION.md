# Auditoría Completa MySQL de Producción (Solo Lectura)

- **Fecha/hora de consulta (servidor):** `2026-11-16 18:44:37 CST` (`19:45` hora Ecuador)
- **Servidor:** `tienda.gadmsigchos.gob.ec` (`MySQL 8.4.9`)
- **Alcance:** Auditoría completa de las **4 bases de datos** (`auth_service`, `productos`, `inventario`, `ventas`), cuentas `mysql.user`, compatibilidad de columnas para `ddl-auto=validate` contra las 14 entidades JPA de `main` y consistencia con archivos en disco (`uploads/` y `facturas/`).
- **Seguridad:** Consultas exclusivamente de **solo lectura**, sin exponer tokens, contraseñas, hashes ni datos personales (PII).

---

## Resumen Ejecutivo Global

| Área auditada | Resultado observado | Estado |
|---|---|---|
| Esquema vs `ddl-auto=validate` | Las **14 tablas de negocio** y sus columnas coinciden en `auth_service`, `productos`, `inventario` y `ventas` (+ `49` tablas Camunda `ACT_*`) | ✅ Listo |
| Migraciones previas (`db-10`, `db-11`) | `costo_envio`, `payphone_token` (`text`) y `password_reset_codes` **ya existen** | ✅ Listo |
| Índice **T11** (`uk_emprendedor_empresa`) | **0 duplicados** (`4` emprendedores, `4` empresas distintas); el índice aún no está creado | ⏳ Aplicar tras respaldo |
| Cuentas MySQL | `admin`@`localhost`, `admin`@`%` y `root`@`localhost` (`caching_sha2_password`), permisos completos en las 4 BD | ⚠️ Cerrar `3306` en `firewalld` por existir `admin@%` |
| `max_connections` | Límite `151` / uso actual y pico `31` | ⚠️ Suma Hikari de `main` llega a `210` (ajustar pool o `max_connections`) |
| Usuarios y hashes BCrypt (`A-10`) | **`13/13` usuarios activos** (`1` `ROLE_ADMIN`, `4` `ROLE_EMP`, `8` `ROLE_USER`) con hash BCrypt válido (`60` chars: `11` `$2a$`, `2` `$2b$`) | ✅ Listo |
| PayPhone en BD | **`1` configuración** con `payphone_token` en **texto plano** (`0` `enc:v1:`), `payphone_app_id` y `costo_envio` | 🔐 Obligatorio definir `PAYPHONE_TOKEN_ENC_KEY` antes del primer arranque |
| QR Deuna en BD vs disco | `qr_deuna_url` registrado y archivo presente físicamente en `/var/www/tienda-virtual-backend/auth-service/uploads/qr-deuna/` (`_test_qr.png`) | ✅ Consistente (confirmar si es QR definitivo o de prueba) |
| Catálogo, propiedad e inventario | `6/6` categorías, `4/4` empresas, **`39/39` productos activos** con **`39/39` inventarios activos** (`> 0`), `0` huérfanos y propiedad `empresa_id` ↔ `emprendedor_id` 100% alineada | ✅ Íntegro |
| Ventas, pagos y logística | `7/7` clientes activos, `6` ventas (`0` huérfanas), `11` detalles (`0` huérfanos), `6` pagos, `7` registros logísticos, `17` PDFs en `facturas/` | ℹ️ `3` ventas viejas de julio tienen `estado_pago=PENDIENTE` aunque su fila en `pagos` dice `APROBADO` |

---

## 1. Cuentas MySQL, Privilegios y Conexiones

### Usuarios y estado (`mysql.user`)
| `user` | `host` | `plugin` | `account_locked` | `password_expired` |
|---|---|---|---|---|
| `admin` | `%` | `caching_sha2_password` | `N` | `N` |
| `admin` | `localhost` | `caching_sha2_password` | `N` | `N` |
| `root` | `localhost` | `caching_sha2_password` | `N` | `N` |

* `'admin'@'%'` posee privilegios completos de esquema (`SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CREATE`, `ALTER`, `DROP`, `INDEX`, `REFERENCES`, `TRIGGER`, `EVENT`, `EXECUTE`, `LOCK TABLES`, `CREATE VIEW`, `SHOW VIEW`, `CREATE ROUTINE`, `ALTER ROUTINE`, `CREATE TEMPORARY TABLES`) sobre `auth_service`, `productos`, `inventario` y `ventas`.
* **Nota de seguridad:** como `mysqld` escucha en `*:3306` y existe `'admin'@'%'`, confirmar con `firewall-cmd --list-all` que el puerto `3306` esté bloqueado hacia Internet.

### Límites y uso de conexiones
| Variable / Estado | Valor |
|---|---|
| `max_connections` | **`151`** |
| `Threads_connected` | `31` |
| `Max_used_connections` | `31` |

* **Observación:** en `main`, la suma de `spring.datasource.hikari.maximum-pool-size` de los 4 servicios asciende a **`210`** (`msvc-producto=100`, `msvc-inventario=50`, `msvc-ventas=50`, `demo=10`), por encima de `151`. Se recomienda ajustar el tamaño de pool vía `.env` o subir `max_connections` (ej. `250`).

---

## 2. Validación de Esquema por Base de Datos (`ddl-auto=validate`)

### Tablas verificadas en las 4 bases
| Base (`TABLE_SCHEMA`) | Tablas totales | Tablas de negocio verificadas |
|---|---|---|
| `auth_service` | `5` | `usuarios`, `usuario_roles`, `emprendedores`, `configuracion_metodos_pago`, `password_reset_codes` |
| `productos` | `3` | `categorias`, `empresas`, `productos` |
| `inventario` | `2` | `inventarios`, `movimientos_inventario` |
| `ventas` | `54` | `clientes`, `ventas`, `detalles_venta`, `pagos`, `seguimiento_logistica` + **`49` tablas Camunda `ACT_*`** (`~130,788` filas estimadas) |

* Todas las columnas requeridas por las 14 entidades JPA de `main` existen en la base real (`costo_envio` `decimal(10,2)`, `payphone_token` `text`, `password_reset_codes`, `seguimiento_logistica`, etc.).
* Único cambio de esquema pendiente: agregar el índice único **`uk_emprendedor_empresa`** sobre `auth_service.emprendedores(empresa_id)` ([`T11-emprendedores-empresa-unica.sql`](../scripts/prod/migraciones/T11-emprendedores-empresa-unica.sql)).

---

## 3. Auditoría de `auth_service` (Sin PII ni Secretos)

### Usuarios, roles y hashes BCrypt
* **Total usuarios:** `13` (`13` activos, `0` inactivos).
* **Formato de contraseña:** **`13/13` válidos en BCrypt de 60 caracteres** (`11` con prefijo `$2a$` y `2` con prefijo `$2b$`).
* **Roles asignados (`usuario_roles`):**
  * `ROLE_ADMIN`: `1`
  * `ROLE_EMP`: `4`
  * `ROLE_USER`: `8`
* **`password_reset_codes`:** `3` registros históricos (`1` verificado, `1` usado).

### Emprendedores y pre-chequeo T11 (`emprendedores`)
| `emprendedor_id` | `usuario_id` | `empresa_id` | `emp_activo` | `estado_usuario` | `estado_empresa` |
|---|---|---|---|---|---|
| `1` | `2` | `1` | `1` | `OK` | `OK` |
| `2` | `3` | `2` | `1` | `OK` | `OK` |
| `3` | `4` | `3` | `1` | `OK` | `OK` |
| `4` | `5` | `4` | `1` | `OK` | `OK` |

* **Duplicados T11 (`HAVING COUNT(*) > 1`):** **0 filas** (`4` filas, `4` con `empresa_id`, `0` `NULL`, `4` empresas distintas).
* **Índices actuales en `emprendedores`:** `PRIMARY (id)`, `usuario_id (NON_UNIQUE=0)`, `idx_emprendedores_usuario_id (NON_UNIQUE=1)`, `idx_emprendedores_empresa_id (NON_UNIQUE=1)`. Falta `uk_emprendedor_empresa`.

### Métodos de pago (`configuracion_metodos_pago`) y disco
| `total_configuraciones` | `con_payphone_token` | `token_ya_cifrado_enc_v1` | `token_texto_plano` | `con_payphone_app_id` | `con_qr_deuna` | `con_costo_envio` |
|---|---|---|---|---|---|---|
| `1` | `1` | `0` | **`1`** | `1` | `1` | `1` |

* **Consistencia con disco (`uploads/qr-deuna`):** la ruta en BD (`/uploads/qr-deuna/2_8d69ec19-f893-4db5-9f65-5f4859ae7a30_test_qr.png`) **existe físicamente** en `/var/www/tienda-virtual-backend/auth-service/uploads/qr-deuna/2_8d69ec19-f893-4db5-9f65-5f4859ae7a30_test_qr.png` (`1` archivo en `uploads/`).
* **Pendiente operativo:**
  1. Configurar `PAYPHONE_TOKEN_ENC_KEY` antes del primer arranque de `auth-service` para que `PayphoneTokenMigracion` cifre ese token en texto plano a `enc:v1:...`.
  2. Confirmar con el administrador si el QR actual (`_test_qr.png`) y el token PayPhone son de prueba o productivos.

---

## 4. Auditoría de `productos` e `inventario`

### Totales e integridad referencial
| Tabla | Total | Activos |
|---|---|---|
| `productos.categorias` | `6` | `6` |
| `productos.empresas` | `4` | `4` |
| `productos.productos` | `39` | `39` |
| `inventario.inventarios` | `39` | `39` |

* **Cruce de integridad en `productos`:** `0` con categoría inexistente, `0` con empresa inexistente, `0` sin emprendedor válido, `0` con precio inválido (`< 0` o `NULL`), `0` sin imagen.
* **Cruce productos activos vs inventarios:** **`39/39` activos** tienen registro activo en `inventario.inventarios` con **stock `> 0`** (`0` sin inventario, `0` inventario inactivo, `0` en stock `0`, `0` inventarios huérfanos).

### Alineación `empresa_id` ↔ `emprendedor_id` (clave para seguridad multi-tenant A-03/A-07)
| `empresa_id` | `prod_emprendedor_id` | `total_prods` | `emp_id_dueno_empresa` | `usuario_id_dueno_empresa` |
|---|---|---|---|---|
| `1` | `1` | `13` | `1` | `2` |
| `2` | `2` | `10` | `2` | `3` |
| `3` | `3` | `8` | `3` | `4` |
| `4` | `4` | `8` | `4` | `5` |

Todos los productos apuntan correctamente al `emprendedor_id` dueño de su respectiva `empresa_id`.

### Historial en `inventario.movimientos_inventario` (`54` registros)
| `tipo_movimiento` | `total` | `primero` | `ultimo` |
|---|---|---|---|
| `ENTRADA` | `39` | `2026-07-04 10:48:07` | `2026-07-04 10:48:07` |
| `SALIDA` | `11` | `2026-07-04 12:52:09` | `2026-07-17 11:39:43` |
| `AJUSTE` | `4` | `2026-07-06 09:24:48` | `2026-07-06 11:58:09` |

---

## 5. Auditoría de `ventas` (Negocio + Camunda + Facturas en Disco)

* **`clientes`:** `7` registrados (`7/7` activos).
* **`ventas` (`6` registros, entre `2026-07-04 12:52:08` y `2026-07-17 11:39:43`):** `0` huérfanos sin cliente.
  * `estado=COMPLETADA`, `metodo_pago=TARJETA`, `estado_pago=PENDIENTE`: `3` ventas (`$40.54`).
  * `estado=COMPLETADA`, `metodo_pago=TARJETA`, `estado_pago=APROBADO`: `3` ventas (`$12.77`).
* **`detalles_venta` (`11` registros):** `0` sin venta, `0` con producto inexistente.
* **`pagos` (`6` registros):** todos con `metodo_pago=TARJETA` y `estado_pago=APROBADO` (`6`).
  * *(Observación histórica: `3` ventas antiguas de julio quedaron en `ventas.estado_pago='PENDIENTE'` aunque su registro en `pagos` marca `APROBADO`. No afecta el arranque ni futuras ventas).*
* **`seguimiento_logistica` (`7` registros):** `6` en `PAGO_APROBADO` y `1` en `LISTO_PARA_ENVIO`.
* **Facturas PDF en disco:** hay **`17` archivos `.pdf`** en `/var/www/tienda-virtual-backend/msvc-ventas/facturas` (restos de pruebas previas de julio; conservar carpeta en respaldos).
* **Camunda BPM:** `49` tablas `ACT_*` presentes en `ventas` (`~130,788` filas estimadas).

# Registro T07 (optimización de autorización de inventario) y T01 (preparación de respaldos)

Fecha: 2026-11-16. Sin commit.

## Decisiones del equipo aplicadas

- **Acceso global del administrador (T13 y regla general):** se mantiene. ROLE_ADMIN puede gestionar productos, inventario y empresas de cualquier emprendedor para corregir errores. No se modificó `ProductoAuthorizationService`.
- **Categorías:** siguen siendo creadas solo por ADMIN, como quedó en T07.
- **T01:** solo preparación. Scripts, unidades systemd y guía listos; no se instalan hasta la puesta en producción.

## T07: autorización de inventario en lote

### Problema
`canManageAllInventarios` validaba cada inventario por separado: una consulta a la base y una llamada HTTP a msvc-producto por inventario. Un emprendedor con 200 productos generaba 200 llamadas en cada carga o filtro de Movimientos. Además, la lista de inventarios a consultar la decidía el navegador.

### Cambios

| Componente | Cambio |
|---|---|
| msvc-producto `GET /api/productos/empresa/{empresaId}/ids` | Nuevo. Devuelve en una sola consulta (`SELECT p.id ... WHERE p.empresa.id = :empresaId`) los ids de todos los productos de la empresa, activos e inactivos. Exige autenticación en el filtro (401 anónimo) y `canManageCompany`: ADMIN o el emprendedor de esa empresa (403 para otra). |
| inventario `ProductoClient` | Nuevo `obtenerIdsProductosPorEmpresa`. El fallback devuelve `null`, que se trata como **denegar**. |
| inventario `InventarioRepository` | `findProductoIdPorInventarioId(ids)` y `findIdsByProductoIdIn(productos)`: consultas agrupadas con `IN`, que solo leen las columnas necesarias. |
| `InventarioAuthorizationService` | Inyección por constructor (`@RequiredArgsConstructor`); se eliminó `@Autowired(required = false)`. Si falta una dependencia, la aplicación no arranca, en lugar de denegar en silencio. La empresa siempre sale del claim `empresaId` del JWT. |
| `canManageAllInventarios` | 1 consulta agrupada + 1 llamada a productos. Si algún inventario no existe, es nulo o pertenece a otra empresa, se deniega todo el lote. |
| `canManageSalidaLote` | 1 llamada a productos para todo el lote (antes, una por ítem). |
| `inventariosDeEmpresaDelToken` | Nuevo. Calcula en el servidor los inventarios de la empresa del JWT. |
| `MovimientoInventarioController` | Inyección por constructor. En `GET /api/movimientos/paginado`, para ROLE_EMP el conjunto permitido se calcula en el servidor y los filtros del navegador (`inventarioId`, `inventarioIds`) solo pueden **restringirlo**. Un inventario ajeno sigue respondiendo **403**. |

### Cambio de comportamiento
- `GET /api/movimientos/paginado` como emprendedor **sin filtro** ya no responde 403: devuelve los movimientos de **sus** inventarios. Si la empresa no tiene inventarios, se devuelve una página vacía sin consultar la base, porque una lista vacía no debe convertirse en "sin filtro".
- Si msvc-producto no responde, la consulta del emprendedor responde 403 (fail-closed), igual que antes.
- La pantalla del emprendedor sigue funcionando sin cambios: envía `inventarioIds` propios, que ahora se validan contra el conjunto del servidor.

### Pruebas

| Suite | Casos | Verifica |
|---|---|---|
| `InventarioAutorizacionLoteTest` (nueva) | 8 | Varios inventarios propios con **1 llamada y 1 consulta** (sin `obtenerProducto` ni `findById`); un inventario de otra empresa deniega todo el lote; inexistente, nulo o lista vacía deniegan; sin claim no se llama a productos; servicio caído o fallback deniegan; ADMIN sin llamadas; conjunto de inventarios por empresa (empresas 1 y 2); salida en lote con 1 llamada y rechazo de productos ajenos. |
| `MovimientoPropiedadTest` (actualizada) | 3 | Sin filtro, el emprendedor obtiene 200 limitado a su inventario (`listarPaginado(null, [10], ...)`); los inventarios ajenos siguen respondiendo 403. |
| `ProductoControllerApiAuthorizationTest` (caso nuevo) | 1 | `/empresa/{id}/ids`: 401 anónimo, 403 otra empresa (sin llamar al servicio), 200 con los ids. |

Resultados: **msvc-inventario 56/56**; **msvc-producto 45/46**. La prueba que falla es `MsvcProductoApplicationTests`, que requiere MySQL y ya fallaba antes.

## T01: preparación de respaldos (AlmaLinux + systemd)

Archivos en `scripts/prod/`:

| Archivo | Función |
|---|---|
| `backup-db.sh` | Un `.sql.gz` por base (`auth_service`, `productos`, `inventario`, `ventas`), con `--single-transaction`; escritura atómica en `.tmp`; validación (código de salida, `gzip -t`, marca `Dump completed`, tablas > 0); `SHA256SUMS` y `MANIFEST`; `flock`; rotación por días que conserva siempre los `MIN_KEEP` más recientes. |
| `restore-test-db.sh` | Verifica SHA256 y restaura cada base en `restore_test_<base>_<marca>`. Compara el número de tablas, ejecuta `CHECK TABLE` y registra el conteo de filas. Elimina las bases temporales aunque falle. |
| `systemd/tienda-backup.{service,timer}` | Respaldo diario a las 02:30 (`Persistent=true`), con usuario `tienda-backup` y endurecimiento (`ProtectSystem=strict`, solo escribe en `/var/backups/tienda`). |
| `systemd/tienda-restore-test.{service,timer}` | Prueba de restauración los domingos a las 04:00, con acceso de solo lectura a los respaldos. |
| `backup.env.example` | Configuración, sin credenciales. |
| `INSTALACION-RESPALDOS.md` | Usuarios MySQL de privilegio mínimo, instalación, operación y restauración real manual. |
| `pruebas/simular-respaldo.sh` | Simulación local con `mysqldump`/`mysql` falsos. |

**Credenciales:** nunca en los scripts. Están en `/etc/tienda-backup/my.cnf` y `restore.cnf` (permisos 600; los scripts se niegan a ejecutarse con otros permisos) y se pasan con `--defaults-extra-file`, por lo que no aparecen en la línea de comandos ni en los logs. El usuario de prueba de restauración solo tiene privilegios sobre `restore\_test\_%`: no puede tocar las bases reales.

**Validación local:** `bash -n` correcto en ambos scripts. La simulación da 25/25. Cubre:
- Respaldo correcto: 4 volcados, SHA256 válido, sin `CREATE DATABASE` y sin contraseña en el log.
- Permisos 644 rechazados.
- Fallo de `mysqldump`, sin que quede respaldo ni `.tmp`.
- Volcado truncado detectado.
- Rotación con `MIN_KEEP`, sin tocar otros directorios.
- Restauración solo sobre bases `restore_test_*`, con borrado de las temporales y eliminación de los `DEFINER`.
- Tablas faltantes, `CHECK TABLE` con error y respaldo alterado (rechazado por SHA256 antes de crear bases).

No se probó contra MySQL real.

`.gitattributes` fuerza saltos de línea LF en `scripts/prod`, para que los scripts y unidades funcionen al clonar desde Windows.

**Fuera de alcance (documentado en la guía):**
- Copia de los respaldos fuera del servidor.
- Respaldo de los archivos subidos (QR, comprobantes, facturas).
- Cifrado de los respaldos que salgan del servidor.

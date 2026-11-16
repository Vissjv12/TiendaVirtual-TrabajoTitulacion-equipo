# Respaldos de base de datos en producción (T01)

Preparación para el servidor AlmaLinux con MySQL nativo y systemd. **No se ha instalado ni ejecutado en el servidor.** Se aplica cuando el equipo prepare la puesta en producción.

## Qué hace

| Pieza | Función |
|---|---|
| `backup-db.sh` | Respalda `auth_service`, `productos`, `inventario` y `ventas`: un `.sql.gz` por base, `SHA256SUMS` y `MANIFEST`. Rota los respaldos antiguos. |
| `restore-test-db.sh` | Restaura el último respaldo (o el indicado) en bases temporales `restore_test_*`, las valida y las elimina. |
| `tienda-backup.timer` | Respaldo diario a las 02:30. |
| `tienda-restore-test.timer` | Prueba de restauración los domingos a las 04:00. |

Estructura resultante:

```
/var/backups/tienda/
  20261116_023012/
    auth_service.sql.gz
    inventario.sql.gz
    productos.sql.gz
    ventas.sql.gz
    SHA256SUMS
    MANIFEST
```

### Garantías del respaldo
- **Consistencia:** `--single-transaction`, sin bloquear las tablas InnoDB mientras la tienda funciona.
- **Nunca queda un respaldo a medias con nombre válido:** se escribe en `AAAAMMDD_HHMMSS.tmp` y solo se renombra si todas las bases se volcaron y validaron.
- **Validación de cada volcado:** código de salida de `mysqldump` (con `pipefail`), integridad gzip, marca final `-- Dump completed`, al menos una tabla y `SHA256SUMS`.
- **Una ejecución a la vez:** `flock`, por si coinciden el timer y una ejecución manual.
- **Rotación:** borra los respaldos con más de `RETENTION_DAYS` días (14 por defecto), pero conserva siempre los `MIN_KEEP` más recientes (3), aunque sean antiguos. Solo toca directorios con formato `AAAAMMDD_HHMMSS`.

### Validación de la restauración
1. `sha256sum -c`: el respaldo no se modificó.
2. Restauración de cada base en `restore_test_<base>_<marca>`. **Nunca toca las bases reales.**
3. El número de tablas restauradas coincide con los `CREATE TABLE` del volcado.
4. `CHECK TABLE` de cada tabla devuelve `OK`.
5. Se registra el conteo de filas de cada tabla en el log.
6. Se eliminan las bases temporales, también si algo falla.

## Credenciales (nunca en los scripts)

Se usan dos usuarios MySQL con privilegios mínimos. Sus contraseñas solo existen en archivos de opciones de MySQL con permisos `600`, legibles por el usuario de sistema `tienda-backup`. Los scripts se niegan a ejecutarse si esos archivos tienen otros permisos.

```sql
-- Usuario de respaldo: solo lectura
CREATE USER 'tienda_backup'@'localhost' IDENTIFIED BY '<CONTRASENA_GENERADA>';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES
  ON auth_service.* TO 'tienda_backup'@'localhost';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES
  ON productos.*    TO 'tienda_backup'@'localhost';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES
  ON inventario.*   TO 'tienda_backup'@'localhost';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES
  ON ventas.*       TO 'tienda_backup'@'localhost';
-- MySQL 8.0.20+: necesario para --routines
GRANT SHOW_ROUTINE ON *.* TO 'tienda_backup'@'localhost';

-- Usuario de prueba de restauración: solo bases restore_test_*
CREATE USER 'tienda_restore_test'@'localhost' IDENTIFIED BY '<OTRA_CONTRASENA_GENERADA>';
GRANT ALL PRIVILEGES ON `restore\_test\_%`.* TO 'tienda_restore_test'@'localhost';
```

El usuario de prueba **no tiene ningún privilegio sobre las bases reales**. Aunque el script fallara, no podría modificarlas.

Para generar las contraseñas, sin dejarlas en el historial:

```bash
openssl rand -base64 32
```

## Instalación (como root)

```bash
# 1. Usuario de sistema sin login
useradd --system --no-create-home --shell /sbin/nologin tienda-backup

# 2. Scripts (repositorio desplegado en /opt/tienda): propiedad de root, solo ejecutables
chown root:root /opt/tienda/scripts/prod/backup-db.sh /opt/tienda/scripts/prod/restore-test-db.sh
chmod 755 /opt/tienda/scripts/prod/backup-db.sh /opt/tienda/scripts/prod/restore-test-db.sh

# 3. Configuración y credenciales
install -d -o root -g tienda-backup -m 750 /etc/tienda-backup
install -o root -g tienda-backup -m 640 /opt/tienda/scripts/prod/backup.env.example /etc/tienda-backup/backup.env

# Crear los archivos de credenciales VACÍOS con permisos 600 y luego editarlos con vi,
# para que las contraseñas no queden en el historial de la terminal
install -o tienda-backup -g tienda-backup -m 600 /dev/null /etc/tienda-backup/my.cnf
install -o tienda-backup -g tienda-backup -m 600 /dev/null /etc/tienda-backup/restore.cnf
vi /etc/tienda-backup/my.cnf
#   [client]
#   user=tienda_backup
#   password=<CONTRASENA_GENERADA>
#   host=localhost
vi /etc/tienda-backup/restore.cnf
#   [client]
#   user=tienda_restore_test
#   password=<OTRA_CONTRASENA_GENERADA>
#   host=localhost

# 4. Directorio de respaldos
install -d -o tienda-backup -g tienda-backup -m 700 /var/backups/tienda

# 5. Unidades systemd
cp /opt/tienda/scripts/prod/systemd/tienda-*.service /opt/tienda/scripts/prod/systemd/tienda-*.timer /etc/systemd/system/
systemctl daemon-reload
systemd-analyze verify /etc/systemd/system/tienda-backup.service /etc/systemd/system/tienda-restore-test.service

# 6. Primera ejecución manual y prueba de restauración ANTES de activar los timers
systemctl start tienda-backup.service      && journalctl -u tienda-backup.service -n 50 --no-pager
systemctl start tienda-restore-test.service && journalctl -u tienda-restore-test.service -n 80 --no-pager

# 7. Activar los timers
systemctl enable --now tienda-backup.timer tienda-restore-test.timer
systemctl list-timers 'tienda-*'
```

**MariaDB:** en `backup.env`, dejar `MYSQLDUMP_EXTRA_OPTS=` vacío y omitir `GRANT SHOW_ROUTINE`. En las unidades, `After=mysqld.service` puede cambiarse por `mariadb.service`.

**SELinux (AlmaLinux):** si `/var/backups/tienda` o `/opt/tienda` tienen un contexto no estándar y el servicio falla con `Permission denied`, revisar con `ausearch -m avc -ts recent` antes de cambiar contextos.

## Operación

| Acción | Comando |
|---|---|
| Respaldo manual | `systemctl start tienda-backup.service` |
| Probar el último respaldo | `systemctl start tienda-restore-test.service` |
| Probar un respaldo concreto | `sudo -u tienda-backup env $(grep -v '^#' /etc/tienda-backup/backup.env \| xargs) /opt/tienda/scripts/prod/restore-test-db.sh /var/backups/tienda/20261116_023012` |
| Ver resultados | `journalctl -u tienda-backup.service -u tienda-restore-test.service --since today` |
| Próximas ejecuciones | `systemctl list-timers 'tienda-*'` |

Una ejecución fallida deja la unidad en estado `failed` (`systemctl --failed`). Se recomienda conectar ese estado a una alerta, por ejemplo con `OnFailure=` y un correo.

## Restauración real (desastre)

La restauración sobre las bases reales **no está automatizada a propósito**. Procedimiento manual:

1. Detener los servicios de la tienda (`systemctl stop` de los 4 microservicios).
2. Verificar el respaldo: `cd /var/backups/tienda/<marca> && sha256sum -c SHA256SUMS`.
3. Por cada base, como administrador de MySQL: `gunzip -c <base>.sql.gz | mysql <base>`.
4. Iniciar los servicios y comprobar el login, el catálogo y una venta de prueba.

## Pendiente fuera del alcance de este script

- **Copia fuera del servidor:** un respaldo en el mismo disco no protege ante la pérdida del servidor. Se recomienda copiar `/var/backups/tienda` a otro equipo o almacenamiento del GAD (por ejemplo, `rsync` en un timer propio).
- **Archivos subidos** (QR de Deuna, comprobantes, facturas PDF): están en disco, no en MySQL, y deben incluirse en esa copia.
- **Cifrado de los respaldos** si se copian fuera del servidor. Contienen datos personales de clientes.

#!/usr/bin/env bash
# ============================================================================
# T01 - Respaldo de las bases de datos de la Tienda Virtual (AlmaLinux + systemd)
#
# - Un volcado por base (sin CREATE DATABASE/USE): cada archivo puede restaurarse
#   en una base con otro nombre (prueba de restauración en base temporal).
# - Consistente sin bloquear tablas InnoDB (--single-transaction).
# - Escritura atómica: se trabaja en un directorio .tmp y solo se renombra si TODO
#   salió bien. Un respaldo incompleto nunca queda con el nombre definitivo.
# - Validación: código de salida de mysqldump, marca "Dump completed", gzip -t y SHA256SUMS.
# - Rotación: elimina respaldos con más de RETENTION_DAYS días, conservando siempre
#   los MIN_KEEP más recientes.
#
# Credenciales: NUNCA en este script. Se leen de un archivo de opciones de MySQL
# protegido (MYSQL_DEFAULTS_FILE, por defecto /etc/tienda-backup/my.cnf, permisos 600).
# Configuración: variables de entorno (systemd: EnvironmentFile=/etc/tienda-backup/backup.env).
# ============================================================================
set -Eeuo pipefail
umask 077

MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-/etc/tienda-backup/my.cnf}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/tienda}"
DATABASES="${DATABASES:-auth_service productos inventario ventas}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
MIN_KEEP="${MIN_KEEP:-3}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"
# MySQL 8: --set-gtid-purged=OFF permite restaurar en otro servidor/base. En MariaDB dejar vacio.
MYSQLDUMP_EXTRA_OPTS="${MYSQLDUMP_EXTRA_OPTS---set-gtid-purged=OFF}"
LOCK_FILE="${LOCK_FILE:-${BACKUP_DIR}/.backup.lock}"

log()  { printf '%s [backup] %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*"; }
fail() { log "ERROR: $*"; exit 1; }

# ---------------------------------------------------------------- comprobaciones previas
[[ -r "$MYSQL_DEFAULTS_FILE" ]] || fail "No se puede leer el archivo de credenciales $MYSQL_DEFAULTS_FILE"
permisos=$(stat -c '%a' "$MYSQL_DEFAULTS_FILE")
[[ "$permisos" =~ ^[46]00$ ]] || fail "$MYSQL_DEFAULTS_FILE debe tener permisos 600 o 400 (tiene $permisos)"
[[ "$RETENTION_DAYS" =~ ^[0-9]+$ && "$MIN_KEEP" =~ ^[0-9]+$ ]] || fail "RETENTION_DAYS y MIN_KEEP deben ser enteros"
(( MIN_KEEP >= 1 )) || fail "MIN_KEEP debe ser al menos 1"
for comando in "$MYSQLDUMP_BIN" gzip sha256sum flock find; do
    command -v "$comando" >/dev/null 2>&1 || fail "Falta el comando requerido: $comando"
done
read -r -a BASES <<< "$DATABASES"
read -r -a OPCIONES_EXTRA <<< "$MYSQLDUMP_EXTRA_OPTS"
(( ${#BASES[@]} > 0 )) || fail "DATABASES está vacío"
for base in "${BASES[@]}"; do
    [[ "$base" =~ ^[A-Za-z0-9_]+$ ]] || fail "Nombre de base no válido: $base"
done

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

# Un solo respaldo a la vez (timer + ejecución manual simultáneos)
exec 9>"$LOCK_FILE"
flock -n 9 || fail "Ya hay un respaldo en ejecución (lock: $LOCK_FILE)"

marca=$(date '+%Y%m%d_%H%M%S')
destino="${BACKUP_DIR}/${marca}"
temporal="${destino}.tmp"
[[ ! -e "$destino" ]] || fail "Ya existe $destino"
trap 'rm -rf -- "$temporal"' EXIT
mkdir "$temporal"

log "Inicio del respaldo en $destino (bases: ${BASES[*]})"

# ---------------------------------------------------------------- volcado por base
for base in "${BASES[@]}"; do
    archivo="${temporal}/${base}.sql.gz"
    log "Volcando $base"
    # --defaults-extra-file debe ser la PRIMERA opción. Las credenciales no aparecen en la línea de comandos.
    "$MYSQLDUMP_BIN" --defaults-extra-file="$MYSQL_DEFAULTS_FILE" \
        --single-transaction --quick --routines --triggers --events \
        --hex-blob --no-tablespaces --default-character-set=utf8mb4 \
        "${OPCIONES_EXTRA[@]}" \
        "$base" | gzip -c -6 > "$archivo"
    # pipefail ya aborta si mysqldump falla; además se valida el contenido
    gzip -t "$archivo" || fail "$base: el archivo comprimido está dañado"
    gzip -dc "$archivo" | tail -n 3 | grep -q -- '-- Dump completed' \
        || fail "$base: el volcado no terminó correctamente (falta la marca 'Dump completed')"
    tablas=$(gzip -dc "$archivo" | grep -c '^CREATE TABLE' || true)
    (( tablas > 0 )) || fail "$base: el volcado no contiene tablas"
    log "  $base: ${tablas} tablas, $(stat -c '%s' "$archivo") bytes comprimidos"
done

# ---------------------------------------------------------------- integridad y manifiesto
(
    cd "$temporal"
    sha256sum -- *.sql.gz > SHA256SUMS
    sha256sum -c --quiet SHA256SUMS
)
{
    echo "fecha=${marca}"
    echo "host=$(hostname)"
    echo "bases=${BASES[*]}"
    echo "mysqldump=$("$MYSQLDUMP_BIN" --version 2>/dev/null | head -n 1)"
} > "${temporal}/MANIFEST"

mv -- "$temporal" "$destino"
trap - EXIT
log "Respaldo completado: $destino"

# ---------------------------------------------------------------- rotación
# Solo directorios con el formato AAAAMMDD_HHMMSS (nunca otros archivos de BACKUP_DIR).
mapfile -t respaldos < <(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d \
    -regextype posix-extended -regex '.*/[0-9]{8}_[0-9]{6}' -printf '%f\n' | sort -r)
eliminados=0
for (( i = MIN_KEEP; i < ${#respaldos[@]}; i++ )); do
    nombre="${respaldos[$i]}"
    fecha_respaldo=$(date -d "${nombre:0:8}" '+%s' 2>/dev/null) || continue
    limite=$(date -d "-${RETENTION_DAYS} days" '+%s')
    if (( fecha_respaldo < limite )); then
        rm -rf -- "${BACKUP_DIR:?}/${nombre}"
        eliminados=$((eliminados + 1))
        log "Rotación: eliminado ${nombre}"
    fi
done
# Restos de ejecuciones interrumpidas
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '*.tmp' -mmin +120 -exec rm -rf -- {} +
log "Rotación terminada: ${eliminados} respaldo(s) eliminado(s); conservados $(( ${#respaldos[@]} - eliminados ))"

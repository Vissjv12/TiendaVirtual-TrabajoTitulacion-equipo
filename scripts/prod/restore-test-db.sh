#!/usr/bin/env bash
# ============================================================================
# T01 - Prueba de restauración de un respaldo en bases TEMPORALES
#
# Uso: restore-test-db.sh [DIRECTORIO_DEL_RESPALDO]
#      Sin argumento usa el respaldo más reciente de BACKUP_DIR.
#
# Para cada base del respaldo:
#   1. Verifica SHA256SUMS (el archivo no cambió desde que se creó).
#   2. Crea una base temporal  restore_test_<base>_<marca>  (NUNCA toca las bases reales).
#   3. Restaura el volcado en ella.
#   4. Valida: número de tablas restauradas = número de CREATE TABLE del volcado,
#      CHECK TABLE sin errores y conteo de filas por tabla (queda en el log).
#   5. Elimina la base temporal (también si algo falla).
#
# Credenciales: archivo de opciones protegido (RESTORE_DEFAULTS_FILE, por defecto
# /etc/tienda-backup/restore.cnf), con un usuario que SOLO puede crear y borrar bases
# restore_test_% (ver INSTALACION-RESPALDOS.md). Nunca en este script.
# ============================================================================
set -Eeuo pipefail
umask 077

RESTORE_DEFAULTS_FILE="${RESTORE_DEFAULTS_FILE:-/etc/tienda-backup/restore.cnf}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/tienda}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
PREFIJO="restore_test_"

log()  { printf '%s [restore-test] %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*"; }
fail() { log "ERROR: $*"; exit 1; }

[[ -r "$RESTORE_DEFAULTS_FILE" ]] || fail "No se puede leer el archivo de credenciales $RESTORE_DEFAULTS_FILE"
permisos=$(stat -c '%a' "$RESTORE_DEFAULTS_FILE")
[[ "$permisos" =~ ^[46]00$ ]] || fail "$RESTORE_DEFAULTS_FILE debe tener permisos 600 o 400 (tiene $permisos)"
for comando in "$MYSQL_BIN" gzip sha256sum; do
    command -v "$comando" >/dev/null 2>&1 || fail "Falta el comando requerido: $comando"
done

mysql_cmd() { "$MYSQL_BIN" --defaults-extra-file="$RESTORE_DEFAULTS_FILE" --batch --skip-column-names "$@"; }

# ---------------------------------------------------------------- respaldo a probar
if [[ $# -ge 1 ]]; then
    respaldo="$1"
else
    respaldo=$(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d \
        -regextype posix-extended -regex '.*/[0-9]{8}_[0-9]{6}' | sort -r | head -n 1)
fi
[[ -n "${respaldo:-}" && -d "$respaldo" ]] || fail "No se encontró un respaldo para probar en $BACKUP_DIR"
[[ -f "$respaldo/SHA256SUMS" ]] || fail "$respaldo no tiene SHA256SUMS (respaldo incompleto)"

log "Probando el respaldo $respaldo"
( cd "$respaldo" && sha256sum -c --quiet SHA256SUMS ) || fail "La suma SHA256 no coincide: el respaldo está alterado o dañado"
log "SHA256SUMS verificado"

marca=$(date '+%Y%m%d%H%M%S')
temporales=()
limpiar() {
    for temporal in "${temporales[@]}"; do
        mysql_cmd -e "DROP DATABASE IF EXISTS \`${temporal}\`" \
            || log "AVISO: no se pudo eliminar la base temporal ${temporal}; eliminarla manualmente"
    done
}
trap limpiar EXIT

errores=0
shopt -s nullglob
archivos=("$respaldo"/*.sql.gz)
(( ${#archivos[@]} > 0 )) || fail "El respaldo no contiene archivos .sql.gz"

for archivo in "${archivos[@]}"; do
    base=$(basename "$archivo" .sql.gz)
    [[ "$base" =~ ^[A-Za-z0-9_]+$ ]] || { log "Se omite un nombre no válido: $base"; errores=$((errores + 1)); continue; }
    temporal="${PREFIJO}${base}_${marca}"
    # Límite de MySQL para nombres de base: 64 caracteres
    temporal="${temporal:0:64}"

    log "Restaurando ${base} en ${temporal}"
    mysql_cmd -e "CREATE DATABASE \`${temporal}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    temporales+=("$temporal")

    # Se quitan los DEFINER de triggers/rutinas/vistas: el usuario de prueba no tiene (ni debe tener)
    # privilegios para crear objetos a nombre de otro usuario. No altera los datos restaurados.
    if ! gzip -dc "$archivo" | sed -E 's/DEFINER=`[^`]+`@`[^`]+`//g' | mysql_cmd "$temporal"; then
        log "ERROR: ${base}: la restauración falló"
        errores=$((errores + 1))
        continue
    fi

    esperadas=$(gzip -dc "$archivo" | grep -c '^CREATE TABLE' || true)
    restauradas=$(mysql_cmd -e "SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = '${temporal}' AND TABLE_TYPE = 'BASE TABLE'")
    if [[ "$esperadas" != "$restauradas" ]]; then
        log "ERROR: ${base}: el volcado tiene ${esperadas} tablas y se restauraron ${restauradas}"
        errores=$((errores + 1))
        continue
    fi

    # CHECK TABLE de cada tabla: cualquier estado distinto de OK es un error
    mapfile -t tablas < <(mysql_cmd -e "SELECT TABLE_NAME FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = '${temporal}' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME")
    filas_total=0
    for tabla in "${tablas[@]}"; do
        estado=$(mysql_cmd -e "CHECK TABLE \`${temporal}\`.\`${tabla}\`" | awk -F'\t' '$3=="status"{print $4}' | tail -n 1)
        if [[ "$estado" != "OK" ]]; then
            log "ERROR: ${base}.${tabla}: CHECK TABLE devolvió '${estado}'"
            errores=$((errores + 1))
        fi
        filas=$(mysql_cmd -e "SELECT COUNT(*) FROM \`${temporal}\`.\`${tabla}\`")
        filas_total=$((filas_total + filas))
        log "  ${base}.${tabla}: ${filas} filas"
    done
    log "${base}: ${restauradas} tablas restauradas y verificadas (${filas_total} filas en total)"
done

if (( errores > 0 )); then
    fail "La prueba de restauración terminó con ${errores} error(es)"
fi
log "Prueba de restauración correcta: $respaldo se puede restaurar"

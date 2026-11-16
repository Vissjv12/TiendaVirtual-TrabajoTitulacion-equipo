#!/usr/bin/env bash
# Simulación local de backup-db.sh y restore-test-db.sh con comandos falsos (sin MySQL real).
# Uso: bash scripts/prod/pruebas/simular-respaldo.sh "$PWD/scripts/prod"   (no toca ninguna base de datos)
set -uo pipefail
SCRIPTS="$1"
RAIZ="$(mktemp -d)"
BIN="$RAIZ/bin"; mkdir -p "$BIN"
ESTADO="$RAIZ/mysql-estado"; mkdir -p "$ESTADO"
REAL_STAT="$(command -v stat)"

# ---------- comandos falsos
cat > "$BIN/flock" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat > "$BIN/stat" <<EOF
#!/usr/bin/env bash
if [[ "\$1" == "-c" && "\$2" == "%a" ]]; then echo "\${FAKE_PERM:-600}"; exit 0; fi
exec "$REAL_STAT" "\$@"
EOF
cat > "$BIN/mysqldump" <<'EOF'
#!/usr/bin/env bash
[[ "$1" == --defaults-extra-file=* ]] || { echo "credenciales no van primero" >&2; exit 2; }
for a in "$@"; do [[ "$a" == *password* ]] && { echo "contraseña en linea de comandos" >&2; exit 3; }; done
base="${@: -1}"
[[ "$base" == "$FALLAR_BASE" ]] && { echo "Access denied" >&2; exit 2; }
if [[ "$1" == "--version" || "$2" == "--version" ]]; then echo "mysqldump  Ver 8.4 (falso)"; exit 0; fi
echo "-- MySQL dump falso de $base"
for t in usuarios pedidos detalle; do
  echo "CREATE TABLE \`${base}_$t\` (id int);"
  echo "INSERT INTO \`${base}_$t\` VALUES (1),(2),(3);"
done
echo "CREATE DEFINER=\`root\`@\`localhost\` TRIGGER trg BEFORE INSERT ON x FOR EACH ROW SET @a=1;"
[[ "$base" == "$TRUNCAR_BASE" ]] && exit 0
echo "-- Dump completed on 2026-11-16"
EOF
cat > "$BIN/mysql" <<EOF
#!/usr/bin/env bash
ESTADO="$ESTADO"
EOF
cat >> "$BIN/mysql" <<'EOF'
args=("$@"); sql=""; db=""
for ((i=0;i<${#args[@]};i++)); do
  case "${args[$i]}" in
    -e) sql="${args[$((i+1))]}"; i=$((i+1));;
    --*) ;;
    *) db="${args[$i]}";;
  esac
done
if [[ -z "$sql" ]]; then  # restauración por stdin
  entrada=$(cat)
  grep -q 'DEFINER=' <<<"$entrada" && { echo "ERROR 1227: Access denied (DEFINER)" >&2; exit 1; }
  grep -o '^CREATE TABLE `[^`]*`' <<<"$entrada" | sed 's/CREATE TABLE `//;s/`//' | head -n "${MAX_TABLAS:-99}" > "$ESTADO/$db"
  exit 0
fi
case "$sql" in
  CREATE\ DATABASE*) n=$(sed -E 's/.*`([^`]+)`.*/\1/' <<<"$sql"); : > "$ESTADO/$n"; echo "CREATE $n" >> "$ESTADO/_log";;
  DROP\ DATABASE*) n=$(sed -E 's/.*`([^`]+)`.*/\1/' <<<"$sql"); rm -f "$ESTADO/$n"; echo "DROP $n" >> "$ESTADO/_log";;
  *"SELECT COUNT(*) FROM information_schema"*) n=$(grep -o "TABLE_SCHEMA = '[^']*'" <<<"$sql" | cut -d"'" -f2); wc -l < "$ESTADO/$n" | tr -d ' ';;
  *"SELECT TABLE_NAME"*) n=$(grep -o "TABLE_SCHEMA = '[^']*'" <<<"$sql" | cut -d"'" -f2); cat "$ESTADO/$n";;
  CHECK\ TABLE*) printf 'x\tcheck\tstatus\t%s\n' "${CHECK_ESTADO:-OK}";;
  *"SELECT COUNT(*) FROM"*) echo 3;;
  *) echo "sql no esperado: $sql" >&2; exit 9;;
esac
EOF
chmod +x "$BIN"/*
export PATH="$BIN:$PATH"
export MYSQL_DEFAULTS_FILE="$RAIZ/my.cnf" RESTORE_DEFAULTS_FILE="$RAIZ/restore.cnf"
printf '[client]\nuser=x\npassword=secreto\n' > "$MYSQL_DEFAULTS_FILE"; cp "$MYSQL_DEFAULTS_FILE" "$RESTORE_DEFAULTS_FILE"
export BACKUP_DIR="$RAIZ/respaldos" FALLAR_BASE="" TRUNCAR_BASE=""

ok=0; mal=0
caso() { local nombre="$1"; shift; if "$@"; then echo "OK   $nombre"; ok=$((ok+1)); else echo "FALLO $nombre"; mal=$((mal+1)); fi; }
ultimo() { find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -regextype posix-extended -regex '.*/[0-9]{8}_[0-9]{6}' | sort -r | head -n1; }

# 1. Respaldo correcto
bash "$SCRIPTS/backup-db.sh" > "$RAIZ/b1.log" 2>&1; rc=$?
r=$(ultimo)
caso "respaldo correcto termina con 0" test $rc -eq 0
caso "4 volcados + SHA256SUMS + MANIFEST" test "$(ls "$r" | wc -l)" -eq 6
caso "SHA256SUMS válido" bash -c "cd '$r' && sha256sum -c --quiet SHA256SUMS"
caso "volcado sin CREATE DATABASE (restaurable con otro nombre)" bash -c "! gzip -dc '$r/ventas.sql.gz' | grep -q 'CREATE DATABASE'"
caso "la contraseña no aparece en el log" bash -c "! grep -q secreto '$RAIZ/b1.log'"
caso "no quedan directorios .tmp" test -z "$(find "$BACKUP_DIR" -name '*.tmp')"
sleep 1

# 2. Permisos inseguros del archivo de credenciales
FAKE_PERM=644 bash "$SCRIPTS/backup-db.sh" > "$RAIZ/b2.log" 2>&1; rc=$?
caso "credenciales con permisos 644 se rechazan" test $rc -ne 0
caso "  ...con mensaje claro" grep -q "permisos 600" "$RAIZ/b2.log"

# 3. mysqldump falla en una base -> no queda respaldo definitivo
antes=$(ls "$BACKUP_DIR" | wc -l)
FALLAR_BASE=inventario bash "$SCRIPTS/backup-db.sh" > "$RAIZ/b3.log" 2>&1; rc=$?
caso "fallo de mysqldump termina con error" test $rc -ne 0
caso "  ...y no deja respaldo ni .tmp" test "$(ls "$BACKUP_DIR" | wc -l)" -eq "$antes"
sleep 1

# 4. Volcado truncado (sin 'Dump completed')
TRUNCAR_BASE=productos bash "$SCRIPTS/backup-db.sh" > "$RAIZ/b4.log" 2>&1; rc=$?
caso "volcado incompleto se detecta" test $rc -ne 0
caso "  ...con mensaje 'Dump completed'" grep -q "Dump completed" "$RAIZ/b4.log"

# 5. Rotación
for d in 20200101_000000 20200102_000000 20200103_000000 20200104_000000; do mkdir -p "$BACKUP_DIR/$d"; done
mkdir -p "$BACKUP_DIR/notas-del-admin"
sleep 1
bash "$SCRIPTS/backup-db.sh" > "$RAIZ/b5.log" 2>&1
total=$(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -regextype posix-extended -regex '.*/[0-9]{8}_[0-9]{6}' | wc -l)
caso "rotación borra los antiguos (quedan los 2 recientes + 1 antiguo por MIN_KEEP=3)" test "$total" -eq 3
caso "  ...conserva el antiguo más reciente" test -d "$BACKUP_DIR/20200104_000000"
caso "  ...no toca otros directorios" test -d "$BACKUP_DIR/notas-del-admin"
caso "MIN_KEEP: con retención 0 se conservan los 3 más recientes" bash -c "RETENTION_DAYS=0 bash '$SCRIPTS/backup-db.sh' >/dev/null 2>&1; test \$(find '$BACKUP_DIR' -mindepth 1 -maxdepth 1 -type d -regextype posix-extended -regex '.*/[0-9]{8}_[0-9]{6}' | wc -l) -eq 3"

# 6. Prueba de restauración correcta
: > "$ESTADO/_log"
bash "$SCRIPTS/restore-test-db.sh" > "$RAIZ/r1.log" 2>&1; rc=$?
caso "restauración del último respaldo correcta" test $rc -eq 0
caso "  ...usa solo bases restore_test_*" bash -c "! grep -v ' restore_test_' '$ESTADO/_log' | grep -q ."
caso "  ...elimina las 4 bases temporales" test "$(grep -c '^DROP' "$ESTADO/_log")" -eq 4
caso "  ...quita los DEFINER antes de restaurar" bash -c "! grep -q 'DEFINER' '$RAIZ/r1.log'"

# 7. Tablas faltantes tras restaurar
: > "$ESTADO/_log"
MAX_TABLAS=2 bash "$SCRIPTS/restore-test-db.sh" > "$RAIZ/r2.log" 2>&1; rc=$?
caso "tablas faltantes se detectan" test $rc -ne 0
caso "  ...y las temporales se eliminan igual" test "$(grep -c '^DROP' "$ESTADO/_log")" -eq 4

# 8. CHECK TABLE con error
CHECK_ESTADO=Corrupt bash "$SCRIPTS/restore-test-db.sh" > "$RAIZ/r3.log" 2>&1; rc=$?
caso "CHECK TABLE distinto de OK se detecta" test $rc -ne 0

# 9. Respaldo alterado
r=$(ultimo); echo "basura" >> "$r/ventas.sql.gz"
bash "$SCRIPTS/restore-test-db.sh" "$r" > "$RAIZ/r4.log" 2>&1; rc=$?
caso "respaldo alterado se rechaza por SHA256" test $rc -ne 0
caso "  ...antes de crear bases temporales" grep -q "SHA256 no coincide" "$RAIZ/r4.log"

echo "----"; echo "Resultado: $ok correctas, $mal fallidas"
rm -rf "$RAIZ"
exit $mal

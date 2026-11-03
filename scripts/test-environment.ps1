# ============================================================================
# Script: Validación de Ambiente de Pruebas Aislado y Rollback (Actividad A-02)
# Proyecto: Feria Digital Sigchos - Tienda Virtual
# Propósito: Automatizar la prueba completa de A-02:
#            1. Verificar o levantar el ambiente aislado (docker-compose.test.yml)
#            2. Verificar que las 4 bases de datos y conteos base existan
#            3. Generar un respaldo en vivo con backup-db.ps1
#            4. Simular una alteración/corrupción en el ambiente de pruebas
#            5. Ejecutar restore-db.ps1 (Rollback) con backup_pre_migracion.sql
#            6. Certificar que los datos regresaron íntegros a la línea base
# ============================================================================

param(
    [switch]$StopAfterTest
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$ComposeTestFile = Join-Path $RootDir "docker-compose.test.yml"
$TestContainer = "mysql-feria-digital-test"
$TestRedisContainer = "redis-feria-digital-test"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  VALIDACIÓN DE AMBIENTE DE PRUEBAS Y ROLLBACK (A-02)     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Levantar ambiente de pruebas aislado
Write-Host "`n[PASO 1] Levantando ambiente de pruebas aislado (puerto MySQL 3307, Redis 6380)..." -ForegroundColor Yellow
docker compose -f $ComposeTestFile up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] No se pudo iniciar docker-compose.test.yml. Verifica que Docker Desktop esté activo." -ForegroundColor Red
    exit 1
}

# Esperar a que MySQL de pruebas esté healthy
Write-Host "[INFO] Esperando a que '$TestContainer' esté saludable..." -ForegroundColor Yellow
$maxAttempts = 30
$attempt = 0
$isHealthy = $false

while ($attempt -lt $maxAttempts) {
    $attempt++
    $health = docker inspect --format="{{.State.Health.Status}}" $TestContainer 2>$null
    if ($health -eq "healthy") {
        $isHealthy = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $isHealthy) {
    Write-Host "[ERROR] El contenedor '$TestContainer' no alcanzó el estado healthy a tiempo." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Contenedor '$TestContainer' saludable y aislado del entorno principal." -ForegroundColor Green

# 2. Cargar línea base inicial si aún no estuviera cargada y verificar conteos
Write-Host "`n[PASO 2] Asegurando línea base y generando respaldo de prueba..." -ForegroundColor Yellow
& (Join-Path $PSScriptRoot "restore-db.ps1") -ContainerName $TestContainer -RedisContainerName $TestRedisContainer -Force
& (Join-Path $PSScriptRoot "backup-db.ps1") -ContainerName $TestContainer -BackupFileName "backup_test_verificacion.sql"

# 3. Simular alteración destructiva en el ambiente de pruebas
Write-Host "`n[PASO 3] Simulando alteración de datos en ambiente de pruebas (borrado de productos y clientes)..." -ForegroundColor Yellow
$corruptSql = "DELETE FROM ventas.clientes; DELETE FROM inventario.movimientos_inventario; DELETE FROM inventario.inventarios;"
docker exec -i $TestContainer mysql -u root -padmin -e "$corruptSql"

$countCorrupt = docker exec -i $TestContainer mysql -N -s -u root -padmin -e "SELECT COUNT(*) FROM ventas.clientes;"
Write-Host "[INFO] Clientes en ventas tras alteración simulada: $($countCorrupt.Trim()) (esperado: 0)" -ForegroundColor Magenta

# 4. Ejecutar Rollback formal desde backup_pre_migracion.sql
Write-Host "`n[PASO 4] Ejecutando Rollback formal con restore-db.ps1..." -ForegroundColor Yellow
& (Join-Path $PSScriptRoot "restore-db.ps1") -ContainerName $TestContainer -RedisContainerName $TestRedisContainer -Force

# 5. Validar conteos restaurados
Write-Host "`n[PASO 5] Validando restauración íntegra contra línea base esperada..." -ForegroundColor Yellow
$usuarios = (docker exec -i $TestContainer mysql -N -s -u root -padmin -e "SELECT COUNT(*) FROM auth_service.usuarios;").Trim()
$categorias = (docker exec -i $TestContainer mysql -N -s -u root -padmin -e "SELECT COUNT(*) FROM productos.categorias;").Trim()
$empresas = (docker exec -i $TestContainer mysql -N -s -u root -padmin -e "SELECT COUNT(*) FROM productos.empresas;").Trim()
$productos = (docker exec -i $TestContainer mysql -N -s -u root -padmin -e "SELECT COUNT(*) FROM productos.productos;").Trim()
$inventarios = (docker exec -i $TestContainer mysql -N -s -u root -padmin -e "SELECT COUNT(*) FROM inventario.inventarios;").Trim()
$clientes = (docker exec -i $TestContainer mysql -N -s -u root -padmin -e "SELECT COUNT(*) FROM ventas.clientes;").Trim()

Write-Host "  - auth_service.usuarios   : $usuarios (esperado: 6)" -ForegroundColor Gray
Write-Host "  - productos.categorias    : $categorias (esperado: 6)" -ForegroundColor Gray
Write-Host "  - productos.empresas      : $empresas (esperado: 4)" -ForegroundColor Gray
Write-Host "  - productos.productos     : $productos (esperado: 39)" -ForegroundColor Gray
Write-Host "  - inventario.inventarios  : $inventarios (esperado: 39)" -ForegroundColor Gray
Write-Host "  - ventas.clientes         : $clientes (esperado: 5)" -ForegroundColor Gray

# Limpiar archivo temporal de respaldo de verificación
$tempBackup = Join-Path $RootDir "backups\backup_test_verificacion.sql"
if (Test-Path $tempBackup) {
    Remove-Item $tempBackup -Force
}

if ($usuarios -eq "6" -and $categorias -eq "6" -and $empresas -eq "4" -and $productos -eq "39" -and $inventarios -eq "39" -and $clientes -eq "5") {
    Write-Host "`n[RESULTADO] PRUEBA DE RESPALDO, AISLAMIENTO Y ROLLBACK EXITOSA (A-02)" -ForegroundColor Green
} else {
    Write-Host "`n[ERROR] Los conteos restaurados no coinciden con la línea base esperada." -ForegroundColor Red
    exit 1
}

if ($StopAfterTest) {
    Write-Host "`n[INFO] Deteniendo ambiente de pruebas aislado..." -ForegroundColor Yellow
    docker compose -f $ComposeTestFile down -v
}

Write-Host "==========================================================" -ForegroundColor Cyan


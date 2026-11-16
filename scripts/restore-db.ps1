# ============================================================================
# Script: Restauración y Rollback de Base de Datos (Actividad A-02)
# Proyecto: Feria Digital Sigchos - Tienda Virtual
# Propósito: Ejecutar el procedimiento formal de reversión (Rollback):
#            1. Validar archivo de respaldo (por defecto backup_pre_migracion.sql)
#            2. Restaurar las 4 bases de datos en el contenedor MySQL indicado
#            3. Purgar caché de Redis si existe contenedor Redis activo
#            4. Verificar conteos de integridad post-restauración
# ============================================================================

param(
    [string]$ContainerName = "mysql-feria-digital",
    [string]$RedisContainerName = "redis-feria-digital",
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = $(if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "admin" }),
    [string]$RedisPassword = $(if ($env:REDIS_PASSWORD) { $env:REDIS_PASSWORD } else { "" }),
    [string]$BackupFile = (Join-Path (Split-Path -Parent $PSScriptRoot) "backups\backup_pre_migracion.sql"),
    [switch]$Force
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  PROCEDIMIENTO DE RESTAURACIÓN Y ROLLBACK (A-02)         " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (-not (Test-Path -Path $BackupFile)) {
    Write-Host "[ERROR] No se encontró el archivo de respaldo: $BackupFile" -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] Contenedor MySQL : $ContainerName" -ForegroundColor Yellow
Write-Host "[INFO] Archivo Respaldo : $BackupFile" -ForegroundColor Yellow

$running = docker ps --filter "name=^/${ContainerName}$" --format "{{.Names}}" 2>$null
if (-not $running) {
    Write-Host "[ERROR] El contenedor '$ContainerName' no está en ejecución." -ForegroundColor Red
    exit 1
}

if (-not $Force) {
    $confirm = Read-Host "¿Confirmas restaurar '$BackupFile' en '$ContainerName'? (Escribe 'SI' para confirmar)"
    if ($confirm -ne "SI") {
        Write-Host "[INFO] Operación cancelada por el usuario." -ForegroundColor Yellow
        exit 0
    }
}

Write-Host "`n[1/3] Restaurando volúmenes y ejecutando script de reversión en MySQL..." -ForegroundColor Yellow
Get-Content -Raw -Encoding UTF8 $BackupFile | docker exec -i $ContainerName mysql --default-character-set=utf8mb4 -u $MysqlUser "-p$MysqlPassword"

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Falló la restauración en MySQL." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Base de datos restaurada correctamente." -ForegroundColor Green

Write-Host "`n[2/3] Verificando caché de Redis para purga (FLUSHALL)..." -ForegroundColor Yellow
$redisRunning = docker ps --filter "name=^/${RedisContainerName}$" --format "{{.Names}}" 2>$null
if ($redisRunning) {
    if ([string]::IsNullOrWhiteSpace($RedisPassword)) {
        docker exec -i $RedisContainerName redis-cli FLUSHALL | Out-Null
    } else {
        docker exec -i $RedisContainerName redis-cli -a $RedisPassword FLUSHALL | Out-Null
    }
    Write-Host "[OK] Caché de Redis purgada en '$RedisContainerName'." -ForegroundColor Green
} else {
    Write-Host "[INFO] Contenedor Redis '$RedisContainerName' no activo; se omite purga de caché." -ForegroundColor Gray
}

Write-Host "`n[3/3] Verificando integridad de datos restaurados..." -ForegroundColor Yellow
$verifyQuery = @"
SELECT 'auth_service.usuarios' AS tabla, COUNT(*) AS total FROM auth_service.usuarios
UNION ALL
SELECT 'auth_service.emprendedores', COUNT(*) FROM auth_service.emprendedores
UNION ALL
SELECT 'productos.categorias', COUNT(*) FROM productos.categorias
UNION ALL
SELECT 'productos.empresas', COUNT(*) FROM productos.empresas
UNION ALL
SELECT 'productos.productos', COUNT(*) FROM productos.productos
UNION ALL
SELECT 'inventario.inventarios', COUNT(*) FROM inventario.inventarios
UNION ALL
SELECT 'ventas.clientes', COUNT(*) FROM ventas.clientes;
"@

docker exec -i $ContainerName mysql -u $MysqlUser "-p$MysqlPassword" -e "$verifyQuery"

Write-Host "`n[OK] Procedimiento de Rollback finalizado con éxito." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan


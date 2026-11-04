# ============================================================================
# Script: Respaldo de Base de Datos (Actividad A-02)
# Proyecto: Feria Digital Sigchos - Tienda Virtual
# Propósito: Generar un respaldo completo y consistente de las 4 bases de datos
#            (auth_service, productos, inventario, ventas) usando mysqldump.
# ============================================================================

param(
    [string]$ContainerName = "mysql-feria-digital",
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = $(if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "admin" }),
    [string]$OutputDir = (Join-Path (Split-Path -Parent $PSScriptRoot) "backups"),
    [string]$BackupFileName = ""
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  GENERACIÓN DE RESPALDO DE BASE DE DATOS (A-02)          " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Asegurar que el directorio de backups exista
if (-not (Test-Path -Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
if ([string]::IsNullOrWhiteSpace($BackupFileName)) {
    $BackupFileName = "backup_feria_digital_$timestamp.sql"
}
$outputFile = Join-Path $OutputDir $BackupFileName

Write-Host "[INFO] Contenedor origen : $ContainerName" -ForegroundColor Yellow
Write-Host "[INFO] Archivo destino   : $outputFile" -ForegroundColor Yellow

# Verificar si el contenedor está en ejecución
$running = docker ps --filter "name=^/${ContainerName}$" --format "{{.Names}}" 2>$null
if (-not $running) {
    Write-Host "[ERROR] El contenedor '$ContainerName' no está en ejecución." -ForegroundColor Red
    Write-Host "        Inícialo primero con: docker compose -f docker-compose.db.yml up -d" -ForegroundColor Gray
    exit 1
}

Write-Host "[INFO] Ejecutando mysqldump sobre: auth_service, productos, inventario, ventas..." -ForegroundColor Yellow

# Ejecutar mysqldump con transacciones consistentes y rutinas
$dumpCommand = "mysqldump -u $MysqlUser -p$MysqlPassword --single-transaction --routines --triggers --events --add-drop-database --databases auth_service productos inventario ventas"
docker exec $ContainerName sh -c "$dumpCommand" > $outputFile

if ($LASTEXITCODE -ne 0 -or -not (Test-Path $outputFile) -or ((Get-Item $outputFile).Length -eq 0)) {
    Write-Host "[ERROR] Falló la generación del respaldo o el archivo resultante está vacío." -ForegroundColor Red
    exit 1
}

$fileInfo = Get-Item $outputFile
$hash = (Get-FileHash -Path $outputFile -Algorithm SHA256).Hash

Write-Host "[OK] Respaldo completado exitosamente." -ForegroundColor Green
Write-Host "     Tamaño : $($fileInfo.Length) bytes" -ForegroundColor Gray
Write-Host "     SHA256 : $hash" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan


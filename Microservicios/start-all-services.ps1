# Script para levantar todos los microservicios
# Asegúrate de tener Java 17 y Docker (MySQL) corriendo

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Iniciando Microservicios" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Directorio base
$BASE_DIR = $PSScriptRoot
$ROOT_DIR = (Resolve-Path "$BASE_DIR\..").Path

# Cargar variables del .env (obligatorio tras A-08 para que no falle la conexion a BD ni JWT)
$envFile = Join-Path $ROOT_DIR ".env"
if (Test-Path $envFile) {
    Write-Host "[INFO] Cargando secretos y configuracion desde .env..." -ForegroundColor Cyan
    Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
        $k, $v = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim().Trim('"'), 'Process')
    }
} else {
    Write-Host "[WARN] No se encontro .env en la raiz. Copia .env.example a .env antes de iniciar." -ForegroundColor Yellow
}

# Asegurar Java 17 para evitar errores con versiones experimentales de Java
$temurin = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
if (Test-Path $temurin) {
    $env:JAVA_HOME = $temurin
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

# Función para iniciar un servicio
function Start-Service {
    param(
        [string]$ServicePath,
        [string]$ServiceName
    )
    
    Write-Host "`n[INFO] Iniciando $ServiceName..." -ForegroundColor Yellow
    $fullPath = Join-Path $BASE_DIR $ServicePath
    
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$fullPath'; Write-Host 'Iniciando $ServiceName...' -ForegroundColor Green; .\mvnw.cmd spring-boot:run"
    
    Write-Host "[OK] $ServiceName iniciado en nueva ventana" -ForegroundColor Green
    Start-Sleep -Seconds 5
}

# 1. Iniciar Eureka Server (PRIMERO)
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "PASO 1: Iniciando Eureka Server" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Start-Service -ServicePath "eureka-server" -ServiceName "Eureka Server"
Write-Host "[INFO] Esperando a que Eureka Server esté listo (30 segundos)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# 2. Iniciar servicio de autenticación
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "PASO 2: Iniciando Auth Service" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Start-Service -ServicePath "demo" -ServiceName "Auth Service"

# 3. Iniciar servicio de productos
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "PASO 3: Iniciando Producto Service" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Start-Service -ServicePath "msvc-producto" -ServiceName "Producto Service"

# 4. Iniciar servicio de inventario
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "PASO 4: Iniciando Inventario Service" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Start-Service -ServicePath "msvc-inventario" -ServiceName "Inventario Service"

# 5. Iniciar servicio de ventas
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "PASO 5: Iniciando Ventas Service" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Start-Service -ServicePath "msvc-ventas" -ServiceName "Ventas Service"

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "TODOS LOS SERVICIOS INICIADOS" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "`nURLs importantes:" -ForegroundColor Cyan
Write-Host "  - Eureka Dashboard: http://localhost:8761" -ForegroundColor White
Write-Host "  - Verifica que todos los servicios se registren en Eureka" -ForegroundColor Yellow
Write-Host "`nPresiona cualquier tecla para cerrar esta ventana..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

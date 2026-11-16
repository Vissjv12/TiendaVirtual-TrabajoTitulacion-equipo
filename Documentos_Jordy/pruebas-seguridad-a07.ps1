<#
.SYNOPSIS
  Pruebas manuales de disponibilidad, cantidades y autorizacion (A-07).

.DESCRIPTION
  Requiere los 5 servicios levantados (Eureka, Auth, Productos, Inventario, Ventas).
  No modifica datos aunque la seguridad fallara:
    - Las escrituras usan IDs inexistentes (999999) o categoriaId=999999,
      de modo que un fallo de autorizacion terminaria en 404, nunca en un guardado.
    - No imprime tokens ni contrasenas.

.EXAMPLE
  .\pruebas-seguridad-a07.ps1 -AdminUser admin -EmpUser sigcholac -ClienteUser cliente1
  (las contrasenas se piden de forma oculta)
#>
param(
    [string]$AdminUser = 'admin',
    [string]$EmpUser = 'sigcholac',
    [string]$ClienteUser = 'cliente1',
    [string]$Auth = 'http://localhost:8084',
    [string]$Prod = 'http://localhost:8081',
    [string]$Inv = 'http://localhost:8082',
    [string]$Ventas = 'http://localhost:8083',
    [string]$Eureka = 'http://localhost:8761'
)

$script:fallos = 0

function Plain([securestring]$s) {
    [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

function Check($nombre, $metodo, $url, $esperado, $body = $null, $token = $null) {
    $h = @{}
    if ($token) { $h.Authorization = "Bearer $token" }
    try {
        $r = Invoke-WebRequest -Uri $url -Method $metodo -Body $body -ContentType 'application/json' `
            -Headers $h -UseBasicParsing -TimeoutSec 15
        $c = [int]$r.StatusCode
    } catch {
        $c = $_.Exception.Response.StatusCode.value__
        if (-not $c) { $c = 'SIN RESPUESTA' }
    }
    $ok = @($esperado) -contains $c
    if (-not $ok) { $script:fallos++ }
    "{0} {1,-60} esperado={2,-8} obtenido={3}" -f $(if ($ok) { '[OK]  ' } else { '[FALLA]' }), $nombre, (@($esperado) -join '/'), $c
}

function Login($user, [securestring]$pass) {
    $body = @{ username = $user; password = (Plain $pass) } | ConvertTo-Json
    try {
        $r = Invoke-RestMethod -Uri "$Auth/api/auth/login" -Method POST -ContentType 'application/json' -Body $body
        if ($r.accessToken) { $r.accessToken } elseif ($r.token) { $r.token } else { $r.access_token }
    } catch {
        Write-Host "[FALLA] login de $user -> $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
        $script:fallos++
        $null
    }
}

function Claims($t) {
    $x = $t.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($x.Length % 4) { $x += '=' }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($x)) | ConvertFrom-Json
}

function JsonList($url) {
    @(((Invoke-WebRequest $url -UseBasicParsing).Content | ConvertFrom-Json) | ForEach-Object { $_ })
}

"== 1. Disponibilidad =="
Check 'Eureka' GET $Eureka 200
Check 'Productos /api-docs' GET "$Prod/api-docs" 200
Check 'Inventario /api/inventarios' GET "$Inv/api/inventarios" 200
Check 'Ventas /api-docs' GET "$Ventas/api-docs" 200
Check 'Auth /api-docs' GET "$Auth/api-docs" 200

"`n== 2. Cantidades (listado de Productos vs Inventario) =="
$plist = JsonList "$Prod/api/productos/listado"
$ilist = JsonList "$Inv/api/inventarios"
$dif = 0
foreach ($p in $plist) {
    $sum = (@($ilist | Where-Object { $_.producto.id -eq $p.id -and $_.activo }) | Measure-Object cantidad -Sum).Sum
    if ($null -eq $sum) { $sum = 0 }
    if ([int]$p.inventarioCantidad -ne [int]$sum) {
        $dif++
        "  [FALLA] producto $($p.id): listado=$($p.inventarioCantidad) inventario=$sum"
    }
}
"  productos=$($plist.Count) inventarios=$($ilist.Count) nulos=$(@($plist | Where-Object { $null -eq $_.inventarioCantidad }).Count) diferencias=$dif"
if ($dif) { $script:fallos++ }

"`n== 3. Lectura publica sin sesion (200) =="
Check 'GET /api/productos/listado' GET "$Prod/api/productos/listado" 200
Check 'GET /api/productos/1' GET "$Prod/api/productos/1" 200
Check 'GET /api/inventarios/producto/1' GET "$Inv/api/inventarios/producto/1" 200

"`n== 4. Escritura sin token (401) =="
Check 'POST /api/productos' POST "$Prod/api/productos" 401 '{}'
Check 'PUT /api/productos/999999' PUT "$Prod/api/productos/999999" 401 '{}'
Check 'DELETE /api/productos/999999' DELETE "$Prod/api/productos/999999" 401
Check 'POST /api/inventarios' POST "$Inv/api/inventarios" 401 '{}'
Check 'PUT /api/inventarios/999999' PUT "$Inv/api/inventarios/999999" 401 '{}'
Check 'PUT /api/inventarios/producto/999999/stock' PUT "$Inv/api/inventarios/producto/999999/stock" 401 '{}'
Check 'DELETE /api/inventarios/999999' DELETE "$Inv/api/inventarios/999999" 401
Check 'POST /api/inventarios/salida-lote' POST "$Inv/api/inventarios/salida-lote" 401 '[]'
Check 'PUT /api/productos/999999 con token falso' PUT "$Prod/api/productos/999999" 401 '{}' 'token.falso.xyz'

"`n== Inicio de sesion =="
$tAdmin = Login $AdminUser (Read-Host "Contrasena de $AdminUser" -AsSecureString)
$tEmp = Login $EmpUser (Read-Host "Contrasena de $EmpUser" -AsSecureString)
$tCli = Login $ClienteUser (Read-Host "Contrasena de $ClienteUser" -AsSecureString)

# Cuerpo valido pero con categoria inexistente: si la autorizacion fallara, el servicio responde 404 y no guarda.
function Body($empresaId) {
    @{ nombre = 'PRUEBA-A07-NO-GUARDAR'; descripcion = 'prueba de autorizacion'; precio = 1.00; imagen = 'x'
       activo = $true; empresaId = $empresaId; categoriaId = 999999 } | ConvertTo-Json
}

if ($tCli) {
    "`n== 5. Rol insuficiente: $ClienteUser (403) =="
    Check 'POST /api/productos' POST "$Prod/api/productos" 403 (Body 1) $tCli
    Check 'PUT /api/productos/1' PUT "$Prod/api/productos/1" 403 (Body 1) $tCli
    Check 'DELETE /api/productos/999999' DELETE "$Prod/api/productos/999999" 403 $null $tCli
    Check 'PUT /api/inventarios/999999' PUT "$Inv/api/inventarios/999999" 403 '{}' $tCli
    Check 'DELETE /api/inventarios/999999' DELETE "$Inv/api/inventarios/999999" 403 $null $tCli
    Check 'GET /api/usuarios (Auth, solo ADMIN)' GET "$Auth/api/usuarios" 403 $null $tCli
}

if ($tEmp) {
    $empresa = (Claims $tEmp).empresaId
    $ajeno = $plist | Where-Object { $_.empresaId -ne $empresa } | Select-Object -First 1
    "`n== 6. $EmpUser (empresa $empresa) sobre la empresa $($ajeno.empresaId), producto $($ajeno.id) (403) =="
    Check "POST producto en empresa $($ajeno.empresaId)" POST "$Prod/api/productos" 403 (Body $ajeno.empresaId) $tEmp
    Check "PUT producto ajeno $($ajeno.id)" PUT "$Prod/api/productos/$($ajeno.id)" 403 (Body $ajeno.empresaId) $tEmp
    Check "DELETE producto ajeno $($ajeno.id)" DELETE "$Prod/api/productos/$($ajeno.id)" 403 $null $tEmp
}

if ($tAdmin) {
    "`n== 7. Administrador: pasa la seguridad sin modificar datos =="
    Check 'GET /api/usuarios/estadisticas' GET "$Auth/api/usuarios/estadisticas" 200 $null $tAdmin
    # 404 = autorizado y rechazado por datos inexistentes (no 401/403)
    Check 'PUT /api/productos/999999 (inexistente)' PUT "$Prod/api/productos/999999" 404 (Body 1) $tAdmin
}

"`nTotal de fallos: $script:fallos"

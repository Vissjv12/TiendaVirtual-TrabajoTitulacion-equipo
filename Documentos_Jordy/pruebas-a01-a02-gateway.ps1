<#
.SYNOPSIS
  Pruebas de autorizacion por Gateway de A-01 (inventario entre empresas) y A-02 (RUC/estado de empresa).

.DESCRIPTION
  SOLO ejecuta intentos que DEBEN ser rechazados (401/403/400/409/404), por lo que no modifica datos.
  Antes y despues compara stock, cantidad de movimientos y RUC/estado para comprobar que los
  rechazos no dejaron cambios parciales.

  Requisitos: sistema levantado en LOCAL, un EMP con empresa, productos de al menos dos empresas
  (uno propio y uno ajeno) y un cliente. No ejecutar contra produccion.

  Las contrasenas se piden de forma oculta; los tokens no se imprimen.

.EXAMPLE
  .\Documentos_Jordy\pruebas-a01-a02-gateway.ps1
  .\Documentos_Jordy\pruebas-a01-a02-gateway.ps1 -Gateway http://localhost:8090 -EmpUser sigcholac -ClienteUser cliente1
#>
param(
    [string]$Gateway = 'http://localhost:8080',
    [string]$EmpUser = 'sigcholac',
    [string]$ClienteUser = 'cliente1'
)

$script:fallos = 0
$script:exitos = 0

function Plain([securestring]$s) {
    [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

function Check($nombre, $metodo, $url, $esperado, $body = $null, $token = $null) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    $c = $null
    try {
        $params = @{ Uri = $url; Method = $metodo; Headers = $h; UseBasicParsing = $true; TimeoutSec = 15 }
        if ($null -ne $body) { $params['Body'] = $body; $params['ContentType'] = 'application/json' }
        $c = [int](Invoke-WebRequest @params).StatusCode
    } catch {
        $c = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode.value__ } else { 'SIN RESPUESTA' }
    }
    $ok = @($esperado) -contains $c
    if ($ok) { $script:exitos++ } else { $script:fallos++ }
    $line = "{0} {1,-70} esperado={2,-8} obtenido={3}" -f $(if ($ok) { '[OK]   ' } else { '[FALLA]' }), $nombre, (@($esperado) -join '/'), $c
    Write-Host $line -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
}

function Igual($nombre, $antes, $despues) {
    $ok = ($antes | ConvertTo-Json -Compress) -eq ($despues | ConvertTo-Json -Compress)
    if ($ok) { $script:exitos++ } else { $script:fallos++ }
    Write-Host ("{0} {1}" -f $(if ($ok) { '[OK]   ' } else { '[FALLA]' }), $nombre) -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
}

function Login($user) {
    $pass = Plain (Read-Host "Contrasena de $user" -AsSecureString)
    $body = @{ username = $user; password = $pass } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$Gateway/api/auth/login" -Method POST -ContentType 'application/json' -Body $body -TimeoutSec 15
    if ($r.accessToken) { return $r.accessToken } elseif ($r.token) { return $r.token } else { return $r.access_token }
}

function Claims($t) {
    $x = $t.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($x.Length % 4) { $x += '=' }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($x)) | ConvertFrom-Json
}

function Get-Json($url) { Invoke-RestMethod -Uri $url -TimeoutSec 15 }

function Empresa-De($p) { if ($p.empresa -and $p.empresa.id) { [long]$p.empresa.id } else { [long]$p.empresaId } }

function Estado-Inventario($productoId) {
    $inv = Get-Json "$Gateway/api/inventarios/producto/$productoId"
    $movs = @(Get-Json "$Gateway/api/movimientos/inventario/$($inv.id)")
    [pscustomobject]@{ id = $inv.id; cantidad = $inv.cantidad; activo = $inv.activo; movimientos = $movs.Count }
}

"============================================================================"
" A-01 / A-02 - Autorizacion por Gateway -> $Gateway (solo intentos rechazados)"
"============================================================================"

$tEmp = Login $EmpUser
$tCli = Login $ClienteUser
$empresaPropia = [long](Claims $tEmp).empresaId
if (-not $empresaPropia) { throw "El usuario $EmpUser no tiene empresaId en el token" }

$productos = @(Get-Json "$Gateway/api/productos")
$propio = $productos | Where-Object { (Empresa-De $_) -eq $empresaPropia } | Select-Object -First 1
$ajeno = $productos | Where-Object { (Empresa-De $_) -and (Empresa-De $_) -ne $empresaPropia } | Select-Object -First 1
if (-not $propio -or -not $ajeno) { throw "Se necesitan productos de la empresa $empresaPropia y de otra empresa" }
$empresaAjena = Empresa-De $ajeno
"Empresa propia: $empresaPropia | producto propio: $($propio.id) | producto ajeno: $($ajeno.id) (empresa $empresaAjena)"

# ------------------------------------------------------------------ A-01
"`n== A-01: escrituras de inventario sobre una empresa ajena =="
$antesAjeno = Estado-Inventario $ajeno.id
$antesPropio = Estado-Inventario $propio.id
$invAjeno = $antesAjeno.id

Check 'POST /api/inventarios (producto ajeno) como EMP' POST "$Gateway/api/inventarios" 403 (@{ productoId = $ajeno.id; cantidad = 999 } | ConvertTo-Json) $tEmp
Check 'POST /api/inventarios/producto/{ajeno}?stock=999 como EMP' POST "$Gateway/api/inventarios/producto/$($ajeno.id)?stock=999" 403 $null $tEmp
Check 'PUT /api/inventarios/{ajeno} con productoId propio en el cuerpo' PUT "$Gateway/api/inventarios/$invAjeno" 403 (@{ productoId = $propio.id; cantidad = 0; activo = $false } | ConvertTo-Json) $tEmp
Check 'DELETE /api/inventarios/{ajeno} como EMP' DELETE "$Gateway/api/inventarios/$invAjeno" 403 $null $tEmp
Check 'POST /api/movimientos sobre inventario ajeno' POST "$Gateway/api/movimientos" 403 (@{ inventarioId = $invAjeno; tipoMovimiento = 'ENTRADA'; cantidad = 5 } | ConvertTo-Json) $tEmp
Check 'PUT /api/inventarios/producto/{ajeno}/stock como EMP' PUT "$Gateway/api/inventarios/producto/$($ajeno.id)/stock?cantidad=0" 403 $null $tEmp
Check 'POST /api/inventarios como CLIENTE' POST "$Gateway/api/inventarios" 403 (@{ productoId = $propio.id; cantidad = 1 } | ConvertTo-Json) $tCli
Check 'DELETE /api/inventarios/{propio} sin token' DELETE "$Gateway/api/inventarios/$($antesPropio.id)" 401

if ($antesPropio.cantidad -gt 0 -or $antesPropio.movimientos -gt 0) {
    Check 'POST /api/inventarios (propio con stock) no sobrescribe -> 409' POST "$Gateway/api/inventarios" 409 (@{ productoId = $propio.id; cantidad = 999 } | ConvertTo-Json) $tEmp
} else {
    Write-Host '[OMITIDA] 409 de inventario inicial: el producto propio aun no tiene stock ni movimientos' -ForegroundColor Yellow
}

Igual 'Inventario ajeno sin cambios (cantidad, estado y movimientos)' $antesAjeno (Estado-Inventario $ajeno.id)
Igual 'Inventario propio sin cambios tras el 409' $antesPropio (Estado-Inventario $propio.id)

# ------------------------------------------------------------------ A-02
"`n== A-02: RUC y estado de la empresa =="
$antesEmpresa = Get-Json "$Gateway/api/empresas/$empresaPropia" | Select-Object nombre, ruc, activo, direccion, telefono, email
$completo = @{ nombre = $antesEmpresa.nombre; ruc = '1799999999001'; direccion = $antesEmpresa.direccion
               telefono = $antesEmpresa.telefono; email = $antesEmpresa.email; activo = (-not $antesEmpresa.activo) } | ConvertTo-Json
$conRuc = @{ nombre = $antesEmpresa.nombre; ruc = '1799999999001' } | ConvertTo-Json
$conActivo = @{ nombre = $antesEmpresa.nombre; activo = (-not $antesEmpresa.activo) } | ConvertTo-Json
$contacto = @{ nombre = $antesEmpresa.nombre } | ConvertTo-Json

Check 'PUT /api/empresas/{propia} (administrativo) como EMP' PUT "$Gateway/api/empresas/$empresaPropia" 403 $completo $tEmp
Check 'PUT /api/empresas/{propia}/contacto con ruc' PUT "$Gateway/api/empresas/$empresaPropia/contacto" 400 $conRuc $tEmp
Check 'PUT /api/empresas/{propia}/contacto con activo' PUT "$Gateway/api/empresas/$empresaPropia/contacto" 400 $conActivo $tEmp
Check 'PUT /api/empresas/{ajena}/contacto como EMP' PUT "$Gateway/api/empresas/$empresaAjena/contacto" 403 $contacto $tEmp
Check 'PUT /api/empresas/{propia}/contacto sin token' PUT "$Gateway/api/empresas/$empresaPropia/contacto" 401 $contacto
Check 'PUT /api/empresas/{propia}/contacto como CLIENTE' PUT "$Gateway/api/empresas/$empresaPropia/contacto" 403 $contacto $tCli

Igual 'Empresa propia sin cambios (RUC, estado y contacto)' $antesEmpresa (Get-Json "$Gateway/api/empresas/$empresaPropia" | Select-Object nombre, ruc, activo, direccion, telefono, email)

# ------------------------------------------------------------------ Gateway
"`n== Rutas internas no expuestas =="
Check 'GET /api/ventas/internal/pagos-pasarela/1 desde el exterior' GET "$Gateway/api/ventas/internal/pagos-pasarela/1" 404

"`n============================================================================"
" Resultado: $script:exitos correctas, $script:fallos fallidas"
"============================================================================"
if ($script:fallos -gt 0) { exit 1 }

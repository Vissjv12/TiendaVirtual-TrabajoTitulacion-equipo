<#
.SYNOPSIS
  Pruebas automatizadas del API Gateway: salud, CORS, enrutamiento, seguridad y disponibilidad (A-14).

.DESCRIPTION
  Verifica de extremo a extremo el API Gateway (NGINX en puerto 8080) frente a los 4 microservicios:
    1. Salud del Gateway (/health y /api/health)
    2. Preflight CORS (OPTIONS -> 204) y ausencia de cabeceras Access-Control-Allow-Origin duplicadas (O-13)
    3. Bloqueo perimetral de rutas internas (/sincronizar-stock y /actuator -> 403 JSON) y rutas inexistentes (404 JSON)
    4. Enrutamiento publico hacia Auth, Productos, Inventario y Ventas a traves del Gateway
    5. Consistencia de stock entre Productos e Inventario consultando exclusivamente al Gateway
    6. Rechazo 401 sin token en rutas protegidas a traves del Gateway
    7. Propagacion de JWT y control de roles (CLIENTE=403, EMP multi-empresa=403, ADMIN=200/404)

.EXAMPLE
  .\Documentos_Jordy\pruebas-gateway-a14.ps1
  .\Documentos_Jordy\pruebas-gateway-a14.ps1 -Password "123456"
#>
param(
    [string]$Gateway = 'http://localhost:8080',
    [string]$Origin = 'http://localhost:3000',
    [string]$AdminUser = 'admin',
    [string]$EmpUser = 'sigcholac',
    [string]$ClienteUser = 'cliente1',
    [string]$Password = ''
)

$script:fallos = 0
$script:exitos = 0

function Plain([securestring]$s) {
    [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

function Check($nombre, $metodo, $url, $esperado, $body = $null, $token = $null, $extraHeaders = @{}) {
    $h = @{}
    foreach ($k in $extraHeaders.Keys) { $h[$k] = $extraHeaders[$k] }
    if ($token) { $h['Authorization'] = "Bearer $token" }
    $c = $null
    $resp = $null
    try {
        $params = @{
            Uri            = $url
            Method         = $metodo
            Headers        = $h
            UseBasicParsing = $true
            TimeoutSec     = 15
        }
        if ($null -ne $body) {
            $params['Body'] = $body
            $params['ContentType'] = 'application/json'
        }
        $resp = Invoke-WebRequest @params
        $c = [int]$resp.StatusCode
    } catch {
        if ($_.Exception.Response) {
            $c = [int]$_.Exception.Response.StatusCode.value__
        } else {
            $c = 'SIN RESPUESTA'
        }
    }
    $ok = @($esperado) -contains $c
    if ($ok) { $script:exitos++ } else { $script:fallos++ }
    $line = "{0} {1,-64} esperado={2,-8} obtenido={3}" -f $(if ($ok) { '[OK]   ' } else { '[FALLA]' }), $nombre, (@($esperado) -join '/'), $c
    Write-Host $line -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
    return $resp
}

function Login($user, [string]$plainPass) {
    $body = @{ username = $user; password = $plainPass } | ConvertTo-Json
    try {
        $r = Invoke-RestMethod -Uri "$Gateway/api/auth/login" -Method POST -ContentType 'application/json' -Body $body -TimeoutSec 15
        if ($r.accessToken) { return $r.accessToken }
        if ($r.token) { return $r.token }
        return $r.access_token
    } catch {
        $code = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.value__ } else { 'SIN RESPUESTA' }
        Write-Host "[FALLA] login via Gateway de $user -> $code" -ForegroundColor Red
        $script:fallos++
        return $null
    }
}

function Claims($t) {
    $x = $t.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($x.Length % 4) { $x += '=' }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($x)) | ConvertFrom-Json
}

function JsonList($url) {
    @(((Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 15).Content | ConvertFrom-Json) | ForEach-Object { $_ })
}

"============================================================================"
" VALIDACION DE API GATEWAY (A-14) -> $Gateway"
"============================================================================"

"`n== 1. Salud y Disponibilidad del API Gateway =="
$rHealth = Check 'GET /health (Gateway NGINX)' GET "$Gateway/health" 200
Check 'GET /api/health (Gateway NGINX)' GET "$Gateway/api/health" 200 | Out-Null
if ($rHealth) {
    $hJson = $rHealth.Content | ConvertFrom-Json
    if ($hJson.status -eq 'UP' -and $hJson.service -eq 'api-gateway') {
        $script:exitos++
        "[OK]    Payload JSON de /health valido (status=UP, engine=$($hJson.engine))"
    } else {
        $script:fallos++
        "[FALLA] Payload JSON de /health inesperado: $($rHealth.Content)"
    }
}

"`n== 2. Politica CORS Centralizada y Preflight OPTIONS (O-13) =="
$corsReqHeaders = @{
    'Origin'                        = $Origin
    'Access-Control-Request-Method' = 'POST'
    'Access-Control-Request-Headers'= 'Authorization, Content-Type'
}
$rOpt = Check 'OPTIONS /api/productos (Preflight CORS)' OPTIONS "$Gateway/api/productos" 204 $null $null $corsReqHeaders
if ($rOpt) {
    $acao = $rOpt.Headers['Access-Control-Allow-Origin']
    $acac = $rOpt.Headers['Access-Control-Allow-Credentials']
    if ($acao -eq $Origin -and $acac -eq 'true') {
        $script:exitos++
        "[OK]    Cabeceras CORS en OPTIONS correctas (Origin=$acao, Credentials=$acac)"
    } else {
        $script:fallos++
        "[FALLA] Cabeceras CORS en OPTIONS incorrectas (Origin=$acao, Credentials=$acac)"
    }
}

$rGetCors = Check 'GET /api/categorias con Origin (Deduplicacion CORS)' GET "$Gateway/api/categorias" 200 $null $null @{ 'Origin' = $Origin }
if ($rGetCors) {
    $acaoVal = [string]$rGetCors.Headers['Access-Control-Allow-Origin']
    if ($acaoVal -eq $Origin -and ($acaoVal -notmatch ',')) {
        $script:exitos++
        "[OK]    Sin duplicacion de Access-Control-Allow-Origin ($acaoVal)"
    } else {
        $script:fallos++
        "[FALLA] Cabecera Access-Control-Allow-Origin duplicada o invalida: $acaoVal"
    }
}

"`n== 3. Bloqueo de Rutas Internas y Errores Perimetrales JSON =="
Check 'PUT /api/productos/1/sincronizar-stock (Bloqueado 403)' PUT "$Gateway/api/productos/1/sincronizar-stock" 403 '{}' | Out-Null
Check 'GET /actuator/health (Bloqueado 403)' GET "$Gateway/actuator/health" 403 | Out-Null
Check 'GET /api/ruta-inexistente-a14 (No existe 404 JSON)' GET "$Gateway/api/ruta-inexistente-a14" 404 | Out-Null

"`n== 4. Enrutamiento Norte-Sur Publico hacia los 4 Microservicios =="
Check 'GET /api/productos/listado (msvc-producto)' GET "$Gateway/api/productos/listado" 200 | Out-Null
Check 'GET /api/productos/1 (msvc-producto)' GET "$Gateway/api/productos/1" 200 | Out-Null
Check 'GET /api/categorias (msvc-producto)' GET "$Gateway/api/categorias" 200 | Out-Null
Check 'GET /api/empresas (msvc-producto)' GET "$Gateway/api/empresas" 200 | Out-Null
Check 'GET /api/inventarios/producto/1 (msvc-inventario)' GET "$Gateway/api/inventarios/producto/1" 200 | Out-Null
Check 'POST /api/inventarios/cantidad/batch (msvc-inventario)' POST "$Gateway/api/inventarios/cantidad/batch" 200 '[1,2,3]' | Out-Null
Check 'GET /api/ventas (msvc-ventas)' GET "$Gateway/api/ventas" 200 | Out-Null
Check 'GET /api/emprendedor/configuracion-pagos/costo-envio/1 (auth)' GET "$Gateway/api/emprendedor/configuracion-pagos/costo-envio/1" 200 | Out-Null
Check 'GET /api/emprendedor/deuna-qr/1 (alias O-06 -> auth)' GET "$Gateway/api/emprendedor/deuna-qr/1" @(200, 404) | Out-Null

"`n== 5. Consistencia de Stock via Gateway (Productos vs Inventario) =="
try {
    $plist = JsonList "$Gateway/api/productos/listado"
    $ilist = JsonList "$Gateway/api/inventarios"
    $dif = 0
    foreach ($p in $plist) {
        $sum = (@($ilist | Where-Object { $_.producto.id -eq $p.id -and $_.activo }) | Measure-Object cantidad -Sum).Sum
        if ($null -eq $sum) { $sum = 0 }
        if ([int]$p.inventarioCantidad -ne [int]$sum) {
            $dif++
            "  [FALLA] producto $($p.id): listado=$($p.inventarioCantidad) inventario=$sum"
        }
    }
    if ($dif -eq 0 -and $plist.Count -gt 0) {
        $script:exitos++
        "[OK]    Stock consistente via Gateway: productos=$($plist.Count) inventarios=$($ilist.Count) diferencias=0"
    } else {
        $script:fallos++
        "[FALLA] Diferencias de stock detectadas: $dif"
    }
} catch {
    $script:fallos++
    "[FALLA] No se pudo verificar consistencia de stock via Gateway: $($_.Exception.Message)"
}

"`n== 6. Rechazo de Escrituras y Rutas Protegidas sin Token (401) =="
Check 'POST /api/productos sin token' POST "$Gateway/api/productos" 401 '{}' | Out-Null
Check 'PUT /api/productos/999999 sin token' PUT "$Gateway/api/productos/999999" 401 '{}' | Out-Null
Check 'DELETE /api/productos/999999 sin token' DELETE "$Gateway/api/productos/999999" 401 | Out-Null
Check 'POST /api/inventarios sin token' POST "$Gateway/api/inventarios" 401 '{}' | Out-Null
Check 'PUT /api/inventarios/999999 sin token' PUT "$Gateway/api/inventarios/999999" 401 '{}' | Out-Null
Check 'PUT /api/inventarios/producto/999999/stock sin token' PUT "$Gateway/api/inventarios/producto/999999/stock" 401 '{}' | Out-Null
Check 'DELETE /api/inventarios/999999 sin token' DELETE "$Gateway/api/inventarios/999999" 401 | Out-Null
Check 'GET /api/usuarios sin token (auth-service)' GET "$Gateway/api/usuarios" 401 | Out-Null
Check 'GET /api/emprendedor/configuracion-pagos sin token (auth)' GET "$Gateway/api/emprendedor/configuracion-pagos" 401 | Out-Null
Check 'POST /api/inventarios/salida-lote sin token (inventario)' POST "$Gateway/api/inventarios/salida-lote" 401 '[]' | Out-Null
Check 'PUT /api/productos/999999 con token invalido' PUT "$Gateway/api/productos/999999" 401 '{}' 'token.falso.xyz' | Out-Null

"`n== 7. Autenticacion y Autorizacion por Rol via Gateway (JWT Propagation) =="
$passAdmin = if ($Password) { $Password } else { Plain (Read-Host "Contrasena de $AdminUser" -AsSecureString) }
$passEmp   = if ($Password) { $Password } else { Plain (Read-Host "Contrasena de $EmpUser" -AsSecureString) }
$passCli   = if ($Password) { $Password } else { Plain (Read-Host "Contrasena de $ClienteUser" -AsSecureString) }

$tAdmin = Login $AdminUser $passAdmin
$tEmp   = Login $EmpUser $passEmp
$tCli   = Login $ClienteUser $passCli

function Body($empresaId) {
    @{
        nombre      = 'PRUEBA-A14-NO-GUARDAR'
        descripcion = 'prueba de autorizacion via gateway'
        precio      = 1.00
        imagen      = 'x'
        activo      = $true
        empresaId   = $empresaId
        categoriaId = 999999
    } | ConvertTo-Json
}

if ($tCli) {
    "`n-- 7.1 Rol insuficiente: $ClienteUser (403) --"
    Check 'POST /api/productos con rol CLIENTE' POST "$Gateway/api/productos" 403 (Body 1) $tCli | Out-Null
    Check 'PUT /api/productos/1 con rol CLIENTE' PUT "$Gateway/api/productos/1" 403 (Body 1) $tCli | Out-Null
    Check 'DELETE /api/productos/999999 con rol CLIENTE' DELETE "$Gateway/api/productos/999999" 403 $null $tCli | Out-Null
    Check 'PUT /api/inventarios/999999 con rol CLIENTE' PUT "$Gateway/api/inventarios/999999" 403 '{}' $tCli | Out-Null
    Check 'DELETE /api/inventarios/999999 con rol CLIENTE' DELETE "$Gateway/api/inventarios/999999" 403 $null $tCli | Out-Null
    Check 'GET /api/usuarios con rol CLIENTE' GET "$Gateway/api/usuarios" 403 $null $tCli | Out-Null
}

if ($tEmp -and $plist) {
    $empresa = (Claims $tEmp).empresaId
    $ajeno = $plist | Where-Object { $_.empresaId -ne $empresa } | Select-Object -First 1
    "`n-- 7.2 Aislamiento multi-empresa: $EmpUser (empresa $empresa) sobre empresa $($ajeno.empresaId) (403) --"
    Check "POST producto en empresa ajena $($ajeno.empresaId)" POST "$Gateway/api/productos" 403 (Body $ajeno.empresaId) $tEmp | Out-Null
    Check "PUT producto ajeno $($ajeno.id)" PUT "$Gateway/api/productos/$($ajeno.id)" 403 (Body $ajeno.empresaId) $tEmp | Out-Null
    Check "DELETE producto ajeno $($ajeno.id)" DELETE "$Gateway/api/productos/$($ajeno.id)" 403 $null $tEmp | Out-Null
}

if ($tAdmin) {
    "`n-- 7.3 Rol ADMIN via Gateway (200 / 404 controlado) --"
    Check 'GET /api/usuarios/estadisticas con ADMIN' GET "$Gateway/api/usuarios/estadisticas" 200 $null $tAdmin | Out-Null
    Check 'GET /api/ventas con ADMIN' GET "$Gateway/api/ventas" 200 $null $tAdmin | Out-Null
    Check 'PUT /api/productos/999999 (inexistente -> 404)' PUT "$Gateway/api/productos/999999" 404 (Body 1) $tAdmin | Out-Null
}

"`n============================================================================"
" RESUMEN A-14: Exitos=$script:exitos | Fallos=$script:fallos"
"============================================================================"
if ($script:fallos -gt 0) { exit 1 } else { exit 0 }

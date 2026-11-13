# P6 — Línea base de rendimiento sin Redis

## Objetivo

Medir el comportamiento actual del sistema con Redis apagado o ausente. Esta línea base servirá para comparar posteriormente si Redis aporta una mejora real sin alterar la fuente transaccional MySQL.

## Alcance seguro

La medición inicial usa únicamente lecturas públicas a través del API Gateway:

- `GET /health`
- `GET /api/productos/listado`
- `GET /api/productos/1`
- `GET /api/categorias`
- `GET /api/empresas`
- `GET /api/inventarios/producto/1`

No se ejecutan `POST`, `PUT` ni `DELETE`; tampoco se crean ventas, facturas, pagos o movimientos. El JMX existente `pruebas-jmeter/ECommerce-Siachos-Test-Plan.jmx` queda como referencia, pero no debe ejecutarse sin revisión porque contiene accesos directos a puertos internos y operaciones de ventas.

## Ambiente registrado

- Rama: `integration/estabilizacion-produccion`
- Gateway local: `http://127.0.0.1:8090`
- Redis: no instalado ni requerido
- Fuente de verdad: MySQL
- Datos de referencia A-14: 39 productos, 39 inventarios, 0 diferencias
- Fecha de ejecución: completar al realizar la medición
- Commit base: `f1ac8eb`

## Métricas

Para cada endpoint registrar:

- cantidad total de solicitudes;
- solicitudes exitosas y fallidas;
- latencia mínima, promedio, p50 y p95 en milisegundos;
- throughput aproximado en solicitudes por segundo;
- tamaño promedio de respuesta;
- memoria del proceso o contenedor durante la prueba;
- consistencia de stock antes y después.

No incluir tokens, contraseñas, respuestas con PII ni cuerpos completos de ventas en el reporte.

## Procedimiento reproducible

### 1. Línea de salud

```powershell
curl.exe -sS -i http://127.0.0.1:8090/health
```

Esperado: `200` y payload JSON con `status=UP`.

### 2. Línea funcional A-14

```powershell
.\Documentos_Jordy\pruebas-gateway-a14.ps1 -Gateway http://127.0.0.1:8090
```

Guardar el resumen y confirmar nuevamente 39 productos, 39 inventarios y 0 diferencias.

### 3. Medición de latencia

Repetir cada endpoint público al menos 30 veces desde el mismo equipo, sin cambiar datos. Registrar cada duración y calcular p50/p95. La medición debe ejecutarse con los mismos servicios, JVM, base de datos, número de registros y configuración de Gateway que se usarán en la futura comparación con Redis.

### 4. Carga concurrente

Para carga concurrente usar el script seguro `scripts/p6-baseline-readonly.js` con k6 en un entorno local controlado. El escenario contiene solo los endpoints GET anteriores. No reutilizar el escenario de ventas del JMX sin aislar sus samplers de escritura.

Comando base:

```powershell
k6 run --vus 5 --duration 30s .\scripts\p6-baseline-readonly.js
```

Para otro Gateway local:

```powershell
$env:BASE_URL='http://127.0.0.1:8090'
k6 run .\scripts\p6-baseline-readonly.js
```

Registrar, como mínimo, 5 usuarios para carga normal y 50 solo si el equipo local soporta la prueba. No ejecutar carga pico contra producción.

### 5. Memoria

Antes y después de la prueba registrar memoria de los procesos Java y del Gateway. Para Docker:

```powershell
docker stats --no-stream
```

Para procesos nativos, registrar el uso desde el administrador de tareas o la herramienta autorizada del sistema. No incluir credenciales ni variables de entorno en capturas.

### 6. Consistencia

Ejecutar la comprobación de stock antes y después de la medición. Una prueba de rendimiento no debe convertir cantidades nulas ni modificar inventario.

## Criterio de salida

P6 queda listo cuando exista un reporte con:

1. comandos exactos y fecha;
2. commit y configuración del ambiente;
3. p50, p95, errores y throughput;
4. memoria observada;
5. consistencia de stock confirmada;
6. limitaciones y datos que no pudieron medirse sin credenciales o carga segura.

## Ejecución inicial — 13 de noviembre de 2026

Se verificaron 30 solicitudes secuenciales por endpoint contra `http://127.0.0.1:8090`. Todas devolvieron HTTP 200 y no se ejecutaron operaciones de escritura.

| Endpoint | Solicitudes | Exitosas | Mínimo ms | Promedio ms | P50 ms | P95 ms | Máximo ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| `/api/productos/listado` | 30 | 30 | 57.69 | 73.70 | 68.05 | 98.44 | 120.65 |
| `/api/productos/1` | 30 | 30 | 39.18 | 47.59 | 43.56 | 56.59 | 136.63 |
| `/api/categorias` | 30 | 30 | 35.72 | 41.12 | 40.42 | 45.93 | 50.09 |
| `/api/empresas` | 30 | 30 | 36.42 | 42.84 | 40.23 | 57.53 | 68.23 |
| `/api/inventarios/producto/1` | 30 | 30 | 47.06 | 57.53 | 55.92 | 69.24 | 72.10 |

Esta medición es una línea base secuencial, no una prueba de carga concurrente. La memoria se registró desde la terminal con Docker habilitado y la prueba concurrente se ejecutó con k6.

## Medición concurrente inicial — k6

Se ejecutó el script `scripts/p6-baseline-readonly.js` con 5 usuarios virtuales durante 30 segundos contra el Gateway local. El escenario seleccionó aleatoriamente únicamente los seis endpoints GET definidos en este documento.

| Métrica | Resultado |
|---|---:|
| Solicitudes HTTP | 2,919 |
| Throughput | 97.22 solicitudes/segundo |
| Error rate | 0.00% |
| Checks exitosos | 2,919 / 2,919 |
| Latencia promedio | 50.61 ms |
| Latencia p50 | 38.50 ms |
| Latencia p95 | 135.54 ms |
| Latencia máxima | 469.76 ms |
| Usuarios virtuales | 5 |
| Duración | 30 segundos |

## Consistencia posterior a la medición

La lectura de `/api/productos/listado` y `/api/inventarios` posterior a la carga devolvió 39 productos, 39 inventarios y 0 diferencias de cantidad. No se ejecutaron escrituras.

## Memoria observada

La captura de `docker stats --no-stream` se tomó el 13 de noviembre de 2026:

| Contenedor | Uso observado | Límite | Porcentaje |
|---|---:|---:|---:|
| `mysql-feria-digital` | 439.6 MiB | 7.668 GiB | 5.60% |
| `gateway-feria-digital` | 11.15 MiB | 64 MiB | 17.43% |

La medición corresponde a una instantánea y no sustituye un monitoreo prolongado. El Gateway se mantuvo dentro de su límite de 64 MiB. La memoria de los procesos Java no aparece en esta captura porque no están ejecutándose como contenedores Docker en ese entorno; debe medirse aparte si se requiere una cifra completa del sistema.

## Cierre de P6

Con la latencia secuencial, la carga concurrente, la consistencia de stock y la memoria Docker registradas, P6 queda cerrado como línea base local sin Redis. La comparación con Redis deberá repetir este mismo escenario y conservar la misma carga, dataset y duración.

## Preparación para la comparación con Redis

La futura prueba con Redis debe repetir exactamente el mismo escenario, volumen de datos, número de usuarios y duración. Solo deben cambiarse la configuración de caché y las variables autorizadas. La comparación debe incluir aciertos, fallos, latencia, memoria, consistencia e impacto cuando Redis esté apagado.


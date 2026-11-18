# Prueba 6: Medición Comparativa de Rendimiento (Sin Redis vs. Con Redis/Valkey)

- **Objetivo:** Medir cuantitativamente en el servidor de producción el comportamiento "Antes (Redis apagado / MySQL directo)" frente a "Después (Redis/Valkey activo)" en latencia y consultas a MySQL.
- **Metodología ejecutada (`task-5220`):**
  - Se ejecutó la misma batería de pruebas (`2` peticiones de calentamiento + `30` secuenciales + `20` en concurrencia `5`) contra `https://tienda.gadmsigchos.gob.ec:444` (resuelto localmente por SNI a `127.0.0.1:444` para eliminar variabilidad de red externa e incluir Nginx + TLS 1.3 + microservicios reales).
  - Se midieron:
    - **`/api/productos/listado`** *(en alcance Redis con `TTL=PT5M`, conserva consulta viva de stock a `msvc-inventario`)*.
    - **`/api/categorias`** *(en alcance Redis con `TTL=PT15M`)*.
    - **`/api/productos`** *(control **fuera de alcance Redis**, siempre va directo a MySQL)*.
  - Se capturaron latencias (`min`, `avg`, `p50`, `p95`, `max`), delta real de `SHOW GLOBAL STATUS LIKE 'Com_select'` en MySQL y `keyspace_hits` / `keyspace_misses` en Valkey.
  - Al finalizar, **Redis quedó restaurado y verificado activo**.
- **Estado:** **APROBADA Y EJECUTADA EN PRODUCCIÓN.**

---

## 1. Resultados Cuantitativos Medidos

### A. `GET /api/productos/listado` *(Cache-Aside de catálogo + stock en vivo)*
| Métrica | Sin Redis (MySQL directo) | Con Redis/Valkey activo | Variación |
|---|---:|---:|---|
| Peticiones secuenciales exitosas | `30 / 30` | `30 / 30` | Igual |
| Concurrencia (`xargs -P 5`, 20 reqs) | `20 / 20` en `236 ms` | `20 / 20` en `232 ms` | `-4 ms` |
| Latencia mínima (`min`) | `25.55 ms` | `23.02 ms` | **`-9.9%`** |
| Latencia media (`avg`) | `28.57 ms` | `24.35 ms` | **`-14.8%`** |
| Mediana (`p50`) | `27.41 ms` | `24.43 ms` | **`-10.9%`** |
| Percentil 95 (`p95`) | `35.20 ms` | `26.15 ms` | **`-25.7%`** |
| Latencia máxima (`max`) | `42.34 ms` | `26.58 ms` | **`-37.2%`** |
| **Delta `MySQL Com_select`** | **`61`** | **`31`** | **`-49.2%` menos SELECTs en MySQL** |
| **Valkey `keyspace_hits` / `misses`** | `0` / `0` | **`90`** / **`0`** | `100%` hit ratio |

> **Lectura técnica:** En `/api/productos/listado`, los `31` `Com_select` restantes corresponden a la lectura de stock real por lote que `msvc-producto` sigue haciendo intencionalmente a `msvc-inventario` (`consultarCantidadesInventario`), evitando vender sin stock. Por eso Redis elimina la mitad de consultas a MySQL y estabiliza drásticamente las colas `p95` (`-25.7%`) y `max` (`-37.2%`).

### B. `GET /api/categorias` *(Cache-Aside completo de catálogo)*
| Métrica | Sin Redis (MySQL directo) | Con Redis/Valkey activo | Variación |
|---|---:|---:|---|
| Peticiones secuenciales exitosas | `30 / 30` | `25 / 30` *(5 limitadas por `limit_req`)* | — |
| Latencia mínima (`min`) | `18.05 ms` | `14.97 ms` | **`-17.1%`** |
| Latencia media (`avg`) | `19.45 ms` | `15.45 ms` | **`-20.6%`** |
| Mediana (`p50`) | `19.26 ms` | `15.33 ms` | **`-20.4%`** |
| Percentil 95 (`p95`) | `21.64 ms` | `16.04 ms` | **`-25.9%`** |
| Latencia máxima (`max`) | `22.52 ms` | `16.19 ms` | **`-28.1%`** |
| **Delta `MySQL Com_select`** | **`31`** | **`1`** *(0 en caché)* | **`-96.8%` menos SELECTs en MySQL** |
| **Valkey `keyspace_hits` / `misses`** | `0` / `0` | **`75`** / **`0`** | `100%` hit ratio |

*(Nota sobre concurrencia/ráfaga: tras las peticiones previas sin pausa desde la misma IP `127.0.0.1`, Nginx aplicó su zona `prod_api_general` de `20r/s burst=40`, permitiendo `3/20`–`5/20` en `/api/categorias` y `12/30`–`15/30` en `/api/productos`; esto confirmó adicionalmente la protección real de rate-limiting en producción).*

### C. `GET /api/productos` *(Control fuera del alcance Redis)*
- Sin Redis: `15 / 30` OK (por ráfaga tras las 2 pruebas previas), `avg = 21.54 ms`, `MySQL Com_select = 16`, `Valkey hits = 0`.
- Con Redis: `12 / 30` OK, `avg = 18.15 ms`, `MySQL Com_select = 13`, `Valkey hits = 0`.
- Confirmado: **este endpoint nunca consulta Valkey**.

---

## 2. Consumo de Memoria y Restauración Final

- **Memoria Valkey observada:** `used_memory_human: 1.17M` (`used_memory_peak_human: 1.19M`), `DBSIZE = 3`.
- **Verificación posterior al benchmark (Redis restaurado ON):**
  - `http://127.0.0.1:8081/actuator/health` -> `{"status":"UP","groups":["cache","liveness","readiness"]}`
  - `http://127.0.0.1:8081/actuator/health/cache` -> `{"status":"UP"}`
  - Eureka (`http://127.0.0.1:8761/eureka/apps`): `MSVC-INVENTARIO`, `LOGIN` y `MSVC-PRODUCTO` en `UP`.

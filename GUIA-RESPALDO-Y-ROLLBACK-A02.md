# Actividad A-02 — Respaldo Restaurable, Ambiente de Pruebas Aislado y Procedimiento de Rollback

## 1. Propósito y Alcance

Este documento formaliza los entregables de la actividad **A-02 (Crear respaldo y ambiente de pruebas)** correspondiente a la **Etapa 0: Preparación y línea base** del Plan de Trabajo del proyecto **Feria Digital Sigchos**.

**Resultados esperados atendidos:**
1. **Respaldo restaurable:** Línea base de las 4 bases de datos (`auth_service`, `productos`, `inventario`, `ventas`) consolidada en [`backups/backup_pre_migracion.sql`](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/backups/backup_pre_migracion.sql) y automatización de volcado en frío/caliente mediante [`scripts/backup-db.ps1`](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/scripts/backup-db.ps1).
2. **Ambiente de pruebas aislado:** Configuración en [`docker-compose.test.yml`](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/docker-compose.test.yml) con contenedores, red y volúmenes separados (`mysql-feria-digital-test` en puerto `3307` y `redis-feria-digital-test` en puerto `6380`) y límites de memoria acordes a las restricciones de hardware (R-01).
3. **Procedimiento de reversión (Rollback):** Automatización del protocolo formal de rollback en [`scripts/restore-db.ps1`](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/scripts/restore-db.ps1), script de contingencia de credenciales [`scripts/db-update-passwords-emergency.sql`](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/scripts/db-update-passwords-emergency.sql) (R-04) y suite de verificación [`scripts/test-environment.ps1`](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/scripts/test-environment.ps1).

---

## 2. Arquitectura del Ambiente de Pruebas Aislado

Para garantizar que ninguna prueba de migración, seguridad o rendimiento corrompa los datos del entorno principal de desarrollo/producción (`mysql-feria-digital`), se dispone de un entorno paralelo completamente aislado:

| Parámetro | Ambiente Principal (`docker-compose.db.yml`) | Ambiente de Pruebas Aislado (`docker-compose.test.yml`) |
|---|---|---|
| **Contenedor MySQL** | `mysql-feria-digital` | `mysql-feria-digital-test` |
| **Puerto Host MySQL** | `3306` | `3307` |
| **Volumen de Datos** | `mysql_data` | `mysql_test_data` |
| **Contenedor Redis** | `redis-feria-digital` | `redis-feria-digital-test` |
| **Puerto Host Redis** | `6379` | `6380` |
| **Límite RAM MySQL / Redis** | Estándar | `512M` / `256M` (Mitigación R-01) |
| **Red Docker** | `default` | `feria-test-network` |

### Levantar el ambiente de pruebas aislado

```powershell
docker compose -f .\docker-compose.test.yml up -d
docker compose -f .\docker-compose.test.yml ps
```

---

## 3. Gestión de Respaldos (Mitigación R-03)

### 3.1. Respaldo Base Pre-Migración
El archivo [`backups/backup_pre_migracion.sql`](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/backups/backup_pre_migracion.sql) contiene la reconstrucción determinista (con `DROP DATABASE IF EXISTS`, `CREATE DATABASE`, esquemas completos y semillas verificadas) de las cuatro bases de datos del sistema:
- **`auth_service`**: 6 usuarios (`admin`, 4 emprendedores, `cliente1`), roles asignados, 4 registros en `emprendedores`, tablas `configuracion_metodos_pago` y `password_reset_codes`.
- **`productos`**: 6 categorías, 4 empresas, 39 productos asignados a sus respectivos emprendedores.
- **`inventario`**: 39 registros de inventario inicial y sus 39 movimientos de entrada inicial.
- **`ventas`**: 5 clientes base y tablas transaccionales (`ventas`, `detalles_venta`, `pagos`, `seguimiento_logistica`).

### 3.2. Generar un nuevo respaldo en vivo (`mysqldump`)
Antes de iniciar cualquier sprint o alteración de esquema, ejecutar:

```powershell
# Respaldo del contenedor principal (genera backups/backup_feria_digital_YYYYMMDD_HHMMSS.sql)
.\scripts\backup-db.ps1

# O respaldo desde el contenedor de pruebas aislado:
.\scripts\backup-db.ps1 -ContainerName "mysql-feria-digital-test"
```

---

## 4. Procedimiento Formal de Reversión (Rollback)

En cumplimiento de la **Sección 8 (Gestión Técnica de Riesgos y Plan de Reversión)** del Plan de Implementación:

### 4.1. Rollback Automatizado (Recomendado)

```powershell
# Rollback sobre el ambiente principal:
.\scripts\restore-db.ps1 -Force

# Rollback sobre el ambiente de pruebas aislado:
.\scripts\restore-db.ps1 -ContainerName "mysql-feria-digital-test" -RedisContainerName "redis-feria-digital-test" -Force
```

### 4.2. Rollback Manual Paso a Paso

1. **Detener contenedores de microservicios** (o procesos Java locales):
   ```powershell
   .\Microservicios\stop-all-services.ps1
   ```
2. **Restaurar base de datos desde el respaldo pre-migración:**
   ```powershell
   Get-Content -Raw -Encoding UTF8 .\backups\backup_pre_migracion.sql | docker exec -i mysql-feria-digital mysql -u root -padmin
   ```
3. **Purgar caché de Redis** para evitar servir datos desalineados:
   ```powershell
   docker exec -i redis-feria-digital redis-cli FLUSHALL
   ```
4. **Reiniciar servicios en orden de dependencia:**
   MySQL -> Redis -> Eureka (`8761`) -> Auth (`8084`) -> Producto (`8081`) -> Inventario (`8082`) -> Ventas (`8083`) -> Gateway (`8080`) -> Frontend (`3000`).

### 4.3. Contingencia de Emergencia para Credenciales BCrypt (Mitigación R-04)

Si durante las pruebas de seguridad se produce un bloqueo de inicio de sesión por hashes BCrypt inválidos, ejecutar inmediatamente:

```powershell
Get-Content -Raw -Encoding UTF8 .\scripts\db-update-passwords-emergency.sql | docker exec -i mysql-feria-digital mysql -u root -padmin auth_service
```

---

## 5. Prueba Automatizada de Aislamiento y Rollback

El script [`scripts/test-environment.ps1`](file:///c:/Users/Administrador/Desktop/Vinculacion/v2/TiendaVirtual-TrabajoTitulacion-equipo/scripts/test-environment.ps1) ejecuta el ciclo completo de validación de la actividad A-02:
1. Levanta el ambiente aislado (`docker-compose.test.yml`).
2. Verifica el estado `healthy` de MySQL y Redis de pruebas.
3. Genera un respaldo con `backup-db.ps1`.
4. Simula una pérdida de datos en el entorno de pruebas (`DELETE` sobre tablas de clientes e inventario).
5. Ejecuta el rollback con `restore-db.ps1` empleando `backups/backup_pre_migracion.sql`.
6. Comprueba que los conteos regresen exactamente a la línea base (`6` usuarios, `6` categorías, `4` empresas, `39` productos, `39` inventarios, `5` clientes).

```powershell
.\scripts\test-environment.ps1
```


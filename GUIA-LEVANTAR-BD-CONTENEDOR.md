# Guía rápida: levantar contenedor de Base de Datos (MySQL)

Esta guía levanta **solo la BD** usando el archivo `docker-compose.db.yml` y los scripts SQL en raíz (`db-01` a `db-09`).

## 1) Prerrequisitos

- Docker Desktop instalado y en ejecución
- Docker Compose disponible (`docker compose version`)
- Estar ubicado en la raíz del proyecto

## 2) Levantar BD (modo normal)

```powershell
docker compose -f .\docker-compose.db.yml up -d
```

Ver estado:

```powershell
docker compose -f .\docker-compose.db.yml ps
```

Ver logs de MySQL:

```powershell
docker compose -f .\docker-compose.db.yml logs -f mysql
```

---

## 3) Levantar BD limpia desde cero (recomendado cuando cambias scripts)

> Esto elimina el volumen y vuelve a ejecutar todos los scripts de inicialización.

```powershell
docker compose -f .\docker-compose.db.yml down -v
docker compose -f .\docker-compose.db.yml up -d
```

---

## 4) Verificar que quedó bien

### Ver bases creadas

```powershell
docker exec -it mysql-feria-digital mysql -u admin -p -e "SHOW DATABASES;"
```

Debes ver: `auth_service`, `productos`, `inventario`, `ventas`.

### Ver conteos clave

```powershell
docker exec mysql-feria-digital mysql -u admin -padmin -e "SELECT 'productos.productos' tabla, COUNT(*) total FROM productos.productos UNION ALL SELECT 'inventario.inventarios', COUNT(*) FROM inventario.inventarios UNION ALL SELECT 'ventas.clientes', COUNT(*) FROM ventas.clientes;"
```

---

## 5) Parar BD

```powershell
docker compose -f .\docker-compose.db.yml stop
```

Para apagar y eliminar contenedor (sin borrar datos):

```powershell
docker compose -f .\docker-compose.db.yml down
```

Para apagar y eliminar datos (volumen):

```powershell
docker compose -f .\docker-compose.db.yml down -v
```

---

## 6) Errores comunes

### Error: puerto 3306 ocupado

Ver qué usa el puerto:

```powershell
netstat -ano | findstr :3306
```

Si tienes otro MySQL corriendo, detenerlo o cambiar el puerto en `docker-compose.db.yml`.

### Error: no se aplican scripts SQL

MySQL solo ejecuta scripts de `docker-entrypoint-initdb.d` cuando el volumen está vacío.

Solución:

```powershell
docker compose -f .\docker-compose.db.yml down -v
docker compose -f .\docker-compose.db.yml up -d
```

### Error de credenciales

Las credenciales se leen desde el archivo `.env` y no deben escribirse en esta guía.
Si cambias las credenciales de una instalación existente, coordina la actualización
de los microservicios y conserva un respaldo antes de aplicar el cambio.

---

## 7) Orden de scripts ejecutados

1. `db-01-create-databases.sql`
2. `db-02-schema-auth-service.sql`
3. `db-03-schema-productos.sql`
4. `db-04-schema-inventario.sql`
5. `db-05-schema-ventas.sql`
6. `db-06-seed-auth-service.sql`
7. `db-07-seed-productos.sql`
8. `db-08-seed-inventario.sql`
9. `db-09-seed-ventas.sql`

---

## 8) Comando recomendado para tu flujo diario

- Si ya existe BD y solo quieres encender:

```powershell
docker compose -f .\docker-compose.db.yml up -d
```

- Si cambiaste scripts SQL y quieres reaplicar todo:

```powershell
docker compose -f .\docker-compose.db.yml down -v
docker compose -f .\docker-compose.db.yml up -d
```

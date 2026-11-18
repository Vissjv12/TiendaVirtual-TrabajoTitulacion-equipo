# Evidencia: pruebas autenticadas del Gateway en producción

- **Fecha:** 2026-11-17
- **Destino:** `https://tienda.gadmsigchos.gob.ec:444`
- **Alcance:** solo lectura (`GET`), más los 3 inicios de sesión. No se modificaron datos.

## Inicio de sesión

```text
ADMIN login: 200    (ROLE_ADMIN, sin empresaId)
EMP login: 200      (ROLE_EMP, empresaId=2)
CLIENTE login: 200  (ROLE_USER, sin empresaId)
```

## Control de acceso por rol

```text
Cliente     -> GET /api/usuarios/paginado: 403
Cliente     -> GET /api/ventas/paginado: 403
Emprendedor -> GET /api/usuarios/paginado: 403
Emprendedor -> GET /api/movimientos (todos): 403
Admin       -> GET /api/usuarios/estadisticas: 200
Admin       -> GET /api/usuarios/paginado: 200
Admin       -> GET /api/ventas/paginado: 200
Admin       -> GET /api/movimientos/paginado: 200
Emprendedor -> GET /api/movimientos/paginado (propios): 200
Emprendedor -> GET /api/ventas/emprendedor/2/paginado (su empresa): 200
Emprendedor -> GET /api/ventas/emprendedor/1/paginado (empresa ajena): 403
```

**Resultado: 11 de 11 pruebas correctas.**

## Sin token

```text
GET /api/usuarios/paginado: 401
GET /api/usuarios/estadisticas: 401
GET /api/ventas/paginado: 401
GET /api/movimientos/paginado: 401
GET /api/ventas/emprendedor/1/paginado: 401
GET /api/productos/listado/paginado (público): 200
GET /api/empresas (público): 200
```

## Notas

- El aislamiento entre empresas se verificó con las ventas de la empresa propia (200) frente a las de otra empresa (403). No se usó `GET /api/productos/{id}`: es una ruta pública del catálogo, así que no demuestra nada sobre permisos.
- Tampoco se ejecutó el `PUT` sobre un producto ajeno. La validación del cuerpo de la petición se hace antes que la autorización, de modo que respondería 400 y no 403. Además modificaría datos de producción.

**JWT:** no compartido. **Contraseñas:** no compartidas.

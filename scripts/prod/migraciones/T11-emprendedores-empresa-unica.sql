-- ============================================================================
-- T11: una empresa pertenece a un solo emprendedor
-- Base: auth_service (MySQL 8). Ejecutar DESPUÉS de respaldar la base.
--
-- Agrega el índice único uk_emprendedor_empresa sobre emprendedores.empresa_id.
-- MySQL permite varias filas con empresa_id NULL en un índice único, así que los
-- emprendedores sin empresa no se ven afectados.
--
-- Es la garantía final contra dos solicitudes simultáneas que vinculen la misma
-- empresa a dos emprendedores: la aplicación responde 409 en lugar de crear un duplicado.
-- ============================================================================

USE auth_service;

-- ----------------------------------------------------------------------------
-- PASO 1 (solo lectura): ¿hay empresas vinculadas a más de un emprendedor?
-- Si devuelve filas, NO continuar: decidir con el responsable cuál vinculación
-- se conserva y desvincular las demás desde la administración (DELETE
-- /api/usuarios/{id}/empresa) o con un UPDATE ... SET empresa_id = NULL revisado.
-- ----------------------------------------------------------------------------
SELECT empresa_id,
       COUNT(*)                                     AS emprendedores,
       GROUP_CONCAT(usuario_id ORDER BY usuario_id) AS usuarios
FROM emprendedores
WHERE empresa_id IS NOT NULL
GROUP BY empresa_id
HAVING COUNT(*) > 1;

-- ----------------------------------------------------------------------------
-- PASO 2: ¿ya existe el índice? (si devuelve una fila, la migración ya se aplicó)
-- ----------------------------------------------------------------------------
SELECT INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'auth_service'
  AND TABLE_NAME = 'emprendedores'
  AND INDEX_NAME = 'uk_emprendedor_empresa';

-- ----------------------------------------------------------------------------
-- PASO 3: aplicar (solo si el paso 1 no devolvió filas y el paso 2 tampoco)
-- ----------------------------------------------------------------------------
ALTER TABLE emprendedores
    ADD CONSTRAINT uk_emprendedor_empresa UNIQUE (empresa_id);

-- Verificación: debe mostrar Non_unique = 0
SHOW INDEX FROM emprendedores WHERE Key_name = 'uk_emprendedor_empresa';

-- Rollback (solo si fuera necesario):
-- ALTER TABLE emprendedores DROP INDEX uk_emprendedor_empresa;

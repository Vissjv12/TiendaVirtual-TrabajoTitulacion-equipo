-- ============================================================================
-- SCRIPT DE EMERGENCIA (R-04): RESTABLECIMIENTO DE CONTRASEÑAS BCRYPT
-- Proyecto: Feria Digital Sigchos - Tienda Virtual
-- Propósito: Mitigar R-04 (Bloqueo de inicio de sesión por hashes BCrypt inválidos)
-- Restablece las cuentas de prueba a un hash BCrypt estándar conocido y funcional
-- Uso:
--   docker exec -i mysql-feria-digital mysql -u root -padmin auth_service < scripts/db-update-passwords-emergency.sql
-- ============================================================================

USE auth_service;

UPDATE usuarios
SET password = '$2a$10$kywJj9SI7ZW2CWuNlBPY/uFmZjjM9TuDakkeD8srL9TZtma59zsAS',
    activo = TRUE
WHERE username IN (
    'admin',
    'ultimo_inca',
    'sigcholac',
    'perla_andina',
    'grandes_foods',
    'cliente1'
);

SELECT id, username, email, activo, LEFT(password, 7) AS bcrypt_prefix
FROM usuarios
WHERE username IN (
    'admin',
    'ultimo_inca',
    'sigcholac',
    'perla_andina',
    'grandes_foods',
    'cliente1'
);


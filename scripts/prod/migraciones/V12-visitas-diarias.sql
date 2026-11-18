-- V12: contador histórico agregado de visitas públicas.
-- Base: productos. Ejecutar después de respaldar la base.

USE productos;

CREATE TABLE IF NOT EXISTS visitas_diarias (
    fecha DATE NOT NULL,
    cantidad BIGINT NOT NULL DEFAULT 0,
    fecha_actualizacion TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (fecha),
    CONSTRAINT chk_visitas_diarias_cantidad
        CHECK (cantidad >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

SHOW CREATE TABLE visitas_diarias;

-- ============================================================================
-- RESPALDO CONSOLIDADO PRE-MIGRACIÓN (LÍNEA BASE A-02)
-- Proyecto: Feria Digital Sigchos - Tienda Virtual
-- Bases incluidas: auth_service, productos, inventario, ventas
-- Propósito: Restauración íntegra determinista (Rollback R-03)
-- Uso:
--   docker exec -i mysql-feria-digital mysql -u root -padmin < backups/backup_pre_migracion.sql
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 1. REINICIO Y CREACIÓN DE BASES DE DATOS
-- ============================================================================
DROP DATABASE IF EXISTS `auth_service`;
DROP DATABASE IF EXISTS `productos`;
DROP DATABASE IF EXISTS `inventario`;
DROP DATABASE IF EXISTS `ventas`;

CREATE DATABASE `auth_service` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE `productos` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE `inventario` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE `ventas` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON auth_service.* TO 'admin'@'%';
GRANT ALL PRIVILEGES ON productos.* TO 'admin'@'%';
GRANT ALL PRIVILEGES ON inventario.* TO 'admin'@'%';
GRANT ALL PRIVILEGES ON ventas.* TO 'admin'@'%';
FLUSH PRIVILEGES;

-- ============================================================================
-- 2. ESQUEMA Y DATOS: auth_service
-- ============================================================================
USE auth_service;

CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100),
    telefono VARCHAR(15),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_usuarios_username (username),
    INDEX idx_usuarios_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS usuario_roles (
    usuario_id BIGINT NOT NULL,
    rol VARCHAR(50) NOT NULL,
    PRIMARY KEY (usuario_id, rol),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS emprendedores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE,
    empresa_id BIGINT,
    descripcion TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    INDEX idx_emprendedores_usuario_id (usuario_id),
    INDEX idx_emprendedores_empresa_id (empresa_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS configuracion_metodos_pago (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    emprendedor_id BIGINT NOT NULL,
    banco VARCHAR(100),
    tipo_cuenta VARCHAR(50),
    numero_cuenta VARCHAR(50),
    titular VARCHAR(255),
    cedula_ruc VARCHAR(20),
    email VARCHAR(255),
    qr_deuna_url TEXT,
    costo_envio DECIMAL(10,2) DEFAULT 5.00,
    payphone_app_id VARCHAR(255),
    payphone_token TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emprendedor_config (emprendedor_id),
    CONSTRAINT fk_config_pagos_emprendedor FOREIGN KEY (emprendedor_id)
        REFERENCES emprendedores(id) ON DELETE CASCADE,
    INDEX idx_config_pagos_emprendedor (emprendedor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS password_reset_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    codigo_hash VARCHAR(255) NOT NULL,
    expira_en TIMESTAMP NOT NULL,
    verificado BOOLEAN NOT NULL DEFAULT FALSE,
    reset_token_hash VARCHAR(255),
    token_expira_en TIMESTAMP NULL,
    token_usado BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id) ON DELETE CASCADE,
    INDEX idx_password_reset_usuario (usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO usuarios (username, email, password, nombre, apellido, telefono, activo) VALUES
('admin', 'admin@feriadigital.com', '$2a$10$kywJj9SI7ZW2CWuNlBPY/uFmZjjM9TuDakkeD8srL9TZtma59zsAS', 'Administrador', 'Sistema', '0999999999', TRUE),
('ultimo_inca', 'contacto@ultimoinca.com', '$2a$10$kywJj9SI7ZW2CWuNlBPY/uFmZjjM9TuDakkeD8srL9TZtma59zsAS', 'María', 'Quispe', '0997279323', TRUE),
('sigcholac', 'sigcholac@gmail.com', '$2a$10$kywJj9SI7ZW2CWuNlBPY/uFmZjjM9TuDakkeD8srL9TZtma59zsAS', 'Pedro', 'Guamán', '0992000198', TRUE),
('perla_andina', 'perlaandina@gmail.com', '$2a$10$kywJj9SI7ZW2CWuNlBPY/uFmZjjM9TuDakkeD8srL9TZtma59zsAS', 'Ana', 'Morocho', '0993396358', TRUE),
('grandes_foods', 'info@grandesfoods.com', '$2a$10$kywJj9SI7ZW2CWuNlBPY/uFmZjjM9TuDakkeD8srL9TZtma59zsAS', 'Carlos', 'Ramírez', '0993665065', TRUE),
('cliente1', 'cliente@ejemplo.com', '$2a$10$kywJj9SI7ZW2CWuNlBPY/uFmZjjM9TuDakkeD8srL9TZtma59zsAS', 'Juan', 'Pérez', '0993336635', TRUE)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO usuario_roles (usuario_id, rol)
SELECT id, 'ROLE_ADMIN' FROM usuarios WHERE username = 'admin'
ON DUPLICATE KEY UPDATE usuario_id=usuario_id;

INSERT INTO usuario_roles (usuario_id, rol)
SELECT id, 'ROLE_EMP' FROM usuarios WHERE username IN ('ultimo_inca', 'sigcholac', 'perla_andina', 'grandes_foods')
ON DUPLICATE KEY UPDATE usuario_id=usuario_id;

INSERT INTO usuario_roles (usuario_id, rol)
SELECT id, 'ROLE_USER' FROM usuarios WHERE username = 'cliente1'
ON DUPLICATE KEY UPDATE usuario_id=usuario_id;

INSERT INTO emprendedores (usuario_id, empresa_id, descripcion, activo)
SELECT u.id, 1, 'Producción artesanal de vinos de frutas andinas. Especialidad en vino de mortiño, frambuesa, pitahaya y maracuyá.', TRUE
FROM usuarios u WHERE u.username = 'ultimo_inca'
ON DUPLICATE KEY UPDATE usuario_id=usuario_id;

INSERT INTO emprendedores (usuario_id, empresa_id, descripcion, activo)
SELECT u.id, 2, 'Producción y comercialización de productos lácteos artesanales: queso fresco, queso mozzarella, yogurt.', TRUE
FROM usuarios u WHERE u.username = 'sigcholac'
ON DUPLICATE KEY UPDATE usuario_id=usuario_id;

INSERT INTO emprendedores (usuario_id, empresa_id, descripcion, activo)
SELECT u.id, 3, 'Vinos premium de frutas andinas. Especialidad en vinos dulces y secos.', TRUE
FROM usuarios u WHERE u.username = 'perla_andina'
ON DUPLICATE KEY UPDATE usuario_id=usuario_id;

INSERT INTO emprendedores (usuario_id, empresa_id, descripcion, activo)
SELECT u.id, 4, 'Producción de lácteos y productos cárnicos. Chochos, salchichas, longanizas y morcillas.', TRUE
FROM usuarios u WHERE u.username = 'grandes_foods'
ON DUPLICATE KEY UPDATE usuario_id=usuario_id;

-- ============================================================================
-- 3. ESQUEMA Y DATOS: productos
-- ============================================================================
USE productos;

CREATE TABLE IF NOT EXISTS categorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    descripcion VARCHAR(250),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS empresas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    ruc VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(100),
    telefono VARCHAR(15),
    direccion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_empresas_ruc (ruc),
    INDEX idx_empresas_activo (activo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS productos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(500),
    precio DECIMAL(10,2) NOT NULL CHECK (precio >= 0),
    imagen VARCHAR(500),
    categoria_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    emprendedor_id BIGINT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    INDEX idx_productos_categoria_id (categoria_id),
    INDEX idx_productos_empresa_id (empresa_id),
    INDEX idx_productos_emprendedor_id (emprendedor_id),
    INDEX idx_productos_activo (activo),
    INDEX idx_productos_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO categorias (nombre, descripcion, activo) VALUES
('Vinos', 'Vinos artesanales de frutas andinas', TRUE),
('Lácteos', 'Productos lácteos frescos y artesanales', TRUE),
('Bebidas', 'Bebidas y jugos naturales', TRUE),
('Snacks', 'Snacks y productos procesados', TRUE),
('Cárnicos', 'Productos cárnicos frescos', TRUE),
('Promociones', 'Combos y paquetes promocionales', TRUE)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO empresas (id, nombre, ruc, email, telefono, direccion, activo) VALUES
(1, 'El Último Inca', '1234567890111', 'contacto@ultimoinca.com', '0997279323', 'Barrio: QUINTICUSIG', TRUE),
(2, 'Sigcholac', '1212154849865', 'sigcholac@gmail.com', '0992000198', 'Producción de productos lácteos artesanales', TRUE),
(3, 'Perla Andina', '3213545484645', 'perlaandina@gmail.com', '0993396358', 'Especialidad en vinos premium', TRUE),
(4, 'Grandes Foods', '1792004411001', 'info@grandesfoods.com', '0993665065', 'C. N74-C, Quito 170307', TRUE)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO productos (nombre, descripcion, precio, imagen, categoria_id, empresa_id, emprendedor_id, activo) VALUES
('Vino de Mortiño | 165ml', 'Vino artesanal elaborado con mortiño andino, sabor único y frutal.', 2.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=600,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679555028968-MORTI%25C3%2583%25C2%2591O%2520MINI.png', 1, 1, 1, TRUE),
('Vino de Mortiño | 375ml', 'Vino artesanal elaborado con mortiño andino, sabor único y frutal.', 4.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=600,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679555023779-MORTI%25C3%2583%25C2%2591O%2520SMALL.png', 1, 1, 1, TRUE),
('Vino de Mortiño | 750ml', 'Vino artesanal elaborado con mortiño andino, sabor único y frutal.', 7.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=656,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679548183215-MORTI%25C3%2583%25C2%2591O.png', 1, 1, 1, TRUE),
('Vino de Frambuesa | 165ml', 'Vino artesanal elaborado con frambuesa, sabor único y frutal.', 2.50, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=861,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679555591286-FRAMBUESA%2520MINI.png', 1, 1, 1, TRUE),
('Vino de Frambuesa | 375ml', 'Vino artesanal elaborado con frambuesa, sabor único y frutal.', 4.50, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=600,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679549878311-FRAMBUESA%2520SMALL.png', 1, 1, 1, TRUE),
('Vino de Frambuesa | 750ml', 'Vino artesanal elaborado con frambuesa, sabor único y frutal.', 8.00, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=656,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679548692567-FRAMBUESA.png', 1, 1, 1, TRUE),
('Vino de Pitahaya | 165ml', 'Vino artesanal elaborado con Pitahaya, sabor único y frutal.', 2.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=861,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679555424389-PITAHAYA%2520MINI.png', 1, 1, 1, TRUE),
('Vino de Pitahaya | 375ml', 'Vino artesanal elaborado con Pitahaya, sabor único y frutal.', 4.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=861,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679549970343-PITAHAYA%2520SMALL.png', 1, 1, 1, TRUE),
('Vino de Pitahaya | 750ml', 'Vino artesanal elaborado con Pitahaya, sabor único y frutal.', 7.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=861,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679549468568-PITAHAYA.png', 1, 1, 1, TRUE),
('Vino de Maracuyá | 165ml', 'Vino artesanal elaborado con Maracuyá, sabor único y frutal.', 2.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=861,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1681336495232-MARACUYA%2520MINI.png', 1, 1, 1, TRUE),
('Vino de Maracuyá | 375ml', 'Vino artesanal elaborado con Maracuyá, sabor único y frutal.', 4.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=861,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1681336495232-MARACUYA%2520SMALL.png', 1, 1, 1, TRUE),
('Vino de Maracuyá | 750ml', 'Vino artesanal elaborado con Maracuyá, sabor único y frutal.', 7.25, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=861,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1679548980880-MARACUYA.png', 1, 1, 1, TRUE),
('Vino de Uva | 1L', 'Vino artesanal elaborado con Uva, sabor único y frutal.', 10.00, 'https://cdn.zyrosite.com/cdn-cgi/image/format=auto,w=861,fit=crop/cdn-ecommerce/store_01GQQFWQ3ZM7PMRXBNP6HY8WRH%2Fassets%2F1681336495232-MARACUYA%2520MINI.png', 1, 1, 1, TRUE),
('Queso fresco | 500g', 'Queso fresco artesanal, elaborado con leche de alta calidad. Producto 100% natural.', 2.40, 'https://i.imgur.com/ZSJbdcm.png', 2, 2, 2, TRUE),
('Queso mozzarella | 500g', 'Queso mozzarella artesanal, textura cremosa. Ideal para pizzas y gratinados.', 3.40, 'https://i.imgur.com/gJIX6pd.png', 2, 2, 2, TRUE),
('Queso mozzarella | 1000g', 'Queso mozzarella artesanal de 1kg. Sabor suave y textura perfecta.', 6.70, 'https://i.imgur.com/88svWy3.png', 2, 2, 2, TRUE),
('Queso tipo finca | 500g', 'Queso tipo finca, sabor tradicional. Ideal para acompañar comidas.', 2.00, 'https://i.imgur.com/default.png', 2, 2, 2, TRUE),
('Queso tipo finca | 1000g', 'Queso tipo finca de 1kg. Producto artesanal 100% natural.', 4.00, 'https://i.imgur.com/default.png', 2, 2, 2, TRUE),
('Yogurt 0.5 litros', 'Yogurt cremoso y nutritivo. Deliciosos sabores naturales.', 1.00, 'https://i.imgur.com/5NmoEJ1.png', 2, 2, 2, TRUE),
('Yogurt 1 litro', 'Yogurt cremoso de 1 litro. Perfecto para toda la familia.', 1.75, 'https://i.imgur.com/7C6VcDe.png', 2, 2, 2, TRUE),
('Yogurt 2 litros', 'Yogurt de 2 litros. Ideal para compartir.', 3.00, 'https://i.imgur.com/WJPBd3I.png', 2, 2, 2, TRUE),
('Yogurt 4 litros', 'Yogurt de 4 litros. Formato económico familiar.', 5.00, 'https://i.imgur.com/WJPBd3I.png', 2, 2, 2, TRUE),
('GRAN PROMOCIÓN', '1 Queso fresco 500gr | 1 Yogurt 2L | 1 Queso Mozzarella 500g', 10.00, 'https://i.imgur.com/vFaigkD.png', 6, 2, 2, TRUE),
('Vino de mortiño | 165ml', 'Vino dulce premium de mortiño andino. Edición especial.', 2.25, 'https://i.imgur.com/39teCT3.png', 1, 3, 3, TRUE),
('Vino de mortiño | 375ml', 'Vino dulce de mortiño. Producción artesanal con frutas de páramo.', 5.00, 'https://i.imgur.com/xNInmeg.png', 1, 3, 3, TRUE),
('Vino de mortiño | 750ml', 'Vino dulce de mortiño premium. Sabor único de los Andes.', 7.00, 'https://i.imgur.com/Ae1cuO6.png', 1, 3, 3, TRUE),
('Vino de mortiño seco | 750ml', 'Vino seco de mortiño. Para paladares exigentes.', 10.00, 'https://i.imgur.com/zRafdjv.png', 1, 3, 3, TRUE),
('Vino de mora | 375ml', 'Vino de mora artesanal. Sabor intenso y natural.', 5.00, 'https://i.imgur.com/SmEGeU2.png', 1, 3, 3, TRUE),
('Vino de mora | 750ml', 'Vino de mora premium. Cosecha de climas templados.', 10.00, 'https://i.imgur.com/SrWVz35.png', 1, 3, 3, TRUE),
('Vino de frambuesa | 750ml', 'Vino de frambuesa artesanal. Dulce y aromático.', 10.00, 'https://i.imgur.com/v9oRrJG.png', 1, 3, 3, TRUE),
('Vino de frambuesa PREMIUM | 750ml', 'Vino de frambuesa edición especial. Producción limitada.', 10.00, 'https://i.imgur.com/SmEGeU2_d.png', 1, 3, 3, TRUE),
('Queso fresco | 500g', 'Queso fresco artesanal de alta calidad.', 3.00, 'https://i.imgur.com/eCJ8Ycr.png', 2, 4, 4, TRUE),
('Queso mozzarella | 500g', 'Queso mozzarella premium para cocina.', 4.25, 'https://i.imgur.com/NaoM78G.png', 2, 4, 4, TRUE),
('Yogurt 1 litro', 'Yogurt natural cremoso y nutritivo.', 2.50, 'https://i.imgur.com/67jahHz.png', 2, 4, 4, TRUE),
('Choco | 100g', 'Snack de chocho, alto en proteína vegetal.', 3.00, 'https://i.imgur.com/pwVF7Yg.png', 4, 4, 4, TRUE),
('Salchicha de ternera | 500g', 'Salchicha 100% carne de res. Jugosa y sabrosa.', 4.00, 'https://i.imgur.com/2YTpcjD.png', 5, 4, 4, TRUE),
('Longaniza de Praga', 'Longaniza ahumada estilo tradicional.', 4.50, 'https://i.imgur.com/2HEc13i.png', 5, 4, 4, TRUE),
('Morcilla de sangre | 500g', 'Morcilla artesanal con especias selectas.', 3.00, 'https://i.imgur.com/faQboVz.png', 5, 4, 4, TRUE),
('Salchicha Frankfurt | 500g', 'Salchicha Frankfurt clásica. Ideal para hot dogs.', 3.00, 'https://i.imgur.com/Y9LX4Gq.png', 5, 4, 4, TRUE);

-- ============================================================================
-- 4. ESQUEMA Y DATOS: inventario
-- ============================================================================
USE inventario;

CREATE TABLE IF NOT EXISTS inventarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT NOT NULL UNIQUE,
    cantidad INT NOT NULL DEFAULT 0 CHECK (cantidad >= 0),
    ubicacion VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_inventarios_producto_id (producto_id),
    INDEX idx_inventarios_activo (activo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS movimientos_inventario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventario_id BIGINT NOT NULL,
    tipo_movimiento ENUM('ENTRADA', 'SALIDA', 'AJUSTE') NOT NULL,
    cantidad INT NOT NULL,
    motivo VARCHAR(200),
    usuario_id BIGINT,
    fecha_movimiento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inventario_id) REFERENCES inventarios(id) ON DELETE CASCADE,
    INDEX idx_movimientos_inventario_id (inventario_id),
    INDEX idx_movimientos_fecha (fecha_movimiento DESC),
    INDEX idx_movimientos_tipo (tipo_movimiento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO inventarios (producto_id, cantidad, ubicacion, activo) VALUES
(1, 500, 'Bodega Principal - Quinticusig', TRUE),
(2, 1000, 'Bodega Principal - Quinticusig', TRUE),
(3, 500, 'Bodega Principal - Quinticusig', TRUE),
(4, 500, 'Bodega Principal - Quinticusig', TRUE),
(5, 1000, 'Bodega Principal - Quinticusig', TRUE),
(6, 500, 'Bodega Principal - Quinticusig', TRUE),
(7, 500, 'Bodega Principal - Quinticusig', TRUE),
(8, 1000, 'Bodega Principal - Quinticusig', TRUE),
(9, 500, 'Bodega Principal - Quinticusig', TRUE),
(10, 500, 'Bodega Principal - Quinticusig', TRUE),
(11, 1000, 'Bodega Principal - Quinticusig', TRUE),
(12, 500, 'Bodega Principal - Quinticusig', TRUE),
(13, 200, 'Bodega Principal - Quinticusig', TRUE),
(14, 300, 'Planta de Producción - Sigcholac', TRUE),
(15, 300, 'Planta de Producción - Sigcholac', TRUE),
(16, 200, 'Planta de Producción - Sigcholac', TRUE),
(17, 300, 'Planta de Producción - Sigcholac', TRUE),
(18, 200, 'Planta de Producción - Sigcholac', TRUE),
(19, 500, 'Cámara Fría - Sigcholac', TRUE),
(20, 500, 'Cámara Fría - Sigcholac', TRUE),
(21, 300, 'Cámara Fría - Sigcholac', TRUE),
(22, 200, 'Cámara Fría - Sigcholac', TRUE),
(23, 50, 'Almacén Principal - Sigcholac', TRUE),
(24, 500, 'Bodega - Perla Andina', TRUE),
(25, 1000, 'Bodega - Perla Andina', TRUE),
(26, 500, 'Bodega - Perla Andina', TRUE),
(27, 300, 'Bodega - Perla Andina', TRUE),
(28, 500, 'Bodega - Perla Andina', TRUE),
(29, 500, 'Bodega - Perla Andina', TRUE),
(30, 300, 'Bodega - Perla Andina', TRUE),
(31, 200, 'Bodega - Perla Andina', TRUE),
(32, 200, 'Planta - Grandes Foods, Quito', TRUE),
(33, 200, 'Planta - Grandes Foods, Quito', TRUE),
(34, 300, 'Planta - Grandes Foods, Quito', TRUE),
(35, 1000, 'Planta - Grandes Foods, Quito', TRUE),
(36, 500, 'Cámara Fría - Grandes Foods', TRUE),
(37, 500, 'Cámara Fría - Grandes Foods', TRUE),
(38, 300, 'Cámara Fría - Grandes Foods', TRUE),
(39, 300, 'Cámara Fría - Grandes Foods', TRUE);

INSERT INTO movimientos_inventario (inventario_id, tipo_movimiento, cantidad, motivo, usuario_id, fecha_movimiento)
SELECT id, 'ENTRADA', cantidad, 'Inventario inicial del producto', NULL, NOW()
FROM inventarios;

-- ============================================================================
-- 5. ESQUEMA Y DATOS: ventas
-- ============================================================================
USE ventas;

CREATE TABLE IF NOT EXISTS clientes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    telefono VARCHAR(15),
    documento VARCHAR(20),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_clientes_email (email),
    INDEX idx_clientes_documento (documento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ventas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_factura VARCHAR(20) NOT NULL UNIQUE,
    cliente_id BIGINT NOT NULL,
    emprendedor_id BIGINT NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL CHECK (subtotal >= 0),
    impuesto DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (impuesto >= 0),
    total DECIMAL(10,2) NOT NULL CHECK (total >= 0),
    estado ENUM('PENDIENTE', 'COMPLETADA', 'CANCELADA') NOT NULL DEFAULT 'PENDIENTE',
    metodo_pago ENUM('TRANSFERENCIA', 'TARJETA', 'DEUNA'),
    estado_pago ENUM('PENDIENTE', 'PROCESANDO', 'APROBADO', 'RECHAZADO') NOT NULL DEFAULT 'PENDIENTE',
    comprobante_pago_url TEXT,
    referencia_transaccion VARCHAR(255),
    fecha_pago TIMESTAMP NULL,
    fecha_venta TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    INDEX idx_ventas_cliente_id (cliente_id),
    INDEX idx_ventas_emprendedor_id (emprendedor_id),
    INDEX idx_ventas_numero_factura (numero_factura),
    INDEX idx_ventas_estado (estado),
    INDEX idx_ventas_fecha (fecha_venta DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS detalles_venta (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    venta_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    emprendedor_id BIGINT NOT NULL,
    nombre_producto VARCHAR(100) NOT NULL,
    cantidad INT NOT NULL CHECK (cantidad > 0),
    precio_unitario DECIMAL(10,2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal DECIMAL(10,2) NOT NULL CHECK (subtotal >= 0),
    FOREIGN KEY (venta_id) REFERENCES ventas(id) ON DELETE CASCADE,
    INDEX idx_detalles_venta_id (venta_id),
    INDEX idx_detalles_producto_id (producto_id),
    INDEX idx_detalles_emprendedor_id (emprendedor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pagos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    venta_id BIGINT NOT NULL,
    metodo_pago ENUM('TRANSFERENCIA', 'TARJETA', 'DEUNA') NOT NULL,
    estado_pago ENUM('PENDIENTE', 'PROCESANDO', 'APROBADO', 'RECHAZADO') NOT NULL DEFAULT 'PENDIENTE',
    banco VARCHAR(100),
    numero_cuenta VARCHAR(50),
    titular_cuenta VARCHAR(100),
    numero_transaccion VARCHAR(100),
    referencia_transaccion VARCHAR(255),
    numero_tarjeta_parcial VARCHAR(20),
    qr_code TEXT,
    comprobante_url TEXT,
    monto DECIMAL(10,2) NOT NULL CHECK (monto >= 0),
    fecha_pago TIMESTAMP NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (venta_id) REFERENCES ventas(id) ON DELETE CASCADE,
    INDEX idx_pagos_venta_id (venta_id),
    INDEX idx_pagos_estado (estado_pago),
    INDEX idx_pagos_metodo (metodo_pago)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS seguimiento_logistica (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    venta_id BIGINT NOT NULL,
    estado_logistica ENUM(
        'PAGO_APROBADO',
        'EN_PREPARACION',
        'LISTO_PARA_ENVIO',
        'EN_CAMINO',
        'EN_PUNTO_ENTREGA',
        'ENTREGADO',
        'CANCELADO'
    ) NOT NULL,
    descripcion TEXT,
    ubicacion VARCHAR(255),
    responsable VARCHAR(100),
    observaciones TEXT,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seguimiento_logistica_venta
        FOREIGN KEY (venta_id) REFERENCES ventas(id) ON DELETE CASCADE,
    INDEX idx_venta_id (venta_id),
    INDEX idx_estado_logistica (estado_logistica),
    INDEX idx_fecha_actualizacion (fecha_actualizacion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO clientes (nombre, apellido, email, telefono, documento, activo) VALUES
('Juan', 'Pérez', 'cliente@ejemplo.com', '0993336635', '1105888034', TRUE),
('María', 'González', 'maria.gonzalez@ejemplo.com', '0996365874', '1104556789', TRUE),
('Carlos', 'Ramírez', 'carlos.ramirez@ejemplo.com', '0993369852', '1103445678', TRUE),
('Ana', 'Martínez', 'ana.martinez@ejemplo.com', '0999999999', '1102334567', TRUE),
('Luis', 'Torres', 'luis.torres@ejemplo.com', '0998888888', '1101223456', TRUE)
ON DUPLICATE KEY UPDATE id=id;

SET FOREIGN_KEY_CHECKS = 1;


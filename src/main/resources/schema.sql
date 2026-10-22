-- =============================================
-- Script de creación de la base de datos
-- Gestión de Usuarios - Arquitectura Hexagonal
-- =============================================

CREATE DATABASE IF NOT EXISTS crud_usuarios
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE crud_usuarios;

CREATE TABLE IF NOT EXISTS users (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        ENUM('ADMIN', 'MEMBER', 'REVIEWER') NOT NULL,
    status      ENUM('ACTIVE', 'INACTIVE', 'PENDING', 'BLOCKED') NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------
-- Códigos de activación de cuenta
-- ---------------------------------------------
-- Se guarda solo el hash SHA-256 del código; el valor en claro viaja únicamente
-- por correo al usuario. Un código caduca y no puede reutilizarse.
CREATE TABLE IF NOT EXISTS activation_codes (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    code_hash   VARCHAR(64)  NOT NULL,
    expires_at  DATETIME     NOT NULL,
    used_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activation_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_activation_codes_user (user_id),
    INDEX idx_activation_codes_hash (code_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Usuario administrador inicial (email: admin@example.com / password: Admin1234!)
-- Cambiar esa contrasena antes de exponer la API en Render.
INSERT INTO users (id, name, email, password, role, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Administrador',
    'admin@example.com',
    '$2a$12$qL.zu/7CP3ghU4KZrzDWZuXR/73Y9kJcxumNvY05KPjuNX/ctI3SK',
    'ADMIN',
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE id = id;


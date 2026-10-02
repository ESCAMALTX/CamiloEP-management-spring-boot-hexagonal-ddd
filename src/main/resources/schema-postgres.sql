-- =============================================
-- Script de creacion de la base de datos
-- Gestion de Usuarios - Arquitectura Hexagonal
-- Motor: PostgreSQL 12+ (compatible con Supabase)
-- =============================================
--
-- NOTA: PostgreSQL no soporta CREATE DATABASE condicional dentro
-- de un script transaccional, ni el modificador "IF NOT EXISTS"
-- de MySQL para bases de datos. La base de datos se crea aparte:
--
--   CREATE DATABASE crud_usuarios;
--
-- o desde Supabase, la base ya viene creada (schema "public").
-- Este script asume que ya estas conectado a la base de datos.
-- =============================================

-- ---------------------------------------------
-- Tipos enumerados nativos de PostgreSQL
-- ---------------------------------------------
-- MySQL declaraba ENUM(...) directamente en la columna.
-- PostgreSQL requiere crear el tipo primero con CREATE TYPE.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        CREATE TYPE user_role AS ENUM ('ADMIN', 'MEMBER', 'REVIEWER');
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_status') THEN
        CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'PENDING', 'BLOCKED');
    END IF;
END
$$;

-- ---------------------------------------------
-- Tabla users
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        user_role    NOT NULL,
    status      user_status  NOT NULL DEFAULT 'PENDING',
    -- TIMESTAMP en lugar de DATETIME: PostgreSQL no tiene DATETIME.
    -- WITHOUT TIME ZONE replica el comportamiento de MySQL DATETIME.
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------
-- Trigger para emular "ON UPDATE CURRENT_TIMESTAMP"
-- ---------------------------------------------
-- MySQL actualizaba updated_at automaticamente con:
--     updated_at DATETIME ... ON UPDATE CURRENT_TIMESTAMP
-- PostgreSQL no tiene esa clausula: se implementa con un trigger.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS users_set_updated_at ON users;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------
-- Usuario administrador inicial (password: Admin1234!)
-- ---------------------------------------------
-- ON CONFLICT (id) DO NOTHING evita el error de clave duplicada
-- si el script se ejecuta mas de una vez (equivalente a
-- INSERT IGNORE de MySQL).
INSERT INTO users (id, name, email, password, role, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Administrador',
    'admin@example.com',
    '$2a$12$placeholderHashReplaceWithRealBCryptHash',
    'ADMIN',
    'ACTIVE'
)
ON CONFLICT (id) DO NOTHING;

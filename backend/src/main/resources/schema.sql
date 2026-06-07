-- ============================================================
-- Независимые таблицы (без FK)
-- ============================================================

CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS users (
    id                      BIGSERIAL PRIMARY KEY,
    username                VARCHAR(50)  NOT NULL,
    password                VARCHAR(255) NOT NULL,
    email                   VARCHAR(100),
    enabled                 BOOLEAN DEFAULT true,
    account_non_expired     BOOLEAN DEFAULT true,
    credentials_non_expired BOOLEAN DEFAULT true,
    account_non_locked      BOOLEAN DEFAULT true,
    created_at              TIMESTAMP DEFAULT NOW(),
    updated_at              TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id)  ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS device_group (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    CONSTRAINT uk_device_group_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS device_os (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    vendor     VARCHAR(255),
    model      VARCHAR(200),
    version    INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_device_os_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS scan_schedules (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    subnet          VARCHAR(50)  NOT NULL,
    mask            INT          NOT NULL DEFAULT 24,
    port            INT          NOT NULL DEFAULT 161,
    community       VARCHAR(100) NOT NULL DEFAULT 'public',
    snmp_version    VARCHAR(10)  NOT NULL DEFAULT 'v2c',
    scan_mode       VARCHAR(50)  NOT NULL DEFAULT 'all',
    cron_expression VARCHAR(100),
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    ssh_username    VARCHAR(255),
    ssh_password    VARCHAR(255),
    winrm_username  VARCHAR(255),
    winrm_password  VARCHAR(255),
    -- SNMPv3 (USM): логин и пароли аутентификации/шифрования (пароли зашифрованы)
    snmp_security_name VARCHAR(255),
    snmp_auth_protocol VARCHAR(20),
    snmp_auth_password VARCHAR(512),
    snmp_priv_protocol VARCHAR(20),
    snmp_priv_password VARCHAR(512),
    last_run_at     TIMESTAMP,
    last_run_status VARCHAR(50),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Профили доступа для сканирования (пароли хранятся зашифрованными)
CREATE TABLE IF NOT EXISTS scan_credentials (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    domain          VARCHAR(255),
    ssh_username    VARCHAR(255),
    ssh_password    VARCHAR(512),
    winrm_username  VARCHAR(255),
    winrm_password  VARCHAR(512),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_scan_credentials_name UNIQUE (name)
);
ALTER TABLE scan_credentials ADD COLUMN IF NOT EXISTS domain VARCHAR(255);

-- ============================================================
-- Таблицы, зависящие от device_group / device_os
-- ============================================================

CREATE TABLE IF NOT EXISTS device_info (
    id            BIGSERIAL PRIMARY KEY,
    hostname      VARCHAR(255) NOT NULL,
    type          SMALLINT     NOT NULL,
    is_active     BOOLEAN      DEFAULT true,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    group_id      BIGINT REFERENCES device_group(id) ON DELETE SET NULL,
    os_version_id BIGINT REFERENCES device_os(id)    ON DELETE SET NULL,
    CONSTRAINT uk_device_info_hostname UNIQUE (hostname)
);

CREATE INDEX IF NOT EXISTS idx_device_info_group      ON device_info(group_id);
CREATE INDEX IF NOT EXISTS idx_device_info_os_version ON device_info(os_version_id);
CREATE INDEX IF NOT EXISTS idx_devices_type           ON device_info(type);

-- ============================================================
-- Таблицы, зависящие от device_info
-- ============================================================

CREATE TABLE IF NOT EXISTS device_ip (
    id             BIGSERIAL PRIMARY KEY,
    device_info_id BIGINT      NOT NULL REFERENCES device_info(id) ON DELETE CASCADE,
    ip             VARCHAR(45) NOT NULL,
    if_name        VARCHAR(100),
    is_primary     BOOLEAN DEFAULT false,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_ip_device_ip UNIQUE (device_info_id, ip)
);

CREATE INDEX IF NOT EXISTS idx_device_ip_ip ON device_ip(ip);

-- ============================================================
-- Версии конфигурации (self-referencing)
-- ============================================================

CREATE TABLE IF NOT EXISTS config_versions (
    id                BIGSERIAL PRIMARY KEY,
    version_num       INTEGER     NOT NULL,
    config_data       JSONB       NOT NULL,
    checksum          CHAR(64)    NOT NULL,
    created_at        TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    parent_version_id BIGINT REFERENCES config_versions(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_config_versions_jsonb ON config_versions USING gin (config_data);

-- ============================================================
-- Активная конфигурация устройства
-- ============================================================

CREATE TABLE IF NOT EXISTS device_config (
    device_id  BIGINT    NOT NULL REFERENCES device_info(id)     ON DELETE CASCADE,
    config_id  BIGINT    NOT NULL REFERENCES config_versions(id) ON DELETE CASCADE,
    applied_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_config_device_id ON device_config(device_id);
CREATE INDEX IF NOT EXISTS idx_cvt_config_id           ON device_config(config_id);

-- ============================================================
-- Аудит
-- ============================================================

CREATE TABLE IF NOT EXISTS event_log (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT,
    event_type     VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50),
    aggregate_id   BIGINT,
    payload        JSONB,
    metadata       JSONB,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_aggregate    ON event_log(aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_audit_time         ON event_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user_created ON event_log(user_id, created_at);

-- ============================================================
-- Шаблоны конфигурации (зависят от users)
-- ============================================================

CREATE TABLE IF NOT EXISTS config_templates (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    content     JSONB        NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_by  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_config_templates_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS template_assignments (
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT    NOT NULL REFERENCES config_templates(id) ON DELETE CASCADE,
    device_id   BIGINT    NOT NULL REFERENCES device_info(id)      ON DELETE CASCADE,
    assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_template_assignments UNIQUE (template_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_template_assignments_template ON template_assignments(template_id);
CREATE INDEX IF NOT EXISTS idx_template_assignments_device   ON template_assignments(device_id);

-- ============================================================
-- МИГРАЦИИ для существующих баз данных (идемпотентные)
-- ============================================================

-- scan_schedules: добавляем credential-колонки если отсутствуют
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS ssh_username   VARCHAR(255);
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS ssh_password   VARCHAR(512);
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS winrm_username VARCHAR(255);
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS winrm_password VARCHAR(512);
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS credential_id  BIGINT;
-- зашифрованные значения длиннее — расширяем существующие колонки
ALTER TABLE scan_schedules ALTER COLUMN ssh_password   TYPE VARCHAR(512);
ALTER TABLE scan_schedules ALTER COLUMN winrm_password TYPE VARCHAR(512);
-- scan_schedules: SNMPv3 (USM) колонки для сканирования v3-устройств
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS snmp_security_name VARCHAR(255);
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS snmp_auth_protocol VARCHAR(20);
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS snmp_auth_password VARCHAR(512);
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS snmp_priv_protocol VARCHAR(20);
ALTER TABLE scan_schedules ADD COLUMN IF NOT EXISTS snmp_priv_password VARCHAR(512);

-- event_log: удаляем устаревший CHECK constraint (ограничивал только 5 типов событий)
ALTER TABLE event_log DROP CONSTRAINT IF EXISTS audit_log_action_type_check;

-- event_log: aggregate_id и aggregate_type должны быть nullable (AuthEvent их не заполняет)
ALTER TABLE event_log ALTER COLUMN aggregate_id   DROP NOT NULL;
ALTER TABLE event_log ALTER COLUMN aggregate_type DROP NOT NULL;

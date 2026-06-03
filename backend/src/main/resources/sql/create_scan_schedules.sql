CREATE TABLE IF NOT EXISTS scan_schedules (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    subnet          VARCHAR(50)     NOT NULL,
    mask            INT             NOT NULL DEFAULT 24,
    port            INT             NOT NULL DEFAULT 161,
    community       VARCHAR(100)    NOT NULL DEFAULT 'public',
    snmp_version    VARCHAR(10)     NOT NULL DEFAULT 'v2c',
    scan_mode       VARCHAR(50)     NOT NULL DEFAULT 'all',
    cron_expression VARCHAR(100),
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    last_run_at     TIMESTAMP,
    last_run_status VARCHAR(50),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

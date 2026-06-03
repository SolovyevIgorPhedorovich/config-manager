CREATE TABLE IF NOT EXISTS config_templates (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL UNIQUE,
    description TEXT,
    content     JSONB           NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT true,
    created_by  BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS template_assignments (
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT          NOT NULL REFERENCES config_templates(id) ON DELETE CASCADE,
    device_id   BIGINT          NOT NULL REFERENCES device_info(id) ON DELETE CASCADE,
    assigned_by BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (template_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_template_assignments_template ON template_assignments(template_id);
CREATE INDEX IF NOT EXISTS idx_template_assignments_device   ON template_assignments(device_id);

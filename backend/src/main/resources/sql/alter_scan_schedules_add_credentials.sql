ALTER TABLE scan_schedules
    ADD COLUMN IF NOT EXISTS ssh_username    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ssh_password    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS winrm_username  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS winrm_password  VARCHAR(255);

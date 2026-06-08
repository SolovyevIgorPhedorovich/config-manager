-- Тестовый администратор для прогона API-тестов.
-- Логин: admin   Пароль: admin123  (BCrypt $2b$10, проверяется BCryptPasswordEncoder)
-- Применять ПОСЛЕ старта бэкенда (schema.sql создаётся на старте приложения).
--   psql "postgresql://igor:8008@localhost:5432/mydatabase" -f 01-seed-admin.sql

INSERT INTO roles (name, description)
VALUES ('ADMIN', 'Administrator')
ON CONFLICT (name) DO NOTHING;

INSERT INTO users (username, password, email, enabled,
                   account_non_expired, credentials_non_expired, account_non_locked)
VALUES ('admin',
        '$2b$10$K1O/15ca3KzrQ1XBkVSijeLoKSolj2d1ILKXLaLdeTmTmP7C3VkzW',
        'admin@test.local', true, true, true, true)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

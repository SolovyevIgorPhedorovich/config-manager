-- Приведение ролей к канонической модели RBAC: ROLE_ADMIN / ROLE_AUDITOR / ROLE_VIEWER.
-- Чистит исторические дубли и легаси-роли (ADMIN/VIEWER без префикса, ROLE_OPERATOR, ROLE_USER).
-- Идемпотентно: безопасно повторно. Применять к существующим БД (свежие — через schema.sql).
BEGIN;

-- 1. Гарантируем наличие канонических ролей.
INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN',   'Администратор: полный доступ, управление пользователями и ролями'),
    ('ROLE_AUDITOR', 'Аудитор: чтение и просмотр аудит-логов'),
    ('ROLE_VIEWER',  'Наблюдатель: только просмотр (read-only)')
ON CONFLICT (name) DO NOTHING;

-- 2. Переносим пользователей с легаси/не-префиксных ролей на канонические.
--    Бывшие операторы становятся ADMIN (в новой модели операции делает ADMIN).
INSERT INTO user_role (user_id, role_id)
SELECT ur.user_id, canon.id
FROM user_role ur
JOIN roles legacy ON legacy.id = ur.role_id
JOIN roles canon  ON canon.name = CASE legacy.name
        WHEN 'ADMIN'         THEN 'ROLE_ADMIN'
        WHEN 'OPERATOR'      THEN 'ROLE_ADMIN'
        WHEN 'ROLE_OPERATOR' THEN 'ROLE_ADMIN'
        WHEN 'VIEWER'        THEN 'ROLE_VIEWER'
    END
WHERE legacy.name IN ('ADMIN', 'OPERATOR', 'ROLE_OPERATOR', 'VIEWER')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 3. Удаляем привязки к легаси/дублям (FK создан без ON DELETE CASCADE),
--    затем сами роли.
DELETE FROM user_role
 WHERE role_id IN (SELECT id FROM roles
                   WHERE name IN ('ROLE_USER', 'ROLE_OPERATOR', 'OPERATOR', 'ADMIN', 'VIEWER'));
DELETE FROM roles WHERE name IN ('ROLE_USER', 'ROLE_OPERATOR', 'OPERATOR', 'ADMIN', 'VIEWER');

COMMIT;

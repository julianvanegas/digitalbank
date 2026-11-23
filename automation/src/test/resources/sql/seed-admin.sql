-- Admin inicial para las pruebas de automatización. SOLO para la base local de QA (bank_qa).
-- Nunca ejecutar en un ambiente desplegado. Es idempotente: si el admin ya existe, no hace nada.
-- Contraseña de prueba: Admin#2026qa (cumple la política: 8-16 caracteres, mayúscula, minúscula,
-- número y carácter especial).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH new_user AS (
    INSERT INTO users (id, email, password_hash, password_changed_at, role_id, status_id,
                       failed_attempts, status_changed_at, created_at)
    VALUES (gen_random_uuid(), 'admin.qa@example.com',
            crypt('Admin#2026qa', gen_salt('bf', 10)),
            now(), 2, 1, 0, now(), now())      -- role 2 = ADMIN, status 1 = ACTIVE
    ON CONFLICT (email) DO NOTHING
    RETURNING id
)
INSERT INTO employees (id, user_id, role_id, first_names, last_names, created_at, updated_at)
SELECT gen_random_uuid(), id, 2, 'Admin', 'QA Automatización', now(), now()
FROM new_user;
-- Módulo auth: identidad de acceso (usuario, sesión y verificaciones). No depende de ningún otro módulo.

-- ============ CATÁLOGOS ============
CREATE TABLE roles (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE user_status (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE challenge_purposes (
    id           SMALLINT PRIMARY KEY,
    -- Se mantiene como "purpose" por decisión de diseño, aunque los demás catálogos usan "code"
    purpose      VARCHAR(30) NOT NULL UNIQUE,
    ttl_minutes  INTEGER NOT NULL CHECK (ttl_minutes > 0),
    max_attempts INTEGER NOT NULL CHECK (max_attempts > 0)
);

-- Los ids son fijos: el CHECK de customers depende del id de role
INSERT INTO roles (id, code) VALUES (1, 'CUSTOMER'), (2, 'ADMIN');

INSERT INTO user_status (id, code) VALUES
    (1, 'ACTIVE'),
    (2, 'PENDING_VERIFICATION'),
    (3, 'INACTIVE'),
    (4, 'BLOCKED');

INSERT INTO challenge_purposes (id, purpose, ttl_minutes, max_attempts) VALUES
    (1, 'LOGIN',               5, 3),
    (2, 'EMAIL_CONFIRMATION', 30, 3),
    (3, 'PASSWORD_RESET',     15, 3);

-- ============ USUARIO ============
CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    -- Siempre en minúsculas: Ana@x.com y ana@x.com son la misma identidad. El CHECK impide guardar
    -- otra cosa, sin importar por dónde entre el dato, y permite un UNIQUE normal.
    email               VARCHAR(254) NOT NULL UNIQUE,
    password_hash       VARCHAR(60) NOT NULL,
    password_changed_at TIMESTAMP NOT NULL,
    role_id             SMALLINT NOT NULL REFERENCES roles (id),
    status_id           SMALLINT NOT NULL REFERENCES user_status (id),
    failed_attempts     SMALLINT NOT NULL DEFAULT 0,
    last_login_at       TIMESTAMP,
    -- Último cambio real de estado: reloj de la inactividad junto con last_login_at
    status_changed_at   TIMESTAMP NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    -- necesaria para la FK compuesta de los perfiles (customers)
    CONSTRAINT uk_users_id_role UNIQUE (id, role_id),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE INDEX idx_users_status_last_login ON users (status_id, last_login_at);

-- ============ SESIÓN ÚNICA Y VERIFICACIONES ============
CREATE TABLE auth_sessions (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    jti        UUID NOT NULL,
    issued_at  TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_auth_sessions_expires CHECK (expires_at > issued_at)
);

CREATE TABLE verification_challenges (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose_id      SMALLINT NOT NULL REFERENCES challenge_purposes (id),
    code_hash       VARCHAR(64) NOT NULL,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMP NOT NULL,
    CONSTRAINT uk_challenge_user_purpose UNIQUE (user_id, purpose_id),
    CONSTRAINT ck_verification_challenges_failed_attempts CHECK (failed_attempts >= 0)
);

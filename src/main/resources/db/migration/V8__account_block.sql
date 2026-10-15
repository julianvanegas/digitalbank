-- Bloqueo temporal de una cuenta por parte de un administrador (HU15).

-- Estado nuevo del catálogo. El id sigue la numeración de V4 (1 ACTIVE, 2 INACTIVE, 3 CLOSED).
INSERT INTO account_status (id, code) VALUES (4, 'BLOCKED');

-- Historial de bloqueos: una fila por cada bloqueo, no se actualiza ni se borra.
-- Se guarda aparte y no como columnas de accounts para conservar el motivo de cada bloqueo y no solo
-- el del último, que es lo que una cuenta bancaria necesita poder auditar.
CREATE TABLE account_blocks (
    id                 UUID PRIMARY KEY,
    account_id         UUID NOT NULL REFERENCES accounts (id),
    reason             VARCHAR(500) NOT NULL,
    -- Identificador del usuario administrador que ordenó el bloqueo. Sin clave foránea a propósito:
    -- accounts depende de customer, no de employee, y una FK aquí crearía una dependencia entre
    -- módulos que la ADR-0002 no contempla.
    blocked_by_user_id UUID NOT NULL,
    blocked_at         TIMESTAMP NOT NULL,
    -- El motivo es obligatorio y no puede ser espacios en blanco
    CONSTRAINT ck_account_blocks_reason CHECK (length(btrim(reason)) > 0)
);

-- Consulta del historial de una cuenta, del bloqueo más reciente al más antiguo
CREATE INDEX idx_account_blocks_account ON account_blocks (account_id, blocked_at DESC);

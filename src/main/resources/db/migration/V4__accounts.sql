-- Módulo accounts: cuentas de ahorros del cliente. Depende de customer (customers).

CREATE TABLE account_status (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO account_status (id, code) VALUES
    (1, 'ACTIVE'),
    (2, 'INACTIVE'),
    (3, 'CLOSED');

CREATE TABLE accounts (
    id             UUID PRIMARY KEY,
    -- Exactamente 11 dígitos y sin cero inicial: así el número conserva su longitud aunque
    -- alguien lo trate como entero en el camino. El UNIQUE es la garantía real de que no se repite.
    account_number VARCHAR(11) NOT NULL UNIQUE,
    customer_id    UUID NOT NULL REFERENCES customers (id),
    -- Posición de la cuenta dentro del cupo del cliente (1, 2 o 3). Existe solo para que el límite
    -- de tres cuentas lo imponga la base: dos aperturas simultáneas que leyeran el mismo conteo
    -- pasarían el control del servicio, pero la segunda choca aquí contra el UNIQUE.
    account_index  SMALLINT NOT NULL,
    status_id      SMALLINT NOT NULL REFERENCES account_status (id),
    -- Una cuenta se abre sin depósito inicial, así que el saldo arranca en cero
    balance        NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    CONSTRAINT uk_accounts_customer_index UNIQUE (customer_id, account_index),
    CONSTRAINT ck_accounts_number  CHECK (account_number ~ '^[1-9][0-9]{10}$'),
    CONSTRAINT ck_accounts_index   CHECK (account_index BETWEEN 1 AND 3),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0)
);

-- Listado de las cuentas de un cliente en orden de apertura
CREATE INDEX idx_accounts_customer ON accounts (customer_id, created_at);

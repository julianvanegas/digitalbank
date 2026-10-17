-- Módulo transactions: movimientos de dinero entre cuentas. Depende de accounts (cuentas) y de auth (usuarios y
-- verificaciones), como fija la dirección de dependencias de las ADR 0002 y 0004. El propósito de verificación
-- TRANSACTION (segundo factor) lo agrega V11, junto con el enum que lo declara.

-- ============ CATÁLOGOS ============
-- Ids fijos: el CHECK de transactions depende de los de transaction_types.
CREATE TABLE transaction_types (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO transaction_types (id, code) VALUES
    (1, 'TRANSFER'),
    (2, 'DEPOSIT'),
    (3, 'WITHDRAWAL');

CREATE TABLE status_transaction (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO status_transaction (id, code) VALUES
    (1, 'PENDING'),
    (2, 'COMPLETED'),
    (3, 'REJECTED'),
    (4, 'REVERSED');

-- ============ TRANSACCIONES ============
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY,
    -- Quien ordena el movimiento: el cliente, o un empleado que registra un depósito
    user_originator_id  UUID NOT NULL REFERENCES users (id),
    -- Las dos FK apuntan al id de la cuenta; el número de cuenta es un dato de la cuenta y se obtiene con un join
    sender_account_id   UUID REFERENCES accounts (id),
    receiver_account_id UUID REFERENCES accounts (id),
    transaction_type_id SMALLINT NOT NULL REFERENCES transaction_types (id),
    status_id           SMALLINT NOT NULL REFERENCES status_transaction (id),
    -- Siempre positivo: el sentido del movimiento lo dan las cuentas, no el signo
    amount              NUMERIC(19, 2) NOT NULL,
    -- Identifica la operación ante el cliente (es su comprobante) y evita registrarla dos veces
    reference           VARCHAR(64) NOT NULL UNIQUE,
    created_at          TIMESTAMP NOT NULL,
    CONSTRAINT ck_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_transactions_other_account CHECK (sender_account_id <> receiver_account_id),
    -- Los lados que exige cada tipo: una transferencia tiene emisor y receptor, un depósito solo receptor
    -- y un retiro solo emisor
    CONSTRAINT ck_transactions_sides CHECK (
        (transaction_type_id = 1 AND sender_account_id IS NOT NULL AND receiver_account_id IS NOT NULL) OR
        (transaction_type_id = 2 AND sender_account_id IS NULL     AND receiver_account_id IS NOT NULL) OR
        (transaction_type_id = 3 AND sender_account_id IS NOT NULL AND receiver_account_id IS NULL)
    )
);

-- Postgres no indexa las FK: estos soportan el extracto (Q11) y el consumo del día (Q10)
CREATE INDEX idx_transactions_sender   ON transactions (sender_account_id, created_at);
CREATE INDEX idx_transactions_receiver ON transactions (receiver_account_id, created_at);
-- Reporte de operación por día y tipo (Q12) y barrido de transferencias pendientes vencidas
CREATE INDEX idx_transactions_created  ON transactions (created_at);
-- La transferencia pendiente de un usuario (a lo sumo una, la crea el servicio): solo indexa las PENDING
CREATE INDEX idx_transactions_pending_user ON transactions (user_originator_id) WHERE status_id = 1;

-- Topes transaccionales: cuánto puede mover un cliente por día, según el tipo de movimiento y el tipo de cuenta de la
-- que sale el dinero. Dependen de transactions (V5) y de accounts (V4).
--
-- Cada fila es una combinación (tipo de movimiento, tipo de cuenta). Un máximo NULL significa «sin ese tope»: las
-- transferencias no tienen cupo de cantidad ni tope por operación, solo el acumulado diario. Un tipo de movimiento sin
-- filas (la consignación) no tiene topes definidos.
--
-- El segundo factor no es una columna: toda operación monetaria lo exige (ADR-0004). Los topes aplican solo a los
-- clientes; un administrador que opera en su nombre no los consume ni los respeta, pero sus movimientos sí cuentan en
-- el acumulado de la cuenta.
--
-- El tipo de cuenta se guarda por código y no por id: así transactions no depende de los ids del catálogo de accounts
-- (account_types.code es UNIQUE, por eso admite la FK).
CREATE TABLE transaction_limits (
    id                  SMALLINT PRIMARY KEY,
    transaction_type_id SMALLINT    NOT NULL REFERENCES transaction_types (id),
    account_type_code   VARCHAR(30) NOT NULL REFERENCES account_types (code),
    -- Cantidad máxima de operaciones completadas por día y cuenta
    max_daily_count     INTEGER,
    -- Monto máximo de una sola operación
    max_per_transaction NUMERIC(19, 2),
    -- Monto máximo acumulado por día y cuenta
    max_daily_amount    NUMERIC(19, 2),
    CONSTRAINT uq_transaction_limits_type_account UNIQUE (transaction_type_id, account_type_code),
    CONSTRAINT ck_transaction_limits_count  CHECK (max_daily_count > 0),
    CONSTRAINT ck_transaction_limits_per_tx CHECK (max_per_transaction > 0),
    CONSTRAINT ck_transaction_limits_daily  CHECK (max_daily_amount > 0),
    -- Un tope por operación mayor que el diario no tendría efecto
    CONSTRAINT ck_transaction_limits_order  CHECK (max_per_transaction <= max_daily_amount)
);

-- Retiros (tipo 3): 4 al día, $2.500.000 por retiro y $10.000.000 acumulados. Transferencias (tipo 1): $80.000.000
-- acumulados al día. Por ahora son los mismos para todos los tipos de cuenta.
INSERT INTO transaction_limits (id, transaction_type_id, account_type_code, max_daily_count, max_per_transaction,
                                max_daily_amount) VALUES
    (1, 3, 'SAVINGS',  4, 2500000.00, 10000000.00),
    (2, 3, 'CHECKING', 4, 2500000.00, 10000000.00),
    (3, 1, 'SAVINGS',  NULL, NULL, 80000000.00),
    (4, 1, 'CHECKING', NULL, NULL, 80000000.00);

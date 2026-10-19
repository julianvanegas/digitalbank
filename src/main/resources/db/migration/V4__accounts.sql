-- Módulo accounts: cuentas del cliente y su historial de acciones. Depende de customer (customers) y de auth (users).

-- ============ CATÁLOGOS ============
-- Ids fijos, como el resto de catálogos.
CREATE TABLE account_status (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO account_status (id, code) VALUES
    (1, 'ACTIVE'),
    (2, 'INACTIVE'),
    (3, 'CLOSED'),
    (4, 'BLOCKED');

-- Determina los límites que aplican a la cuenta.
CREATE TABLE account_types (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO account_types (id, code) VALUES
    (1, 'SAVINGS'),
    (2, 'CHECKING');

-- ============ NÚMERO DE CUENTA ============
-- Cada número sale de esta secuencia, que entrega un valor único sin consultar la tabla, y el servidor lo pasa por
-- una permutación secreta que lo reparte en el rango de 12 dígitos (AccountNumberPermutation), para que no pueda
-- deducirse el número de una cuenta ajena. El UNIQUE de accounts.account_number sigue siendo la garantía final.
--
-- El rango es el de los números válidos: 9 · 10^11 valores (de 100000000000 a 999999999999). NO CYCLE hace que, si
-- algún día se agotara, la apertura falle en vez de repetir números. Una secuencia no es transaccional: una apertura
-- que se revierte deja un hueco, y eso no importa porque los números no son consecutivos para quien los ve.
CREATE SEQUENCE account_number_seq
    START WITH 1
    MINVALUE 1
    MAXVALUE 899999999999
    NO CYCLE;

-- ============ CUENTAS ============
CREATE TABLE accounts (
    id             UUID PRIMARY KEY,
    -- Exactamente 12 dígitos. El UNIQUE es la garantía real de que no se repite.
    account_number CHAR(12) NOT NULL UNIQUE,
    -- Llave visible de la cuenta (CA01): arroba + dos letras del primer nombre + tres dígitos del documento + un
    -- consecutivo. El UUID sigue siendo la clave primaria y la que referencian las claves foráneas; esta llave es
    -- el identificador que ve el usuario. El consecutivo no es la posición de la cuenta dentro del cliente: es el
    -- primer número libre para ese prefijo. Dos clientes que coincidan en nombre y documento continúan la misma
    -- numeración en vez de chocar, así que la columna necesita margen para crecer.
    account_key    VARCHAR(64) NOT NULL,
    customer_id    UUID NOT NULL REFERENCES customers (id),
    account_type_id SMALLINT NOT NULL,
    -- Posición de la cuenta dentro del cupo del cliente (1, 2 o 3). Existe solo para que el límite de tres cuentas
    -- abiertas lo imponga la base: dos aperturas simultáneas que leyeran el mismo conteo pasarían el control del
    -- servicio, pero la segunda choca contra el índice único parcial de más abajo.
    account_index  SMALLINT NOT NULL,
    status_id      SMALLINT NOT NULL REFERENCES account_status (id),
    -- Una cuenta se abre sin depósito inicial, así que el saldo arranca en cero
    balance        NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    -- Fecha en que se cierra la cuenta
    closed_at      TIMESTAMP,
    -- Control optimista de concurrencia (@Version): se incrementa en cada UPDATE y una escritura hecha sobre una
    -- versión vieja se rechaza en vez de pisar el cambio de otra.
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_accounts_key UNIQUE (account_key),
    CONSTRAINT fk_accounts_account_type FOREIGN KEY (account_type_id) REFERENCES account_types (id),
    CONSTRAINT ck_accounts_number  CHECK (account_number ~ '^[0-9]{12}$'),
    -- La forma de la llave: el consecutivo empieza en 1 y no lleva ceros a la izquierda
    CONSTRAINT ck_accounts_key     CHECK (account_key ~ '^@[A-Z]{2}[0-9]{3}[1-9][0-9]*$'),
    CONSTRAINT ck_accounts_index   CHECK (account_index BETWEEN 1 AND 3),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0),
    -- Una cuenta cerrada (status 3, CLOSED) siempre tiene fecha de cierre y una abierta nunca la tiene.
    -- El cierre no puede ser anterior a la apertura.
    CONSTRAINT ck_accounts_closed  CHECK ((status_id = 3) = (closed_at IS NOT NULL)),
    CONSTRAINT ck_accounts_closed_after_created CHECK (closed_at >= created_at)
);

-- Cupo de tres cuentas abiertas: cerrar una cuenta libera su cupo, así que la posición (account_index, de 1 a 3)
-- es única por cliente solo entre las cuentas que no están cerradas. Varias cerradas pueden compartir la misma
-- posición, y el límite de tres abiertas lo sigue imponiendo la base, incluso con dos aperturas simultáneas.
CREATE UNIQUE INDEX uk_accounts_customer_index_open ON accounts (customer_id, account_index) WHERE status_id <> 3;

-- Listado de las cuentas de un cliente en orden de apertura
CREATE INDEX idx_accounts_customer ON accounts (customer_id, created_at);

-- ============ HISTORIAL DE ACCIONES DE LA CUENTA ============
-- Una fila por cada bloqueo, desbloqueo o cierre, con su motivo, quién la hizo y cuándo. El estado actual de la
-- cuenta es accounts.status_id; esta tabla es su historial. No se actualiza ni se borra.
CREATE TABLE account_actions (
    id         UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    -- Obligatorio en todas las acciones. En un cierre dice quién lo hizo: el cliente o un administrador
    reason     VARCHAR(255) NOT NULL,
    action     VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    -- Usuario que hizo la acción, tal como viaja en el JWT: un administrador, o el titular si cerró su cuenta.
    -- Referencia users (auth), que está por encima de accounts en la dirección de dependencias.
    created_by UUID NOT NULL,
    CONSTRAINT fk_account_actions_account    FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_account_actions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_account_actions_action CHECK (action IN ('BLOCKED', 'UNBLOCKED', 'CLOSED')),
    CONSTRAINT ck_account_actions_reason CHECK (length(btrim(reason)) > 0)
);

-- Historial de una cuenta, de la acción más reciente a la más antigua
CREATE INDEX idx_account_actions_account ON account_actions (account_id, created_at DESC);

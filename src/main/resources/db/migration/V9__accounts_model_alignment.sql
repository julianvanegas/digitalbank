-- Alinea el módulo accounts con el modelo del entregable de base de datos y con sus reglas de negocio:
-- tipos de cuenta, número de 12 dígitos, cierre de cuenta con su fecha, cupo de tres cuentas abiertas e historial
-- de acciones (bloqueo, desbloqueo y cierre). Incluye también el índice del listado de clientes que pide el modelo.

-- ============ TIPOS DE CUENTA ============
-- Catálogo con ids fijos, como el resto: determina los límites que aplican a la cuenta.
CREATE TABLE account_types (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO account_types (id, code) VALUES
    (1, 'SAVINGS'),
    (2, 'CHECKING');

-- Las cuentas que ya existen se abrieron como cuentas de ahorros (V4), así que se rellenan con SAVINGS.
ALTER TABLE accounts ADD COLUMN account_type_id SMALLINT;
UPDATE accounts SET account_type_id = 1;
ALTER TABLE accounts
    ALTER COLUMN account_type_id SET NOT NULL,
    ADD CONSTRAINT fk_accounts_account_type FOREIGN KEY (account_type_id) REFERENCES account_types (id);

-- ============ NÚMERO DE CUENTA: CHAR(12), 12 DÍGITOS ============
-- Los números de 11 dígitos se completan con un cero a la izquierda: el valor numérico no cambia, no hay
-- colisiones posibles y el titular sigue reconociendo su número. Los nuevos se generan ya con 12 dígitos.
ALTER TABLE accounts DROP CONSTRAINT ck_accounts_number;
ALTER TABLE accounts ALTER COLUMN account_number TYPE CHAR(12) USING lpad(account_number, 12, '0');
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_number CHECK (account_number ~ '^[0-9]{12}$');

-- ============ CIERRE DE CUENTA ============
-- Sigue habiendo created_at y updated_at; closed_at se suma para la fecha en que se cierra la cuenta.
ALTER TABLE accounts ADD COLUMN closed_at TIMESTAMP;

-- Una cuenta ya cerrada no tiene fecha de cierre guardada: la última modificación es la mejor aproximación.
UPDATE accounts SET closed_at = updated_at WHERE status_id = 3;

-- Una cuenta cerrada (status 3, CLOSED) siempre tiene fecha de cierre y una abierta nunca la tiene.
-- El cierre no puede ser anterior a la apertura.
ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_closed CHECK ((status_id = 3) = (closed_at IS NOT NULL)),
    ADD CONSTRAINT ck_accounts_closed_after_created CHECK (closed_at >= created_at);

-- ============ VERSIÓN DE LA FILA ============
-- Control optimista de concurrencia (@Version): se incrementa en cada UPDATE y una escritura hecha sobre una
-- versión vieja se rechaza en vez de pisar el cambio de otra. Las cuentas que ya existen empiezan en 0.
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ============ CUPO DE TRES CUENTAS ABIERTAS ============
-- Cerrar una cuenta libera su cupo. La posición (account_index, de 1 a 3) era única por cliente entre todas sus
-- cuentas, así que una cerrada seguía ocupando su lugar para siempre. La unicidad pasa a aplicar solo a las
-- cuentas que no están cerradas: el límite de tres abiertas lo sigue imponiendo la base, incluso con dos
-- aperturas simultáneas, y varias cerradas pueden compartir la misma posición.
ALTER TABLE accounts DROP CONSTRAINT uk_accounts_customer_index;

CREATE UNIQUE INDEX uk_accounts_customer_index_open ON accounts (customer_id, account_index) WHERE status_id <> 3;

-- ============ HISTORIAL DE ACCIONES DE LA CUENTA ============
-- account_blocks (V8) guardaba un bloqueo por fila. Se reemplaza por account_actions, un registro de acciones:
-- una fila por cada bloqueo, desbloqueo o cierre, con su motivo, quién la hizo y cuándo. El estado actual de la
-- cuenta sigue siendo accounts.status_id; esta tabla es su historial.
CREATE TABLE account_actions (
    id         UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    -- Obligatorio en todas las acciones. En un cierre dice quién lo hizo: el cliente o un administrador
    reason     VARCHAR(255) NOT NULL,
    action     VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    -- Usuario que hizo la acción, tal como viaja en el JWT: un administrador, o el titular si cerró su cuenta.
    -- Antes no había FK para no acoplar accounts con employee; users es de auth, que está por encima de accounts
    -- en la dirección de dependencias.
    created_by UUID NOT NULL,
    CONSTRAINT fk_account_actions_account    FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_account_actions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_account_actions_action CHECK (action IN ('BLOCKED', 'UNLOCKED', 'CLOSED')),
    CONSTRAINT ck_account_actions_reason CHECK (length(btrim(reason)) > 0)
);

-- Cada bloqueo anterior pasa a ser una acción BLOCKED, conservando su id. Hasta ahora no se podía desbloquear
-- ni cerrar, así que no hay otras acciones que convertir. Si algún motivo supera los 255 caracteres, la migración
-- falla entera en vez de recortarlo.
INSERT INTO account_actions (id, account_id, reason, action, created_at, created_by)
SELECT id, account_id, reason, 'BLOCKED', blocked_at, blocked_by_user_id
FROM account_blocks;

-- Se lleva sus índices. Las cuentas que ya estuvieran cerradas no tienen una acción CLOSED: no se sabe quién las
-- cerró ni cuándo, así que no se inventa. Las que se cierren de aquí en adelante sí la registran.
DROP TABLE account_blocks;

-- Historial de una cuenta, de la acción más reciente a la más antigua
CREATE INDEX idx_account_actions_account ON account_actions (account_id, created_at DESC);

-- ============ LISTADO DE CLIENTES ============
-- Listado paginado de clientes ordenado por apellidos (GET /api/v1/customers). El id desempata a los clientes con
-- el mismo apellido para que ninguno se repita ni se pierda entre páginas. Es el equivalente del índice de
-- employees (V3) y el que el modelo exige para la consulta Q7.
CREATE INDEX idx_customers_last_names ON customers (last_names, id);

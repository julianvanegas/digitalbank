-- La acción de desbloqueo se llamaba UNLOCKED, pero el estado y la acción hermana son BLOCKED. Se unifica en
-- UNBLOCKED. Se suelta la restricción antes de convertir los datos y se vuelve a crear después, porque la
-- anterior no admite el valor nuevo.
ALTER TABLE account_actions DROP CONSTRAINT ck_account_actions_action;

UPDATE account_actions SET action = 'UNBLOCKED' WHERE action = 'UNLOCKED';

ALTER TABLE account_actions
    ADD CONSTRAINT ck_account_actions_action CHECK (action IN ('BLOCKED', 'UNBLOCKED', 'CLOSED'));

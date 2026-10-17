-- Segundo factor de las transacciones monetarias: el código vale 3 minutos y se cancela al tercer fallo.
-- Va aparte de V12 porque PurposeEnum.TRANSACTION y esta fila deben llegar juntos: Catalogs no deja arrancar la
-- aplicación si el enum y la tabla no coinciden.
INSERT INTO challenge_purposes (id, purpose, ttl_minutes, max_attempts) VALUES
    (4, 'TRANSACTION', 3, 3);

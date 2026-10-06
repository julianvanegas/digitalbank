-- Registro público de clientes: el cliente verifica su correo y define su contraseña, y queda
-- pendiente de revisión hasta que un administrador lo active.

INSERT INTO user_status (id, code) VALUES (5, 'PENDING_REVIEW');

-- El código de confirmación de correo vale 5 minutos (antes 30)
UPDATE challenge_purposes SET ttl_minutes = 5 WHERE purpose = 'EMAIL_CONFIRMATION';

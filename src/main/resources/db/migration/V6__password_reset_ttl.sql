-- El código de recuperación de contraseña vale 5 minutos (antes 15)

UPDATE challenge_purposes SET ttl_minutes = 5 WHERE purpose = 'PASSWORD_RESET';

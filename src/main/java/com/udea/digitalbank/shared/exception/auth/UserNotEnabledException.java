package com.udea.digitalbank.shared.exception.auth;

import com.udea.digitalbank.shared.exception.BusinessException;

// El usuario no puede iniciar sesión ahora: está bloqueado, sin confirmar el email o inhabilitado
public class UserNotEnabledException extends BusinessException {
    public UserNotEnabledException(String message) {
        super(Kind.FORBIDDEN, message);
    }
}

package com.udea.digitalbank.shared.exception;

public class InvalidTwoFactorCodeException extends RuntimeException {
    public InvalidTwoFactorCodeException(String message) {
        super(message);
    }
}
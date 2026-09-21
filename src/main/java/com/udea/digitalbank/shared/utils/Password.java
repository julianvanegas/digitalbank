package com.udea.digitalbank.shared.utils;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política de contraseña: de 8 a 16 caracteres, con mayúscula, minúscula, número y un carácter
 * especial. Un valor nulo es válido: se combina con @NotBlank donde la contraseña sea obligatoria.
 */
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#_-]).{8,16}$")
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Documented
public @interface Password {

    String message() default "La contraseña debe tener 8-16 caracteres, mayúscula, minúscula, número y carácter especial";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

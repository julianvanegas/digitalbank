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
 * Teléfono en formato internacional (E.164): un "+" obligatorio, un primer dígito distinto de cero
 * (ningún código de país empieza por 0) y de 7 a 15 dígitos en total, sin espacios ni guiones.
 * Es texto y no número porque conserva el "+". Un valor nulo es válido: se combina con @NotBlank
 * donde el teléfono sea obligatorio.
 */
@Pattern(regexp = "^\\+[1-9][0-9]{6,14}$")
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Documented
public @interface Phone {

    String message() default "El teléfono debe empezar por + y tener de 7 a 15 dígitos, sin espacios ni guiones (ejemplo: +573001234567)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

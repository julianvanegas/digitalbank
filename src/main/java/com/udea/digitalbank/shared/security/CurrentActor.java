package com.udea.digitalbank.shared.security;

import io.swagger.v3.oas.annotations.Parameter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca el parámetro {@link Actor} de un controller que se resuelve con el usuario autenticado (token), no con un dato
 * de la petición. Se oculta de OpenAPI: sin eso Swagger lo mostraría como un parámetro de query que se debe llenar.
 */
@Parameter(hidden = true)
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentActor {
}

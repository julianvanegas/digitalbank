package com.udea.digitalbank.shared.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Cuerpo de toda respuesta de error de la API.
 *
 * @param status    código HTTP de la respuesta
 * @param message   causa del error, redactada para quien consume la API
 * @param timestamp instante en que se produjo el error
 */
public record ErrorResponse(
        @Schema(example = "403") int status,
        @Schema(example = "No tienes permiso para realizar esta operación")
        String message,
        @Schema(example = "2026-10-17T10:15:30")
        LocalDateTime timestamp) {

}

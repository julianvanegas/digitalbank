package com.udea.digitalbank.shared.config;

import com.udea.digitalbank.shared.exception.ErrorResponse;
import com.udea.digitalbank.shared.exception.SecurityErrorHandler;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Esquema {@code ErrorResponse} y respuestas de error comunes (400, 401, 403 y 500) del contrato OpenAPI. Las
 * operaciones las referencian por nombre; {@link OpenApiContractCustomizer} decide a cuáles se aplica cada una.
 */
final class OpenApiErrorComponents {

    static final String BAD_REQUEST = "BadRequest";
    static final String UNAUTHORIZED = "Unauthorized";
    static final String FORBIDDEN = "Forbidden";
    static final String INTERNAL_ERROR = "InternalServerError";

    private static final String JSON = "application/json";
    private static final String ERROR_SCHEMA = "ErrorResponse";

    /** Registra el esquema y las respuestas comunes en los componentes del contrato. */
    void register(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Components components = openApi.getComponents();
        // El esquema se lee del propio record, así que sigue su forma si cambia
        ModelConverters.getInstance(true).readAll(ErrorResponse.class)
                .forEach(components::addSchemas);

        components.addResponses(BAD_REQUEST, badRequestResponse());
        components.addResponses(UNAUTHORIZED, errorResponse(401,
                "No autenticado: falta el token o no es válido",
                SecurityErrorHandler.UNAUTHENTICATED_MESSAGE));
        components.addResponses(FORBIDDEN, errorResponse(403,
                "Sin permiso: el usuario autenticado no puede realizar esta operación",
                SecurityErrorHandler.FORBIDDEN_MESSAGE));
        components.addResponses(INTERNAL_ERROR, errorResponse(500,
                "Error inesperado del servidor",
                "Error interno del servidor"));
    }

    /** Cuerpo de un error: el esquema común y un ejemplo con el código y el mensaje indicados. */
    Content errorContent(int status, String message) {
        return new Content().addMediaType(JSON, new MediaType()
                .schema(errorSchemaRef())
                .example(errorExample(status, message)));
    }

    private ApiResponse errorResponse(int status, String description, String message) {
        return new ApiResponse().description(description).content(errorContent(status, message));
    }

    /**
     * El 400 tiene tres causas con mensajes de forma distinta, y cada una lleva su ejemplo con el texto real que
     * produce la API: un campo del cuerpo inválido ({@code campo: mensaje}), un JSON o un parámetro mal formado, y una
     * regla de negocio que no se cumple.
     */
    private ApiResponse badRequestResponse() {
        Map<String, Example> examples = new LinkedHashMap<>();
        examples.put("Campo inválido", new Example().value(
                errorExample(400, "amount: El monto debe ser mayor que cero")));
        examples.put("Solicitud mal formada", new Example().value(
                errorExample(400, "Solicitud mal formada: revisa el cuerpo y los parámetros")));
        examples.put("Regla de negocio", new Example().value(
                errorExample(400, "El cliente debe ser mayor de 18 años")));
        return new ApiResponse()
                .description("Solicitud inválida: el cuerpo, un parámetro o una regla de negocio no se cumple")
                .content(new Content().addMediaType(JSON, new MediaType()
                        .schema(errorSchemaRef())
                        .examples(examples)));
    }

    private Schema<?> errorSchemaRef() {
        return new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA);
    }

    private Map<String, Object> errorExample(int status, String message) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("status", status);
        example.put("message", message);
        example.put("timestamp", OpenApiExamples.TIMESTAMP);
        return example;
    }
}

package com.udea.digitalbank.shared.config;

import com.udea.digitalbank.shared.exception.ErrorResponse;
import com.udea.digitalbank.shared.exception.SecurityErrorHandler;
import com.udea.digitalbank.shared.security.SecurityModule;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Completa el contrato OpenAPI con lo que se deduce de la configuración de seguridad, para que no dependa de anotar
 * cada endpoint a mano ni pueda desactualizarse:
 * <ul>
 *   <li>Las rutas públicas pierden el candado ({@code security: []}).</li>
 *   <li>Las rutas protegidas reciben las respuestas 401 y, si están restringidas por rol, 403.</li>
 *   <li>Las operaciones con cuerpo o parámetros reciben la respuesta 400, y todas la 500.</li>
 * </ul>
 * Las rutas y los roles salen de {@link SecurityModule}, la misma fuente que usa {@code SecurityConfig}, con la misma
 * regla de coincidencia: las rutas públicas primero y, si no, gana la primera ruta por rol que coincide.
 *
 * <p>Los errores propios de cada operación (404, 409, 429…) no se deducen de aquí: se declaran con
 * {@code @ApiResponse} en su controller. Una respuesta que ya exista en la operación nunca se sobrescribe.
 */
@Component
public class OpenApiContractCustomizer implements OpenApiCustomizer {

    private static final String JSON = "application/json";
    private static final String ERROR_SCHEMA = "ErrorResponse";

    static final String BAD_REQUEST = "BadRequest";
    static final String UNAUTHORIZED = "Unauthorized";
    static final String FORBIDDEN = "Forbidden";
    static final String INTERNAL_ERROR = "InternalServerError";

    private final List<SecurityModule> modules;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public OpenApiContractCustomizer(List<SecurityModule> modules) {
        this.modules = modules;
    }

    @Override
    public void customise(OpenAPI openApi) {
        registerErrorComponents(openApi);
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) ->
                item.readOperationsMap().forEach((method, operation) -> customise(path, method, operation)));
    }

    private void customise(String path, PathItem.HttpMethod method, Operation operation) {
        Access access = accessOf(method, path);
        if (access.isPublic()) {
            // Lista vacía: esta operación no exige el esquema global y Swagger UI no muestra el candado
            operation.setSecurity(List.of());
        } else if (access.isProtected()) {
            addResponse(operation, "401", UNAUTHORIZED);
            if (!access.roles().isEmpty()) {
                addResponse(operation, "403", FORBIDDEN);
            }
        }
        if (operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty())) {
            addResponse(operation, "400", BAD_REQUEST);
        }
        addResponse(operation, "500", INTERNAL_ERROR);
        useErrorBody(operation);
        describePagination(operation);
    }

    // ---- Seguridad de la ruta

    /**
     * Quién puede usar la operación, con la misma prioridad que {@code SecurityConfig}: primero las rutas públicas
     * de todos los módulos y después las rutas por rol, en el orden en que los módulos las declaran.
     */
    private Access accessOf(PathItem.HttpMethod method, String path) {
        // Una ruta con variables ({id}) se compara con un valor cualquiera en su lugar
        String sample = path.replaceAll("\\{[^}]+}", "x");
        for (SecurityModule module : modules) {
            for (SecurityModule.PublicRoute route : module.publicRoutes()) {
                if (matches(route.method(), route.pattern(), method, sample)) {
                    return Access.PUBLIC;
                }
            }
        }
        for (SecurityModule module : modules) {
            for (SecurityModule.RoleRoute route : module.roleRoutes()) {
                if (matches(route.method(), route.pattern(), method, sample)) {
                    return Access.of(route.roles());
                }
            }
        }
        return Access.UNKNOWN;
    }

    private boolean matches(org.springframework.http.HttpMethod routeMethod, String pattern,
                            PathItem.HttpMethod method, String sample) {
        return (routeMethod == null || routeMethod.name().equals(method.name())) && matcher.match(pattern, sample);
    }

    /** Acceso a una operación: público, protegido (con o sin restricción de rol) o no declarado. */
    private record Access(Kind kind, List<String> roles) {

        enum Kind { PUBLIC, PROTECTED, UNKNOWN }

        static final Access PUBLIC = new Access(Kind.PUBLIC, List.of());
        static final Access UNKNOWN = new Access(Kind.UNKNOWN, List.of());

        static Access of(List<String> roles) {
            return new Access(Kind.PROTECTED, roles);
        }

        boolean isPublic() {
            return kind == Kind.PUBLIC;
        }

        boolean isProtected() {
            return kind == Kind.PROTECTED;
        }
    }

    // ---- Respuestas de error comunes

    private void registerErrorComponents(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Components components = openApi.getComponents();
        // El esquema se lee del propio record, así que sigue su forma si cambia
        ModelConverters.getInstance(true).readAll(ErrorResponse.class)
                .forEach((name, schema) -> components.addSchemas(name, schema));

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
                        .schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))
                        .examples(examples)));
    }

    // Cuerpo de un error: el esquema común y un ejemplo con el código y el mensaje indicados
    private Content errorContent(int status, String message) {
        return new Content().addMediaType(JSON, new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))
                .example(errorExample(status, message)));
    }

    private Map<String, Object> errorExample(int status, String message) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("status", status);
        example.put("message", message);
        example.put("timestamp", OpenApiExamples.TIMESTAMP);
        return example;
    }

    /**
     * Los errores que un controller declara con {@code @ApiResponse(responseCode = "404", description = ...)} salen sin
     * cuerpo propio, y springdoc les copia el esquema de la respuesta exitosa (un 404 con el cuerpo de una cuenta).
     * Aquí pasan a describir el cuerpo real de un error: {@link ErrorResponse}, con la descripción como mensaje de
     * ejemplo.
     */
    private void useErrorBody(Operation operation) {
        operation.getResponses().forEach((code, response) -> {
            if (code.matches("[45]\\d\\d") && response.get$ref() == null) {
                response.setContent(errorContent(Integer.parseInt(code), response.getDescription()));
            }
        });
    }

    /**
     * Springdoc expande {@code Pageable} en los parámetros {@code page}, {@code size} y {@code sort}, pero con sus
     * textos en inglés y sin ejemplo. Aquí reciben la descripción y el ejemplo en el mismo estilo del resto de
     * parámetros.
     */
    private void describePagination(Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        List<Parameter> pageable = operation.getParameters().stream()
                .filter(p -> "query".equals(p.getIn()) && List.of("page", "size", "sort").contains(p.getName()))
                .toList();
        if (pageable.size() != 3) {
            return;
        }
        for (Parameter parameter : pageable) {
            switch (parameter.getName()) {
                case "page" -> {
                    parameter.setDescription("Índice de la página, desde 0");
                    parameter.setExample(0);
                }
                case "size" -> {
                    parameter.setDescription("Cantidad de elementos por página");
                    parameter.setExample(20);
                }
                default -> {
                    parameter.setDescription("Criterio de orden: propiedad,asc o propiedad,desc; se puede repetir");
                    parameter.setExample("lastNames,asc");
                }
            }
        }
    }

    // ---- Utilidades

    // No sobrescribe una respuesta que el controller ya declara
    private void addResponse(Operation operation, String code, String componentName) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        if (!operation.getResponses().containsKey(code)) {
            operation.getResponses().addApiResponse(code, new ApiResponse().$ref(componentName));
        }
    }
}

package com.udea.digitalbank.shared.config;

import com.udea.digitalbank.shared.security.SecurityModule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final String BAD_REQUEST = OpenApiErrorComponents.BAD_REQUEST;
    private static final String UNAUTHORIZED = OpenApiErrorComponents.UNAUTHORIZED;
    private static final String FORBIDDEN = OpenApiErrorComponents.FORBIDDEN;
    private static final String INTERNAL_ERROR = OpenApiErrorComponents.INTERNAL_ERROR;

    private static final Map<String, ParameterDoc> PAGEABLE_DOCS = Map.of(
            "page", new ParameterDoc("Índice de la página, desde 0", 0),
            "size", new ParameterDoc("Cantidad de elementos por página", 20),
            "sort", new ParameterDoc(
                    "Criterio de orden: propiedad,asc o propiedad,desc; se puede repetir", "lastNames,asc"));

    private final List<SecurityModule> modules;
    private final OpenApiErrorComponents errorComponents = new OpenApiErrorComponents();
    private final AntPathMatcher matcher = new AntPathMatcher();

    public OpenApiContractCustomizer(List<SecurityModule> modules) {
        this.modules = modules;
    }

    @Override
    public void customise(OpenAPI openApi) {
        errorComponents.register(openApi);
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) ->
                item.readOperationsMap().forEach((method, operation) -> customise(path, method, operation)));
    }

    private void customise(String path, PathItem.HttpMethod method, Operation operation) {
        applySecurity(operation, accessOf(method, path));
        if (hasInput(operation)) {
            addResponse(operation, "400", BAD_REQUEST);
        }
        addResponse(operation, "500", INTERNAL_ERROR);
        useErrorBody(operation);
        describePagination(operation);
    }

    private void applySecurity(Operation operation, Access access) {
        if (access.isPublic()) {
            // Lista vacía: esta operación no exige el esquema global y Swagger UI no muestra el candado
            operation.setSecurity(List.of());
            return;
        }
        if (!access.isProtected()) {
            return;
        }
        addResponse(operation, "401", UNAUTHORIZED);
        if (!access.roles().isEmpty()) {
            addResponse(operation, "403", FORBIDDEN);
        }
    }

    /** Una operación con cuerpo o con parámetros puede recibir una solicitud inválida. */
    private static boolean hasInput(Operation operation) {
        return operation.getRequestBody() != null || !CollectionUtils.isEmpty(operation.getParameters());
    }

    // ---- Seguridad de la ruta

    /**
     * Quién puede usar la operación, con la misma prioridad que {@code SecurityConfig}: primero las rutas públicas
     * de todos los módulos y después las rutas por rol, en el orden en que los módulos las declaran.
     */
    private Access accessOf(PathItem.HttpMethod method, String path) {
        // Una ruta con variables ({id}) se compara con un valor cualquiera en su lugar
        String sample = path.replaceAll("\\{[^}]+}", "x");
        return findPublic(method, sample)
                .or(() -> findByRole(method, sample))
                .orElse(Access.UNKNOWN);
    }

    private Optional<Access> findPublic(PathItem.HttpMethod method, String sample) {
        return modules.stream()
                .flatMap(module -> module.publicRoutes().stream())
                .filter(route -> matches(route.method(), route.pattern(), method, sample))
                .findFirst()
                .map(route -> Access.PUBLIC);
    }

    private Optional<Access> findByRole(PathItem.HttpMethod method, String sample) {
        return modules.stream()
                .flatMap(module -> module.roleRoutes().stream())
                .filter(route -> matches(route.method(), route.pattern(), method, sample))
                .findFirst()
                .map(route -> Access.of(route.roles()));
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

    /**
     * Los errores que un controller declara con {@code @ApiResponse(responseCode = "404", description = ...)} salen sin
     * cuerpo propio, y springdoc les copia el esquema de la respuesta exitosa (un 404 con el cuerpo de una cuenta).
     * Aquí pasan a describir el cuerpo real de un error: {@code ErrorResponse}, con la descripción como mensaje de
     * ejemplo.
     */
    private void useErrorBody(Operation operation) {
        operation.getResponses().forEach((code, response) -> {
            if (code.matches("[45]\\d\\d") && response.get$ref() == null) {
                response.setContent(errorComponents.errorContent(Integer.parseInt(code), response.getDescription()));
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
                .filter(p -> "query".equals(p.getIn()) && PAGEABLE_DOCS.containsKey(p.getName()))
                .toList();
        if (pageable.size() != PAGEABLE_DOCS.size()) {
            return;
        }
        pageable.forEach(parameter -> PAGEABLE_DOCS.get(parameter.getName()).applyTo(parameter));
    }

    /** Descripción y ejemplo de un parámetro de paginación. */
    private record ParameterDoc(String description, Object example) {

        void applyTo(Parameter parameter) {
            parameter.setDescription(description);
            parameter.setExample(example);
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

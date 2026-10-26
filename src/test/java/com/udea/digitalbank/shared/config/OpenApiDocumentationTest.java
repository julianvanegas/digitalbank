package com.udea.digitalbank.shared.config;

import com.udea.digitalbank.accounts.security.AccountSecurity;
import com.udea.digitalbank.auth.security.AuthSecurity;
import com.udea.digitalbank.customer.security.CustomerSecurity;
import com.udea.digitalbank.shared.security.SecurityModule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias (AAA) de la documentación de la API ({@link OpenAPIConfig} y
 * {@link OpenApiContractCustomizer}): el contrato que ve quien consume la API en Swagger debe declarar las respuestas
 * de error que la seguridad y la validación pueden producir. Se usan las clases *Security reales del proyecto.
 */
@DisplayName("Transversal - Documentación OpenAPI")
class OpenApiDocumentationTest {

    private OpenApiContractCustomizer customizer;

    @BeforeEach
    void setUp() {
        List<SecurityModule> modules = List.of(new AuthSecurity(null), new CustomerSecurity(), new AccountSecurity());
        customizer = new OpenApiContractCustomizer(modules);
    }

    private static Operation operation(String code, String description) {
        return new Operation().responses(new ApiResponses().addApiResponse(code, new ApiResponse().description(description)));
    }

    private static Parameter query(String name) {
        return new Parameter().in("query").name(name);
    }

    @Test
    @DisplayName("Transversal - La configuración declara título, las 5 etiquetas y el esquema de seguridad JWT")
    void deberiaDeclararLaInformacionBasica() {
        // Act
        OpenAPI openApi = new OpenAPIConfig().digitalBankOpenAPI();

        // Assert
        assertThat(openApi.getInfo().getTitle()).isEqualTo("Digital Bank API");
        assertThat(openApi.getTags()).extracting(io.swagger.v3.oas.models.tags.Tag::getName)
                .containsExactly(OpenAPIConfig.TAG_AUTH, OpenAPIConfig.TAG_CUSTOMERS, OpenAPIConfig.TAG_EMPLOYEES,
                        OpenAPIConfig.TAG_ACCOUNTS, OpenAPIConfig.TAG_TRANSACTIONS);
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openApi.getSecurity()).isNotEmpty();
    }

    @Test
    @DisplayName("Transversal - Registra el esquema ErrorResponse y las respuestas de error comunes")
    void deberiaRegistrarLosComponentesDeError() {
        // Arrange
        OpenAPI openApi = new OpenAPI();

        // Act
        customizer.customise(openApi);

        // Assert
        assertThat(openApi.getComponents().getSchemas()).containsKey("ErrorResponse");
        assertThat(openApi.getComponents().getResponses())
                .containsKeys("BadRequest", "Unauthorized", "Forbidden", "InternalServerError");
    }

    @Test
    @DisplayName("Transversal - Una ruta pública con cuerpo no exige token y documenta 400 y 500, no 401")
    void deberiaDocumentarUnaRutaPublica() {
        // Arrange
        Operation register = operation("201", "Cliente registrado").requestBody(new RequestBody());
        register.getResponses().addApiResponse("409", new ApiResponse().description("Ya existe un registro"));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/customers", new PathItem().post(register)));

        // Act
        customizer.customise(openApi);

        // Assert
        assertThat(register.getSecurity()).isEmpty();
        assertThat(register.getResponses()).containsKeys("201", "400", "409", "500").doesNotContainKey("401");
        assertThat(register.getResponses().get("409").getContent().get("application/json")).isNotNull();
    }

    @Test
    @DisplayName("Transversal - Una ruta con rol documenta 401 y 403; una paginada describe page, size y sort")
    void deberiaDocumentarUnaRutaConRol() {
        // Arrange
        Operation list = operation("200", "Página de clientes")
                .parameters(List.of(query("page"), query("size"), query("sort")));
        Operation balance = operation("200", "Saldo de la cuenta");
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/customers", new PathItem().get(list))
                .addPathItem("/api/v1/accounts/{accountNumber}/balance", new PathItem().get(balance)));

        // Act
        customizer.customise(openApi);

        // Assert
        assertThat(list.getResponses()).containsKeys("200", "400", "401", "403", "500");
        assertThat(list.getParameters()).allSatisfy(p -> assertThat(p.getDescription()).isNotBlank());
        assertThat(balance.getResponses()).containsKeys("200", "401", "403", "500").doesNotContainKey("400");
    }

    @Test
    @DisplayName("Transversal - Una ruta de cualquier usuario autenticado documenta 401 pero no 403")
    void deberiaDocumentarUnaRutaAutenticada() {
        // Arrange
        Operation logout = operation("204", "Sesión cerrada");
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/auth/logout", new PathItem().post(logout)));

        // Act
        customizer.customise(openApi);

        // Assert
        assertThat(logout.getResponses()).containsKeys("204", "401", "500").doesNotContainKey("403");
    }

    @Test
    @DisplayName("Transversal - Una ruta que ningún módulo declara no se marca como pública ni como protegida")
    void deberiaDejarSinMarcarUnaRutaDesconocida() {
        // Arrange
        Operation unknown = new Operation();
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/desconocida", new PathItem().get(unknown)));

        // Act
        customizer.customise(openApi);

        // Assert
        assertThat(unknown.getSecurity()).isNull();
        assertThat(unknown.getResponses()).containsKey("500").doesNotContainKeys("401", "403");
    }
}

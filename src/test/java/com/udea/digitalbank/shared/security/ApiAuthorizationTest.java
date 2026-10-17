package com.udea.digitalbank.shared.security;

import com.udea.digitalbank.accounts.security.AccountSecurity;
import com.udea.digitalbank.accounts.service.AccountLifecycleService;
import com.udea.digitalbank.accounts.service.AccountOpeningService;
import com.udea.digitalbank.accounts.service.AccountQueryService;
import com.udea.digitalbank.auth.security.AuthSecurity;
import com.udea.digitalbank.auth.security.JwtAuthenticationFilter;
import com.udea.digitalbank.auth.security.JwtUtil;
import com.udea.digitalbank.auth.service.AuthService;
import com.udea.digitalbank.auth.service.EmailVerificationService;
import com.udea.digitalbank.auth.service.PasswordResetService;
import com.udea.digitalbank.auth.service.SessionService;
import com.udea.digitalbank.customer.security.CustomerSecurity;
import com.udea.digitalbank.customer.service.CustomerService;
import com.udea.digitalbank.employee.security.EmployeeSecurity;
import com.udea.digitalbank.employee.service.EmployeeService;
import com.udea.digitalbank.shared.exception.GlobalExceptionHandler;
import com.udea.digitalbank.shared.exception.SecurityErrorHandler;
import com.udea.digitalbank.transactions.security.TransactionSecurity;
import com.udea.digitalbank.transactions.service.TransactionQueryService;
import com.udea.digitalbank.transactions.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Permisos de toda la API contra la cadena de seguridad real: el filtro JWT y las rutas por rol que declara cada
 * módulo (SecurityModule.roleRoutes), que son el único lugar donde se decide quién entra a qué. Los servicios se
 * simulan; los tokens los firma el JwtUtil real.
 * <p>
 * Tres garantías: la matriz fija qué rol puede usar cada endpoint; {@link #everyEndpointIsInTheMatrix} falla si
 * existe un endpoint que la matriz no conoce (se le deben declarar permisos); y una ruta sin regla se deniega
 * ({@link #aRouteWithoutRuleIsDenied}), así que olvidar una la deja cerrada y no abierta.
 * <p>
 * Las peticiones de la matriz llevan parámetros y cuerpos válidos, y {@link #unauthorizedRoleNeverSeesValidationErrors}
 * las repite con datos inválidos: un rol no autorizado debe recibir 403 y nunca un 400 que revele las reglas de
 * validación.
 */
@WebMvcTest
@Import({SecurityConfig.class, AuthSecurity.class, CustomerSecurity.class, EmployeeSecurity.class,
        AccountSecurity.class, TransactionSecurity.class, ApiAuthorizationTest.UndeclaredEndpointController.class, JwtAuthenticationFilter.class,
        JwtUtil.class, SecurityErrorHandler.class, GlobalExceptionHandler.class, ActorWebConfig.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost",
        "jwt.secret=test-only-secret-with-more-than-thirty-two-chars",
        "jwt.expiration-ms=3600000"
})
@DisplayName("Permisos de la API")
class ApiAuthorizationTest {

    private static final String CUSTOMER = "CUSTOMER";
    private static final String ADMIN = "ADMIN";
    private static final String NUMBER = "123456789012";
    private static final String UUID_PATH = "11111111-1111-1111-1111-111111111111";

    /** Quién puede llamar a cada endpoint. */
    private enum Access {
        PUBLIC(true, true, true),
        ANY_AUTHENTICATED(false, true, true),
        NOBODY(false, false, false),
        CUSTOMER_ONLY(false, true, false),
        ADMIN_ONLY(false, false, true),
        CUSTOMER_OR_ADMIN(false, true, true);

        private final boolean anonymous;
        private final boolean customer;
        private final boolean admin;

        Access(boolean anonymous, boolean customer, boolean admin) {
            this.anonymous = anonymous;
            this.customer = customer;
            this.admin = admin;
        }

        boolean allows(String role) {
            return switch (role) {
                case CUSTOMER -> customer;
                case ADMIN -> admin;
                default -> anonymous;
            };
        }
    }

    private record Endpoint(String method, String path, String body, Access access) {
        @Override
        public String toString() {
            return method + " " + path + "  [" + access + "]";
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Los servicios que usan los controllers y el filtro: aquí solo importa la seguridad. SessionService decide, en una
     * sola consulta, si el token es la sesión vigente de un usuario ACTIVE.
     */
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private EmailVerificationService emailVerificationService;
    @MockitoBean
    private PasswordResetService passwordResetService;
    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private AccountOpeningService accountOpeningService;
    @MockitoBean
    private AccountQueryService accountQueryService;
    @MockitoBean
    private AccountLifecycleService accountLifecycleService;
    @MockitoBean
    private TransferService transferService;
    @MockitoBean
    private TransactionQueryService transactionQueryService;
    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void sessionIsValidAndUserActive() {
        when(sessionService.validateAndTouch(any(), anyString())).thenReturn(true);
    }

    static Stream<Endpoint> endpoints() {
        String emailBody = "{\"email\":\"ana@example.com\"}";
        return Stream.of(
                // ---- auth: públicos salvo logout
                new Endpoint("POST", "/api/v1/auth/login", "{}", Access.PUBLIC),
                new Endpoint("POST", "/api/v1/auth/verify-2fa", "{}", Access.PUBLIC),
                new Endpoint("POST", "/api/v1/auth/resend-2fa", "{}", Access.PUBLIC),
                new Endpoint("POST", "/api/v1/auth/verify-email", "{}", Access.PUBLIC),
                new Endpoint("POST", "/api/v1/auth/resend-verification", emailBody, Access.PUBLIC),
                new Endpoint("POST", "/api/v1/auth/recover-password", emailBody, Access.PUBLIC),
                new Endpoint("POST", "/api/v1/auth/reset-password", "{}", Access.PUBLIC),
                new Endpoint("POST", "/api/v1/auth/logout", "{}", Access.ANY_AUTHENTICATED),
                // ---- customers
                new Endpoint("POST", "/api/v1/customers", "{}", Access.PUBLIC),
                new Endpoint("GET", "/api/v1/customers/document-types", "", Access.PUBLIC),
                new Endpoint("GET", "/api/v1/customers/profile", "", Access.CUSTOMER_ONLY),
                new Endpoint("PUT", "/api/v1/customers/profile", "{}", Access.CUSTOMER_ONLY),
                new Endpoint("GET", "/api/v1/customers", "", Access.ADMIN_ONLY),
                new Endpoint("GET", "/api/v1/customers/by-document?documentTypeId=1&documentNumber=1000000001", "",
                        Access.ADMIN_ONLY),
                new Endpoint("PATCH", "/api/v1/customers/status",
                        "{\"documentTypeId\":1,\"documentNumber\":\"1000000001\",\"status\":\"ACTIVE\"}",
                        Access.ADMIN_ONLY),
                // ---- employees: todo es de administradores
                new Endpoint("POST", "/api/v1/employees",
                        "{\"firstNames\":\"Ana\",\"lastNames\":\"Gomez\",\"email\":\"ana@example.com\"}",
                        Access.ADMIN_ONLY),
                new Endpoint("GET", "/api/v1/employees/profile", "", Access.ADMIN_ONLY),
                new Endpoint("PUT", "/api/v1/employees/profile", "{}", Access.ADMIN_ONLY),
                new Endpoint("GET", "/api/v1/employees", "", Access.ADMIN_ONLY),
                new Endpoint("GET", "/api/v1/employees/" + UUID_PATH, "", Access.ADMIN_ONLY),
                new Endpoint("PATCH", "/api/v1/employees/" + UUID_PATH + "/status", "{\"status\":\"ACTIVE\"}",
                        Access.ADMIN_ONLY),
                // ---- accounts
                new Endpoint("POST", "/api/v1/accounts", "{}", Access.CUSTOMER_OR_ADMIN),
                new Endpoint("GET", "/api/v1/accounts", "", Access.CUSTOMER_ONLY),
                new Endpoint("GET", "/api/v1/accounts/" + NUMBER, "", Access.CUSTOMER_OR_ADMIN),
                new Endpoint("GET", "/api/v1/accounts/" + NUMBER + "/balance", "", Access.CUSTOMER_OR_ADMIN),
                new Endpoint("GET", "/api/v1/accounts/by-customer?documentTypeId=1&documentNumber=1000000001", "",
                        Access.ADMIN_ONLY),
                new Endpoint("GET", "/api/v1/accounts/by-customer/balances?documentTypeId=1&documentNumber=1000000001",
                        "", Access.ADMIN_ONLY),
                new Endpoint("PATCH", "/api/v1/accounts/" + NUMBER + "/block", "{\"reason\":\"Fraude\"}",
                        Access.ADMIN_ONLY),
                new Endpoint("PATCH", "/api/v1/accounts/" + NUMBER + "/unlock", "{\"reason\":\"Verificado\"}",
                        Access.ADMIN_ONLY),
                new Endpoint("PATCH", "/api/v1/accounts/" + NUMBER + "/close", "", Access.CUSTOMER_OR_ADMIN),
                // ---- transactions: transferir y confirmar son del cliente; el comprobante lo ve también el admin
                new Endpoint("POST", "/api/v1/transactions/transfers",
                        "{\"senderAccountNumber\":\"" + NUMBER + "\",\"receiverAccountNumber\":\"210000000002\","
                                + "\"amount\":10.00}", Access.CUSTOMER_ONLY),
                new Endpoint("POST", "/api/v1/transactions/TRF-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA/confirm", "{\"code\":\"123456\"}",
                        Access.CUSTOMER_ONLY),
                new Endpoint("GET", "/api/v1/transactions/TRF-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA", "", Access.CUSTOMER_OR_ADMIN),
                // ---- documentación y salud
                new Endpoint("GET", "/actuator/health", "", Access.PUBLIC),
                new Endpoint("GET", "/v3/api-docs", "", Access.PUBLIC),
                // ---- cualquier ruta que ninguna regla declara se deniega (anyRequest().denyAll()), exista o no
                new Endpoint("GET", "/api/v1/ruta-que-no-existe", "", Access.NOBODY)
        );
    }

    private String bearer(String role) {
        Date now = new Date();
        Date expires = new Date(now.getTime() + 3_600_000);
        return "Bearer " + jwtUtil.generateToken(UUID.randomUUID(), role, UUID.randomUUID().toString(), now, expires);
    }

    private MockHttpServletRequestBuilder request(Endpoint endpoint, String authorization) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(
                        org.springframework.http.HttpMethod.valueOf(endpoint.method()), endpoint.path())
                .contentType(MediaType.APPLICATION_JSON);
        if (!endpoint.body().isEmpty()) {
            builder.content(endpoint.body());
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return builder;
    }

    private int statusOf(Endpoint endpoint, String authorization) throws Exception {
        return mockMvc.perform(request(endpoint, authorization)).andReturn().getResponse().getStatus();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Sin token: 401 salvo en las rutas públicas")
    void anonymous(Endpoint endpoint) throws Exception {
        int status = statusOf(endpoint, null);

        if (endpoint.access().allows("ANONYMOUS")) {
            assertThat(status).as("%s sin token no debería exigir autenticación", endpoint).isNotIn(401, 403);
            assertThat(status).as("%s sin token no debería fallar por dentro", endpoint).isLessThan(500);
        } else {
            assertThat(status).as("%s sin token", endpoint).isEqualTo(401);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Con token de cada rol: pasa si el rol está permitido y 403 si no")
    void eachRole(Endpoint endpoint) throws Exception {
        for (String role : List.of(CUSTOMER, ADMIN)) {
            int status = statusOf(endpoint, bearer(role));

            if (endpoint.access().allows(role)) {
                assertThat(status).as("%s con rol %s debería estar permitido", endpoint, role).isNotIn(401, 403);
                // Un 5xx aquí delataría un fallo propio (p. ej. el @CurrentActor sin resolver), no un tema de permisos
                assertThat(status).as("%s con rol %s no debería fallar por dentro", endpoint, role).isLessThan(500);
            } else {
                assertThat(status).as("%s con rol %s debería ser 403", endpoint, role).isEqualTo(403);
            }
        }
    }

    static Stream<Endpoint> restrictedEndpoints() {
        return endpoints().filter(e -> e.access() != Access.PUBLIC && e.access() != Access.ANY_AUTHENTICATED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("restrictedEndpoints")
    @DisplayName("Un rol no autorizado recibe 403 aunque los datos sean inválidos, nunca un 400 de validación")
    void unauthorizedRoleNeverSeesValidationErrors(Endpoint endpoint) throws Exception {
        // Mismo método y ruta, sin los parámetros de consulta y con un cuerpo que ni siquiera es JSON
        String barePath = endpoint.path().contains("?")
                ? endpoint.path().substring(0, endpoint.path().indexOf('?')) : endpoint.path();
        String body = endpoint.method().equals("GET") ? "" : "{esto no es json";
        Endpoint invalid = new Endpoint(endpoint.method(), barePath, body, endpoint.access());

        for (String role : List.of(CUSTOMER, ADMIN)) {
            if (!endpoint.access().allows(role)) {
                assertThat(statusOf(invalid, bearer(role)))
                        .as("%s con rol %s y datos inválidos debería ser 403", endpoint, role).isEqualTo(403);
            }
        }
        assertThat(statusOf(invalid, null)).as("%s sin token y con datos inválidos", endpoint).isEqualTo(401);
    }

    /** Un endpoint al que ninguna clase *Security le declara permisos, como el que se olvidaría un desarrollador. */
    @RestController
    static class UndeclaredEndpointController {
        @GetMapping("/api/v1/zz-sin-regla")
        String forgotten() {
            return "ok";
        }
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Una ruta sin regla se deniega: 403 con token de cualquier rol y 401 sin token")
    void aRouteWithoutRuleIsDenied() throws Exception {
        Endpoint forgotten = new Endpoint("GET", "/api/v1/zz-sin-regla", "", Access.NOBODY);

        assertThat(statusOf(forgotten, bearer(ADMIN))).isEqualTo(403);
        assertThat(statusOf(forgotten, bearer(CUSTOMER))).isEqualTo(403);
        assertThat(statusOf(forgotten, null)).isEqualTo(401);
    }

    @Test
    @DisplayName("Todo endpoint de la aplicación está en la matriz de permisos: uno nuevo sin entrada hace fallar esto")
    void everyEndpointIsInTheMatrix() {
        List<Endpoint> matrix = endpoints().toList();
        List<String> missing = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            Class<?> controller = handler.getBeanType();
            if (!controller.getPackageName().startsWith("com.udea.digitalbank")
                    || controller == UndeclaredEndpointController.class) {
                return;   // solo los controllers de la aplicación, sin el de esta prueba
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String template : info.getPathPatternsCondition().getPatternValues()) {
                boolean covered = matrix.stream().anyMatch(e ->
                        (methods.isEmpty() || methods.contains(RequestMethod.valueOf(e.method())))
                                && pathMatches(template, e.path()));
                if (!covered) {
                    missing.add(methods + " " + template + "  <- " + controller.getSimpleName() + "."
                            + handler.getMethod().getName());
                }
            }
        });

        assertThat(missing)
                .as("Endpoints sin entrada en la matriz: agrégalos a endpoints() y declara sus roles en la clase *Security del módulo")
                .isEmpty();
    }

    // /api/v1/accounts/{accountNumber}/balance contra /api/v1/accounts/123456789012/balance (sin la consulta)
    private static boolean pathMatches(String template, String concretePath) {
        String path = concretePath.contains("?") ? concretePath.substring(0, concretePath.indexOf('?')) : concretePath;
        String regex = java.util.regex.Pattern.quote(template).replaceAll("\\{[^/}]+}", "\\\\E[^/]+\\\\Q");
        return path.matches(regex);
    }

    // ---- el filtro JWT: un token firmado no basta

    @Test
    @DisplayName("Un token con firma inválida responde 401")
    void invalidSignature() throws Exception {
        String forged = bearer(ADMIN).substring(0, bearer(ADMIN).length() - 4) + "abcd";

        assertThat(statusOf(adminEndpoint(), forged)).isEqualTo(401);
    }

    @Test
    @DisplayName("Un token válido de una sesión revocada, vencida o de un usuario que ya no está ACTIVE responde 401")
    void revokedSession() throws Exception {
        when(sessionService.validateAndTouch(any(), anyString())).thenReturn(false);

        assertThat(statusOf(adminEndpoint(), bearer(ADMIN))).isEqualTo(401);
    }

    @Test
    @DisplayName("Un encabezado Authorization que no es Bearer se trata como anónimo")
    void notABearerHeader() throws Exception {
        assertThat(statusOf(adminEndpoint(), "Basic YWRtaW46YWRtaW4=")).isEqualTo(401);
    }

    @Test
    @DisplayName("Los 401 y 403 de la cadena de seguridad llevan el cuerpo ErrorResponse en JSON")
    void securityErrorsAreJson() throws Exception {
        var unauthorized = mockMvc.perform(request(adminEndpoint(), null)).andReturn().getResponse();
        var forbidden = mockMvc.perform(request(adminEndpoint(), bearer(CUSTOMER))).andReturn().getResponse();

        assertThat(unauthorized.getContentType()).startsWith("application/json");
        assertThat(unauthorized.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"status\":401").contains("Autenticación requerida").contains("\"timestamp\":\"");
        assertThat(forbidden.getContentType()).startsWith("application/json");
        assertThat(forbidden.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"status\":403").contains("No tienes permiso");
    }

    private Endpoint adminEndpoint() {
        return new Endpoint("GET", "/api/v1/employees", "", Access.ADMIN_ONLY);
    }
}

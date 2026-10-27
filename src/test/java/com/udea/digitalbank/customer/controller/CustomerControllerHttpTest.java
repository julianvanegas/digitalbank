package com.udea.digitalbank.customer.controller;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.service.CustomerService;
import com.udea.digitalbank.shared.exception.InvalidRequestException;
import com.udea.digitalbank.shared.exception.auth.DuplicateUserException;
import com.udea.digitalbank.shared.exception.auth.InvalidStatusTransitionException;
import com.udea.digitalbank.shared.exception.customer.CustomerNotFoundException;
import com.udea.digitalbank.shared.exception.customer.DuplicateCustomerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.udea.digitalbank.support.HttpTestSupport.authenticate;
import static com.udea.digitalbank.support.HttpTestSupport.body;
import static com.udea.digitalbank.support.HttpTestSupport.clearAuthentication;
import static com.udea.digitalbank.support.HttpTestSupport.get;
import static com.udea.digitalbank.support.HttpTestSupport.patch;
import static com.udea.digitalbank.support.HttpTestSupport.perform;
import static com.udea.digitalbank.support.HttpTestSupport.post;
import static com.udea.digitalbank.support.HttpTestSupport.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas de contrato HTTP (AAA) de {@link CustomerController}: qué código y qué mensaje recibe el cliente de la
 * API en cada criterio, con la validación y el manejador de errores reales y el servicio simulado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU01 / HU06 / HU07 / HU08 / HU27 - Contrato HTTP de clientes")
class CustomerControllerHttpTest {

    private static final String BASE = "/api/v1/customers";
    private static final String VALID_BODY = """
            {"firstNames":"Maria Fernanda","lastNames":"Lopez Gomez","documentTypeId":1,
             "documentNumber":"1032456123","phone":"+573001112233","birthDate":"1990-05-14",
             "email":"maria.lopez@example.com"}""";

    @Mock
    private CustomerService customerService;

    private MockMvc mockMvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = com.udea.digitalbank.support.HttpTestSupport.mockMvc(new CustomerController(customerService));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    private static CustomerResponse customer(String status) {
        CustomerResponse response = new CustomerResponse();
        response.setId(UUID.randomUUID());
        response.setFirstNames("Maria Fernanda");
        response.setDocumentNumber("1032456123");
        response.setEmail("maria.lopez@example.com");
        response.setStatus(status);
        return response;
    }

    @Nested
    @DisplayName("HU01 / HU27 - POST /customers")
    class Register {

        @Test
        @DisplayName("HU01 CA01 / HU27 CA01 - Datos válidos: 201 con el cliente en Pendiente de verificación")
        void deberiaResponder201() throws Exception {
            // Arrange
            when(customerService.create(any(CreateCustomerRequest.class))).thenReturn(customer("PENDING_VERIFICATION"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, VALID_BODY));

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(body(response)).contains("\"status\":\"PENDING_VERIFICATION\"").contains("\"id\":");
        }

        @Test
        @DisplayName("HU01 CA11 / HU27 CA02 - Sin datos: 400 que nombra cada campo obligatorio y el cliente no se crea")
        void deberiaIndicarLosCamposFaltantes() throws Exception {
            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, "{}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("firstNames", "lastNames", "documentTypeId", "documentNumber",
                    "phone", "birthDate", "email");
            verifyNoInteractions(customerService);
        }

        @Test
        @DisplayName("HU01 CA12 - Un correo inválido junto a datos válidos: 400 y el cliente no se crea")
        void deberiaRechazarUnCorreoInvalido() throws Exception {
            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    post(BASE, VALID_BODY.replace("maria.lopez@example.com", "maria.example.com")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("email");
            verifyNoInteractions(customerService);
        }

        @Test
        @DisplayName("HU01 CA03 - Menor de 18 años: 400 con el mensaje de edad mínima")
        void deberiaRechazarAUnMenorDeEdad() throws Exception {
            // Arrange
            when(customerService.create(any(CreateCustomerRequest.class)))
                    .thenThrow(new InvalidRequestException("El cliente debe ser mayor de 18 años"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, VALID_BODY));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("18 años");
        }

        @Test
        @DisplayName("HU01 CA05 / HU27 CA03 - Documento ya registrado: 409 que lo informa")
        void deberiaRechazarUnDocumentoDuplicado() throws Exception {
            // Arrange
            when(customerService.create(any(CreateCustomerRequest.class))).thenThrow(
                    new DuplicateCustomerException("Ya existe un cliente registrado con ese número de documento"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, VALID_BODY));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("número de documento");
        }

        @Test
        @DisplayName("HU01 CA07 / HU27 CA04 - Correo ya registrado: 409 que lo informa")
        void deberiaRechazarUnCorreoDuplicado() throws Exception {
            // Arrange
            when(customerService.create(any(CreateCustomerRequest.class)))
                    .thenThrow(new DuplicateUserException("Ya existe un usuario registrado con ese email"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, post(BASE, VALID_BODY));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(body(response)).contains("email");
        }
    }

    @Nested
    @DisplayName("HU06 / HU07 - /customers/profile")
    class Profile {

        @Test
        @DisplayName("HU06 CA01 / HU06 CA03 - El cliente autenticado recibe su propio perfil")
        void deberiaDevolverElPerfilPropio() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(customerService.getProfile(userId)).thenReturn(customer("ACTIVE"));

            // Act
            MockHttpServletResponse response = perform(mockMvc, get(BASE + "/profile"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body(response)).contains("maria.lopez@example.com");
            verify(customerService).getProfile(userId);
        }

        @Test
        @DisplayName("HU07 CA02 / HU07 CA05 - Un dato inválido junto a uno válido: 400 y no se guarda nada")
        void deberiaRechazarLaActualizacionCompleta() throws Exception {
            // Arrange
            authenticate(userId, false);

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    put(BASE + "/profile", "{\"firstNames\":\"Maria\",\"phone\":\"3001112233\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body(response)).contains("phone");
            verifyNoInteractions(customerService);
        }

        @Test
        @DisplayName("HU07 CA01 / HU07 CA04 - Solo se envían los campos editables que cambian")
        void deberiaActualizarSoloLosCamposEnviados() throws Exception {
            // Arrange
            authenticate(userId, false);
            when(customerService.updateProfile(userId, null, null, "+573009998877")).thenReturn(customer("ACTIVE"));

            // Act
            MockHttpServletResponse response = perform(mockMvc,
                    put(BASE + "/profile", "{\"phone\":\"+573009998877\"}"));

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            verify(customerService).updateProfile(userId, null, null, "+573009998877");
        }
    }

    @Nested
    @DisplayName("HU08 - PATCH /customers/status")
    class ChangeStatus {

        private static final String PATH = BASE + "/status";

        private String body(String status) {
            return "{\"documentTypeId\":1,\"documentNumber\":\"1032456123\",\"status\":\"" + status + "\"}";
        }

        @Test
        @DisplayName("HU08 CA01 - Estado válido: 204 y se aplica al cliente del documento")
        void deberiaCambiarElEstado() throws Exception {
            // Act
            MockHttpServletResponse response = perform(mockMvc, patch(PATH, body("INACTIVE")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(204);
            verify(customerService).changeStatus((short) 1, "1032456123", UserStatusEnum.INACTIVE);
        }

        @Test
        @DisplayName("HU08 CA03 - Cliente inexistente: 404 que informa que no existe")
        void deberiaInformarQueElClienteNoExiste() throws Exception {
            // Arrange
            doThrow(new CustomerNotFoundException("Cliente no encontrado con el documento: 1 1032456123"))
                    .when(customerService).changeStatus(anyShort(), anyString(), any(UserStatusEnum.class));

            // Act
            MockHttpServletResponse response = perform(mockMvc, patch(PATH, body("BLOCKED")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(HttpBody.of(response)).contains("Cliente no encontrado");
        }

        @Test
        @DisplayName("HU08 CA04 - Un estado que no existe: 400 y el servicio no se invoca")
        void deberiaRechazarUnEstadoInexistente() throws Exception {
            // Act
            MockHttpServletResponse suspended = perform(mockMvc, patch(PATH, body("SUSPENDED")));
            MockHttpServletResponse empty = perform(mockMvc, patch(PATH, body("")));

            // Assert
            assertThat(suspended.getStatus()).isEqualTo(400);
            assertThat(empty.getStatus()).isEqualTo(400);
            verifyNoInteractions(customerService);
        }

        @Test
        @DisplayName("HU08 CA04 - Una transición no permitida: 409 y el estado se conserva")
        void deberiaRechazarUnaTransicionNoPermitida() throws Exception {
            // Arrange
            doThrow(new InvalidStatusTransitionException(
                    "No se puede cambiar el estado de un usuario de BLOCKED a INACTIVE"))
                    .when(customerService).changeStatus(eq((short) 1), eq("1032456123"), eq(UserStatusEnum.INACTIVE));

            // Act
            MockHttpServletResponse response = perform(mockMvc, patch(PATH, body("INACTIVE")));

            // Assert
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(HttpBody.of(response)).contains("No se puede cambiar el estado");
        }
    }

    /** Evita el choque de nombres con el método body(String) de la clase anidada ChangeStatus. */
    private static final class HttpBody {
        static String of(MockHttpServletResponse response) throws Exception {
            return com.udea.digitalbank.support.HttpTestSupport.body(response);
        }
    }
}

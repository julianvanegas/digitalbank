package com.udea.digitalbank.customer.service;

import com.udea.digitalbank.auth.api.AuthFacade;
import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserView;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.customer.dto.CreateCustomerRequest;
import com.udea.digitalbank.customer.dto.CustomerResponse;
import com.udea.digitalbank.customer.mapper.CustomerMapper;
import com.udea.digitalbank.customer.repository.CustomerRepository;
import com.udea.digitalbank.customer.repository.DocumentTypeRepository;
import com.udea.digitalbank.shared.exception.auth.DuplicateUserException;
import com.udea.digitalbank.shared.exception.customer.CustomerNotFoundException;
import com.udea.digitalbank.shared.exception.customer.DuplicateCustomerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link CustomerService}.
 * Todas las dependencias (repositorios, AuthFacade y mapper) se mockean: aquí no se prueba
 * persistencia real ni la política de contraseña (eso corresponde a pruebas de integración
 * y a {@code PasswordConstraintTest} respectivamente), solo la lógica de negocio del servicio.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;
    @Mock
    private AuthFacade authFacade;
    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerService customerService;

    private CreateCustomerRequest buildValidRequest() {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFirstNames("Ana");
        request.setLastNames("Gómez");
        request.setDocumentTypeId((short) 1);
        request.setDocumentNumber("1000000001");
        request.setPhone("+573001234567");
        request.setBirthDate(LocalDate.now().minusYears(20));
        request.setEmail("ana.gomez@example.com");
        request.setPassword("Abcdef1$");
        return request;
    }

    private DocumentType buildDocumentType() {
        DocumentType documentType = new DocumentType();
        // DocumentType no expone setters (@Getter + @NoArgsConstructor + campos final por catálogo),
        // por lo que se accede por reflexión solo para armar el fixture de la prueba.
        setField(documentType, "id", (short) 1);
        setField(documentType, "code", "Cédula de ciudadanía");
        return documentType;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("create - HU01 Registrar cliente")
    class Create {

        @Test
        @DisplayName("CA01 - Registro exitoso con todos los datos válidos")
        void deberiaCrearClienteCuandoTodosLosDatosSonValidos() {
            // Arrange
            CreateCustomerRequest request = buildValidRequest();
            DocumentType documentType = buildDocumentType();
            UserView userView = new UserView(UUID.randomUUID(), request.getEmail(), "CUSTOMER", "PENDING_VERIFICATION");
            CustomerResponse expectedResponse = new CustomerResponse();

            when(documentTypeRepository.findById(request.getDocumentTypeId()))
                    .thenReturn(Optional.of(documentType));
            when(customerRepository.existsByDocumentTypeIdAndDocumentNumber(documentType.getId(), request.getDocumentNumber()))
                    .thenReturn(false);
            when(authFacade.createUser(request.getEmail(), request.getPassword(), RoleEnum.CUSTOMER))
                    .thenReturn(userView);
            when(customerRepository.save(any(Customer.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(customerMapper.toResponse(any(Customer.class), eq(userView)))
                    .thenReturn(expectedResponse);

            // Act
            CustomerResponse result = customerService.create(request);

            // Assert
            assertThat(result).isSameAs(expectedResponse);
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            Customer savedCustomer = customerCaptor.getValue();
            assertThat(savedCustomer.getUserId()).isEqualTo(userView.id());
            assertThat(savedCustomer.getFirstNames()).isEqualTo(request.getFirstNames());
            assertThat(savedCustomer.getLastNames()).isEqualTo(request.getLastNames());
            assertThat(savedCustomer.getDocumentType()).isEqualTo(documentType);
            assertThat(savedCustomer.getDocumentNumber()).isEqualTo(request.getDocumentNumber());
            assertThat(savedCustomer.getPhone()).isEqualTo(request.getPhone());
            assertThat(savedCustomer.getBirthDate()).isEqualTo(request.getBirthDate());
        }

        @Test
        @DisplayName("CA02 - Se permite el registro cuando el usuario cumple 18 años exactamente hoy")
        void deberiaPermitirRegistroCuandoElUsuarioCumple18AñosHoy() {
            // Arrange
            CreateCustomerRequest request = buildValidRequest();
            request.setBirthDate(LocalDate.now().minusYears(18)); // cumple 18 exactamente hoy
            DocumentType documentType = buildDocumentType();
            UserView userView = new UserView(UUID.randomUUID(), request.getEmail(), "CUSTOMER", "PENDING_VERIFICATION");
            CustomerResponse expectedResponse = new CustomerResponse();

            when(documentTypeRepository.findById(request.getDocumentTypeId()))
                    .thenReturn(Optional.of(documentType));
            when(customerRepository.existsByDocumentTypeIdAndDocumentNumber(documentType.getId(), request.getDocumentNumber()))
                    .thenReturn(false);
            when(authFacade.createUser(request.getEmail(), request.getPassword(), RoleEnum.CUSTOMER))
                    .thenReturn(userView);
            when(customerRepository.save(any(Customer.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(customerMapper.toResponse(any(Customer.class), eq(userView)))
                    .thenReturn(expectedResponse);

            // Act
            CustomerResponse result = customerService.create(request);

            // Assert: no debe rechazarse por edad, y el registro se completa con normalidad
            assertThat(result).isSameAs(expectedResponse);
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("CA03 - Rechaza el registro cuando el usuario es menor de 18 años")
        void deberiaRechazarRegistroCuandoElUsuarioEsMenorDeEdad() {
            // Arrange
            CreateCustomerRequest request = buildValidRequest();
            request.setBirthDate(LocalDate.now().minusYears(18).plusDays(1)); // le falta un día para los 18

            // Act
            Throwable thrown = catchThrowable(() -> customerService.create(request));

            // Assert
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("18");
            verifyNoInteractions(documentTypeRepository, customerRepository, authFacade);
        }

        @Test
        @DisplayName("Rechaza el registro cuando el tipo de documento no existe en el catálogo")
        void deberiaRechazarRegistroCuandoElTipoDeDocumentoNoExiste() {
            // Arrange
            CreateCustomerRequest request = buildValidRequest();
            when(documentTypeRepository.findById(request.getDocumentTypeId()))
                    .thenReturn(Optional.empty());

            // Act
            Throwable thrown = catchThrowable(() -> customerService.create(request));

            // Assert
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(customerRepository, authFacade);
        }

        @Test
        @DisplayName("CA05 - Rechaza el registro cuando el número de documento ya está registrado")
        void deberiaRechazarRegistroCuandoElDocumentoYaExiste() {
            // Arrange
            CreateCustomerRequest request = buildValidRequest();
            DocumentType documentType = buildDocumentType();
            when(documentTypeRepository.findById(request.getDocumentTypeId()))
                    .thenReturn(Optional.of(documentType));
            when(customerRepository.existsByDocumentTypeIdAndDocumentNumber(documentType.getId(), request.getDocumentNumber()))
                    .thenReturn(true);

            // Act
            Throwable thrown = catchThrowable(() -> customerService.create(request));

            // Assert
            assertThat(thrown).isInstanceOf(DuplicateCustomerException.class);
            // El documento se valida antes de crear el usuario en auth: no debe llegar a invocarse
            verifyNoInteractions(authFacade);
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("CA07 - Rechaza el registro cuando el correo electrónico ya está registrado")
        void deberiaRechazarRegistroCuandoElCorreoYaExiste() {
            // Arrange
            CreateCustomerRequest request = buildValidRequest();
            DocumentType documentType = buildDocumentType();
            when(documentTypeRepository.findById(request.getDocumentTypeId()))
                    .thenReturn(Optional.of(documentType));
            when(customerRepository.existsByDocumentTypeIdAndDocumentNumber(documentType.getId(), request.getDocumentNumber()))
                    .thenReturn(false);
            when(authFacade.createUser(request.getEmail(), request.getPassword(), RoleEnum.CUSTOMER))
                    .thenThrow(new DuplicateUserException("Ya existe un usuario registrado con ese email"));

            // Act
            Throwable thrown = catchThrowable(() -> customerService.create(request));

            // Assert
            assertThat(thrown).isInstanceOf(DuplicateUserException.class);
            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getProfile - HU06 Consultar perfil")
    class GetProfile {

        @Test
        @DisplayName("CA01 - Retorna el perfil del cliente autenticado")
        void deberiaRetornarElPerfilCuandoElClienteExiste() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Customer customer = new Customer();
            customer.setUserId(userId);
            UserView userView = new UserView(userId, "cliente@example.com", "CUSTOMER", "ACTIVE");
            CustomerResponse expectedResponse = new CustomerResponse();

            when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));
            when(authFacade.getUser(userId)).thenReturn(userView);
            when(customerMapper.toResponse(customer, userView)).thenReturn(expectedResponse);

            // Act
            CustomerResponse result = customerService.getProfile(userId);

            // Assert
            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        @DisplayName("Rechaza la consulta cuando no existe un perfil para el usuario autenticado")
        void deberiaLanzarExcepcionCuandoElPerfilNoExiste() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(customerRepository.findByUserId(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> customerService.getProfile(userId))
                    .isInstanceOf(CustomerNotFoundException.class);
            verifyNoInteractions(authFacade);
        }
    }

    @Nested
    @DisplayName("updateProfile - HU07 Actualizar datos de perfil")
    class UpdateProfile {

        @Test
        @DisplayName("CA01 / CA04 - Actualiza solo el dato enviado y conserva los demás sin cambios")
        void deberiaActualizarSoloElCampoEnviadoYConservarLosDemas() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Customer existing = new Customer();
            existing.setUserId(userId);
            existing.setFirstNames("Nombre Original");
            existing.setLastNames("Apellido Original");
            existing.setPhone("+573000000000");

            UserView userView = new UserView(userId, "cliente@example.com", "CUSTOMER", "ACTIVE");
            when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
            when(customerRepository.save(any(Customer.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(authFacade.getUser(userId)).thenReturn(userView);
            when(customerMapper.toResponse(any(Customer.class), eq(userView)))
                    .thenReturn(new CustomerResponse());

            // Act: solo se envía firstNames; lastNames y phone llegan como null
            customerService.updateProfile(userId, "Nombre Nuevo", null, null);

            // Assert
            ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(captor.capture());
            Customer saved = captor.getValue();
            assertThat(saved.getFirstNames()).isEqualTo("Nombre Nuevo");
            assertThat(saved.getLastNames()).isEqualTo("Apellido Original"); // sin cambios
            assertThat(saved.getPhone()).isEqualTo("+573000000000");        // sin cambios
        }

        @Test
        @DisplayName("Rechaza la actualización cuando no existe un perfil para el usuario autenticado")
        void deberiaLanzarExcepcionCuandoElPerfilNoExiste() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(customerRepository.findByUserId(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> customerService.updateProfile(userId, "X", null, null))
                    .isInstanceOf(CustomerNotFoundException.class);
            verify(customerRepository, never()).save(any());
        }
    }
}
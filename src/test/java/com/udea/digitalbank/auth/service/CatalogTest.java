package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.repository.ChallengePurposeRepository;
import com.udea.digitalbank.auth.repository.RoleRepository;
import com.udea.digitalbank.auth.repository.UserStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link Catalogs}: carga los catálogos en memoria al arrancar y valida que
 * los enums del código (RoleEnum, UserStatusEnum, PurposeEnum) coincidan exactamente con lo que
 * hay en la base de datos. {@code load()} es package-private (se invoca normalmente vía
 * {@code @PostConstruct}), así que se llama directamente desde este test, que está en el mismo paquete.
 */
@ExtendWith(MockitoExtension.class)
class CatalogsTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserStatusRepository statusRepository;
    @Mock
    private ChallengePurposeRepository purposeRepository;

    private static Role role(short id, String code) {
        Role role = new Role();
        setField(role, "id", id);
        setField(role, "code", code);
        return role;
    }

    private static UserStatus status(short id, String code) {
        UserStatus status = new UserStatus();
        setField(status, "id", id);
        setField(status, "code", code);
        return status;
    }

    private static ChallengePurpose purpose(short id, String code) {
        ChallengePurpose purpose = new ChallengePurpose();
        setField(purpose, "id", id);
        setField(purpose, "purpose", code);
        return purpose;
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

    // Catálogos completos y consistentes con los enums: la configuración "feliz" que comparten
    // la mayoría de los tests de esta clase.
    private Catalogs buildCatalogsWithAllCatalogsLoaded() {
        when(roleRepository.findAll()).thenReturn(List.of(role((short) 1, "CUSTOMER"), role((short) 2, "ADMIN")));
        when(statusRepository.findAll()).thenReturn(List.of(
                status((short) 1, "ACTIVE"), status((short) 2, "PENDING_VERIFICATION"), status((short) 5, "PENDING_REVIEW"),
                status((short) 3, "INACTIVE"), status((short) 4, "BLOCKED")));
        when(purposeRepository.findAll()).thenReturn(List.of(
                purpose((short) 1, "LOGIN"), purpose((short) 2, "EMAIL_CONFIRMATION"), purpose((short) 3, "PASSWORD_RESET")));

        Catalogs catalogs = new Catalogs(roleRepository, statusRepository, purposeRepository);
        catalogs.load();
        return catalogs;
    }

    @Nested
    @DisplayName("Catálogos consistentes con los enums del código")
    class CatalogosConsistentes {

        @Test
        @DisplayName("Carga correctamente y resuelve cada catálogo por su enum")
        void deberiaCargarYResolverCadaCatalogoPorSuEnum() {
            // Arrange
            Catalogs catalogs = buildCatalogsWithAllCatalogsLoaded();

            // Act & Assert
            assertThat(catalogs.role(RoleEnum.CUSTOMER).getCode()).isEqualTo("CUSTOMER");
            assertThat(catalogs.status(UserStatusEnum.BLOCKED).getCode()).isEqualTo("BLOCKED");
            assertThat(catalogs.status(UserStatusEnum.PENDING_REVIEW).getCode()).isEqualTo("PENDING_REVIEW");
            assertThat(catalogs.purpose(PurposeEnum.PASSWORD_RESET).getPurpose()).isEqualTo("PASSWORD_RESET");
        }
    }

    @Nested
    @DisplayName("Inconsistencias entre los enums del código y la base de datos: la app no debe arrancar")
    class CatalogosInconsistentes {

        @Test
        @DisplayName("Falta un estado en la base de datos que sí existe en el enum: falla al cargar")
        void deberiaFallarSiFaltaUnEstadoEnLaBaseDeDatos() {
            // Arrange: falta BLOCKED en la base, pero el enum UserStatusEnum sí lo declara
            when(roleRepository.findAll()).thenReturn(List.of(role((short) 1, "CUSTOMER"), role((short) 2, "ADMIN")));
            when(statusRepository.findAll()).thenReturn(List.of(
                    status((short) 1, "ACTIVE"), status((short) 2, "PENDING_VERIFICATION"), status((short) 5, "PENDING_REVIEW"),
                    status((short) 3, "INACTIVE")));
            when(purposeRepository.findAll()).thenReturn(List.of(
                    purpose((short) 1, "LOGIN"), purpose((short) 2, "EMAIL_CONFIRMATION"), purpose((short) 3, "PASSWORD_RESET")));
            Catalogs catalogs = new Catalogs(roleRepository, statusRepository, purposeRepository);

            // Act & Assert
            assertThatThrownBy(catalogs::load)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BLOCKED");
        }

        @Test
        @DisplayName("La base de datos tiene un valor que el enum no declara: también falla al cargar")
        void deberiaFallarSiLaBaseTieneUnValorQueElEnumNoDeclara() {
            // Arrange: la base trae un rol "SUPPORT" que RoleEnum no conoce
            when(roleRepository.findAll()).thenReturn(List.of(
                    role((short) 1, "CUSTOMER"), role((short) 2, "ADMIN"), role((short) 3, "SUPPORT")));
            when(statusRepository.findAll()).thenReturn(List.of(
                    status((short) 1, "ACTIVE"), status((short) 2, "PENDING_VERIFICATION"), status((short) 5, "PENDING_REVIEW"),
                    status((short) 3, "INACTIVE"), status((short) 4, "BLOCKED")));
            when(purposeRepository.findAll()).thenReturn(List.of(
                    purpose((short) 1, "LOGIN"), purpose((short) 2, "EMAIL_CONFIRMATION"), purpose((short) 3, "PASSWORD_RESET")));
            Catalogs catalogs = new Catalogs(roleRepository, statusRepository, purposeRepository);

            // Act & Assert
            assertThatThrownBy(catalogs::load)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SUPPORT");
        }
    }
}
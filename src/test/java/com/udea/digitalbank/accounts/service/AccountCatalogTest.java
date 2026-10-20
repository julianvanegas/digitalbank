package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.domain.AccountStatus;
import com.udea.digitalbank.accounts.domain.AccountType;
import com.udea.digitalbank.accounts.repository.AccountStatusRepository;
import com.udea.digitalbank.accounts.repository.AccountTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link AccountCatalog}: los enums del código y las tablas de catálogo deben
 * coincidir en ambos sentidos, o la app no arranca.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountCatalog")
class AccountCatalogTest {

    @Mock
    private AccountStatusRepository statusRepository;
    @Mock
    private AccountTypeRepository typeRepository;

    private AccountCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new AccountCatalog(statusRepository, typeRepository);
    }

    private static AccountStatus status(String code) {
        AccountStatus status = new AccountStatus();
        ReflectionTestUtils.setField(status, "code", code);
        return status;
    }

    private static AccountType type(String code) {
        AccountType type = new AccountType();
        ReflectionTestUtils.setField(type, "code", code);
        return type;
    }

    private List<AccountStatus> allStatuses() {
        return Arrays.stream(AccountStatusEnum.values()).map(e -> status(e.name())).toList();
    }

    private List<AccountType> allTypes() {
        return Arrays.stream(AccountTypeEnum.values()).map(e -> type(e.name())).toList();
    }

    @Test
    @DisplayName("Carga estados y tipos cuando la base coincide con los enums")
    void deberiaCargarCuandoCoinciden() {
        // Arrange
        when(statusRepository.findAll()).thenReturn(allStatuses());
        when(typeRepository.findAll()).thenReturn(allTypes());

        // Act
        catalog.load();

        // Assert
        assertThat(catalog.status(AccountStatusEnum.BLOCKED).getCode()).isEqualTo("BLOCKED");
        assertThat(catalog.type(AccountTypeEnum.CHECKING).getCode()).isEqualTo("CHECKING");
    }

    @Test
    @DisplayName("No arranca si falta un tipo de cuenta en la base")
    void deberiaFallarSiFaltaUnTipoEnLaBase() {
        // Arrange
        when(statusRepository.findAll()).thenReturn(allStatuses());
        when(typeRepository.findAll()).thenReturn(List.of(type("SAVINGS")));

        // Act & Assert
        assertThatThrownBy(catalog::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AccountTypeEnum.CHECKING no existe en la base");
    }

    @Test
    @DisplayName("No arranca si la base tiene un estado que el enum no conoce")
    void deberiaFallarSiLaBaseTieneUnEstadoDeMas() {
        // Arrange
        List<AccountStatus> statuses = new java.util.ArrayList<>(allStatuses());
        statuses.add(status("FROZEN"));
        when(statusRepository.findAll()).thenReturn(statuses);
        when(typeRepository.findAll()).thenReturn(allTypes());

        // Act & Assert
        assertThatThrownBy(catalog::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AccountStatusEnum no declara 'FROZEN'");
    }
}

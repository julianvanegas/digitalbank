package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.UserRepository;
import com.udea.digitalbank.shared.exception.auth.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link VerificationApiService}, el contrato de 2FA que auth ofrece a transactions. */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationApiService")
class VerificationApiServiceTest {

    private final UUID userId = UUID.randomUUID();

    @Mock
    private VerificationService verificationService;
    @Mock
    private VerificationDispatcher verificationDispatcher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Catalogs catalogs;

    @InjectMocks
    private VerificationApiService verificationApiService;

    @Test
    @DisplayName("sendTransactionCode - emite y envía un código TRANSACTION al usuario, con el detalle")
    void sendTransactionCode_deberiaEmitirYEnviarElCodigo() {
        // Arrange
        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        verificationApiService.sendTransactionCode(userId, "detalle");

        // Assert
        verify(verificationDispatcher).issueAndSend(user, PurposeEnum.TRANSACTION, "detalle");
    }

    @Test
    @DisplayName("sendTransactionCode - lanza UserNotFoundException si el usuario no existe")
    void sendTransactionCode_deberiaFallarSiElUsuarioNoExiste() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> verificationApiService.sendTransactionCode(userId, "detalle"))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(verificationDispatcher);
    }

    @Test
    @DisplayName("verifyTransactionCode - devuelve el resultado de la verificación del propósito TRANSACTION")
    void verifyTransactionCode_deberiaDelegarEnLaVerificacion() {
        // Arrange
        CodeCheckResult expected = CodeCheckResult.wrong(2);
        when(verificationService.check(userId, PurposeEnum.TRANSACTION, "123456")).thenReturn(expected);

        // Act
        CodeCheckResult result = verificationApiService.verifyTransactionCode(userId, "123456");

        // Assert
        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("transactionCodeTtlMinutes - es la vigencia del propósito TRANSACTION en el catálogo")
    void transactionCodeTtlMinutes_deberiaLeerLaVigenciaDelCatalogo() {
        // Arrange
        ChallengePurpose purpose = new ChallengePurpose();
        ReflectionTestUtils.setField(purpose, "ttlMinutes", 3);
        when(catalogs.purpose(PurposeEnum.TRANSACTION)).thenReturn(purpose);

        // Act + Assert
        assertThat(verificationApiService.transactionCodeTtlMinutes()).isEqualTo(3);
    }
}

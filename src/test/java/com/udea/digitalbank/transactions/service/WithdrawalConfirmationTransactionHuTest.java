package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.auth.api.CodeCheckResult;
import com.udea.digitalbank.auth.api.VerificationApi;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.transactions.InvalidTransactionCodeException;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.mapper.TransactionMapper;
import com.udea.digitalbank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.status;
import static com.udea.digitalbank.transactions.TransactionFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba que el {@code noRollbackFor = BusinessException.class} de {@link WithdrawalConfirmationService#confirm} sirve:
 * que un rechazo esperado se <b>confirma</b> en la base en vez de revertirse, y con él el retiro {@code REJECTED} y el
 * intento fallido del código.
 * <p>
 * A diferencia de las demás pruebas del módulo, levanta un contexto Spring: el {@code @Transactional} lo aplica un
 * proxy de Spring, y con el servicio instanciado con new nunca se evalúa. El contexto es mínimo: el servicio, sus
 * colaboradores simulados y un gestor de transacciones que solo anota si cada transacción se confirmó o se revirtió.
 * Sin base de datos: lo que se prueba es la decisión de confirmar o revertir, no la escritura.
 */
@SpringJUnitConfig(WithdrawalConfirmationTransactionHuTest.Config.class)
@DisplayName("HU19 - WithdrawalConfirmationService - un rechazo se confirma, no se revierte")
class WithdrawalConfirmationTransactionHuTest {

    private static final String REFERENCE = "RET-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";
    private static final String CODE = "123456";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    /**
     * Gestor de transacciones que no toca ninguna base: anota "commit" o "rollback" por cada transacción física.
     * Reconoce la transacción en curso, así que una llamada transaccional anidada participa en ella en vez de abrir
     * otra, y propaga el "solo revertir" que marque un participante, como hace el gestor real.
     */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        static final class Tx implements SmartTransactionObject {
            boolean rollbackOnly;

            @Override
            public boolean isRollbackOnly() {
                return rollbackOnly;
            }

            @Override
            public void flush() {
                // Sin efecto: este gestor no escribe en ninguna base, solo anota commit/rollback
            }
        }

        final List<String> outcomes = new ArrayList<>();
        private final ThreadLocal<Tx> current = new ThreadLocal<>();

        @Override
        protected Object doGetTransaction() {
            Tx tx = current.get();
            return tx != null ? tx : new Tx();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return transaction == current.get();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            current.set((Tx) transaction);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            outcomes.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            outcomes.add("rollback");
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((Tx) status.getTransaction()).rollbackOnly = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            current.remove();
        }
    }

    /**
     * Solo el servicio que se prueba y el gestor son beans: los colaboradores son mocks fuera del contexto, para que
     * Spring no les ponga también un proxy transaccional (TransactionLimitService tiene métodos @Transactional).
     */
    @Configuration
    @EnableTransactionManagement
    static class Config {

        static final TransactionRepository TRANSACTION_REPOSITORY = mock(TransactionRepository.class);
        static final TransactionCatalog CATALOG = mock(TransactionCatalog.class);
        static final TransactionLimitService LIMIT_SERVICE = mock(TransactionLimitService.class);
        static final AccountApi ACCOUNT_API = mock(AccountApi.class);
        static final VerificationApi VERIFICATION_API = mock(VerificationApi.class);

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        WithdrawalConfirmationService withdrawalConfirmationService() {
            return new WithdrawalConfirmationService(TRANSACTION_REPOSITORY, CATALOG,
                    new TransactionResponses(Mappers.getMapper(TransactionMapper.class), ACCOUNT_API),
                    new TransferParties(ACCOUNT_API, LIMIT_SERVICE),
                    new PendingTransfers(TRANSACTION_REPOSITORY, CATALOG, ACCOUNT_API, VERIFICATION_API,
                            Clock.systemDefaultZone()),
                    LIMIT_SERVICE, ACCOUNT_API, VERIFICATION_API);
        }
    }

    @Autowired
    private WithdrawalConfirmationService service;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final TransactionRepository transactionRepository = Config.TRANSACTION_REPOSITORY;
    private final TransactionCatalog catalog = Config.CATALOG;
    private final TransactionLimitService limitService = Config.LIMIT_SERVICE;
    private final AccountApi accountApi = Config.ACCOUNT_API;
    private final VerificationApi verificationApi = Config.VERIFICATION_API;

    private final UUID userId = UUID.randomUUID();
    private final AccountView account = new AccountView(UUID.randomUUID(), "@AN1231", "100000000001",
            UUID.randomUUID(), "SAVINGS", AccountStatusEnum.ACTIVE.name(), new BigDecimal("500.00"));
    private Transaction withdrawal;

    @BeforeEach
    void setUp() {
        when(catalog.status(any())).thenAnswer(i -> status(i.getArgument(0)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        withdrawal = Transaction.withdrawal(UUID.randomUUID(), userId, account.id(), AMOUNT, REFERENCE,
                type(TransactionTypeEnum.WITHDRAWAL), status(TransactionStatusEnum.PENDING), LocalDateTime.now());
        when(transactionRepository.findByReferenceForUpdate(REFERENCE)).thenReturn(Optional.of(withdrawal));
        when(accountApi.getAccount(account.id())).thenReturn(account);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(transactionRepository, catalog, limitService, accountApi, verificationApi);
        outcomes().clear();
    }

    private List<String> outcomes() {
        return ((RecordingTransactionManager) transactionManager).outcomes;
    }

    @Test
    @DisplayName("HU19 CA02 - Si al confirmar el saldo ya no alcanza, el REJECTED se confirma en la base aunque se lance la excepción")
    void deberiaConfirmarElRechazoPorSaldo() {
        // Arrange
        when(verificationApi.verifyTransactionCode(userId, CODE)).thenReturn(CodeCheckResult.valid());
        when(accountApi.applyWithdrawal(account.id(), AMOUNT))
                .thenThrow(new InsufficientFundsException("Fondos insuficientes en la cuenta"));

        // Act
        Actor actor = new Actor(userId, false);
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, CODE))
                .isInstanceOf(InsufficientFundsException.class);

        // Assert: una sola transacción, confirmada, y en ella el retiro guardado como REJECTED
        assertThat(outcomes()).containsExactly("commit");
        assertThat(withdrawal.is(TransactionStatusEnum.REJECTED)).isTrue();
        verify(transactionRepository).save(withdrawal);
    }

    @Test
    @DisplayName("HU19 CA04 - Con un código equivocado la transacción se confirma, así que el intento fallido queda registrado")
    void deberiaConfirmarElIntentoFallido() {
        // Arrange
        when(verificationApi.verifyTransactionCode(userId, CODE)).thenReturn(CodeCheckResult.wrong(2));

        // Act
        Actor actor = new Actor(userId, false);
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, CODE))
                .isInstanceOf(InvalidTransactionCodeException.class);

        // Assert: el contador de intentos que auth guarda al verificar participa de esta transacción
        assertThat(outcomes()).containsExactly("commit");
    }

    // Control: el gestor distingue los dos resultados. Un error que no es de negocio sí revierte, y con él el REJECTED
    @Test
    @DisplayName("HU19 - Control: un error que no es de negocio sí revierte la transacción")
    void deberiaRevertirUnErrorQueNoEsDeNegocio() {
        // Arrange
        when(verificationApi.verifyTransactionCode(userId, CODE)).thenReturn(CodeCheckResult.valid());
        when(accountApi.applyWithdrawal(account.id(), AMOUNT))
                .thenThrow(new IllegalArgumentException("El monto de un movimiento debe ser positivo"));

        // Act
        Actor actor = new Actor(userId, false);
        assertThatThrownBy(() -> service.confirm(actor, REFERENCE, CODE))
                .isInstanceOf(IllegalArgumentException.class);

        // Assert
        assertThat(outcomes()).containsExactly("rollback");
    }
}

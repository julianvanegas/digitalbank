package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountApi;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.udea.digitalbank.transactions.TransactionFixtures.transfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pruebas unitarias (AAA) de {@link TransactionAccess}: quién puede ver una transacción. */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionAccess")
class TransactionAccessTest {

    @Mock
    private AccountApi accountApi;

    private TransactionAccess access;

    private final UUID orderingUserId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        access = new TransactionAccess(accountApi);
        transaction = transfer(orderingUserId, senderId, UUID.randomUUID(), "75.00", TransactionStatusEnum.COMPLETED);
    }

    @Test
    @DisplayName("El ordenante la ve, sin consultar cuentas")
    void deberiaPermitirAlOrdenante() {
        assertThat(access.canView(new Actor(orderingUserId, false), transaction)).isTrue();
        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("Un administrador la ve, sin consultar cuentas")
    void deberiaPermitirAUnAdministrador() {
        assertThat(access.canView(new Actor(UUID.randomUUID(), true), transaction)).isTrue();
        verifyNoInteractions(accountApi);
    }

    @Test
    @DisplayName("El titular de la cuenta de origen la ve aunque la ordenara otro usuario")
    void deberiaPermitirAlTitularDeLaCuentaDeOrigen() {
        UUID holderId = UUID.randomUUID();
        when(accountApi.getOwnerUserId(senderId)).thenReturn(holderId);

        assertThat(access.canView(new Actor(holderId, false), transaction)).isTrue();
    }

    @Test
    @DisplayName("Otro cliente no la ve")
    void noDeberiaPermitirAOtroCliente() {
        when(accountApi.getOwnerUserId(senderId)).thenReturn(UUID.randomUUID());

        assertThat(access.canView(new Actor(UUID.randomUUID(), false), transaction)).isFalse();
    }
}

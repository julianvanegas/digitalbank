package com.udea.digitalbank.accounts.domain;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.shared.exception.accounts.AccountNotBlockableException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotClosableException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotUnblockableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas de estado y saldo viven en la cuenta: se prueban directamente, sin servicios ni mocks. */
@DisplayName("Account")
class AccountTest {

    private static AccountStatus status(AccountStatusEnum statusEnum) {
        AccountStatus status = new AccountStatus();
        ReflectionTestUtils.setField(status, "id", (short) (statusEnum.ordinal() + 1));
        ReflectionTestUtils.setField(status, "code", statusEnum.name());
        return status;
    }

    private static AccountType savings() {
        AccountType type = new AccountType();
        ReflectionTestUtils.setField(type, "id", (short) 1);
        ReflectionTestUtils.setField(type, "code", AccountTypeEnum.SAVINGS.name());
        return type;
    }

    private static Account account(AccountStatusEnum current, String balance) {
        Account account = Account.open(UUID.randomUUID(), "123456789012", "@AN1231", (short) 1, savings(),
                status(AccountStatusEnum.ACTIVE));
        ReflectionTestUtils.setField(account, "status", status(current));
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        return account;
    }

    @Test
    @DisplayName("No expone setters de estado, saldo ni fecha de cierre: solo cambian con block, unlock y close")
    void noDeberiaExponerSettersDeEstadoSaldoNiCierre() {
        assertThat(Arrays.stream(Account.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Nested
    @DisplayName("open")
    class Open {

        @Test
        @DisplayName("Nace activa, sin saldo, sin fecha de cierre y con los datos recibidos")
        void deberiaNacerActivaYSinSaldo() {
            UUID customerId = UUID.randomUUID();

            Account account = Account.open(customerId, "123456789012", "@AN1231", (short) 2, savings(),
                    status(AccountStatusEnum.ACTIVE));

            assertThat(account.is(AccountStatusEnum.ACTIVE)).isTrue();
            assertThat(account.getBalance()).isEqualTo(new BigDecimal("0.00"));
            assertThat(account.getClosedAt()).isNull();
            assertThat(account.getCustomerId()).isEqualTo(customerId);
            assertThat(account.getAccountNumber()).isEqualTo("123456789012");
            assertThat(account.getAccountKey()).isEqualTo("@AN1231");
            assertThat(account.getAccountIndex()).isEqualTo((short) 2);
        }

        @Test
        @DisplayName("Rechaza abrirla con un estado distinto de ACTIVE")
        void deberiaRechazarUnEstadoInicialQueNoSeaActive() {
            AccountStatus blocked = status(AccountStatusEnum.BLOCKED);

            assertThatThrownBy(() -> Account.open(UUID.randomUUID(), "123456789012", "@AN1231", (short) 1, savings(), blocked))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("block")
    class Block {

        @Test
        @DisplayName("Bloquea una cuenta activa y conserva su saldo")
        void deberiaBloquearUnaCuentaActiva() {
            Account account = account(AccountStatusEnum.ACTIVE, "150.75");

            account.block(status(AccountStatusEnum.BLOCKED));

            assertThat(account.is(AccountStatusEnum.BLOCKED)).isTrue();
            assertThat(account.getBalance()).isEqualByComparingTo("150.75");
        }

        @Test
        @DisplayName("Una cuenta ya bloqueada se rechaza (CA02)")
        void deberiaRechazarUnaCuentaYaBloqueada() {
            Account account = account(AccountStatusEnum.BLOCKED, "0");

            assertThatThrownBy(() -> account.block(status(AccountStatusEnum.BLOCKED)))
                    .isInstanceOf(AccountNotBlockableException.class)
                    .hasMessageContaining("ya está bloqueada");
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatusEnum.class, names = {"INACTIVE", "CLOSED"})
        @DisplayName("Una cuenta inactiva o cerrada no se puede bloquear y queda como estaba")
        void deberiaRechazarUnaCuentaQueNoEstaActiva(AccountStatusEnum current) {
            Account account = account(current, "0");

            assertThatThrownBy(() -> account.block(status(AccountStatusEnum.BLOCKED)))
                    .isInstanceOf(AccountNotBlockableException.class)
                    .hasMessageContaining(current.name());
            assertThat(account.is(current)).isTrue();
        }

        @Test
        @DisplayName("Rechaza que le pasen un estado que no es BLOCKED")
        void deberiaRechazarUnEstadoDestinoEquivocado() {
            Account account = account(AccountStatusEnum.ACTIVE, "0");

            assertThatThrownBy(() -> account.block(status(AccountStatusEnum.CLOSED)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(account.is(AccountStatusEnum.ACTIVE)).isTrue();
        }
    }

    @Nested
    @DisplayName("unlock")
    class Unlock {

        @Test
        @DisplayName("Devuelve a ACTIVE una cuenta bloqueada")
        void deberiaActivarUnaCuentaBloqueada() {
            Account account = account(AccountStatusEnum.BLOCKED, "10.00");

            account.unlock(status(AccountStatusEnum.ACTIVE));

            assertThat(account.is(AccountStatusEnum.ACTIVE)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatusEnum.class, names = "BLOCKED", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Una cuenta que no está bloqueada no se puede desbloquear")
        void deberiaRechazarUnaCuentaQueNoEstaBloqueada(AccountStatusEnum current) {
            Account account = account(current, "0");

            assertThatThrownBy(() -> account.unlock(status(AccountStatusEnum.ACTIVE)))
                    .isInstanceOf(AccountNotUnblockableException.class)
                    .hasMessageContaining(current.name());
            assertThat(account.is(current)).isTrue();
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @ParameterizedTest
        @EnumSource(value = AccountStatusEnum.class, names = {"ACTIVE", "INACTIVE"})
        @DisplayName("Cierra una cuenta activa o inactiva con saldo cero y deja la fecha de cierre")
        void deberiaCerrarUnaCuentaSinSaldo(AccountStatusEnum current) {
            Account account = account(current, "0");
            LocalDateTime when = LocalDateTime.now();

            account.close(status(AccountStatusEnum.CLOSED), when);

            assertThat(account.is(AccountStatusEnum.CLOSED)).isTrue();
            assertThat(account.getClosedAt()).isEqualTo(when);
        }

        @Test
        @DisplayName("Con saldo, aunque sea un centavo, no se cierra")
        void deberiaRechazarUnaCuentaConSaldo() {
            Account account = account(AccountStatusEnum.ACTIVE, "0.01");

            assertThatThrownBy(() -> account.close(status(AccountStatusEnum.CLOSED), LocalDateTime.now()))
                    .isInstanceOf(AccountNotClosableException.class)
                    .hasMessageContaining("saldo");
            assertThat(account.is(AccountStatusEnum.ACTIVE)).isTrue();
            assertThat(account.getClosedAt()).isNull();
        }

        @Test
        @DisplayName("Bloqueada no se cierra: primero hay que desbloquearla")
        void deberiaRechazarUnaCuentaBloqueada() {
            Account account = account(AccountStatusEnum.BLOCKED, "0");

            assertThatThrownBy(() -> account.close(status(AccountStatusEnum.CLOSED), LocalDateTime.now()))
                    .isInstanceOf(AccountNotClosableException.class)
                    .hasMessageContaining("bloqueada");
        }

        @Test
        @DisplayName("Una cuenta ya cerrada no se cierra otra vez ni cambia su fecha de cierre")
        void deberiaRechazarUnaCuentaYaCerrada() {
            Account account = account(AccountStatusEnum.ACTIVE, "0");
            LocalDateTime first = LocalDateTime.now().minusDays(1);
            account.close(status(AccountStatusEnum.CLOSED), first);

            assertThatThrownBy(() -> account.close(status(AccountStatusEnum.CLOSED), LocalDateTime.now()))
                    .isInstanceOf(AccountNotClosableException.class)
                    .hasMessageContaining("ya está cerrada");
            assertThat(account.getClosedAt()).isEqualTo(first);
        }

        @Test
        @DisplayName("Rechaza que le pasen un estado que no es CLOSED")
        void deberiaRechazarUnEstadoDestinoEquivocado() {
            Account account = account(AccountStatusEnum.ACTIVE, "0");

            assertThatThrownBy(() -> account.close(status(AccountStatusEnum.BLOCKED), LocalDateTime.now()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(account.getClosedAt()).isNull();
        }
    }
}

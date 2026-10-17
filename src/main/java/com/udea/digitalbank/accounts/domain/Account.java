package com.udea.digitalbank.accounts.domain;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.shared.exception.accounts.AccountNotBlockableException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotActiveException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotClosableException;
import com.udea.digitalbank.shared.exception.accounts.InsufficientFundsException;
import com.udea.digitalbank.shared.exception.accounts.AccountNotUnblockableException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cuenta de un cliente. Los datos del titular viven en customer: aquí solo su id.
 * <p>
 * Es la dueña de sus reglas: el estado y el saldo no tienen setters, solo cambian con {@link #block},
 * {@link #unlock}, {@link #close}, {@link #debit} y {@link #credit}, que validan que el cambio sea posible. La cuenta nace con {@link #open}.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor
public class Account {

    public static final BigDecimal INITIAL_BALANCE = new BigDecimal("0.00");

    public static final int ACCOUNT_NUMBER_LENGTH = 12;

    // El consecutivo de la llave puede crecer, así que la columna deja margen de sobra
    public static final int MAX_KEY_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificador público de la cuenta: 12 dígitos (CHAR(12)), único e inmutable
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, updatable = false, length = ACCOUNT_NUMBER_LENGTH)
    private String accountNumber;

    /**
     * Llave visible de la cuenta, con la estructura de CA01: arroba, dos letras del primer nombre,
     * tres dígitos del documento y un consecutivo. Se calcula una sola vez al abrir la cuenta y no
     * se recalcula después: si el cliente corrige su nombre, la llave de una cuenta existente no cambia.
     */
    @Column(nullable = false, unique = true, updatable = false, length = MAX_KEY_LENGTH)
    private String accountKey;

    // Sin @ManyToOne hacia customer para mantener los módulos desacoplados
    @Column(nullable = false, updatable = false)
    private UUID customerId;

    // Posición dentro del cupo del titular (1 a 3): junto con el UNIQUE parcial (customer_id, account_index)
    // de la base, que solo mira las cuentas no cerradas, impide una cuarta cuenta abierta incluso con dos
    // aperturas simultáneas. Una cuenta cerrada libera su posición para la siguiente
    @Column(nullable = false, updatable = false)
    private Short accountIndex;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_type_id", nullable = false, updatable = false)
    private AccountType accountType;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private AccountStatus status;

    // Con la escala de la columna desde el principio: así el saldo se serializa igual
    // recién creada la cuenta ("0.00") que leída de la base, y no como "0"
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = INITIAL_BALANCE;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Solo tiene valor mientras la cuenta está CLOSED: un CHECK de la base lo exige en ambos sentidos
    private LocalDateTime closedAt;

    /**
     * Control optimista de concurrencia: Hibernate la incrementa en cada UPDATE y rechaza una escritura hecha sobre
     * una versión vieja. Protege los cambios que no leen la fila con bloqueo, como los de saldo del módulo de
     * transacciones; bloquear, desbloquear y cerrar además la leen con bloqueo pesimista.
     */
    @Version
    private Long version;

    /** Una cuenta nueva: activa y sin saldo, porque la apertura no exige depósito inicial. */
    public static Account open(UUID customerId, String accountNumber, String accountKey, short accountIndex,
                               AccountType accountType, AccountStatus activeStatus) {
        requireTarget(activeStatus, AccountStatusEnum.ACTIVE);
        Account account = new Account();
        account.customerId = customerId;
        account.accountNumber = accountNumber;
        account.accountKey = accountKey;
        account.accountIndex = accountIndex;
        account.accountType = accountType;
        account.status = activeStatus;
        return account;
    }

    public boolean is(AccountStatusEnum expected) {
        return status.is(expected);
    }

    /** Bloqueo temporal (CA01): solo una cuenta activa; una ya bloqueada se rechaza por CA02. */
    public void block(AccountStatus blockedStatus) {
        if (is(AccountStatusEnum.BLOCKED)) {
            throw new AccountNotBlockableException("La cuenta " + accountNumber + " ya está bloqueada");
        }
        if (!is(AccountStatusEnum.ACTIVE)) {
            throw new AccountNotBlockableException("Solo se puede bloquear una cuenta activa; la cuenta "
                    + accountNumber + " está en estado " + status.getCode());
        }
        requireTarget(blockedStatus, AccountStatusEnum.BLOCKED);
        status = blockedStatus;
    }

    /** Desbloqueo: solo una cuenta bloqueada, para no reactivar por accidente una que no lo estaba. */
    public void unlock(AccountStatus activeStatus) {
        if (!is(AccountStatusEnum.BLOCKED)) {
            throw new AccountNotUnblockableException("Solo se puede desbloquear una cuenta bloqueada; la cuenta "
                    + accountNumber + " está en estado " + status.getCode());
        }
        requireTarget(activeStatus, AccountStatusEnum.ACTIVE);
        status = activeStatus;
    }

    /**
     * Cierre: solo una cuenta ACTIVE o INACTIVE y con saldo cero. Con dinero hay que retirarlo antes, y una
     * bloqueada hay que desbloquearla primero, para que cerrarla no sirva para esquivar un bloqueo. Deja el estado
     * y la fecha de cierre a la vez, porque la base exige las dos cosas juntas.
     */
    public void close(AccountStatus closedStatus, LocalDateTime closedAt) {
        if (is(AccountStatusEnum.CLOSED)) {
            throw new AccountNotClosableException("La cuenta " + accountNumber + " ya está cerrada");
        }
        if (is(AccountStatusEnum.BLOCKED)) {
            throw new AccountNotClosableException(
                    "La cuenta " + accountNumber + " está bloqueada; hay que desbloquearla antes de cerrarla");
        }
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new AccountNotClosableException(
                    "La cuenta " + accountNumber + " tiene saldo; debe estar en cero para poder cerrarla");
        }
        requireTarget(closedStatus, AccountStatusEnum.CLOSED);
        this.status = closedStatus;
        this.closedAt = closedAt;
    }

    /**
     * Retiro de saldo por una transferencia: solo una cuenta ACTIVE y con saldo suficiente. El saldo nunca queda
     * negativo (la base también lo exige con un CHECK). Quien la llama debe haberla leído con bloqueo.
     */
    public void debit(BigDecimal amount) {
        requireCanDebit(amount);
        balance = balance.subtract(amount);
    }

    /** Abono de saldo por una transferencia: solo a una cuenta ACTIVE. */
    public void credit(BigDecimal amount) {
        requireCanCredit(amount);
        balance = balance.add(amount);
    }

    /**
     * Lo que debe cumplirse para retirar el monto, sin cambiar nada. Existe aparte para que una transferencia
     * valide las dos cuentas antes de mover dinero en cualquiera: si la segunda falla, la primera no queda a medias.
     */
    public void requireCanDebit(BigDecimal amount) {
        requirePositive(amount);
        requireActiveForMovement();
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Fondos insuficientes en la cuenta " + accountNumber);
        }
    }

    /** Lo que debe cumplirse para abonar el monto, sin cambiar nada. */
    public void requireCanCredit(BigDecimal amount) {
        requirePositive(amount);
        requireActiveForMovement();
    }

    // Una cuenta inactiva, bloqueada o cerrada no mueve dinero, ni para entregarlo ni para recibirlo
    private void requireActiveForMovement() {
        if (!is(AccountStatusEnum.ACTIVE)) {
            throw new AccountNotActiveException("La cuenta " + accountNumber + " no está activa; su estado es "
                    + status.getCode());
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto de un movimiento debe ser positivo");
        }
    }

    // El estado que se pasa debe ser el del cambio que se pide: evita dejar una cuenta en un estado equivocado
    private static void requireTarget(AccountStatus target, AccountStatusEnum expected) {
        if (target == null || !target.is(expected)) {
            throw new IllegalArgumentException("Se esperaba el estado " + expected + " del catálogo");
        }
    }
}

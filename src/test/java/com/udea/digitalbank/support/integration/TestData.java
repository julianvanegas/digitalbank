package com.udea.digitalbank.support.integration;

import com.udea.digitalbank.accounts.domain.Account;
import com.udea.digitalbank.accounts.domain.AccountStatus;
import com.udea.digitalbank.accounts.domain.AccountType;
import com.udea.digitalbank.auth.domain.AuthSession;
import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.domain.VerificationChallenge;
import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.customer.domain.DocumentType;
import com.udea.digitalbank.transactions.domain.Transaction;
import com.udea.digitalbank.transactions.domain.TransactionStatus;
import com.udea.digitalbank.transactions.domain.TransactionType;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Crea filas válidas para las pruebas de repositorios. Los catálogos no se crean: se leen de los que siembran las
 * migraciones, con los ids fijos que estas declaran.
 */
public final class TestData {

    public static final LocalDateTime T0 = MutableClock.START;

    // Ids fijos de los catálogos (V1, V2, V4 y V5)
    public static final short CUSTOMER = 1;
    public static final short ADMIN = 2;
    public static final short ACTIVE = 1;
    public static final short PENDING_VERIFICATION = 2;
    public static final short INACTIVE = 3;
    public static final short LOGIN = 1;
    public static final short PASSWORD_RESET = 3;
    public static final short CEDULA = 1;
    public static final short PASAPORTE = 3;
    public static final short SAVINGS = 1;
    public static final short ACCOUNT_ACTIVE = 1;
    public static final short TRANSFER = 1;
    public static final short DEPOSIT = 2;
    public static final short WITHDRAWAL = 3;
    public static final short PENDING = 1;
    public static final short COMPLETED = 2;
    public static final short REJECTED = 3;

    private final TestEntityManager em;
    private int sequence;

    TestData(TestEntityManager em) {
        this.em = em;
    }

    // ============ CATÁLOGOS ============

    public UserStatus userStatus(short id) {
        return em.find(UserStatus.class, id);
    }

    public TransactionType transactionType(short id) {
        return em.find(TransactionType.class, id);
    }

    public TransactionStatus transactionStatus(short id) {
        return em.find(TransactionStatus.class, id);
    }

    // ============ AUTH ============

    public User customerUser(String email) {
        return user(email, CUSTOMER, ACTIVE, T0, null, T0);
    }

    public User user(String email, short roleId, short statusId, LocalDateTime createdAt,
                     LocalDateTime lastLoginAt, LocalDateTime statusChangedAt) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$" + "x".repeat(53));
        user.setPasswordChangedAt(createdAt);
        user.setRole(em.find(Role.class, roleId));
        user.setStatus(userStatus(statusId));
        user.setCreatedAt(createdAt);
        user.setLastLoginAt(lastLoginAt);
        user.setStatusChangedAt(statusChangedAt);
        return em.persistAndFlush(user);
    }

    public AuthSession session(User user, UUID jti, LocalDateTime expiresAt) {
        return em.persistAndFlush(new AuthSession(user.getId(), jti, expiresAt.minusMinutes(30), expiresAt));
    }

    public VerificationChallenge challenge(User user, short purposeId, LocalDateTime expiresAt) {
        VerificationChallenge challenge = new VerificationChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setUserId(user.getId());
        challenge.setPurpose(em.find(ChallengePurpose.class, purposeId));
        challenge.setCodeHash("a".repeat(64));
        challenge.setExpiresAt(expiresAt);
        return em.persistAndFlush(challenge);
    }

    // ============ CUSTOMER ============

    public Customer customer(String email) {
        return customer(customerUser(email), CEDULA, String.valueOf(1_000_000 + next()));
    }

    public Customer customer(User user, short documentTypeId, String documentNumber) {
        Customer customer = new Customer();
        customer.setUserId(user.getId());
        customer.setFirstNames("Ana María");
        customer.setLastNames("Pérez Gómez");
        customer.setDocumentType(em.find(DocumentType.class, documentTypeId));
        customer.setDocumentNumber(documentNumber);
        customer.setPhone("+573001234567");
        customer.setBirthDate(LocalDate.of(1990, 5, 17));
        return em.persistAndFlush(customer);
    }

    // ============ ACCOUNTS ============

    public Account account(Customer customer) {
        int n = next();
        return account(customer, String.valueOf(100_000_000_000L + n), "@AN123" + n, (short) 1);
    }

    public Account account(Customer customer, String accountNumber, String accountKey, short accountIndex) {
        Account account = Account.open(customer.getId(), accountNumber, accountKey, accountIndex,
                em.find(AccountType.class, SAVINGS), em.find(AccountStatus.class, ACCOUNT_ACTIVE));
        return em.persistAndFlush(account);
    }

    // ============ TRANSACTIONS ============

    public Transaction transfer(Customer originator, Account from, Account to, String amount,
                                short statusId, LocalDateTime createdAt) {
        Transaction transaction = Transaction.transfer(UUID.randomUUID(), originator.getUserId(), from.getId(),
                to.getId(), new BigDecimal(amount), reference("TRF"), transactionType(TRANSFER),
                transactionStatus(PENDING), createdAt);
        return persistIn(transaction, statusId);
    }

    public Transaction withdrawal(Customer originator, Account from, String amount,
                                  short statusId, LocalDateTime createdAt) {
        Transaction transaction = Transaction.withdrawal(UUID.randomUUID(), originator.getUserId(), from.getId(),
                new BigDecimal(amount), reference("RET"), transactionType(WITHDRAWAL), transactionStatus(PENDING),
                createdAt);
        return persistIn(transaction, statusId);
    }

    public Transaction deposit(User admin, Account to, String amount, LocalDateTime createdAt) {
        Transaction transaction = Transaction.deposit(UUID.randomUUID(), admin.getId(), to.getId(),
                new BigDecimal(amount), reference("DEP"), transactionType(DEPOSIT), transactionStatus(COMPLETED),
                createdAt);
        return em.persistAndFlush(transaction);
    }

    private Transaction persistIn(Transaction pending, short statusId) {
        if (statusId == COMPLETED) {
            pending.complete(transactionStatus(COMPLETED));
        } else if (statusId == REJECTED) {
            pending.reject(transactionStatus(REJECTED));
        }
        return em.persistAndFlush(pending);
    }

    private String reference(String prefix) {
        return prefix + "-20261123-" + UUID.randomUUID().toString().replace("-", "");
    }

    private int next() {
        return ++sequence;
    }
}

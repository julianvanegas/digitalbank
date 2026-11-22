package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.api.AccountView;
import com.udea.digitalbank.shared.exception.transactions.TransactionLimitExceededException;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.repository.TransactionLimitRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Cumplimiento de los topes transaccionales: lo usa quien mueve dinero de un cliente para comprobar que la operación
 * los respeta. La consulta de los topes vigentes es de {@link TransactionLimitQueryService}.
 *
 * <p><b>Qué se limita.</b> Cada fila de {@code transaction_limits} es una combinación de tipo de movimiento y tipo de
 * cuenta. El consumo se acumula por <b>cuenta de origen</b> y por día calendario (en la zona del {@link Clock}), y solo
 * cuenta las transacciones {@code COMPLETED} (ver {@link DailyUsage}). Un máximo {@code null} significa que ese tope no
 * existe.
 *
 * <p><b>Cómo se hacen cumplir.</b> Cada tope es una {@link LimitRule}; este servicio solo elige el tope que aplica y
 * recorre las reglas que están definidas para él.
 *
 * <p><b>A quién se aplican.</b> Solo a los clientes: quien llama decide si los exige (en transferencias, mediante
 * {@link TransferParties}). Un movimiento de un administrador no los consulta, pero sí cuenta en el consumo de la
 * cuenta, porque el consumo se mide sobre la cuenta y no sobre quien ordena.
 */
@Service
public class TransactionLimitService {

    /** Tipos de movimiento que deben tener topes para cada tipo de cuenta; la consignación no tiene. */
    private static final Set<TransactionTypeEnum> LIMITED_TYPES =
            EnumSet.of(TransactionTypeEnum.TRANSFER, TransactionTypeEnum.WITHDRAWAL);

    private final TransactionLimitRepository limitRepository;
    private final List<LimitRule> rules;
    private final Clock clock;

    TransactionLimitService(TransactionLimitRepository limitRepository, List<LimitRule> rules, Clock clock) {
        this.limitRepository = limitRepository;
        this.rules = rules;
        this.clock = clock;
    }

    /**
     * Comprueba al arrancar que existan topes para cada tipo de movimiento limitado y cada tipo de cuenta. Si falta
     * una combinación la aplicación no inicia: de lo contrario una transferencia desde ese tipo de cuenta pasaría sin
     * tope por un olvido de configuración.
     */
    @PostConstruct
    void validate() {
        List<String> missing = LIMITED_TYPES.stream()
                .flatMap(type -> Arrays.stream(AccountTypeEnum.values())
                        .filter(accountType -> limitRepository
                                .findByTypeAndAccountType(type.name(), accountType.name()).isEmpty())
                        .map(accountType -> type + " / " + accountType))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Faltan topes en transaction_limits para: " + missing);
        }
    }

    /**
     * Exige que un movimiento de un cliente respete los topes de su cuenta de origen: el máximo por operación, la
     * cantidad diaria y el acumulado diario, cada uno como una {@link LimitRule}. No hace nada para un tope que no
     * está definido.
     *
     * @param sender cuenta de la que sale el dinero
     * @param type   tipo de movimiento
     * @param amount monto del movimiento
     * @throws TransactionLimitExceededException si el movimiento supera alguno de los topes
     * @throws IllegalStateException             si el tipo de movimiento es limitado pero no hay tope para el tipo de
     *                                           cuenta, lo que el arranque ya habría impedido
     */
    @Transactional(readOnly = true)
    public void requireWithinLimits(AccountView sender, TransactionTypeEnum type, BigDecimal amount) {
        TransactionLimit limit = limitRepository.findByTypeAndAccountType(type.name(), sender.accountType())
                .orElseThrow(() -> new IllegalStateException(
                        "No hay topes para " + type + " en cuentas " + sender.accountType()));

        LocalDateTime from = LocalDate.now(clock).atStartOfDay();
        LimitContext context = new LimitContext(limit, sender, type, amount, from, from.plusDays(1));
        rules.stream()
                .filter(rule -> rule.appliesTo(limit))
                .forEach(rule -> rule.check(context));
    }
}

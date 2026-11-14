package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.exception.transactions.LimitsNotDefinedException;
import com.udea.digitalbank.transactions.domain.TransactionLimit;
import com.udea.digitalbank.transactions.dto.TransactionLimitResponse;
import com.udea.digitalbank.transactions.repository.TransactionLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Consulta de los topes transaccionales vigentes, de solo lectura. Cualquier usuario autenticado puede conocerlos;
 * hacerlos cumplir es de {@link TransactionLimitService}.
 *
 * <p>Segundo factor: toda operación monetaria lo exige (ADR-0004), por lo que no es un dato configurable y la respuesta
 * siempre lo informa.
 */
@Service
public class TransactionLimitQueryService {

    private final TransactionLimitRepository limitRepository;

    TransactionLimitQueryService(TransactionLimitRepository limitRepository) {
        this.limitRepository = limitRepository;
    }

    /**
     * Devuelve los topes vigentes. No modifica ninguna regla.
     *
     * @param type        tipo de movimiento ({@code TRANSFER}, {@code WITHDRAWAL}); {@code null} o vacío para todos
     * @param accountType tipo de cuenta ({@code SAVINGS}, {@code CHECKING}); {@code null} o vacío para todos
     * @return los topes de cada combinación pedida, ordenados por tipo de movimiento y de cuenta
     * @throws LimitsNotDefinedException si el tipo de movimiento o de cuenta pedido no tiene topes definidos
     */
    @Transactional(readOnly = true)
    public List<TransactionLimitResponse> getLimits(String type, String accountType) {
        String typeFilter = normalize(type);
        String accountTypeFilter = normalize(accountType);
        Function<TransactionLimit, String> typeOf = l -> l.getType().getCode();
        Function<TransactionLimit, String> accountTypeOf = TransactionLimit::getAccountTypeCode;
        List<TransactionLimit> all = limitRepository.findAllOrdered();

        requireDefined(all, typeOf, typeFilter, "el tipo de operación ");
        requireDefined(all, accountTypeOf, accountTypeFilter, "el tipo de cuenta ");
        return all.stream()
                .filter(matching(typeOf, typeFilter))
                .filter(matching(accountTypeOf, accountTypeFilter))
                .map(TransactionLimitQueryService::toResponse)
                .toList();
    }

    // Un filtro indicado debe existir entre los topes definidos; si no, ese tipo no tiene topes
    private static void requireDefined(List<TransactionLimit> all, Function<TransactionLimit, String> field,
                                       String filter, String label) {
        if (filter != null && all.stream().map(field).noneMatch(filter::equals)) {
            throw new LimitsNotDefinedException("No existen topes definidos para " + label + filter);
        }
    }

    // Sin filtro, cualquier fila sirve
    private static Predicate<TransactionLimit> matching(Function<TransactionLimit, String> field, String filter) {
        return limit -> filter == null || filter.equals(field.apply(limit));
    }

    private static TransactionLimitResponse toResponse(TransactionLimit limit) {
        TransactionLimitResponse response = new TransactionLimitResponse();
        response.setType(limit.getType().getCode());
        response.setAccountType(limit.getAccountTypeCode());
        response.setCurrency(LimitRule.CURRENCY);
        response.setMaxDailyCount(limit.getMaxDailyCount());
        response.setMaxPerTransaction(limit.getMaxPerTransaction());
        response.setMaxDailyAmount(limit.getMaxDailyAmount());
        response.setRequiresTwoFactor(true);
        return response;
    }

    // Un filtro vacío equivale a no indicarlo; el resto se compara sin importar mayúsculas
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}

package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.domain.AccountStatus;
import com.udea.digitalbank.accounts.repository.AccountStatusRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de estados de cuenta cargado en memoria al arrancar, igual que el de auth: la base es la
 * fuente del contenido y AccountStatusEnum es el contrato del código. Si no coinciden, la app no arranca.
 */
@Component
class AccountCatalog {

    private final AccountStatusRepository statusRepository;
    private final Map<String, AccountStatus> statuses = new HashMap<>();

    AccountCatalog(AccountStatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    @PostConstruct
    void load() {
        statusRepository.findAll().forEach(s -> statuses.put(s.getCode(), s));
        validate();
    }

    AccountStatus status(AccountStatusEnum statusEnum) {
        AccountStatus status = statuses.get(statusEnum.name());
        if (status == null) {
            throw new IllegalArgumentException("Estado de cuenta no válido: " + statusEnum);
        }
        return status;
    }

    // Comprueba en ambos sentidos: cada valor del enum debe estar en la base y cada fila de la base en el enum
    private void validate() {
        Set<String> inCode = Arrays.stream(AccountStatusEnum.values())
                .map(Enum::name).collect(Collectors.toSet());
        Set<String> missingInDb = new HashSet<>(inCode);
        missingInDb.removeAll(statuses.keySet());
        Set<String> missingInCode = new HashSet<>(statuses.keySet());
        missingInCode.removeAll(inCode);
        if (!missingInDb.isEmpty() || !missingInCode.isEmpty()) {
            throw new IllegalStateException("AccountStatusEnum no coincide con la tabla account_status: "
                    + "faltan en la base " + missingInDb + ", faltan en el enum " + missingInCode);
        }
    }
}

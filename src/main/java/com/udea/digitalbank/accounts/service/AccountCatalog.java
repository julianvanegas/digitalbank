package com.udea.digitalbank.accounts.service;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import com.udea.digitalbank.accounts.api.AccountTypeEnum;
import com.udea.digitalbank.accounts.domain.AccountStatus;
import com.udea.digitalbank.accounts.domain.AccountType;
import com.udea.digitalbank.accounts.repository.AccountStatusRepository;
import com.udea.digitalbank.accounts.repository.AccountTypeRepository;
import com.udea.digitalbank.shared.catalog.CatalogValidator;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogos de estados y tipos de cuenta cargados en memoria al arrancar, igual que el de auth: la base es
 * la fuente del contenido y los enums son el contrato del código. Si no coinciden, la app no arranca.
 */
@Component
class AccountCatalog {

    private final AccountStatusRepository statusRepository;
    private final AccountTypeRepository typeRepository;
    private final Map<String, AccountStatus> statuses = new HashMap<>();
    private final Map<String, AccountType> types = new HashMap<>();

    AccountCatalog(AccountStatusRepository statusRepository, AccountTypeRepository typeRepository) {
        this.statusRepository = statusRepository;
        this.typeRepository = typeRepository;
    }

    @PostConstruct
    void load() {
        statusRepository.findAll().forEach(s -> statuses.put(s.getCode(), s));
        typeRepository.findAll().forEach(t -> types.put(t.getCode(), t));
        validate();
    }

    AccountStatus status(AccountStatusEnum statusEnum) {
        AccountStatus status = statuses.get(statusEnum.name());
        if (status == null) {
            throw new IllegalStateException("Estado de cuenta no válido: " + statusEnum);
        }
        return status;
    }

    AccountType type(AccountTypeEnum typeEnum) {
        AccountType type = types.get(typeEnum.name());
        if (type == null) {
            throw new IllegalStateException("Tipo de cuenta no válido: " + typeEnum);
        }
        return type;
    }

    // Cada valor del enum debe estar en la base y cada fila de la base en el enum
    private void validate() {
        List<String> problems = new ArrayList<>();
        CatalogValidator.compare(AccountStatusEnum.class, statuses.keySet(), problems);
        CatalogValidator.compare(AccountTypeEnum.class, types.keySet(), problems);
        CatalogValidator.requireNoProblems(problems);
    }
}

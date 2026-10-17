package com.udea.digitalbank.transactions.service;

import com.udea.digitalbank.shared.catalog.CatalogValidator;
import com.udea.digitalbank.transactions.domain.TransactionStatus;
import com.udea.digitalbank.transactions.domain.TransactionStatusEnum;
import com.udea.digitalbank.transactions.domain.TransactionType;
import com.udea.digitalbank.transactions.domain.TransactionTypeEnum;
import com.udea.digitalbank.transactions.repository.TransactionStatusRepository;
import com.udea.digitalbank.transactions.repository.TransactionTypeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogos de tipos y estados de transacción, cargados en memoria al arrancar con el mismo mecanismo que los de
 * {@code auth} y {@code accounts}: la base es la fuente del contenido y los enums son el contrato del código. Si no
 * coinciden, la aplicación no arranca.
 */
@Component
class TransactionCatalog {

    private final TransactionStatusRepository statusRepository;
    private final TransactionTypeRepository typeRepository;
    private final Map<String, TransactionStatus> statuses = new HashMap<>();
    private final Map<String, TransactionType> types = new HashMap<>();

    TransactionCatalog(TransactionStatusRepository statusRepository, TransactionTypeRepository typeRepository) {
        this.statusRepository = statusRepository;
        this.typeRepository = typeRepository;
    }

    /** Carga ambos catálogos y valida que coincidan con los enums; falla el arranque si no es así. */
    @PostConstruct
    void load() {
        statusRepository.findAll().forEach(s -> statuses.put(s.getCode(), s));
        typeRepository.findAll().forEach(t -> types.put(t.getCode(), t));
        validate();
    }

    /**
     * @return la fila del catálogo correspondiente al estado
     * @throws IllegalStateException si el estado no está cargado
     */
    TransactionStatus status(TransactionStatusEnum statusEnum) {
        TransactionStatus status = statuses.get(statusEnum.name());
        if (status == null) {
            throw new IllegalStateException("Estado de transacción no válido: " + statusEnum);
        }
        return status;
    }

    /**
     * @return la fila del catálogo correspondiente al tipo
     * @throws IllegalStateException si el tipo no está cargado
     */
    TransactionType type(TransactionTypeEnum typeEnum) {
        TransactionType type = types.get(typeEnum.name());
        if (type == null) {
            throw new IllegalStateException("Tipo de transacción no válido: " + typeEnum);
        }
        return type;
    }

    // Cada valor del enum debe existir en la base y cada fila de la base, en el enum
    private void validate() {
        List<String> problems = new ArrayList<>();
        CatalogValidator.compare(TransactionStatusEnum.class, statuses.keySet(), problems);
        CatalogValidator.compare(TransactionTypeEnum.class, types.keySet(), problems);
        CatalogValidator.requireNoProblems(problems);
    }
}

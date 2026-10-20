package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.PurposeEnum;
import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.api.RoleEnum;
import com.udea.digitalbank.auth.domain.UserStatus;
import com.udea.digitalbank.auth.api.UserStatusEnum;
import com.udea.digitalbank.auth.repository.ChallengePurposeRepository;
import com.udea.digitalbank.auth.repository.RoleRepository;
import com.udea.digitalbank.auth.repository.UserStatusRepository;
import com.udea.digitalbank.shared.catalog.CatalogValidator;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogos de solo lectura cargados en memoria al arrancar. La base es la fuente del contenido y
 * los enums (RoleEnum, UserStatusEnum, ...) son el contrato del código. Si no coinciden, la app no arranca.
 */
@Component
public class Catalogs {

    private final RoleRepository roleRepository;
    private final UserStatusRepository statusRepository;
    private final ChallengePurposeRepository purposeRepository;

    private final Map<String, Role> roles = new HashMap<>();
    private final Map<String, UserStatus> statuses = new HashMap<>();
    private final Map<String, ChallengePurpose> purposes = new HashMap<>();

    public Catalogs(RoleRepository roleRepository,
                    UserStatusRepository statusRepository,
                    ChallengePurposeRepository purposeRepository) {
        this.roleRepository = roleRepository;
        this.statusRepository = statusRepository;
        this.purposeRepository = purposeRepository;
    }

    @PostConstruct
    void load() {
        roleRepository.findAll().forEach(r -> roles.put(r.getCode(), r));
        statusRepository.findAll().forEach(s -> statuses.put(s.getCode(), s));
        purposeRepository.findAll().forEach(p -> purposes.put(p.getPurpose(), p));
        validate();
    }

    public Role role(RoleEnum code) {
        return find(roles, code.name(), "Rol");
    }

    public UserStatus status(UserStatusEnum code) {
        return find(statuses, code.name(), "Estado");
    }

    public ChallengePurpose purpose(PurposeEnum code) {
        return find(purposes, code.name(), "Propósito de verificación");
    }

    private static <T> T find(Map<String, T> map, String key, String label) {
        T value = map.get(key);
        if (value == null) {
            throw new IllegalStateException(label + " no válido: " + key);
        }
        return value;
    }

    // Cada valor del enum debe estar en la base y cada fila de la base en el enum
    private void validate() {
        List<String> problems = new ArrayList<>();
        CatalogValidator.compare(RoleEnum.class, roles.keySet(), problems);
        CatalogValidator.compare(UserStatusEnum.class, statuses.keySet(), problems);
        CatalogValidator.compare(PurposeEnum.class, purposes.keySet(), problems);
        CatalogValidator.requireNoProblems(problems);
    }
}

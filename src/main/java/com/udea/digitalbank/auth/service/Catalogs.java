package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.Category;
import com.udea.digitalbank.auth.domain.CategoryCode;
import com.udea.digitalbank.auth.domain.ChallengePurpose;
import com.udea.digitalbank.auth.domain.PurposeCode;
import com.udea.digitalbank.auth.domain.Role;
import com.udea.digitalbank.auth.domain.RoleCode;
import com.udea.digitalbank.auth.domain.Status;
import com.udea.digitalbank.auth.domain.StatusCode;
import com.udea.digitalbank.auth.repository.CategoryRepository;
import com.udea.digitalbank.auth.repository.ChallengePurposeRepository;
import com.udea.digitalbank.auth.repository.RoleRepository;
import com.udea.digitalbank.auth.repository.StatusRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogos de solo lectura cargados en memoria al arrancar. La base es la fuente del contenido y
 * los enums (RoleCode, StatusCode, ...) son el contrato del código. Si no coinciden, la app no arranca.
 */
@Component
public class Catalogs {

    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final StatusRepository statusRepository;
    private final ChallengePurposeRepository purposeRepository;

    private final Map<String, Role> roles = new HashMap<>();
    private final Map<String, Category> categories = new HashMap<>();
    private final Map<String, Status> statuses = new HashMap<>();   // clave: categoría + "/" + estado
    private final Map<String, ChallengePurpose> purposes = new HashMap<>();

    public Catalogs(RoleRepository roleRepository,
                    CategoryRepository categoryRepository,
                    StatusRepository statusRepository,
                    ChallengePurposeRepository purposeRepository) {
        this.roleRepository = roleRepository;
        this.categoryRepository = categoryRepository;
        this.statusRepository = statusRepository;
        this.purposeRepository = purposeRepository;
    }

    @PostConstruct
    void load() {
        roleRepository.findAll().forEach(r -> roles.put(r.getCode(), r));
        categoryRepository.findAll().forEach(c -> categories.put(c.getCode(), c));
        statusRepository.findAll().forEach(s -> statuses.put(key(s.getCategory().getCode(), s.getCode()), s));
        purposeRepository.findAll().forEach(p -> purposes.put(p.getCode(), p));
        validate();
    }

    public Role role(RoleCode code) {
        return find(roles, code.name(), "Rol");
    }

    public Category category(CategoryCode code) {
        return find(categories, code.name(), "Categoría");
    }

    // Un estado solo es válido dentro de su categoría (p. ej. TERMINATED no existe para clientes)
    public Status status(CategoryCode category, StatusCode code) {
        return find(statuses, key(category.name(), code.name()), "Estado (categoría/código)");
    }

    public ChallengePurpose purpose(PurposeCode code) {
        return find(purposes, code.name(), "Propósito de verificación");
    }

    private static <T> T find(Map<String, T> map, String key, String label) {
        T value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(label + " no válido: " + key);
        }
        return value;
    }

    // Comprueba en ambos sentidos: cada valor del enum debe estar en la base y cada fila de la base en el enum
    private void validate() {
        Set<String> statusCodes = statuses.values().stream().map(Status::getCode).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        compare(RoleCode.class, roles.keySet(), problems);
        compare(CategoryCode.class, categories.keySet(), problems);
        compare(StatusCode.class, statusCodes, problems);
        compare(PurposeCode.class, purposes.keySet(), problems);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Los enums no coinciden con la base de datos: " + problems);
        }
    }

    private static <E extends Enum<E>> void compare(Class<E> enumType, Set<String> inDb, List<String> problems) {
        Set<String> inCode = Arrays.stream(enumType.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        Set<String> missingInDb = new HashSet<>(inCode);
        missingInDb.removeAll(inDb);
        Set<String> missingInCode = new HashSet<>(inDb);
        missingInCode.removeAll(inCode);
        missingInDb.forEach(v -> problems.add(enumType.getSimpleName() + "." + v + " no existe en la base"));
        missingInCode.forEach(v -> problems.add(enumType.getSimpleName() + " no declara '" + v + "', que sí está en la base"));
    }

    private static String key(String categoryCode, String code) {
        return categoryCode + "/" + code;
    }
}

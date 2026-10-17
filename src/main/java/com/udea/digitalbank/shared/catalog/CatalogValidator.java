package com.udea.digitalbank.shared.catalog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comprueba que un enum del código y su tabla de catálogo coincidan, en ambos sentidos: cada valor del enum debe
 * estar en la base y cada fila de la base en el enum. Lo usan los catálogos de cada módulo al arrancar; si algo no
 * coincide, la app no arranca. Acumula los problemas de todos los catálogos antes de fallar, para verlos juntos.
 */
public final class CatalogValidator {

    private CatalogValidator() {
    }

    /** Agrega a {@code problems} lo que no coincida entre el enum y los códigos que hay en la base. */
    public static <E extends Enum<E>> void compare(Class<E> enumType, Set<String> codesInDb, List<String> problems) {
        Set<String> inCode = Arrays.stream(enumType.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        Set<String> missingInDb = new HashSet<>(inCode);
        missingInDb.removeAll(codesInDb);
        Set<String> missingInCode = new HashSet<>(codesInDb);
        missingInCode.removeAll(inCode);
        missingInDb.stream().sorted().forEach(v ->
                problems.add(enumType.getSimpleName() + "." + v + " no existe en la base"));
        missingInCode.stream().sorted().forEach(v ->
                problems.add(enumType.getSimpleName() + " no declara '" + v + "', que sí está en la base"));
    }

    /** Falla con todos los problemas juntos, o no hace nada si no hay ninguno. */
    public static void requireNoProblems(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Los enums no coinciden con la base de datos: " + problems);
        }
    }
}

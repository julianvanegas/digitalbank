package com.udea.digitalbank.shared.utils;

import java.util.regex.Pattern;

/**
 * Normaliza los nombres de personas para que se guarden siempre igual: sin espacios al inicio ni al final y con un
 * solo espacio entre palabras. Solo toca los espacios y las tabulaciones; los saltos de línea se dejan para que la
 * validación del DTO los rechace, y las letras y tildes no se modifican.
 */
public final class Names {

    // Espacios y tabulaciones, sin saltos de línea
    private static final Pattern HORIZONTAL_SPACES = Pattern.compile("[^\\S\\r\\n\\u0085\\u2028\\u2029]+");

    private Names() {
    }

    /** Devuelve el nombre normalizado; un valor nulo sigue siendo nulo. */
    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String collapsed = HORIZONTAL_SPACES.matcher(name).replaceAll(" ");
        int start = collapsed.startsWith(" ") ? 1 : 0;
        int end = collapsed.length() > start && collapsed.endsWith(" ") ? collapsed.length() - 1 : collapsed.length();
        return collapsed.substring(start, end);
    }
}

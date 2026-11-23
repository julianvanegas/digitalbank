package co.edu.udea.certificacion.digitalbank.utils;

import java.util.Map;

/** Arma cuerpos JSON planos (texto, números y booleanos) sin depender de una librería de serialización. */
public final class Json {

    private Json() {
    }

    public static String objeto(Map<String, ?> campos) {
        StringBuilder json = new StringBuilder("{");
        boolean primero = true;
        for (Map.Entry<String, ?> campo : campos.entrySet()) {
            if (!primero) {
                json.append(',');
            }
            primero = false;
            json.append(texto(campo.getKey())).append(':').append(valor(campo.getValue()));
        }
        return json.append('}').toString();
    }

    private static String valor(Object valor) {
        if (valor == null) {
            return "null";
        }
        if (valor instanceof Number || valor instanceof Boolean) {
            return valor.toString();
        }
        return texto(valor.toString());
    }

    private static String texto(String texto) {
        StringBuilder escapado = new StringBuilder("\"");
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> escapado.append("\\\"");
                case '\\' -> escapado.append("\\\\");
                case '\n' -> escapado.append("\\n");
                case '\r' -> escapado.append("\\r");
                case '\t' -> escapado.append("\\t");
                default -> escapado.append(c);
            }
        }
        return escapado.append('"').toString();
    }
}

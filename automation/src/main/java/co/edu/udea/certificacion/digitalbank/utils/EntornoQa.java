package co.edu.udea.certificacion.digitalbank.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resuelve las variables de configuración de QA. Primero mira las variables de entorno del sistema (así un
 * pipeline o una terminal pueden sobrescribir) y, si no están, las lee del archivo {@code .env} del repositorio,
 * el mismo que usa el backend. Así no hace falta cargar el {@code .env} a mano antes de correr las pruebas.
 */
public final class EntornoQa {

    private static final int NIVELES_HACIA_ARRIBA = 4;
    private static final Map<String, String> ARCHIVO = cargarArchivo();

    private EntornoQa() {
    }

    /** Valor de la variable, o {@code null} si no está ni en el entorno ni en el {@code .env}. */
    public static String valor(String nombre) {
        String delEntorno = System.getenv(nombre);
        if (delEntorno != null && !delEntorno.isBlank()) {
            return delEntorno;
        }
        return ARCHIVO.get(nombre);
    }

    /** Interpreta líneas {@code CLAVE=VALOR}: ignora vacías y comentarios, y quita comillas alrededor del valor. */
    static Map<String, String> parsear(List<String> lineas) {
        Map<String, String> variables = new HashMap<>();
        for (String linea : lineas) {
            String texto = linea.trim();
            if (texto.isEmpty() || texto.startsWith("#")) {
                continue;
            }
            if (texto.startsWith("export ")) {
                texto = texto.substring("export ".length()).trim();
            }
            int igual = texto.indexOf('=');
            if (igual <= 0) {
                continue;
            }
            variables.put(texto.substring(0, igual).trim(), sinComillas(texto.substring(igual + 1).trim()));
        }
        return variables;
    }

    private static String sinComillas(String valor) {
        if (valor.length() >= 2) {
            char primero = valor.charAt(0);
            char ultimo = valor.charAt(valor.length() - 1);
            if ((primero == '"' || primero == '\'') && primero == ultimo) {
                return valor.substring(1, valor.length() - 1);
            }
        }
        return valor;
    }

    private static Map<String, String> cargarArchivo() {
        Path archivo = ubicarArchivo();
        if (archivo == null) {
            return Map.of();
        }
        try {
            return parsear(Files.readAllLines(archivo, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static Path ubicarArchivo() {
        String explicito = System.getenv("QA_ENV_FILE");
        if (explicito != null && !explicito.isBlank()) {
            Path ruta = Path.of(explicito);
            return Files.isRegularFile(ruta) ? ruta : null;
        }
        Path carpeta = Path.of("").toAbsolutePath();
        for (int nivel = 0; carpeta != null && nivel <= NIVELES_HACIA_ARRIBA; nivel++) {
            Path candidato = carpeta.resolve(".env");
            if (Files.isRegularFile(candidato)) {
                return candidato;
            }
            carpeta = carpeta.getParent();
        }
        return null;
    }
}

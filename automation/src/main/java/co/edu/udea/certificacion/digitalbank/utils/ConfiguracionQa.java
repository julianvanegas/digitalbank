package co.edu.udea.certificacion.digitalbank.utils;

/** Configuración del ambiente de QA: variables de entorno o archivo .env, con valores por defecto para el ambiente local. */
public final class ConfiguracionQa {

    private ConfiguracionQa() {
    }

    public static String urlBase() {
        String url = EntornoQa.valor("API_BASE_URL");
        if (url != null && !url.isBlank()) {
            return url;
        }
        return "http://localhost:" + conDefecto("SERVER_PORT", "8080");
    }

    public static String correoAdministrador() {
        return conDefecto("QA_ADMIN_EMAIL", "admin.qa@example.com");
    }

    public static String contrasenaAdministrador() {
        return conDefecto("QA_ADMIN_PASSWORD", "Admin#2026qa");
    }

    private static String conDefecto(String nombre, String defecto) {
        String valor = EntornoQa.valor(nombre);
        return valor == null || valor.isBlank() ? defecto : valor;
    }
}

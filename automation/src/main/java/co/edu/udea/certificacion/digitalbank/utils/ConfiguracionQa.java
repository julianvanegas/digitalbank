package co.edu.udea.certificacion.digitalbank.utils;

/** Configuración del ambiente de QA. Todo viene de variables de entorno, con valores por defecto para el ambiente local. */
public final class ConfiguracionQa {

    private ConfiguracionQa() {
    }

    public static String urlBase() {
        String url = System.getenv("API_BASE_URL");
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
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? defecto : valor;
    }
}

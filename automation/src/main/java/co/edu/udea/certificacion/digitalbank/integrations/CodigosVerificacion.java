package co.edu.udea.certificacion.digitalbank.integrations;

import co.edu.udea.certificacion.digitalbank.utils.CodigoHmac;
import co.edu.udea.certificacion.digitalbank.utils.EntornoQa;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Lee de la base de QA el código de verificación vigente de un usuario. Usa las mismas variables que el backend. */
public final class CodigosVerificacion {

    /** Los ids coinciden con la tabla challenge_purposes del backend. */
    public enum Proposito {
        LOGIN(1), EMAIL_CONFIRMATION(2), PASSWORD_RESET(3), TRANSACTION(4);

        private final short id;

        Proposito(int id) {
            this.id = (short) id;
        }

        public short id() {
            return id;
        }
    }

    private static final String CONSULTA = """
            SELECT c.user_id, c.code_hash
            FROM verification_challenges c
            JOIN users u ON u.id = c.user_id
            WHERE u.email = ? AND c.purpose_id = ?
            """;
    private static final int INTENTOS = 20;
    private static final long ESPERA_MS = 250;

    private CodigosVerificacion() {
    }

    public static String obtener(String email, Proposito proposito) {
        String secreto = requerida("VERIFICATION_HMAC_SECRET");
        String url = "jdbc:postgresql://" + conDefecto("DB_HOST", "localhost:5432")
                + "/" + conDefecto("DB_NAME", "bank_qa");
        try (Connection conexion = DriverManager.getConnection(
                url, conDefecto("DB_USERNAME", "postgres"), requerida("DB_PASSWORD"));
             PreparedStatement consulta = conexion.prepareStatement(CONSULTA)) {
            consulta.setString(1, email.toLowerCase());
            consulta.setShort(2, proposito.id());
            for (int intento = 1; intento <= INTENTOS; intento++) {
                try (ResultSet fila = consulta.executeQuery()) {
                    if (fila.next()) {
                        UUID usuarioId = fila.getObject("user_id", UUID.class);
                        return CodigoHmac.descubrirCodigo(secreto, usuarioId, proposito.id(), fila.getString("code_hash"))
                                .orElseThrow(() -> new IllegalStateException(
                                        "No se pudo recuperar el código; revisa que VERIFICATION_HMAC_SECRET sea el del backend"));
                    }
                }
                esperar();
            }
            throw new IllegalStateException("No hay un código " + proposito + " vigente para " + email);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer el código de la base de datos", e);
        }
    }

    private static void esperar() {
        try {
            Thread.sleep(ESPERA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera interrumpida", e);
        }
    }

    private static String requerida(String nombre) {
    String valor = EntornoQa.valor(nombre);
    if (valor == null || valor.isBlank()) {
        throw new IllegalStateException("Falta la variable " + nombre + " (en el entorno o en el archivo .env)");
    }
    return valor;
}

    private static String conDefecto(String nombre, String defecto) {
        String valor = EntornoQa.valor(nombre);
        return valor == null || valor.isBlank() ? defecto : valor;
    }
}
package co.edu.udea.certificacion.digitalbank.utils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Recupera el código de 6 dígitos de un reto de verificación a partir de su hash. El backend guarda
 * HMAC-SHA256(secreto, "usuarioId:propositoId:código"); como el código solo tiene un millón de combinaciones y en
 * QA conocemos el secreto, se puede recalcular. Solo sirve en el ambiente local de pruebas.
 */
public final class CodigoHmac {

    private static final String ALGORITMO = "HmacSHA256";
    private static final int CODIGO_DIGITOS = 6;

    private CodigoHmac() {
    }

    public static Optional<String> descubrirCodigo(String secreto, UUID usuarioId, short propositoId,
                                                   String hashEsperado) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            byte[] esperado = HexFormat.of().parseHex(hashEsperado);
            byte[] prefijo = (usuarioId + ":" + propositoId + ":").getBytes(StandardCharsets.UTF_8);
            byte[] mensaje = new byte[prefijo.length + CODIGO_DIGITOS];
            System.arraycopy(prefijo, 0, mensaje, 0, prefijo.length);
            for (int n = 0; n < 1_000_000; n++) {
                int resto = n;
                for (int i = CODIGO_DIGITOS - 1; i >= 0; i--) {
                    mensaje[prefijo.length + i] = (byte) ('0' + resto % 10);
                    resto /= 10;
                }
                if (Arrays.equals(mac.doFinal(mensaje), esperado)) {
                    return Optional.of(new String(mensaje, prefijo.length, CODIGO_DIGITOS, StandardCharsets.US_ASCII));
                }
            }
            return Optional.empty();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el HMAC del código", e);
        }
    }
}

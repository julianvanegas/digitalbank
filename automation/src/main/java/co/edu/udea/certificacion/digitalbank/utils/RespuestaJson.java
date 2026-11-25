package co.edu.udea.certificacion.digitalbank.utils;

import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import java.math.BigDecimal;
import net.serenitybdd.rest.SerenityRest;

/**
 * Lectura de campos de la última respuesta del servidor. Los números se leen como {@link BigDecimal}: con la
 * configuración por defecto, Rest Assured los convierte a float y un monto como 80000001.00 pierde precisión.
 */
public final class RespuestaJson {

    private static final JsonPathConfig DECIMALES_EXACTOS =
            JsonPathConfig.jsonPathConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL);

    private RespuestaJson() {
    }

    /** Valor del campo como texto, o null si la respuesta no lo trae. */
    public static String texto(String campo) {
        return cuerpo().getString(campo);
    }

    /** Valor numérico exacto del campo, o null si la respuesta no lo trae. */
    public static BigDecimal decimal(String campo) {
        String valor = cuerpo().getString(campo);
        return valor == null ? null : new BigDecimal(valor);
    }

    private static JsonPath cuerpo() {
        return SerenityRest.lastResponse().jsonPath(DECIMALES_EXACTOS);
    }
}

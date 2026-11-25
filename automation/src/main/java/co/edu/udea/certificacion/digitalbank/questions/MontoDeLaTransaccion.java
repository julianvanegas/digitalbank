package co.edu.udea.certificacion.digitalbank.questions;

import co.edu.udea.certificacion.digitalbank.utils.RespuestaJson;
import java.math.BigDecimal;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;

/** Monto exacto de la transacción en la última respuesta (comprobante de retiro, transferencia o consignación). */
public class MontoDeLaTransaccion implements Question<BigDecimal> {

    public static MontoDeLaTransaccion enLaRespuesta() {
        return new MontoDeLaTransaccion();
    }

    @Override
    public BigDecimal answeredBy(Actor actor) {
        return RespuestaJson.decimal("amount");
    }
}

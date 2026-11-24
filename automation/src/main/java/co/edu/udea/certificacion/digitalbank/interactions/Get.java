package co.edu.udea.certificacion.digitalbank.interactions;

import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Tasks;

public class Get extends PeticionHttp {

    public Get(String ruta, String token) {
        super(ruta, "", token);
    }

    public static Get de(String ruta, String token) {
        return Tasks.instrumented(Get.class, ruta, token);
    }

    @Override
    @Step("{0} envía GET a #ruta")
    public <T extends Actor> void performAs(T actor) {
        preparar().get(ruta);
    }
}

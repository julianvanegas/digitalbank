package co.edu.udea.certificacion.digitalbank.interactions;

import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Tasks;

public class Patch extends PeticionHttp {

    public Patch(String ruta, String cuerpo, String token) {
        super(ruta, cuerpo, token);
    }

    public static Patch en(String ruta, String cuerpo, String token) {
        return Tasks.instrumented(Patch.class, ruta, cuerpo, token);
    }

    public static Patch sinSesion(String ruta, String cuerpo) {
        return en(ruta, cuerpo, "");
    }

    @Override
    @Step("{0} envía PATCH a #ruta")
    public <T extends Actor> void performAs(T actor) {
        preparar().patch(ruta);
    }
}

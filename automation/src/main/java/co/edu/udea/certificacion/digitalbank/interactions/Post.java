package co.edu.udea.certificacion.digitalbank.interactions;

import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Tasks;

public class Post extends PeticionHttp {

    public Post(String ruta, String cuerpo, String token) {
        super(ruta, cuerpo, token);
    }

    public static Post en(String ruta, String cuerpo, String token) {
        return Tasks.instrumented(Post.class, ruta, cuerpo, token);
    }

    public static Post sinSesion(String ruta, String cuerpo) {
        return en(ruta, cuerpo, "");
    }

    @Override
    @Step("{0} envía POST a #ruta")
    public <T extends Actor> void performAs(T actor) {
        preparar().post(ruta);
    }
}

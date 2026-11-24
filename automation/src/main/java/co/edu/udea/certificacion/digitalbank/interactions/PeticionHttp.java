package co.edu.udea.certificacion.digitalbank.interactions;

import co.edu.udea.certificacion.digitalbank.utils.ConfiguracionQa;
import io.restassured.specification.RequestSpecification;
import net.serenitybdd.rest.SerenityRest;
import net.serenitybdd.screenplay.Interaction;

/** Base de las interacciones HTTP: arma la petición con la URL del ambiente, el JSON y, si hay, el token. */
public abstract class PeticionHttp implements Interaction {

    protected final String ruta;
    protected final String cuerpo;
    protected final String token;

    protected PeticionHttp(String ruta, String cuerpo, String token) {
        this.ruta = ruta;
        this.cuerpo = cuerpo;
        this.token = token;
    }

    protected RequestSpecification preparar() {
        RequestSpecification peticion = SerenityRest.given()
                .baseUri(ConfiguracionQa.urlBase())
                .accept("application/json")
                .contentType("application/json; charset=UTF-8");
        if (!token.isBlank()) {
            peticion = peticion.header("Authorization", "Bearer " + token);
        }
        if (!cuerpo.isBlank()) {
            peticion = peticion.body(cuerpo);
        }
        return peticion;
    }
}

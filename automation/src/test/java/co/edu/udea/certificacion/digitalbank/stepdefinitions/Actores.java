package co.edu.udea.certificacion.digitalbank.stepdefinitions;

import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.actors.OnStage;

/** Los actores de los escenarios. Cada uno guarda en su memoria sus datos y su token de sesión. */
final class Actores {

    private Actores() {
    }

    static Actor cliente() {
        return OnStage.theActorCalled("Cliente");
    }

    static Actor clienteExistente() {
        return OnStage.theActorCalled("Cliente existente");
    }

    static Actor administrador() {
        return OnStage.theActorCalled("Administrador");
    }
}

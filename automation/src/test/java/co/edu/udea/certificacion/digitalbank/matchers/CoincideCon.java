package co.edu.udea.certificacion.digitalbank.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/** Matcher de Hamcrest para validar el formato de un texto con una expresión regular (número de cuenta, llave). */
public final class CoincideCon extends TypeSafeMatcher<String> {

    private final String patron;

    private CoincideCon(String patron) {
        this.patron = patron;
    }

    public static Matcher<String> elPatron(String patron) {
        return new CoincideCon(patron);
    }

    @Override
    protected boolean matchesSafely(String texto) {
        return texto.matches(patron);
    }

    @Override
    public void describeTo(Description descripcion) {
        descripcion.appendText("un texto con el formato ").appendValue(patron);
    }
}

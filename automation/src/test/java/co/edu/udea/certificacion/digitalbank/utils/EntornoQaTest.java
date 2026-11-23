package co.edu.udea.certificacion.digitalbank.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class EntornoQaTest {

    @Test
    public void leeClavesYValores() {
        Map<String, String> variables = EntornoQa.parsear(List.of("DB_NAME=bank_qa", "  DB_HOST = localhost:5432  "));
        assertEquals("bank_qa", variables.get("DB_NAME"));
        assertEquals("localhost:5432", variables.get("DB_HOST"));
    }

    @Test
    public void ignoraComentariosYLineasVacias() {
        Map<String, String> variables = EntornoQa.parsear(List.of("# comentario", "", "   ", "SERVER_PORT=8080"));
        assertEquals(1, variables.size());
        assertEquals("8080", variables.get("SERVER_PORT"));
    }

    @Test
    public void quitaLasComillasQueRodeanElValor() {
        Map<String, String> variables = EntornoQa.parsear(List.of("A=\"con comillas\"", "B='simples'", "C=sin"));
        assertEquals("con comillas", variables.get("A"));
        assertEquals("simples", variables.get("B"));
        assertEquals("sin", variables.get("C"));
    }

    @Test
    public void conservaElSignoIgualDentroDelValorYNoCortaPorNumeral() {
        Map<String, String> variables = EntornoQa.parsear(List.of("SECRETO=abc=def#ghi"));
        assertEquals("abc=def#ghi", variables.get("SECRETO"));
    }

    @Test
    public void aceptaElPrefijoExportYIgnoraLineasSinIgual() {
        Map<String, String> variables = EntornoQa.parsear(List.of("export JWT_SECRET=valor", "sin igual"));
        assertEquals("valor", variables.get("JWT_SECRET"));
        assertFalse(variables.containsKey("sin igual"));
    }
}

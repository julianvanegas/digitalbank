package com.udea.digitalbank.shared.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Names")
class NamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"Ana Vélez", "    Ana     Vélez  ", "\tAna\t\tVélez ", "Ana Vélez   "})
    @DisplayName("Transversal - normalize - deja un solo espacio entre palabras y ninguno al inicio ni al final")
    void deberiaDejarUnSoloEspacioEntrePalabras(String name) {
        assertThat(Names.normalize(name)).isEqualTo("Ana Vélez");
    }

    @Test
    @DisplayName("Transversal - normalize - no cambia letras, mayúsculas ni tildes")
    void noDeberiaCambiarLasLetras() {
        assertThat(Names.normalize("  María  JOSÉ ")).isEqualTo("María JOSÉ");
    }

    @Test
    @DisplayName("Transversal - normalize - un valor nulo sigue siendo nulo y uno en blanco queda vacío")
    void deberiaManejarNuloYBlanco() {
        assertThat(Names.normalize(null)).isNull();
        assertThat(Names.normalize("     ")).isEmpty();
    }

    @Test
    @DisplayName("Transversal - normalize - conserva los saltos de línea para que la validación los rechace")
    void deberiaConservarLosSaltosDeLinea() {
        assertThat(Names.normalize("Ana \n Vélez")).contains("\n");
    }
}

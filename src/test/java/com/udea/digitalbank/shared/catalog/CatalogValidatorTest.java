package com.udea.digitalbank.shared.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CatalogValidator")
class CatalogValidatorTest {

    private enum Color { RED, GREEN }

    @Test
    @DisplayName("Transversal - Sin diferencias entre el enum y la base no reporta nada y no falla")
    void noDeberiaReportarNadaSiCoinciden() {
        List<String> problems = new ArrayList<>();

        CatalogValidator.compare(Color.class, Set.of("RED", "GREEN"), problems);

        assertThat(problems).isEmpty();
        assertThatCode(() -> CatalogValidator.requireNoProblems(problems)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Transversal - Reporta el valor del enum que falta en la base")
    void deberiaReportarLoQueFaltaEnLaBase() {
        List<String> problems = new ArrayList<>();

        CatalogValidator.compare(Color.class, Set.of("RED"), problems);

        assertThat(problems).containsExactly("Color.GREEN no existe en la base");
    }

    @Test
    @DisplayName("Transversal - Reporta la fila de la base que el enum no declara")
    void deberiaReportarLoQueFaltaEnElEnum() {
        List<String> problems = new ArrayList<>();

        CatalogValidator.compare(Color.class, Set.of("RED", "GREEN", "BLUE"), problems);

        assertThat(problems).containsExactly("Color no declara 'BLUE', que sí está en la base");
    }

    @Test
    @DisplayName("Transversal - Acumula los problemas de varios catálogos y falla una sola vez con todos")
    void deberiaFallarConTodosLosProblemasJuntos() {
        List<String> problems = new ArrayList<>();
        CatalogValidator.compare(Color.class, Set.of("RED"), problems);
        CatalogValidator.compare(Color.class, Set.of("RED", "GREEN", "BLUE"), problems);

        assertThatThrownBy(() -> CatalogValidator.requireNoProblems(problems))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Color.GREEN no existe en la base")
                .hasMessageContaining("Color no declara 'BLUE'");
    }
}

package com.udea.digitalbank.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El Actor se arma solo con el contexto de seguridad: el id del principal y la autoridad ROLE_ADMIN. */
@DisplayName("ActorArgumentResolver")
class ActorArgumentResolverTest {

    private final ActorArgumentResolver resolver = new ActorArgumentResolver();

    @SuppressWarnings("unused")
    void endpoint(@CurrentActor Actor actor, Actor withoutAnnotation, @CurrentActor String wrongType) {
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Object principal, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private MethodParameter parameter(int index) throws NoSuchMethodException {
        return new MethodParameter(getClass().getDeclaredMethod("endpoint", Actor.class, Actor.class, String.class), index);
    }

    @Test
    @DisplayName("Solo atiende el parámetro Actor marcado con @CurrentActor")
    void soloAtiendeElParametroMarcado() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isFalse();  // sin la anotación
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();  // anotado pero de otro tipo
    }

    @Test
    @DisplayName("Un cliente autenticado resuelve a un Actor sin permisos de administrador")
    void clienteNoEsAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId, "ROLE_CUSTOMER");

        assertThat(resolver.resolveArgument(parameter(0), null, null, null)).isEqualTo(new Actor(userId, false));
    }

    @Test
    @DisplayName("Un administrador autenticado resuelve a un Actor con admin=true")
    void adminEsAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId, "ROLE_ADMIN");

        assertThat(resolver.resolveArgument(parameter(0), null, null, null)).isEqualTo(new Actor(userId, true));
    }

    @Test
    @DisplayName("Sin autenticación o con un principal que no es el id del usuario falla con claridad")
    void sinUsuarioAutenticado() throws Exception {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, null, null))
                .isInstanceOf(IllegalStateException.class);

        authenticate("anonimo", "ROLE_CUSTOMER");
        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}

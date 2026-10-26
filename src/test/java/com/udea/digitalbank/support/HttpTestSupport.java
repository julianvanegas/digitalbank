package com.udea.digitalbank.support;

import com.udea.digitalbank.shared.exception.GlobalExceptionHandler;
import com.udea.digitalbank.shared.security.ActorArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Apoyo de pruebas (no es una prueba) para las pruebas de contrato HTTP: MockMvc en modo standalone con el
 * controller real, la validación de Bean Validation, el {@link GlobalExceptionHandler} real y el resolvedor real
 * de {@code @CurrentActor}. Los servicios se simulan, así que cada prueba fija qué responde el servicio y verifica
 * qué código HTTP y qué mensaje recibe el cliente. La seguridad por rol se prueba aparte en ApiAuthorizationTest.
 */
public final class HttpTestSupport {

    private HttpTestSupport() {
    }

    public static MockMvc mockMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new ActorArgumentResolver())
                .build();
    }

    /** Deja autenticado al usuario indicado, como lo haría el filtro JWT tras validar su token. */
    public static void authenticate(UUID userId, boolean admin) {
        String role = admin ? "ROLE_ADMIN" : "ROLE_CUSTOMER";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(role))));
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    public static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    public static MockHttpServletRequestBuilder post(String path, String body) {
        return json(MockMvcRequestBuilders.post(path), body);
    }

    public static MockHttpServletRequestBuilder put(String path, String body) {
        return json(MockMvcRequestBuilders.put(path), body);
    }

    public static MockHttpServletRequestBuilder patch(String path, String body) {
        return json(MockMvcRequestBuilders.patch(path), body);
    }

    public static MockHttpServletRequestBuilder get(String path) {
        return MockMvcRequestBuilders.get(path);
    }

    public static MockHttpServletResponse perform(MockMvc mockMvc, MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    public static String body(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }
}

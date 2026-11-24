package com.udea.digitalbank.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Protege {@code /actuator/prometheus} con autenticación Basic para que quien lee las métricas (Prometheus local o
 * Grafana Cloud) pueda hacerlo sin un token de la API.
 * <p>
 * Es una cadena aparte y anterior a la de SecurityConfig: solo atiende esa ruta, no usa el JWT ni sesiones y no
 * comparte usuarios con el banco. Un token de la API no sirve aquí, y las credenciales de métricas no sirven en la API.
 * Lo demás sigue en la cadena principal, que no se modifica.
 */
@Configuration
public class MetricsSecurityConfig {

    static final String METRICS_PATH = "/actuator/prometheus";

    @Bean
    @Order(1)
    public SecurityFilterChain metricsFilterChain(HttpSecurity http,
                                                  @Value("${metrics.security.user}") String user,
                                                  @Value("${metrics.security.password}") String password)
            throws Exception {
        http
                .securityMatcher(METRICS_PATH)
                .csrf(csrf -> csrf.disable()) // solo lectura (GET), sin formularios ni sesión de navegador
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .authenticationManager(authentication -> authenticate(authentication, user, password));
        return http.build();
    }

    private Authentication authenticate(Authentication authentication, String user, String password) {
        // Las dos comparaciones se hacen siempre y en tiempo constante, para no revelar cuál de las dos falló
        boolean userMatches = sameBytes(user, authentication.getName());
        boolean passwordMatches = sameBytes(password, String.valueOf(authentication.getCredentials()));

        // Sin credenciales configuradas el endpoint queda cerrado, nunca abierto
        if (user.isBlank() || password.isBlank() || !(userMatches & passwordMatches)) {
            throw new BadCredentialsException("Credenciales de métricas inválidas");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_METRICS")));
    }

    private boolean sameBytes(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}

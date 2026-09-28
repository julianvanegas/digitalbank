package com.udea.digitalbank.shared.security;

import com.udea.digitalbank.shared.exception.SecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reglas comunes a toda la API. Las rutas públicas y el mecanismo de autenticación los aporta
 * cada módulo mediante SecurityModule; todo lo demás exige autenticación.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize("hasRole('ADMIN')") en los controllers
public class SecurityConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, List<SecurityModule> modules,
                                                   SecurityErrorHandler errorHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless, sin sesión de navegador que proteger con CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 401 sin token o con token inválido, 403 sin permiso; ambos con el formato ErrorResponse
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> {
                    modules.stream().flatMap(m -> m.publicRoutes().stream()).forEach(route -> {
                        if (route.method() == null) {
                            auth.requestMatchers(route.pattern()).permitAll();
                        } else {
                            auth.requestMatchers(route.method(), route.pattern()).permitAll();
                        }
                    });
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Ruta interna de errores de Spring: si exigiera autenticación, un error de un endpoint
                        // público llegaría al cliente anónimo como 403. No expone trazas, solo el cuerpo de error.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated();
                });

        for (SecurityModule module : modules) {
            module.customize(http);
        }
        return http.build();
    }

    // Ajusta el origen según dónde corra tu frontend
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .collect(Collectors.toList()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

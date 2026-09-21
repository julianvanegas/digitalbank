package com.udea.digitalbank.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Reglas comunes a toda la API. Las rutas públicas y el mecanismo de autenticación los aporta
 * cada módulo mediante SecurityModule; todo lo demás exige autenticación.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize("hasRole('ADMIN')") en los controllers
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, List<SecurityModule> modules) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless, sin sesión de navegador que proteger con CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

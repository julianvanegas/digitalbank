package com.udea.digitalbank.auth.security;

import com.udea.digitalbank.shared.security.SecurityModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/** Parte de la seguridad que pertenece a auth: contraseñas, JWT con sesión única y sus rutas públicas. */
@Configuration
public class AuthSecurity implements SecurityModule {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public AuthSecurity(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // BCrypt: nunca se guarda el password en texto plano
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // /api/auth/logout no está aquí a propósito: exige autenticación
    @Override
    public List<PublicRoute> publicRoutes() {
        return List.of(
                PublicRoute.post("/api/auth/login"),
                PublicRoute.post("/api/auth/verify-2fa"),
                PublicRoute.post("/api/auth/verify-email"),
                PublicRoute.post("/api/auth/resend-verification"),
                PublicRoute.post("/api/auth/recover-password"),
                PublicRoute.post("/api/auth/reset-password"));
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }
}

package com.udea.digitalbank.auth.security;

import com.udea.digitalbank.auth.domain.StatusCode;
import com.udea.digitalbank.auth.domain.AuthSession;
import com.udea.digitalbank.auth.repository.AuthSessionRepository;
import com.udea.digitalbank.auth.repository.UserAccountRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AuthSessionRepository sessionRepository;
    private final UserAccountRepository accountRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   AuthSessionRepository sessionRepository,
                                   UserAccountRepository accountRepository) {
        this.jwtUtil = jwtUtil;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (!jwtUtil.isValid(token)) {
            filterChain.doFilter(request, response); // token inválido/expirado -> Spring responderá 401 más adelante
            return;
        }

        Claims claims = jwtUtil.parseClaims(token);
        Long accountId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        String jti = claims.getId();

        // Sesión única: el token solo vale si su jti es el de la fila de la cuenta, sigue vigente
        // y la cuenta está ACTIVE. Logout, reset de contraseña, bloqueo o inactividad borran la fila.
        Optional<AuthSession> session = sessionRepository.findById(accountId);
        boolean sessionValid = session.isPresent()
                && jti != null
                && jti.equals(session.get().getJti())
                && session.get().getExpiresAt().isAfter(LocalDateTime.now());
        boolean accountActive = sessionValid && accountRepository.findById(accountId)
                .map(a -> a.getStatus().is(StatusCode.ACTIVE))
                .orElse(false);

        if (!accountActive) {
            filterChain.doFilter(request, response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                accountId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}

package com.udea.digitalbank.auth.security;

import com.udea.digitalbank.auth.service.SessionService;
import com.udea.digitalbank.auth.service.UserStatusService;
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
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final UserStatusService userStatusService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   SessionService sessionService,
                                   UserStatusService userStatusService) {
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
        this.userStatusService = userStatusService;
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
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        String jti = claims.getId();

        // Sesión única: el token solo vale si es la sesión vigente del usuario y el usuario está ACTIVE
        boolean userActive = sessionService.isValid(userId, jti) && userStatusService.isActive(userId);

        if (!userActive) {
            filterChain.doFilter(request, response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}

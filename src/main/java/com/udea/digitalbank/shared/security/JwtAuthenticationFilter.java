package com.udea.digitalbank.shared.security;

import com.udea.digitalbank.identity.domain.Customer;
import com.udea.digitalbank.identity.domain.CustomerStatus;
import com.udea.digitalbank.identity.repository.CustomerRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomerRepository customerRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomerRepository customerRepository) {
        this.jwtUtil = jwtUtil;
        this.customerRepository = customerRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

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
        Long customerId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        String jti = claims.getId();

        Optional<Customer> customerOpt = customerRepository.findById(customerId);

        // Sesión única: si el jti del token no coincide con el último emitido, quedó invalidado
        // por un login posterior o un logout, aunque todavía no haya expirado
        if (customerOpt.isEmpty()
            || customerOpt.get().getStatus() != CustomerStatus.ACTIVE
            || !jti.equals(customerOpt.get().getCurrentTokenId())) {
            filterChain.doFilter(request, response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                customerId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}

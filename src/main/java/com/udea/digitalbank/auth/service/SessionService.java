package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.AuthSession;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.AuthSessionRepository;
import com.udea.digitalbank.auth.security.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Ciclo de vida de la única sesión vigente de cada usuario. La sesión es la fila de auth_session y el
 * token que recibe el cliente: se abre, se revoca, se comprueba que un token sigue siendo la sesión
 * vigente y se limpian las vencidas.
 */
@Service
public class SessionService {

    private final AuthSessionRepository sessionRepository;
    private final JwtUtil jwtUtil;

    public SessionService(AuthSessionRepository sessionRepository, JwtUtil jwtUtil) {
        this.sessionRepository = sessionRepository;
        this.jwtUtil = jwtUtil;
    }

    // Abre la sesión del usuario y devuelve el token firmado que la representa
    @Transactional
    public String start(User user) {
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plus(Duration.ofMillis(jwtUtil.getExpirationMs()));
        String jti = jwtUtil.generateJti();
        open(user.getId(), jti, issuedAt, expiresAt);
        return jwtUtil.generateToken(user.getId(), user.getRole().getCode(), jti, toDate(issuedAt), toDate(expiresAt));
    }

    // Sesión única: sobrescribe la fila del usuario y con ello invalida el jti anterior
    @Transactional
    void open(Long userId, String jti, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        sessionRepository.save(new AuthSession(userId, jti, issuedAt, expiresAt));
    }

    // Logout, reset de contraseña, bloqueo o inactividad: el token deja de valer aunque no haya expirado
    @Transactional
    public void revoke(Long userId) {
        sessionRepository.deleteByUser(userId);
    }

    // Un token solo vale si su jti es el de la fila del usuario y esa sesión no ha expirado
    public boolean isValid(Long userId, String jti) {
        return jti != null
                && sessionRepository.existsByUserIdAndJtiAndExpiresAtAfter(userId, jti, LocalDateTime.now());
    }

    // Las sesiones vencidas ya no valen (isValid las rechaza); esto solo evita que se acumulen
    @Transactional
    public void deleteExpired() {
        sessionRepository.deleteExpired(LocalDateTime.now());
    }

    private static Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}

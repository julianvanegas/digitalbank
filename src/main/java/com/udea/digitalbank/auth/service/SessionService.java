package com.udea.digitalbank.auth.service;

import com.udea.digitalbank.auth.domain.AuthSession;
import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.repository.AuthSessionRepository;
import com.udea.digitalbank.auth.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Ciclo de vida de la única sesión vigente de cada usuario. La sesión es la fila de auth_session y el
 * token que recibe el cliente: se abre, se revoca, se comprueba que un token sigue siendo la sesión
 * vigente y se limpian las vencidas.
 */
@Service
public class SessionService {

    private final AuthSessionRepository sessionRepository;
    private final JwtUtil jwtUtil;
    private final Duration inactivityTimeout;

    public SessionService(AuthSessionRepository sessionRepository,
                          JwtUtil jwtUtil,
                          @Value("${session.inactivity-timeout-minutes}") long inactivityTimeoutMinutes) {
        this.sessionRepository = sessionRepository;
        this.jwtUtil = jwtUtil;
        this.inactivityTimeout = Duration.ofMinutes(inactivityTimeoutMinutes);
    }

    // Abre la sesión del usuario y devuelve el token firmado que la representa
    @Transactional
    public String start(User user) {
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plus(inactivityTimeout);
        String jti = jwtUtil.generateJti();
        open(user.getId(), jti, issuedAt, expiresAt);
        return jwtUtil.generateToken(user.getId(), user.getRole().getCode(), jti, toDate(issuedAt));
    }

    // Sesión única: sobrescribe la fila del usuario y con ello invalida el jti anterior
    @Transactional
    void open(UUID userId, String jti, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        sessionRepository.save(new AuthSession(userId, UUID.fromString(jti), issuedAt, expiresAt));
    }

    // Logout, reset de contraseña, bloqueo o inactividad: el token deja de valer aunque no haya expirado
    @Transactional
    public void revoke(UUID userId) {
        sessionRepository.deleteByUser(userId);
    }

    // Un token solo vale si su jti es el de la fila del usuario y la sesión no ha vencido. expires_at es
    // "última actividad + inactividad": si vale, la petición cuenta como actividad y lo desplaza
    @Transactional
    public boolean validateAndTouch(UUID userId, String jti) {
        if (jti == null) {
            return false;
        }
        UUID jtiValue;
        try {
            jtiValue = UUID.fromString(jti);
        } catch (IllegalArgumentException e) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return sessionRepository.touch(userId, jtiValue, now, now.plus(inactivityTimeout)) > 0;
    }

    // Las sesiones vencidas ya no valen (validateAndTouch las rechaza); esto solo evita que se acumulen
    @Transactional
    public void deleteExpired() {
        sessionRepository.deleteExpired(LocalDateTime.now());
    }

    private static Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}

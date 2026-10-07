package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    // Valida y renueva en una sola sentencia atómica: devuelve 1 si la sesión sigue vigente (y desplaza su
    // vencimiento), 0 si no existe, es otro jti o ya venció por inactividad.
    // Va a la base (no findById): un DELETE masivo previo no actualiza la caché de la transacción
    @Modifying
    @Query("""
            update AuthSession s set s.expiresAt = :newExpiresAt
            where s.userId = :userId and s.jti = :jti and s.expiresAt > :now
            """)
    int touch(@Param("userId") UUID userId, @Param("jti") UUID jti,
              @Param("now") LocalDateTime now, @Param("newExpiresAt") LocalDateTime newExpiresAt);

    @Modifying
    @Query("delete from AuthSession s where s.userId = :userId")
    void deleteByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

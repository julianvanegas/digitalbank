package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    // Consulta a la base (no findById): un DELETE masivo previo no actualiza la caché de la transacción
    boolean existsByUserIdAndJtiAndExpiresAtAfter(UUID userId, UUID jti, LocalDateTime now);

    @Modifying
    @Query("delete from AuthSession s where s.userId = :userId")
    void deleteByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

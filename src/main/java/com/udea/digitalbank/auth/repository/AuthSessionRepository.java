package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    // Consulta a la base (no findById): un DELETE masivo previo no actualiza la caché de la transacción
    boolean existsByUserIdAndJtiAndExpiresAtAfter(Long userId, String jti, LocalDateTime now);

    @Modifying
    @Query("delete from AuthSession s where s.userId = :userId")
    void deleteByUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

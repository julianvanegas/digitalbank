package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    /**
     * Valida y renueva en una sola sentencia atómica: devuelve 1 si la sesión sigue vigente y su usuario tiene el
     * estado indicado (y desplaza su vencimiento), 0 si no existe, es otro jti, ya ha vencido por inactividad o el
     * usuario ya no está en ese estado.
     * Va a la base (no findById): un DELETE masivo previo no actualiza la caché de la transacción.
     */
    @Modifying
    @Query("""
            update AuthSession s set s.expiresAt = :newExpiresAt
            where s.userId = :userId and s.jti = :jti and s.expiresAt > :now
              and exists (select 1 from User u where u.id = s.userId and u.status.code = :userStatus)
            """)
    int touch(@Param("userId") UUID userId, @Param("jti") UUID jti,
              @Param("now") LocalDateTime now, @Param("newExpiresAt") LocalDateTime newExpiresAt,
              @Param("userStatus") String userStatus);

    @Modifying
    @Query("delete from AuthSession s where s.userId = :userId")
    void deleteByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from AuthSession s where s.userId in :userIds")
    void deleteByUsers(@Param("userIds") Collection<UUID> userIds);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

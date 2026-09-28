package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.VerificationChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface VerificationChallengeRepository extends JpaRepository<VerificationChallenge, UUID> {

    // Bloqueo de escritura: evita que intentos en paralelo se salten el conteo de fallos
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VerificationChallenge c where c.id = :id and c.purpose.code = :purpose")
    Optional<VerificationChallenge> lockByIdAndPurpose(@Param("id") UUID id, @Param("purpose") String purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VerificationChallenge c where c.userId = :userId and c.purpose.code = :purpose")
    Optional<VerificationChallenge> lockByUserAndPurpose(@Param("userId") Long userId,
                                                            @Param("purpose") String purpose);

    @Modifying
    @Query("delete from VerificationChallenge c where c.userId = :userId and c.purpose.code = :purpose")
    void deleteByUserAndPurpose(@Param("userId") Long userId, @Param("purpose") String purpose);

    @Modifying
    @Query("delete from VerificationChallenge c where c.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

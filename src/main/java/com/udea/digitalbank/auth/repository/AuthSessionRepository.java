package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    @Modifying
    @Query("delete from AuthSession s where s.userAccountId = :accountId")
    void deleteByAccount(@Param("accountId") Long accountId);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

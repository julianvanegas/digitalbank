package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    // Cuentas sin ingresar desde el corte; si nunca ingresaron cuenta la fecha de creación
    @Query("""
            select a from UserAccount a
            where a.status.category.code = :category
              and a.status.code = :status
              and coalesce(a.lastLoginAt, a.createdAt) < :cutoff
            """)
    List<UserAccount> findIdle(@Param("category") String category,
                               @Param("status") String status,
                               @Param("cutoff") LocalDateTime cutoff);
}

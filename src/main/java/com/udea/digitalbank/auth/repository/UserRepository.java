package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // El email se guarda en minúsculas: la búsqueda normaliza el dato recibido igual que lo hace setEmail
    @Query("select u from User u where u.email = lower(trim(:email))")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where u.email = lower(trim(:email))")
    boolean existsByEmail(@Param("email") String email);

    // Sin ingresar y sin cambio de estado desde el corte (statusChangedAt nace igual a createdAt)
    @Query("""
            select a from User a
            where a.role.code = :role
              and a.status.code = :status
              and (a.lastLoginAt is null or a.lastLoginAt < :cutoff)
              and a.statusChangedAt < :cutoff
            """)
    List<User> findIdle(@Param("role") String role,
                               @Param("status") String status,
                               @Param("cutoff") LocalDateTime cutoff);
}

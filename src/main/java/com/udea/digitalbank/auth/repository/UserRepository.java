package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.User;
import com.udea.digitalbank.auth.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** El email se guarda en minúsculas: la búsqueda normaliza el dato recibido igual que lo hace setEmail. */
    @Query("select u from User u where u.email = lower(trim(:email))")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where u.email = lower(trim(:email))")
    boolean existsByEmail(@Param("email") String email);

    /**
     * Sin ingresar y sin cambio de estado desde el corte (statusChangedAt nace igual a createdAt). Solo los ids:
     * el trabajo de inactividad no necesita cargar las entidades.
     */
    @Query("""
            select a.id from User a
            where a.role.code = :role
              and a.status.code = :status
              and (a.lastLoginAt is null or a.lastLoginAt < :cutoff)
              and a.statusChangedAt < :cutoff
            """)
    List<UUID> findIdleIds(@Param("role") String role,
                           @Param("status") String status,
                           @Param("cutoff") LocalDateTime cutoff);

    /**
     * Cambio de estado en bloque con una sola sentencia. Solo mueve a quien siga en el estado de origen: si alguien
     * cambia entre la consulta de ids y este UPDATE (p. ej. un administrador lo reactiva), no se toca.
     */
    @Modifying
    @Query("""
            update User u set u.status = :target, u.statusChangedAt = :now
            where u.id in :ids and u.status = :source
            """)
    int moveStatus(@Param("ids") Collection<UUID> ids, @Param("source") UserStatus source,
                   @Param("target") UserStatus target, @Param("now") LocalDateTime now);
}

package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {
}

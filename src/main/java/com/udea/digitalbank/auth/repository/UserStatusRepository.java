package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatusRepository extends JpaRepository<UserStatus, Short> {
}

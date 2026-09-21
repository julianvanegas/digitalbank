package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusRepository extends JpaRepository<Status, Short> {
}

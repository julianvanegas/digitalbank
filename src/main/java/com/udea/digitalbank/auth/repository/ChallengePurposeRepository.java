package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.ChallengePurpose;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengePurposeRepository extends JpaRepository<ChallengePurpose, Short> {
}

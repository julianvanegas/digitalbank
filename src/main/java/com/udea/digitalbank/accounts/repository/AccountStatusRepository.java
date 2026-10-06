package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountStatusRepository extends JpaRepository<AccountStatus, Short> {
}

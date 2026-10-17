package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.AccountAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountActionRepository extends JpaRepository<AccountAction, UUID> {
}

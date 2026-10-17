package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTypeRepository extends JpaRepository<AccountType, Short> {
}

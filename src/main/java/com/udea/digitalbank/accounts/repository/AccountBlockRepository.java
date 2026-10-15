package com.udea.digitalbank.accounts.repository;

import com.udea.digitalbank.accounts.domain.AccountBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountBlockRepository extends JpaRepository<AccountBlock, UUID> {
}

package com.udea.digitalbank.accounts.domain;

import com.udea.digitalbank.accounts.api.AccountStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

// Catálogo de solo lectura de los estados en los que puede estar una cuenta
@Entity
@Immutable
@Table(name = "account_status")
@Getter
@NoArgsConstructor
public class AccountStatus {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    public boolean is(AccountStatusEnum expected) {
        return code.equals(expected.name());
    }
}

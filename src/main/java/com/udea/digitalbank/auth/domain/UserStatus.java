package com.udea.digitalbank.auth.domain;

import com.udea.digitalbank.auth.api.UserStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "user_status")
@Getter
@NoArgsConstructor
public class UserStatus {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    public boolean is(UserStatusEnum expected) {
        return code.equals(expected.name());
    }
}

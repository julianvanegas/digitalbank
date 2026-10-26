package com.udea.digitalbank.accounts.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountView")
class AccountViewTest {

    @ParameterizedTest
    @EnumSource(AccountStatusEnum.class)
    @DisplayName("HU20 CA04 - isActive - solo una cuenta ACTIVE puede mover dinero")
    void isActive_soloEsVerdaderoParaActive(AccountStatusEnum status) {
        AccountView view = new AccountView(UUID.randomUUID(), "@AN1231", "123456789012", UUID.randomUUID(),
                status.name(), BigDecimal.ZERO);

        assertThat(view.isActive()).isEqualTo(status == AccountStatusEnum.ACTIVE);
    }
}

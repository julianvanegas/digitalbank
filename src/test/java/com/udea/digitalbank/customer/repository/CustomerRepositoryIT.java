package com.udea.digitalbank.customer.repository;

import com.udea.digitalbank.customer.domain.Customer;
import com.udea.digitalbank.support.integration.RepositoryTest;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.udea.digitalbank.support.integration.TestData.ADMIN;
import static com.udea.digitalbank.support.integration.TestData.ACTIVE;
import static com.udea.digitalbank.support.integration.TestData.CEDULA;
import static com.udea.digitalbank.support.integration.TestData.PASAPORTE;
import static com.udea.digitalbank.support.integration.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CustomerRepository contra PostgreSQL")
class CustomerRepositoryIT extends RepositoryTest {

    @Autowired
    private CustomerRepository repository;

    @Test
    @DisplayName("Busca al cliente por usuario y por documento")
    void findsByUserAndDocument() {
        Customer customer = data.customer(data.customerUser("ana@example.com"), CEDULA, "1020304050");

        assertThat(repository.findByUserId(customer.getUserId())).get()
                .extracting(Customer::getId).isEqualTo(customer.getId());
        assertThat(repository.existsByDocumentTypeIdAndDocumentNumber(CEDULA, "1020304050")).isTrue();
        assertThat(repository.findByDocumentTypeIdAndDocumentNumber(CEDULA, "1020304050")).isPresent();
        assertThat(repository.existsByDocumentTypeIdAndDocumentNumber(PASAPORTE, "1020304050")).isFalse();
    }

    @Test
    @DisplayName("El mismo número de documento puede existir en otro tipo de documento")
    void sameNumberInOtherDocumentType() {
        data.customer(data.customerUser("a@example.com"), CEDULA, "1020304050");
        data.customer(data.customerUser("b@example.com"), PASAPORTE, "1020304050");

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("La base rechaza repetir tipo y número de documento")
    void rejectsDuplicatedDocument() {
        data.customer(data.customerUser("a@example.com"), CEDULA, "1020304050");

        assertThatThrownBy(() -> data.customer(data.customerUser("b@example.com"), CEDULA, "1020304050"))
                .isInstanceOf(ConstraintViolationException.class)
                .extracting("constraintName").isEqualTo("uk_customers_document");
    }

    @Test
    @DisplayName("La base impide que un administrador tenga perfil de cliente")
    void rejectsCustomerProfileForAdmin() {
        var admin = data.user("admin@example.com", ADMIN, ACTIVE, T0, null, T0);

        assertThatThrownBy(() -> data.customer(admin, CEDULA, "1020304050"))
                .isInstanceOf(ConstraintViolationException.class)
                .extracting("constraintName").isEqualTo("fk_customers_user");
    }
}

package com.udea.digitalbank.transactions.repository;

import com.udea.digitalbank.transactions.domain.TransactionLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Acceso de solo lectura a los topes transaccionales. */
public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, Short> {

    /** Todos los topes, en un orden estable: por tipo de movimiento y luego por tipo de cuenta. */
    @Query("select l from TransactionLimit l order by l.type.id, l.accountTypeCode")
    List<TransactionLimit> findAllOrdered();

    /** El tope de un tipo de movimiento para un tipo de cuenta, si está definido. */
    @Query("select l from TransactionLimit l where l.type.code = :typeCode and l.accountTypeCode = :accountTypeCode")
    Optional<TransactionLimit> findByTypeAndAccountType(@Param("typeCode") String typeCode,
                                                        @Param("accountTypeCode") String accountTypeCode);
}

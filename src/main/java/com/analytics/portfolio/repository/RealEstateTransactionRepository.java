package com.analytics.portfolio.repository;

import com.analytics.portfolio.enums.RealEstateTransactionType;
import com.analytics.portfolio.model.RealEstateTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RealEstateTransactionRepository extends JpaRepository<RealEstateTransaction, Long> {

    List<RealEstateTransaction> findByPropertyIdOrderByTransactionDateDesc(Long propertyId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM RealEstateTransaction t " +
           "WHERE t.property.id = :propertyId AND t.type IN :types " +
           "AND t.transactionDate BETWEEN :from AND :to")
    BigDecimal sumByTypesAndPeriod(@Param("propertyId") Long propertyId,
                                    @Param("types") List<RealEstateTransactionType> types,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM RealEstateTransaction t " +
           "WHERE t.property.id = :propertyId AND t.type IN :types")
    BigDecimal sumByTypes(@Param("propertyId") Long propertyId,
                           @Param("types") List<RealEstateTransactionType> types);
}

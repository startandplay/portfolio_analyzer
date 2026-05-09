package com.analytics.portfolio.repository;

import com.analytics.portfolio.model.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {
    List<Dividend> findByPortfolioId(Long portfolioId);
    
    List<Dividend> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);
    
    @Query("SELECT d FROM Dividend d WHERE d.portfolio.id = :portfolioId " +
           "ORDER BY d.paymentDate DESC")
    List<Dividend> findByPortfolioIdOrderByPaymentDateDesc(
        @Param("portfolioId") Long portfolioId
    );
    
    @Query("SELECT SUM(d.netAmount) FROM Dividend d WHERE d.portfolio.id = :portfolioId")
    java.math.BigDecimal getTotalDividendsByPortfolioId(@Param("portfolioId") Long portfolioId);
}

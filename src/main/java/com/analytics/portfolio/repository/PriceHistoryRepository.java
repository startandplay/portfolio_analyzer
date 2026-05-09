package com.analytics.portfolio.repository;

import com.analytics.portfolio.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByAssetIdOrderByPriceDateDesc(Long assetId);
    
    @Query("SELECT p FROM PriceHistory p WHERE p.asset.id = :assetId " +
           "AND p.priceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY p.priceDate ASC")
    List<PriceHistory> findByAssetIdAndDateRange(
        @Param("assetId") Long assetId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT p FROM PriceHistory p WHERE p.asset.id = :assetId " +
           "ORDER BY p.priceDate DESC LIMIT 1")
    Optional<PriceHistory> findLatestPriceByAssetId(@Param("assetId") Long assetId);
}

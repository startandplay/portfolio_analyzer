package com.analytics.portfolio.repository;

import com.analytics.portfolio.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByPortfolioId(Long portfolioId);

    Optional<Position> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);

    @Query("SELECT p FROM Position p WHERE p.portfolio.id = :portfolioId " +
            "AND p.quantity > 0 ORDER BY p.currentValue DESC")
    List<Position> findActivePositionsByPortfolioId(@Param("portfolioId") Long portfolioId);

    // Deletar todas as positions de um portfolio (para recalcular)
    void deleteByPortfolioId(Long portfolioId);
}

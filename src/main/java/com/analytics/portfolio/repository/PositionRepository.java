package com.analytics.portfolio.repository;

import com.analytics.portfolio.dto.IPositionSummaryAggregation;
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

    @Query(value = """
            WITH Aggregated_Positions AS (
                SELECT 
                    asset_id,
                    SUM(CASE WHEN type = 'BUY' THEN quantity ELSE 0 END) as total_qty_bought,
                    SUM(CASE WHEN type = 'BUY' THEN total_amount ELSE 0 END) as total_amount_bought,
                    SUM(CASE WHEN type = 'SELL' THEN quantity ELSE 0 END) as total_qty_sold,
                    SUM(CASE WHEN type = 'SELL' THEN total_amount ELSE 0 END) as total_amount_sold
                FROM transaction
                WHERE portfolio_id = :portfolioId
                GROUP BY asset_id
            ),
            Aggregated_Closed AS (
                SELECT 
                    asset_id,
                    SUM(volume) as total_volume_sold,
                    SUM(purchase_value) as total_purchace_value_of_sold_shares,
                    SUM(profit_loss) as total_realized_pnl
                FROM closed_positions
                WHERE portfolio_id = :portfolioId
                GROUP BY asset_id
            )
            SELECT 
                p.asset_id as assetId,
                a.ticker as ticker, -- <-- Buscado diretamente da tabela assets
                (p.total_qty_bought - p.total_qty_sold) as currentQuantity,
                ((p.total_amount_bought * -1)- COALESCE(c.total_purchace_value_of_sold_shares, 0)) as currentTotalPurchaseValue,
                CASE 
                    WHEN (p.total_qty_bought - p.total_qty_sold) > 0 
                    THEN ((p.total_amount_bought * -1)- COALESCE(c.total_purchace_value_of_sold_shares, 0)) / (p.total_qty_bought - p.total_qty_sold)
                    ELSE 0 
                END as averagePurchasePrice, COALESCE(c.total_realized_pnl, 0) as totalRealizedPnl
            FROM Aggregated_Positions p
            INNER JOIN assets a ON p.asset_id = a.id -- <-- Junção adicionada
            LEFT JOIN Aggregated_Closed c ON p.asset_id = c.asset_id
            WHERE (p.total_qty_bought - p.total_qty_sold) > 0 OR COALESCE(c.total_realized_pnl, 0) != 0
            """, nativeQuery = true)
    List<IPositionSummaryAggregation> recalculatePortfolioPositions(@Param("portfolioId") Long portfolioId);

}

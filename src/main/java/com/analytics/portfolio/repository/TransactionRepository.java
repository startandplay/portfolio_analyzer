package com.analytics.portfolio.repository;

import com.analytics.portfolio.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByPortfolioId(Long portfolioId);

    List<Transaction> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);

    List<Transaction> findByPortfolioIdOrderByTransactionDateDesc(Long portfolioId);

    @Query("SELECT t FROM Transaction t WHERE t.portfolio.id = :portfolioId " +
            "AND t.transactionDate BETWEEN :startDate AND :endDate " +
            "ORDER BY t.transactionDate DESC")
    List<Transaction> findByPortfolioIdAndDateRange(
            @Param("portfolioId") Long portfolioId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    List<Transaction> findByImportSource(String importSource);

    // Verificar se existe por fingerprint (prevenir duplicatas)
    boolean existsByImportFingerprint(String fingerprint);

    // Buscar por fingerprint
    Optional<Transaction> findByImportFingerprint(String fingerprint);

    // Buscar por external ID e fonte
    Optional<Transaction> findByExternalIdAndImportSource(
            String externalId, String importSource);

    // Contar transações por fonte de importação
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.importSource = :source")
    long countByImportSource(@Param("source") String source);


    /**
     * Calcula holdings (quantidade total de ações por ativo)
     * Query SQL otimizada que calcula diretamente no banco
     */
    @Query("""
        SELECT a.symbol as symbol,
               a.ticker as ticker,
               SUM(CASE WHEN t.type = 'BUY' THEN t.quantity ELSE -t.quantity END) as totalQuantity,
               SUM(CASE WHEN t.type = 'BUY' THEN t.quantity * t.price ELSE 0 END) as totalInvested,
               SUM(CASE WHEN t.type = 'BUY' THEN t.quantity ELSE 0 END) as totalBought,
               COALESCE((SELECT p.realizedPL FROM Position p WHERE p.asset.id = a.id AND p.portfolio.id = :portfolioId), 0) as totalProfit
        FROM Transaction t
        JOIN t.asset a
        WHERE t.portfolio.id = :portfolioId
        GROUP BY a.id, a.symbol, a.ticker
        HAVING SUM(CASE WHEN t.type = 'BUY' THEN t.quantity ELSE -t.quantity END) > 0
        ORDER BY a.symbol
        """)
    List<HoldingSummary> calculateHoldings(@Param("portfolioId") Long portfolioId);

    interface HoldingSummary {
        String getSymbol();
        String getName();
        BigDecimal getTotalQuantity();
        BigDecimal getTotalInvested();
        BigDecimal getTotalBought();
        BigDecimal getTotalDividend();
        BigDecimal getTotalProfit();

        default BigDecimal getAverageBuyPrice() {
            if (getTotalBought() != null && getTotalBought().compareTo(BigDecimal.ZERO) > 0) {
                return getTotalInvested()
                        .divide(getTotalBought(), 4, RoundingMode.HALF_UP);
            }
            return BigDecimal.ZERO;
        }
    }
}

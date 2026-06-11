package com.analytics.portfolio.repository;

import com.analytics.portfolio.enums.TransactionType;
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


    // ════════════════════════════════════════════════════════════
    // Income queries — filtro por tipo(s)
    // ════════════════════════════════════════════════════════════

    /**
     * Devolve transações filtradas por lista de tipos, ordenadas por data desc.
     */
    @Query("SELECT t FROM Transaction t " +
            "WHERE t.portfolio.id = :portfolioId " +
            "AND t.type IN :types " +
            "ORDER BY t.transactionDate DESC")
    List<Transaction> findByPortfolioIdAndTypes(
            @Param("portfolioId") Long portfolioId,
            @Param("types") List<TransactionType> types
    );

    /**
     * Soma total de um conjunto de tipos para um portfólio.
     * Útil para calcular gross dividends, gross interest, etc.
     */
    @Query("SELECT COALESCE(SUM(t.totalAmount), 0) FROM Transaction t " +
            "WHERE t.portfolio.id = :portfolioId " +
            "AND t.type IN :types")
    BigDecimal sumAmountByPortfolioAndTypes(
            @Param("portfolioId") Long portfolioId,
            @Param("types") List<TransactionType> types
    );

    /**
     * Dividendos brutos agrupados por asset.
     * Devolve: [assetId, ticker, instrument, sumGross, count]
     */
    @Query("SELECT t.asset.id, t.asset.ticker, t.asset.instrument, " +
            "SUM(t.totalAmount), COUNT(t) " +
            "FROM Transaction t " +
            "WHERE t.portfolio.id = :portfolioId " +
            "AND t.type = 'DIVIDEND' " +
            "GROUP BY t.asset.id, t.asset.ticker, t.asset.instrument " +
            "ORDER BY SUM(t.totalAmount) DESC")
    List<Object[]> getDividendsGroupedByAsset(@Param("portfolioId") Long portfolioId);

    /**
     * Withholding tax agrupado por asset.
     * Devolve: [assetId, sumTax]
     */
    @Query("SELECT t.asset.id, SUM(t.totalAmount) " +
            "FROM Transaction t " +
            "WHERE t.portfolio.id = :portfolioId " +
            "AND t.type = 'WITHHOLDING_TAX' " +
            "GROUP BY t.asset.id")
    List<Object[]> getWithholdingTaxGroupedByAsset(@Param("portfolioId") Long portfolioId);

    /**
     * Income total líquido:
     * dividends + interest - withholding_tax - interest_tax
     */
    @Query("SELECT COALESCE(SUM(" +
            "  CASE WHEN t.type IN ('DIVIDEND', 'INTEREST') THEN t.totalAmount " +
            "       WHEN t.type IN ('WITHHOLDING_TAX', 'INTEREST_TAX') THEN t.totalAmount " +
            "       ELSE 0 END" +
            "), 0) " +
            "FROM Transaction t " +
            "WHERE t.portfolio.id = :portfolioId " +
            "AND t.type IN ('DIVIDEND', 'INTEREST', 'WITHHOLDING_TAX', 'INTEREST_TAX')")
    BigDecimal getTotalIncomeNet(@Param("portfolioId") Long portfolioId);

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

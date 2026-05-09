package com.analytics.portfolio.repository;

import com.analytics.portfolio.model.ClosedPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClosedPositionRepository extends JpaRepository<ClosedPosition, Long> {

    // ── Queries base ────────────────────────────────────────────────
    List<ClosedPosition> findByPortfolioIdOrderByCloseTimeDesc(Long portfolioId);
    List<ClosedPosition> findByPortfolioIdAndTicker(Long portfolioId, String ticker);
    Optional<ClosedPosition> findByPositionId(String positionId);
    boolean existsByPositionId(String positionId);

    boolean existsByImportFingerprint(String fingerprint);

    Optional<ClosedPosition> findByImportFingerprint(String fingerprint);

    List<ClosedPosition> findByPortfolioIdAndCloseTimeBetweenOrderByCloseTimeDesc(
        Long portfolioId, LocalDateTime from, LocalDateTime to);

    // ── Métricas de P&L ─────────────────────────────────────────────

    /** Lucro/Prejuízo total realizado do portfolio */
    @Query("SELECT COALESCE(SUM(cp.profitLoss), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid")
    BigDecimal getTotalRealizedPL(@Param("pid") Long portfolioId);

    /** P&L realizado por ticker */
    @Query("SELECT COALESCE(SUM(cp.profitLoss), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid AND cp.ticker = :ticker")
    BigDecimal getRealizedPLByTicker(@Param("pid") Long portfolioId, @Param("ticker") String ticker);

    /** P&L realizado num período */
    @Query("SELECT COALESCE(SUM(cp.profitLoss), 0) FROM ClosedPosition cp " +
           "WHERE cp.portfolio.id = :pid AND cp.closeTime BETWEEN :from AND :to")
    BigDecimal getRealizedPLInPeriod(@Param("pid") Long portfolioId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    // ── Custos reais ────────────────────────────────────────────────

    /** Total de comissões pagas */
    @Query("SELECT COALESCE(SUM(ABS(cp.commission)), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid")
    BigDecimal getTotalCommissions(@Param("pid") Long portfolioId);

    /** Total de swap/financing costs */
    @Query("SELECT COALESCE(SUM(ABS(cp.swap)), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid")
    BigDecimal getTotalSwapCosts(@Param("pid") Long portfolioId);

    /** Total de rollover costs */
    @Query("SELECT COALESCE(SUM(ABS(cp.rollover)), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid")
    BigDecimal getTotalRolloverCosts(@Param("pid") Long portfolioId);

    /** Todos os custos combinados */
    @Query("SELECT COALESCE(SUM(ABS(cp.commission) + ABS(COALESCE(cp.swap,0)) + ABS(COALESCE(cp.rollover,0))), 0) " +
           "FROM ClosedPosition cp WHERE cp.portfolio.id = :pid")
    BigDecimal getTotalAllCosts(@Param("pid") Long portfolioId);

    // ── Win Rate ─────────────────────────────────────────────────────

    /** Número de trades vencedores */
    @Query("SELECT COUNT(cp) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid AND cp.profitLoss > 0")
    long countWinners(@Param("pid") Long portfolioId);

    /** Número de trades perdedores */
    @Query("SELECT COUNT(cp) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid AND cp.profitLoss < 0")
    long countLosers(@Param("pid") Long portfolioId);

    /** Total de trades */
    @Query("SELECT COUNT(cp) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid")
    long countAllTrades(@Param("pid") Long portfolioId);

    // ── Médias ───────────────────────────────────────────────────────

    /** Lucro médio por trade vencedor */
    @Query("SELECT COALESCE(AVG(cp.profitLoss), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid AND cp.profitLoss > 0")
    BigDecimal getAverageWin(@Param("pid") Long portfolioId);

    /** Perda média por trade perdedor */
    @Query("SELECT COALESCE(AVG(cp.profitLoss), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid AND cp.profitLoss < 0")
    BigDecimal getAverageLoss(@Param("pid") Long portfolioId);

    /** Soma de todos os ganhos */
    @Query("SELECT COALESCE(SUM(cp.profitLoss), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid AND cp.profitLoss > 0")
    BigDecimal getSumOfWins(@Param("pid") Long portfolioId);

    /** Soma de todas as perdas (valor absoluto) */
    @Query("SELECT COALESCE(SUM(ABS(cp.profitLoss)), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid AND cp.profitLoss < 0")
    BigDecimal getSumOfLosses(@Param("pid") Long portfolioId);

    // ── Extremos ──────────────────────────────────────────────────────

    /** Melhor trade */
    @Query("SELECT cp FROM ClosedPosition cp WHERE cp.portfolio.id = :pid ORDER BY cp.profitLoss DESC LIMIT 1")
    Optional<ClosedPosition> getBestTrade(@Param("pid") Long portfolioId);

    /** Pior trade */
    @Query("SELECT cp FROM ClosedPosition cp WHERE cp.portfolio.id = :pid ORDER BY cp.profitLoss ASC LIMIT 1")
    Optional<ClosedPosition> getWorstTrade(@Param("pid") Long portfolioId);

    // ── Por instrumento ───────────────────────────────────────────────

    /** P&L agrupado por ticker */
    @Query("SELECT cp.ticker, cp.instrument, COUNT(cp), SUM(cp.profitLoss), AVG(cp.profitLoss) " +
           "FROM ClosedPosition cp WHERE cp.portfolio.id = :pid " +
           "GROUP BY cp.ticker, cp.instrument ORDER BY SUM(cp.profitLoss) DESC")
    List<Object[]> getPLGroupedByTicker(@Param("pid") Long portfolioId);

    /** Total do valor investido (purchase value) */
    @Query("SELECT COALESCE(SUM(ABS(cp.purchaseValue)), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid")
    BigDecimal getTotalInvested(@Param("pid") Long portfolioId);

    /** Custo médio de comissão por trade */
    @Query("SELECT COALESCE(AVG(ABS(cp.commission)), 0) FROM ClosedPosition cp WHERE cp.portfolio.id = :pid")
    BigDecimal getAvgCommissionPerTrade(@Param("pid") Long portfolioId);
}

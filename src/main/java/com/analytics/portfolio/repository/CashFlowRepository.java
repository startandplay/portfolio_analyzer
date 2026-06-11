package com.analytics.portfolio.repository;

import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.CashFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository para CashFlow (movimentações de caixa sem asset)
 */
@Repository
public interface CashFlowRepository extends JpaRepository<CashFlow, Long> {

    /**
     * Busca todos os cash flows de um portfolio
     */
    List<CashFlow> findByPortfolioId(Long portfolioId);

    /**
     * Busca cash flows ordenados por data (mais recentes primeiro)
     */
    List<CashFlow> findByPortfolioIdOrderByFlowDateDesc(Long portfolioId);

    /**
     * Busca cash flows por tipo
     */
    List<CashFlow> findByPortfolioIdAndType(Long portfolioId, TransactionType type);

    /**
     * Calcula saldo total de cash flows
     * Positivo = mais entradas que saídas
     * Negativo = mais saídas que entradas
     */
    @Query("SELECT COALESCE(SUM(cf.amount), 0) FROM CashFlow cf WHERE cf.portfolio.id = :portfolioId")
    BigDecimal calculateTotalCashFlow(@Param("portfolioId") Long portfolioId);

    /**
     * Busca cash flows por período
     */
    @Query("SELECT cf FROM CashFlow cf WHERE cf.portfolio.id = :portfolioId " +
            "AND cf.flowDate BETWEEN :startDate AND :endDate " +
            "ORDER BY cf.flowDate DESC")
    List<CashFlow> findByPortfolioIdAndDateRange(
            @Param("portfolioId") Long portfolioId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // ── Totais simples ────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(cf.amount), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type = 'DEPOSIT'")
    BigDecimal getTotalDeposits(@Param("portfolioId") Long portfolioId);

    @Query("SELECT COALESCE(SUM(ABS(cf.amount)), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type = 'WITHDRAWAL'")
    BigDecimal getTotalWithdrawals(@Param("portfolioId") Long portfolioId);

    @Query("SELECT COALESCE(SUM(cf.amount), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type = 'TRANSFER'")
    BigDecimal getTotalTransfers(@Param("portfolioId") Long portfolioId);

    @Query("SELECT COALESCE(SUM(ABS(cf.amount)), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type IN ('TAX', 'DIVIDEND_TAX')")
    BigDecimal getTotalTaxesPaid(@Param("portfolioId") Long portfolioId);

    @Query("SELECT COALESCE(SUM(ABS(cf.amount)), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type = 'FEE'")
    BigDecimal getTotalFeesPaid(@Param("portfolioId") Long portfolioId);

    // ── Saldo líquido de movimentos de caixa ─────────────────────

    /**
     * Movimento líquido = depósitos + levantamentos (os levantamentos
     * já têm sinal negativo na tabela — a soma reflecte o saldo real).
     */
    @Query("SELECT COALESCE(SUM(cf.amount), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId " +
            "AND cf.type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')")
    BigDecimal getNetCashMovement(@Param("portfolioId") Long portfolioId);

    // ── Resumo agrupado por tipo ──────────────────────────────────

    /**
     * Agrega cash flows por tipo.
     * Devolve: [type (String), totalAmount, count]
     */
    @Query("SELECT CAST(cf.type AS string), SUM(cf.amount), COUNT(cf) " +
            "FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId " +
            "GROUP BY cf.type " +
            "ORDER BY SUM(cf.amount) DESC")
    List<Object[]> getCashFlowSummaryByType(@Param("portfolioId") Long portfolioId);

    /**
     * Devolve transações filtradas por lista de tipos, ordenadas por data desc.
     */
    @Query("SELECT cf FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId " +
            "AND cf.type IN :types " +
            "ORDER BY cf.flowDate DESC")
    List<CashFlow> findByPortfolioIdAndTypes(
            @Param("portfolioId") Long portfolioId,
            @Param("types") List<TransactionType> types
    );

    /**
     * Soma total de um conjunto de tipos para um portfólio.
     * Útil para calcular gross dividends, gross interest, etc.
     */
    @Query("SELECT COALESCE(SUM(cf.amount), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId " +
            "AND cf.type IN :types")
    BigDecimal sumAmountByPortfolioAndTypes(
            @Param("portfolioId") Long portfolioId,
            @Param("types") List<TransactionType> types
    );


    // ── Duplicados ────────────────────────────────────────────────

    boolean existsByImportFingerprint(String fingerprint);

    Optional<CashFlow> findByImportFingerprint(String fingerprint);

}
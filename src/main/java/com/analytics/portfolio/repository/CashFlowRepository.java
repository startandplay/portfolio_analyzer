package com.analytics.portfolio.repository;

import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.CashFlow;
import com.analytics.portfolio.model.Transaction;
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

    /**
     * Total de depósitos
     */
    @Query("SELECT COALESCE(SUM(cf.amount), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type = 'DEPOSIT'")
    BigDecimal getTotalDeposits(@Param("portfolioId") Long portfolioId);

    /**
     * Total de saques (retorna valor positivo)
     */
    @Query("SELECT COALESCE(SUM(ABS(cf.amount)), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type = 'WITHDRAWAL'")
    BigDecimal getTotalWithdrawals(@Param("portfolioId") Long portfolioId);

    /**
     * Total de impostos pagos (retorna valor positivo)
     */
    @Query("SELECT COALESCE(SUM(ABS(cf.amount)), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type IN ('TAX', 'DIVIDEND_TAX')")
    BigDecimal getTotalTaxesPaid(@Param("portfolioId") Long portfolioId);

    /**
     * Total de taxas pagas (retorna valor positivo)
     */
    @Query("SELECT COALESCE(SUM(ABS(cf.amount)), 0) FROM CashFlow cf " +
            "WHERE cf.portfolio.id = :portfolioId AND cf.type = 'FEE'")
    BigDecimal getTotalFeesPaid(@Param("portfolioId") Long portfolioId);

    // Verificar se existe por fingerprint (prevenir duplicatas)
    boolean existsByImportFingerprint(String fingerprint);

    // Buscar por fingerprint
    Optional<Transaction> findByImportFingerprint(String fingerprint);


}
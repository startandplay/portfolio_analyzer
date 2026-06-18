package com.analytics.portfolio.dto;

import com.analytics.portfolio.enums.AssetType;
import com.analytics.portfolio.enums.PortfolioSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Visão consolidada de todos os portfolios de um utilizador.
 * <p>
 * Calculada on-demand a partir das métricas individuais de cada portfolio.
 * Todos os valores são convertidos para a moeda base do utilizador (default: EUR).
 */
@Data
@Builder
public class AggregatePortfolioMetrics {

    private Long userId;
    private String baseCurrency;
    private LocalDateTime calculatedAt;
    private int portfoliosIncluded;

    // ── Totais consolidados ───────────────────────────────────────
    private BigDecimal totalNetWorth;           // currentValue de todos os portfolios
    private BigDecimal totalInvested;           // capital total investido
    private BigDecimal totalReturn;             // ganho/perda absoluto total
    private BigDecimal totalReturnPercentage;   // % total
    private BigDecimal weightedAnnualizedReturn;// CAGR ponderado por valor investido

    // ── Income consolidado ────────────────────────────────────────
    private BigDecimal totalDividendsNet;
    private BigDecimal totalInterestNet;
    private BigDecimal totalIncomeNet;          // dividendos + juros + rendas

    // ── Ganhos/perdas ─────────────────────────────────────────────
    private BigDecimal totalUnrealizedGains;
    private BigDecimal totalRealizedGains;

    // ── Alocação por tipo de ativo ────────────────────────────────
    /**
     * Percentagem do net worth por tipo de ativo.
     * Ex: { STOCK: 45.2, CRYPTO: 28.7, REAL_ESTATE: 26.1 }
     */
    private Map<AssetType, BigDecimal> allocationByAssetType;

    /**
     * Valor absoluto por tipo de ativo (em moeda base).
     */
    private Map<AssetType, BigDecimal> valueByAssetType;

    // ── Alocação por exchange/fonte ───────────────────────────────
    /**
     * Percentagem do net worth por source.
     * Ex: { XTB: 60.0, BYBIT: 25.0, REAL_ESTATE: 15.0 }
     */
    private Map<PortfolioSource, BigDecimal> allocationBySource;

    private Map<PortfolioSource, BigDecimal> valueBySource;

    // ── Risco e diversificação ────────────────────────────────────
    /**
     * Percentagem do maior ativo único sobre o net worth total.
     * Valor alto = risco de concentração.
     */
    private BigDecimal concentrationRisk;

    /**
     * Ticker/nome do ativo com maior peso.
     */
    private String largestHoldingTicker;
    private BigDecimal largestHoldingPercentage;

    /**
     * Número total de ativos distintos em todos os portfolios.
     */
    private int numberOfDistinctAssets;

    /**
     * Número de portfolios com retorno positivo.
     */
    private int portfoliosInProfit;
    private int portfoliosInLoss;

    // ── Breakdown por portfolio ───────────────────────────────────
    /**
     * Resumo individual de cada portfolio incluído na agregação.
     */
    private List<PortfolioContribution> portfolioBreakdown;

    // ── Inner DTO ─────────────────────────────────────────────────

    @Data
    @Builder
    public static class PortfolioContribution {
        private Long portfolioId;
        private String portfolioName;
        private PortfolioSource source;
        private String currency;

        private BigDecimal currentValue;          // em moeda base
        private BigDecimal totalInvested;
        private BigDecimal totalReturn;
        private BigDecimal totalReturnPercentage;
        private BigDecimal weightPercentage;      // % do net worth total

        private int numberOfPositions;
        private boolean inProfit;
    }
}

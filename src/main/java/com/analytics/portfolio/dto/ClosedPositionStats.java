package com.analytics.portfolio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estatísticas completas de posições fechadas de um portfolio.
 *
 * Métricas incluídas:
 * ─ P&L realizado total
 * ─ Win rate
 * ─ Profit factor
 * ─ Risk/Reward ratio
 * ─ Custos reais (comissão, swap, rollover)
 * ─ Custo real do investimento actual (open positions ajustadas)
 * ─ Melhor e pior trade
 * ─ P&L por instrumento
 */
@Data
@Builder
public class ClosedPositionStats {

    // ── P&L ───────────────────────────────────────────────────────
    private BigDecimal totalRealizedPL;
    private BigDecimal totalGrossProfit;

    // ── Custos reais ──────────────────────────────────────────────
    private BigDecimal totalCommissions;
    private BigDecimal totalSwapCosts;
    private BigDecimal totalRolloverCosts;
    private BigDecimal totalAllCosts;           // comissão + swap + rollover
    private BigDecimal avgCommissionPerTrade;

    // ── Trade Statistics ──────────────────────────────────────────
    private long   totalTrades;
    private long   winners;
    private long   losers;
    private long   breakEven;
    private double winRate;                     // % de trades vencedores
    private double lossRate;

    // ── Profit Factor & Risk/Reward ───────────────────────────────
    /** Profit Factor = Total Wins / Total Losses (>1 = lucrativo) */
    private BigDecimal profitFactor;
    /** Risk/Reward = Average Win / Average Loss */
    private BigDecimal riskRewardRatio;

    private BigDecimal averageWin;              // lucro médio por trade ganho
    private BigDecimal averageLoss;             // perda média por trade perdido
    private BigDecimal averagePLPerTrade;       // P&L médio por todos os trades

    // ── Extremos ──────────────────────────────────────────────────
    private TradeInfo bestTrade;
    private TradeInfo worstTrade;

    // ── Custo real do investimento actual ─────────────────────────
    /**
     * Custo real médio de comissão/swap que deve ser considerado
     * ao calcular o break-even de posições abertas actuais
     */
    private BigDecimal avgCostPercentage;       // % dos custos sobre o valor investido
    private BigDecimal totalInvestedInClosed;   // capital total que passou pelas closed positions
    private BigDecimal realizedROI;             // ROI total considerando custos reais

    // ── Por instrumento ───────────────────────────────────────────
    private List<TickerStats> byTicker;

    // ── Período ───────────────────────────────────────────────────
    private String periodFrom;
    private String periodTo;
    private int    importedCount;

    // ── Inner DTOs ────────────────────────────────────────────────

    @Data @Builder
    public static class TradeInfo {
        private String   ticker;
        private String   instrument;
        private String   type;
        private BigDecimal profitLoss;
        private BigDecimal openPrice;
        private BigDecimal closePrice;
        private BigDecimal volume;
        private String   openTime;
        private String   closeTime;
        private long     holdingHours;
        private String   closeOrigin;
    }

    @Data @Builder
    public static class TickerStats {
        private String     ticker;
        private String     instrument;
        private long       tradeCount;
        private BigDecimal totalPL;
        private BigDecimal avgPL;
        private BigDecimal totalCommissions;
        private BigDecimal winRate;
    }
}

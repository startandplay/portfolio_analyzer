package com.analytics.portfolio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agregação de todos os rendimentos de um portfólio.
 * <p>
 * Inclui:
 * - Dividendos (bruto, imposto retido, líquido) por asset e total
 * - Juros (free funds interest, bruto e líquido de imposto)
 * - Resumo de cash flows (depósitos, levantamentos, transferências)
 * - Total de income líquido
 */
@Data
@Builder
public class IncomeStatement {

    private Long portfolioId;
    private String portfolioName;
    private LocalDateTime generatedAt;

    // ── Dividendos ────────────────────────────────────────────────
    private BigDecimal totalDividendsGross;     // soma bruta de DIVIDEND
    private BigDecimal totalWithholdingTax;     // soma de WITHHOLDING_TAX (valor absoluto)
    private BigDecimal totalDividendsNet;       // gross - withholding

    /**
     * Detalhe por ativo
     */
    private List<DividendByAsset> dividendsByAsset;

    // ── Juros ─────────────────────────────────────────────────────
    private BigDecimal totalInterestGross;      // soma de INTEREST
    private BigDecimal totalInterestTax;        // soma de INTEREST_TAX (valor absoluto)
    private BigDecimal totalInterestNet;        // gross - tax

    // ── Total Income ──────────────────────────────────────────────
    /**
     * dividendsNet + interestNet
     */
    private BigDecimal totalIncomeNet;

    // ── Cash Flows ────────────────────────────────────────────────
    private BigDecimal totalDeposits;
    private BigDecimal totalWithdrawals;        // valor absoluto
    private BigDecimal totalTransfers;
    private BigDecimal netCashMovement;         // deposits - withdrawals + transfers

    /**
     * Detalhe de cash flows agrupado por tipo
     */
    private List<CashFlowSummary> cashFlowByType;

    // ── Inner DTOs ────────────────────────────────────────────────

    @Data
    @Builder
    public static class DividendByAsset {
        private Long assetId;
        private String ticker;
        private String instrument;
        private BigDecimal grossAmount;
        private BigDecimal taxAmount;           // valor absoluto
        private BigDecimal netAmount;
        private long paymentCount;
        /**
         * Yield on Cost = dividendo líquido / custo total investido no asset × 100
         * Null se não houver posição aberta com custo > 0
         */
        private BigDecimal yieldOnCost;
    }

    @Data
    @Builder
    public static class CashFlowSummary {
        private String type;               // TransactionType name
        private BigDecimal totalAmount;
        private long count;
    }
}
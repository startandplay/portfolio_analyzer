package com.analytics.portfolio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Posição fechada importada do XTB
 * <p>
 * Representa um trade completo (abertura + fecho) com todos os
 * custos reais: comissão, swap/rollover, conversão de moeda.
 * <p>
 * Colunas XTB:
 * Instrument, Category, Ticker, Type, Volume, Open Price, Open Time (UTC),
 * Close Price, Close Time (UTC), Product, Profit/Loss, Gross Profit,
 * Purchase Value, Sale Value, Stop Loss, Take Profit, Commission,
 * Margin, Swap, Rollover, Open Conversion Rate, Close Conversion Rate,
 * Close Origin, Position ID, Comment
 */
@Entity
@Table(name = "closed_positions", indexes = {
        @Index(name = "idx_cp_portfolio", columnList = "portfolio_id"),
        @Index(name = "idx_cp_ticker", columnList = "ticker"),
        @Index(name = "idx_cp_position_id", columnList = "position_id", unique = true),
        @Index(name = "idx_cp_close_time", columnList = "close_time")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosedPosition implements Fingerprintable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Portfolio ──────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    @JsonIgnoreProperties({"transactions", "positions", "dividends", "cashFlows", "closedPositions"})
    private Portfolio portfolio;

    // ── Identificação ─────────────────────────────────────
    /**
     * ID único da posição no XTB
     */
    @Column(name = "position_id", unique = true, length = 50)
    private String positionId;

    @Column(name = "instrument", length = 100)
    private String instrument;     // ex: "Apple"

    @Column(name = "ticker", length = 30)
    private String ticker;         // ex: "AAPL.US"

    @Column(name = "category", length = 50)
    private String category;       // ex: "Stocks", "Forex", "Crypto"

    @Column(name = "product", length = 50)
    private String product;        // ex: "Real", "CFD"

    /**
     * BUY ou SELL
     */
    @Column(name = "type", length = 10)
    private String type;

    // ── Trade Details ──────────────────────────────────────
    @Column(name = "volume", precision = 19, scale = 6)
    private BigDecimal volume;

    @Column(name = "open_price", precision = 19, scale = 6)
    private BigDecimal openPrice;

    @Column(name = "close_price", precision = 19, scale = 6)
    private BigDecimal closePrice;

    @Column(name = "open_time")
    private LocalDateTime openTime;

    @Column(name = "close_time")
    private LocalDateTime closeTime;

    // ── Valores financeiros ────────────────────────────────
    /**
     * Valor de compra total (openPrice × volume)
     */
    @Column(name = "purchase_value", precision = 19, scale = 4)
    private BigDecimal purchaseValue;

    /**
     * Valor de venda total (closePrice × volume)
     */
    @Column(name = "sale_value", precision = 19, scale = 4)
    private BigDecimal saleValue;

    /**
     * Lucro bruto antes de custos
     */
    @Column(name = "gross_profit", precision = 19, scale = 4)
    private BigDecimal grossProfit;

    /**
     * Lucro líquido (após comissão, swap, rollover)
     */
    @Column(name = "profit_loss", precision = 19, scale = 4)
    private BigDecimal profitLoss;

    // ── Custos reais ───────────────────────────────────────
    @Column(name = "commission", precision = 19, scale = 4)
    private BigDecimal commission;

    /**
     * Custo de financiamento overnight
     */
    @Column(name = "swap", precision = 19, scale = 4)
    private BigDecimal swap;

    /**
     * Rollover (extensão de posições)
     */
    @Column(name = "rollover", precision = 19, scale = 4)
    private BigDecimal rollover;

    /**
     * Margem utilizada
     */
    @Column(name = "margin", precision = 19, scale = 4)
    private BigDecimal margin;

    // ── Risk Management ────────────────────────────────────
    @Column(name = "stop_loss", precision = 19, scale = 6)
    private BigDecimal stopLoss;

    @Column(name = "take_profit", precision = 19, scale = 6)
    private BigDecimal takeProfit;

    // ── Conversão de moeda ─────────────────────────────────
    @Column(name = "open_conversion_rate", precision = 19, scale = 6)
    private BigDecimal openConversionRate;

    @Column(name = "close_conversion_rate", precision = 19, scale = 6)
    private BigDecimal closeConversionRate;

    /**
     * Como a posição foi fechada: Manual, SL, TP, etc
     */
    @Column(name = "close_origin", length = 50)
    private String closeOrigin;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "import_source", length = 20)
    @Builder.Default
    private String importSource = "XTB";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "import_fingerprint", unique = true, length = 64)
    private String importFingerprint;

    @PrePersist
    @PreUpdate
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        if (importFingerprint == null) {
            importFingerprint = generateFingerprint();
        }

    }

    @Override
    public String generateFingerprint() {
        return new GenerateFingerprint(positionId, openPrice, openTime, portfolio.getId()).generate();
    }

    private String nullSafe(Object o) {
        return o != null ? o.toString() : "null";
    }

    // ── Campos calculados (não persistidos) ────────────────

    /**
     * Custo total real = comissão + swap + rollover
     */
    @Transient
    public BigDecimal getTotalCosts() {
        BigDecimal c = commission != null ? commission.abs() : BigDecimal.ZERO;
        BigDecimal s = swap != null ? swap.abs() : BigDecimal.ZERO;
        BigDecimal r = rollover != null ? rollover.abs() : BigDecimal.ZERO;
        return c.add(s).add(r);
    }

    /**
     * ROI da posição = profitLoss / purchaseValue * 100
     */
    @Transient
    public BigDecimal getRoi() {
        if (purchaseValue == null || purchaseValue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return profitLoss.divide(purchaseValue.abs(), 6, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /**
     * Duração do trade em horas
     */
    @Transient
    public long getHoldingHours() {
        if (openTime == null || closeTime == null) return 0;
        return java.time.Duration.between(openTime, closeTime).toHours();
    }

    /**
     * Posição foi lucrativa?
     */
    @Transient
    public boolean isWinner() {
        return profitLoss != null && profitLoss.compareTo(BigDecimal.ZERO) > 0;
    }
}

package com.analytics.portfolio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividends")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class
Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    @JsonIgnoreProperties({"transactions", "positions", "dividends", "hibernateLazyInitializer", "handler"})
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    @JsonIgnoreProperties({"transactions", "positions", "dividends", "hibernateLazyInitializer", "handler"})
    private Asset asset;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "ex_dividend_date")
    private LocalDate exDividendDate;

    @Column(name = "amount_per_share", nullable = false)
    private BigDecimal amountPerShare;

    @Column(name = "shares_owned", nullable = false)
    private BigDecimal sharesOwned;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "tax_withheld")
    private BigDecimal taxWithheld;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    private String currency;

    @Column(name = "import_source")
    private String importSource; // XTB, BINANCE, MANUAL

    @Column(name = "external_id")
    private String externalId;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (totalAmount == null && amountPerShare != null && sharesOwned != null) {
            totalAmount = amountPerShare.multiply(sharesOwned);
        }
        if (netAmount == null) {
            netAmount = totalAmount;
            if (taxWithheld != null) {
                netAmount = netAmount.subtract(taxWithheld);
            }
        }
    }
}

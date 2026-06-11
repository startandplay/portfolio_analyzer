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
@Builder(toBuilder = true)
public class Dividend implements Fingerprintable {

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
    private LocalDateTime paymentDate;

    @Column(name = "ex_dividend_date")
    private LocalDate exDividendDate;

    @Column(name = "amount_per_share", precision = 6, scale = 4, nullable = false)
    private BigDecimal amountPerShare;

    @Column(name = "shares_owned", nullable = false)
    private BigDecimal sharesOwned;

    @Column(name = "total_amount", precision = 6, scale = 3, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "tax_withheld")
    private BigDecimal taxWithheld;

    @Column(name = "tax_percentage", nullable = false)
    private BigDecimal taxPercentage;

    @Column(name = "net_amount" , precision = 6, scale = 3)
    private BigDecimal netAmount;

    private String currency;

    @Column(name = "import_source")
    private String importSource; // XTB, BINANCE, MANUAL

    @Column(name = "external_id")
    private String externalId;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "import_fingerprint", unique = true, length = 64)
    private String importFingerprint;

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

        if (importFingerprint == null) {
            importFingerprint = generateFingerprint();
        }

    }

    @Override
    public String generateFingerprint() {
        return new GenerateFingerprint(externalId, amountPerShare, paymentDate, null, portfolio.getId()).generate();
    }
}

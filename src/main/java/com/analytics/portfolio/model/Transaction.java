package com.analytics.portfolio.model;

import com.analytics.portfolio.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Transaction  implements Fingerprintable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    @JsonIgnore
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    @JsonBackReference
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type; // BUY, SELL, DIVIDEND

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal price;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 3)
    private BigDecimal totalAmount; // quantity * price

    private BigDecimal fees;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "tax_percentage", nullable = false)
    private BigDecimal taxPercentage;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "import_source")
    private String importSource; // XTB, BINANCE, MANUAL

    @Column(name = "external_id")
    private String externalId; // ID from XTB or Binance

    @Column(name = "import_fingerprint", unique = true, length = 64)
    private String importFingerprint;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        if (totalAmount == null && quantity != null && price != null) {
            totalAmount = quantity.multiply(price);
            if (fees != null) {
                totalAmount = totalAmount.add(fees);
            }
        }

        if (importFingerprint == null) {
            importFingerprint = generateFingerprint();
        }
    }

    @Override
    public String generateFingerprint() {
        return  new GenerateFingerprint(externalId, totalAmount, transactionDate, null, portfolio.getId()).generate();
    }

}

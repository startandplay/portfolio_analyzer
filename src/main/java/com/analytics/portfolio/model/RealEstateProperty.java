package com.analytics.portfolio.model;

import com.analytics.portfolio.enums.PropertyStatus;
import com.analytics.portfolio.enums.PropertyStrategy;
import com.analytics.portfolio.enums.PropertyType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "real_estate_properties", indexes = {
        @Index(name = "idx_re_property_portfolio", columnList = "portfolio_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RealEstateProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyStrategy strategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PropertyStatus status = PropertyStatus.ACTIVE;

    // ── Aquisição ──
    @Column(name = "purchase_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_date", nullable = false)
    private LocalDateTime purchaseDate;

    // ── Financiamento (null = comprado a dinheiro) ──
    @Column(name = "down_payment_amount", precision = 19, scale = 4)
    private BigDecimal downPaymentAmount;

    @Column(name = "loan_amount", precision = 19, scale = 4)
    private BigDecimal loanAmount;

    @Column(name = "interest_rate_percentage", precision = 6, scale = 3)
    private BigDecimal interestRatePercentage;

    // ── Avaliação corrente ──
    @Column(name = "current_estimated_value", precision = 19, scale = 4)
    private BigDecimal currentEstimatedValue;

    @Column(name = "last_valuation_date")
    private LocalDateTime lastValuationDate;

    // ── Venda (preenchido quando status = SOLD) ──
    @Column(name = "sale_price", precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "sale_date")
    private LocalDateTime saleDate;

    @Column(length = 10)
    @Builder.Default
    private String currency = "EUR";

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    public boolean isFinanced() {
        return loanAmount != null && loanAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
package com.analytics.portfolio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {

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

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "average_buy_price", precision = 19, scale = 3, nullable = false)
    private BigDecimal averageBuyPrice;

    @Column(name = "current_price", precision = 19, scale = 3)
    private BigDecimal currentPrice;

    @Column(name = "total_invested", precision = 19, scale = 3, nullable = false)
    private BigDecimal totalInvested;

    @Column(name = "current_value", precision = 19, scale = 3)
    private BigDecimal currentValue;

    @Column(name = "unrealized_pl", precision = 19, scale = 3)
    private BigDecimal unrealizedPL; // Profit/Loss

    @Column(name = "unrealized_pl_percentage", precision = 19, scale = 3)
    private BigDecimal unrealizedPLPercentage;

    @Column(name = "realized_pl", precision = 19, scale = 3)
    private BigDecimal realizedPL;

    @Column(name = "total_dividends_received", precision = 19, scale = 3)
    private BigDecimal totalDividendsReceived;

    @Column(name = "first_purchase_date")
    private LocalDateTime firstPurchaseDate;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    protected void calculateFields() {
        if (currentPrice != null && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
            currentValue = currentPrice.multiply(quantity);
            // the base calculation should not be based on the totalInvested only. because some assets may already have been sold
            unrealizedPL = currentValue.subtract(totalInvested);
            unrealizedPLPercentage = unrealizedPL
                    .divide(totalInvested, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        lastUpdated = LocalDateTime.now();
    }
}

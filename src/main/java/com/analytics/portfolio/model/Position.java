package com.analytics.portfolio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    @Column(name = "average_buy_price", nullable = false)
    private BigDecimal averageBuyPrice;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "total_invested", nullable = false)
    private BigDecimal totalInvested;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    @Column(name = "unrealized_pl")
    private BigDecimal unrealizedPL; // Profit/Loss

    @Column(name = "unrealized_pl_percentage")
    private BigDecimal unrealizedPLPercentage;

    @Column(name = "realized_pl")
    private BigDecimal realizedPL;

    @Column(name = "total_dividends_received")
    private BigDecimal totalDividendsReceived;

    @Column(name = "first_purchase_date")
    private LocalDateTime firstPurchaseDate;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    protected void calculateFields() {
        if (currentPrice != null && quantity != null) {
            currentValue = currentPrice.multiply(quantity);
            unrealizedPL = currentValue.subtract(totalInvested);
            
            if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
                unrealizedPLPercentage = unrealizedPL
                    .divide(totalInvested, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            }
        }
        lastUpdated = LocalDateTime.now();
    }
}

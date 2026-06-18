package com.analytics.portfolio.model;

import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.enums.AssetType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticker;

    @Column(nullable = false, unique = true)
    private String symbol;

    private String instrument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType type; // STOCK, CRYPTO, ETF, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PortfolioSource source; // XTB, BINANCE, MANUAL

    private BigDecimal currentPrice;

    private LocalDateTime lastPriceUpdate;

    private String exchange; // NASDAQ, NYSE, BINANCE, etc.

    private String sector;

    private String industry;

    @Column(name = "created_at", nullable = false)
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

}

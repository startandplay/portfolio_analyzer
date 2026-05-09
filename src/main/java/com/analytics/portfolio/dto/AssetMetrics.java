package com.analytics.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetMetrics {

    private Long assetId;
    private String ticker;
    private String symbol;
    private String instrument;
    private String assetType;
    
    // Posição atual
    private BigDecimal quantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal totalInvested;
    private BigDecimal currentValue;
    
    // Retornos
    private BigDecimal unrealizedPL;
    private BigDecimal unrealizedPLPercentage;
    private BigDecimal realizedPL;
    private BigDecimal totalPL; // unrealized + realized
    private BigDecimal totalPLPercentage;
    
    // Retornos anualizados
    private BigDecimal annualizedReturn;
    private BigDecimal annualizedReturnPercentage;
    
    // Dividendos
    private BigDecimal totalDividends;
    private BigDecimal dividendYield;
    private BigDecimal annualizedDividendYield;
    
    // Retorno total (ganho de capital + dividendos)
    private BigDecimal totalReturnWithDividends;
    private BigDecimal totalReturnWithDividendsPercentage;
    
    // Peso no portfólio
    private BigDecimal portfolioWeight; // % do valor total do portfólio
    
    // Métricas de risco
    private BigDecimal volatility;
    private BigDecimal beta;
    private BigDecimal sharpeRatio;
    
    // Informações temporais
    private LocalDateTime firstPurchaseDate;
    private Integer daysHeld;
    private LocalDateTime lastTransactionDate;
    
    // Transações
    private Integer totalBuyTransactions;
    private Integer totalSellTransactions;
    private BigDecimal totalBuyVolume;
    private BigDecimal totalSellVolume;
    
    // Fees
    private BigDecimal totalFees;
}

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
public class PortfolioMetrics {

    private Long portfolioId;
    private String portfolioName;
    
    // Valores atuais
    private BigDecimal totalInvested;
    private BigDecimal currentValue;
    private BigDecimal cashBalance;
    private BigDecimal totalValue; // currentValue + cashBalance
    
    // Retornos
    private BigDecimal totalReturn; // Valor absoluto
    private BigDecimal totalReturnPercentage; // %
    private BigDecimal annualizedReturn; // % anualizado
    private BigDecimal monthlyReturn; // % último mês
    private BigDecimal yearToDateReturn; // % YTD
    
    // Dividendos
    private BigDecimal totalDividendsReceived;
    private BigDecimal dividendYield; // % anual baseado em dividendos recebidos
    private BigDecimal annualizedDividendYield;
    
    // Métricas de Risco
    private BigDecimal volatility; // Desvio padrão anualizado
    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;
    private BigDecimal maxDrawdown; // Maior queda desde o pico
    private BigDecimal currentDrawdown;
    private BigDecimal beta; // Em relação ao mercado (se aplicável)
    
    // Análise temporal
    private Integer daysInvested;
    private LocalDateTime firstInvestmentDate;
    private LocalDateTime lastUpdateDate;
    
    // Composição
    private Integer numberOfPositions;
    private Integer numberOfAssets;
    
    // Ganhos/Perdas
    private BigDecimal realizedGains;
    private BigDecimal unrealizedGains;
    private BigDecimal totalGains; // realizedGains + unrealizedGains
    
    // Taxas e custos
    private BigDecimal totalFees;
    private BigDecimal totalTaxes;
    
    // Performance ajustada
    private BigDecimal netReturn; // Retorno após taxas e impostos
    private BigDecimal netReturnPercentage;
    
    // Money-weighted return (TWR)
    private BigDecimal timeWeightedReturn;
    private BigDecimal moneyWeightedReturn; // IRR
}

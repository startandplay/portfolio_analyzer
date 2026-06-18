package com.analytics.portfolio.dto;

import com.analytics.portfolio.enums.PortfolioSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Visão resumida de um portfolio — usada em listagens.
 * Evita carregar todas as posições/transações desnecessariamente.
 */
@Data
@Builder
public class PortfolioSummary {

    private Long id;
    private String name;
    private String description;

    private PortfolioSource source;
    private String currency;
    private boolean includeInAggregate;

    // Valores calculados (do último recalculo de posições)
    private BigDecimal totalInvested;
    private BigDecimal currentValue;
    private BigDecimal totalReturn;
    private BigDecimal totalReturnPercentage;

    // Dividendos/income acumulado
    private BigDecimal totalIncome;

    // Composição
    private int numberOfPositions;
    private int numberOfTransactions;

    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
}

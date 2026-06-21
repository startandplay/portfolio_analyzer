package com.analytics.portfolio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

public class RealEstateMetrics {

    @Data @Builder
    public static class RentalMetrics {
        private Long propertyId;
        private BigDecimal grossAnnualRent;
        private BigDecimal operatingExpensesAnnual;
        private BigDecimal noi;                    // Net Operating Income
        private BigDecimal capRate;                 // NOI / valor atual
        private BigDecimal grossRentalYield;         // renda anual / preço compra
        private BigDecimal annualDebtService;        // soma de MORTGAGE_PAYMENT
        private BigDecimal dscr;                     // NOI / serviço da dívida
        private BigDecimal cashInvested;              // entrada + custos de aquisição
        private BigDecimal annualCashFlow;            // NOI - serviço da dívida
        private BigDecimal cashOnCashReturn;          // cash flow anual / cash investido
        private BigDecimal totalReturnPercentage;     // cash flow acumulado + valorização
    }

    @Data @Builder
    public static class FlipMetrics {
        private Long propertyId;
        private BigDecimal acquisitionCost;
        private BigDecimal renovationCost;
        private BigDecimal holdingCost;
        private BigDecimal sellingCost;
        private BigDecimal totalProjectCost;
        private BigDecimal saleProceeds;
        private boolean saleProjected;                // true se ainda não vendido
        private BigDecimal profit;
        private BigDecimal cashInvested;
        private BigDecimal roi;
        private long daysHeld;
        private BigDecimal annualizedRoi;
    }
}
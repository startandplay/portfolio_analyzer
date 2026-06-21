package com.analytics.portfolio.service;

import com.analytics.portfolio.dto.RealEstateMetrics;
import com.analytics.portfolio.enums.RealEstateTransactionType;
import com.analytics.portfolio.model.RealEstateProperty;
import com.analytics.portfolio.repository.RealEstatePropertyRepository;
import com.analytics.portfolio.repository.RealEstateTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.analytics.portfolio.enums.RealEstateTransactionType.*;

@Service
@RequiredArgsConstructor
public class RealEstateMetricsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 6;

    private static final List<RealEstateTransactionType> INCOME_TYPES =
            List.of(RENT, OTHER_INCOME);

    private static final List<RealEstateTransactionType> OPERATING_EXPENSE_TYPES =
            List.of(INSURANCE, PROPERTY_TAX, MAINTENANCE, MANAGEMENT_FEE, UTILITIES, HOA, OTHER_EXPENSE);

    private static final List<RealEstateTransactionType> ACQUISITION_COST_TYPES =
            List.of(CLOSING_COST_PURCHASE, RENOVATION);

    private static final List<RealEstateTransactionType> FLIP_HOLDING_COST_TYPES =
            List.of(INSURANCE, PROPERTY_TAX, UTILITIES, HOA, MORTGAGE_PAYMENT);

    private final RealEstatePropertyRepository propertyRepository;
    private final RealEstateTransactionRepository transactionRepository;

    // ══════════════════════════════════════
    // RENTAL
    // ══════════════════════════════════════

    @Transactional(readOnly = true)
    public RealEstateMetrics.RentalMetrics calculateRentalMetrics(Long propertyId) {
        RealEstateProperty property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property não encontrado: " + propertyId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trailing12mStart = now.minusMonths(12);

        BigDecimal grossAnnualRent = transactionRepository
                .sumByTypesAndPeriod(propertyId, INCOME_TYPES, trailing12mStart, now);

        BigDecimal operatingExpensesAnnual = transactionRepository
                .sumByTypesAndPeriod(propertyId, OPERATING_EXPENSE_TYPES, trailing12mStart, now)
                .abs();

        BigDecimal annualDebtService = transactionRepository
                .sumByTypesAndPeriod(propertyId, List.of(MORTGAGE_PAYMENT), trailing12mStart, now)
                .abs();

        BigDecimal noi = grossAnnualRent.subtract(operatingExpensesAnnual);

        BigDecimal capRate = percentageOf(noi, property.getCurrentEstimatedValue());
        BigDecimal grossRentalYield = percentageOf(grossAnnualRent, property.getPurchasePrice());

        BigDecimal dscr = annualDebtService.compareTo(BigDecimal.ZERO) > 0
                ? noi.divide(annualDebtService, SCALE, RoundingMode.HALF_UP)
                : null;

        BigDecimal acquisitionCosts = transactionRepository
                .sumByTypes(propertyId, ACQUISITION_COST_TYPES).abs();

        BigDecimal baseInvestment = property.isFinanced()
                ? property.getDownPaymentAmount()
                : property.getPurchasePrice();

        BigDecimal cashInvested = baseInvestment.add(acquisitionCosts);

        BigDecimal annualCashFlow = noi.subtract(annualDebtService);

        BigDecimal cashOnCashReturn = cashInvested.compareTo(BigDecimal.ZERO) > 0
                ? annualCashFlow.divide(cashInvested, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED)
                : BigDecimal.ZERO;

        // ── Retorno total desde a compra (cash flow acumulado + valorização) ──
        BigDecimal cumulativeIncome = transactionRepository
                .sumByTypesAndPeriod(propertyId, INCOME_TYPES, property.getPurchaseDate(), now);
        BigDecimal cumulativeOutflows = transactionRepository
                .sumByTypesAndPeriod(propertyId,
                        List.of(INSURANCE, PROPERTY_TAX, MAINTENANCE, MANAGEMENT_FEE,
                                UTILITIES, HOA, OTHER_EXPENSE, MORTGAGE_PAYMENT),
                        property.getPurchaseDate(), now).abs();

        BigDecimal cumulativeCashFlow = cumulativeIncome.subtract(cumulativeOutflows);
        BigDecimal appreciation = property.getCurrentEstimatedValue().subtract(property.getPurchasePrice());
        BigDecimal totalReturn = cumulativeCashFlow.add(appreciation);
        BigDecimal totalReturnPercentage = percentageOf(totalReturn, cashInvested);

        return RealEstateMetrics.RentalMetrics.builder()
                .propertyId(propertyId)
                .grossAnnualRent(grossAnnualRent)
                .operatingExpensesAnnual(operatingExpensesAnnual)
                .noi(noi)
                .capRate(capRate)
                .grossRentalYield(grossRentalYield)
                .annualDebtService(annualDebtService)
                .dscr(dscr)
                .cashInvested(cashInvested)
                .annualCashFlow(annualCashFlow)
                .cashOnCashReturn(cashOnCashReturn)
                .totalReturnPercentage(totalReturnPercentage)
                .build();
    }

    // ══════════════════════════════════════
    // FIX & FLIP
    // ══════════════════════════════════════

    @Transactional(readOnly = true)
    public RealEstateMetrics.FlipMetrics calculateFlipMetrics(Long propertyId) {
        RealEstateProperty property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property não encontrado: " + propertyId));

        BigDecimal closingCostPurchase = transactionRepository
                .sumByTypes(propertyId, List.of(CLOSING_COST_PURCHASE)).abs();
        BigDecimal acquisitionCost = property.getPurchasePrice().add(closingCostPurchase);

        BigDecimal renovationCost = transactionRepository
                .sumByTypes(propertyId, List.of(RENOVATION)).abs();

        BigDecimal holdingCost = transactionRepository
                .sumByTypes(propertyId, FLIP_HOLDING_COST_TYPES).abs();

        BigDecimal sellingCost = transactionRepository
                .sumByTypes(propertyId, List.of(CLOSING_COST_SALE)).abs();

        BigDecimal totalProjectCost = acquisitionCost.add(renovationCost).add(holdingCost).add(sellingCost);

        boolean sold = property.getSalePrice() != null;
        BigDecimal saleProceeds = sold ? property.getSalePrice() : property.getCurrentEstimatedValue();

        BigDecimal profit = saleProceeds.subtract(totalProjectCost);

        // Assume-se que reno + holding + selling saem sempre de capital próprio,
        // mesmo que a aquisição tenha sido financiada
        BigDecimal cashInvested = property.isFinanced()
                ? property.getDownPaymentAmount().add(closingCostPurchase).add(renovationCost).add(holdingCost).add(sellingCost)
                : totalProjectCost;

        BigDecimal roi = percentageOf(profit, cashInvested);

        LocalDateTime endDate = sold ? property.getSaleDate() : LocalDateTime.now();
        long daysHeld = ChronoUnit.DAYS.between(property.getPurchaseDate(), endDate);

        BigDecimal annualizedRoi = calculateAnnualizedReturn(roi, daysHeld);

        return RealEstateMetrics.FlipMetrics.builder()
                .propertyId(propertyId)
                .acquisitionCost(acquisitionCost)
                .renovationCost(renovationCost)
                .holdingCost(holdingCost)
                .sellingCost(sellingCost)
                .totalProjectCost(totalProjectCost)
                .saleProceeds(saleProceeds)
                .saleProjected(!sold)
                .profit(profit)
                .cashInvested(cashInvested)
                .roi(roi)
                .daysHeld(daysHeld)
                .annualizedRoi(annualizedRoi)
                .build();
    }

    // ══════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════

    private BigDecimal percentageOf(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    /** Mesma fórmula composta usada em MetricsCalculationService */
    private BigDecimal calculateAnnualizedReturn(BigDecimal returnPercentage, long days) {
        if (days <= 0) return BigDecimal.ZERO;

        BigDecimal returnDecimal = returnPercentage.divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal onePlusReturn = BigDecimal.ONE.add(returnDecimal);
        double exponent = 365.25 / days;
        double annualizedFactor = Math.pow(onePlusReturn.doubleValue(), exponent);

        return BigDecimal.valueOf(annualizedFactor)
                .subtract(BigDecimal.ONE)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }
}

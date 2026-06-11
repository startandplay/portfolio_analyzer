package com.analytics.portfolio.service;

import com.analytics.portfolio.dto.IncomeStatement;
import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.Portfolio;
import com.analytics.portfolio.repository.CashFlowRepository;
import com.analytics.portfolio.repository.PortfolioRepository;
import com.analytics.portfolio.repository.PositionRepository;
import com.analytics.portfolio.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula o IncomeStatement de um portfólio:
 * - Dividendos (bruto / WHT / líquido) por asset e total
 * - Juros (bruto / imposto / líquido)
 * - Total income líquido
 * - Resumo de cash flows (depósitos, levantamentos, transferências)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IncomeService {

    private static final List<TransactionType> DIVIDEND_TYPES =
            List.of(TransactionType.DIVIDEND);

    private static final List<TransactionType> WHT_TYPES =
            List.of(TransactionType.WITHHOLDING_TAX);

    private static final List<TransactionType> INTEREST_TYPES =
            List.of(TransactionType.INTEREST);

    private static final List<TransactionType> INTEREST_TAX_TYPES =
            List.of(TransactionType.INTEREST_TAX);

    private final TransactionRepository transactionRepository;
    private final CashFlowRepository cashFlowRepository;
    private final PositionRepository positionRepository;
    private final PortfolioRepository portfolioRepository;

    @Transactional(readOnly = true)
    public IncomeStatement getIncomeStatement(Long portfolioId) {

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio não encontrado: " + portfolioId));

        // ── 1. Dividendos ─────────────────────────────────────────
        BigDecimal dividendsGross = transactionRepository
                .sumAmountByPortfolioAndTypes(portfolioId, DIVIDEND_TYPES);

        BigDecimal withholdingTax = transactionRepository
                .sumAmountByPortfolioAndTypes(portfolioId, WHT_TYPES).abs();

        BigDecimal dividendsNet = dividendsGross.subtract(withholdingTax);

        // ── 2. Dividendos por asset ───────────────────────────────
        List<IncomeStatement.DividendByAsset> dividendsByAsset =
                buildDividendsByAsset(portfolioId);

        // ── 3. Juros ──────────────────────────────────────────────
        BigDecimal interestGross = cashFlowRepository
                .sumAmountByPortfolioAndTypes(portfolioId, INTEREST_TYPES);

        BigDecimal interestTax = cashFlowRepository
                .sumAmountByPortfolioAndTypes(portfolioId, INTEREST_TAX_TYPES).abs();

        BigDecimal interestNet = interestGross.subtract(interestTax);

        // ── 4. Total income líquido ───────────────────────────────
        BigDecimal totalIncomeNet = dividendsNet.add(interestNet);

        // ── 5. Cash flows ─────────────────────────────────────────
        BigDecimal totalDeposits = cashFlowRepository.getTotalDeposits(portfolioId);
        BigDecimal totalWithdrawals = cashFlowRepository.getTotalWithdrawals(portfolioId);
        BigDecimal totalTransfers = cashFlowRepository.getTotalTransfers(portfolioId);
        BigDecimal netCash = cashFlowRepository.getNetCashMovement(portfolioId);

        List<IncomeStatement.CashFlowSummary> cashFlowByType =
                buildCashFlowSummary(portfolioId);

        log.info("IncomeStatement calculado para portfolio {}: incomeNet={}, dividendsNet={}, interestNet={}",
                portfolioId, totalIncomeNet, dividendsNet, interestNet);

        return IncomeStatement.builder()
                .portfolioId(portfolioId)
                .portfolioName(portfolio.getName())
                .generatedAt(LocalDateTime.now())
                // dividends
                .totalDividendsGross(dividendsGross)
                .totalWithholdingTax(withholdingTax)
                .totalDividendsNet(dividendsNet)
                .dividendsByAsset(dividendsByAsset)
                // interest
                .totalInterestGross(interestGross)
                .totalInterestTax(interestTax)
                .totalInterestNet(interestNet)
                // totals
                .totalIncomeNet(totalIncomeNet)
                // cash flows
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .totalTransfers(totalTransfers)
                .netCashMovement(netCash)
                .cashFlowByType(cashFlowByType)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Constrói lista de dividendos por asset com WHT e yield on cost.
     */
    private List<IncomeStatement.DividendByAsset> buildDividendsByAsset(Long portfolioId) {

        // Dividendos brutos por asset
        List<Object[]> grossRows = transactionRepository
                .getDividendsGroupedByAsset(portfolioId);

        // WHT por asset → map assetId -> taxAmount
        Map<Long, BigDecimal> whtMap = new HashMap<>();
        transactionRepository.getWithholdingTaxGroupedByAsset(portfolioId)
                .forEach(row -> {
                    Long assetId = ((Number) row[0]).longValue();
                    BigDecimal tax = (BigDecimal) row[1];
                    whtMap.put(assetId, tax != null ? tax.abs() : BigDecimal.ZERO);
                });

        // Custo total por asset (para yield on cost)
        Map<Long, BigDecimal> costMap = new HashMap<>();
        positionRepository.findByPortfolioId(portfolioId)
                .forEach(p -> costMap.put(p.getAsset().getId(), p.getTotalInvested()));

        List<IncomeStatement.DividendByAsset> result = new ArrayList<>();

        for (Object[] row : grossRows) {
            Long assetId = ((Number) row[0]).longValue();
            String ticker = (String) row[1];
            String instrument = (String) row[2];
            BigDecimal gross = (BigDecimal) row[3];
            long count = ((Number) row[4]).longValue();

            if (gross == null) gross = BigDecimal.ZERO;

            BigDecimal tax = whtMap.getOrDefault(assetId, BigDecimal.ZERO);
            BigDecimal net = gross.subtract(tax);

            // Yield on Cost = net dividends / custo da posição * 100
            BigDecimal yieldOnCost = null;
            BigDecimal cost = costMap.get(assetId);
            if (cost != null && cost.compareTo(BigDecimal.ZERO) > 0) {
                yieldOnCost = net.divide(cost, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            result.add(IncomeStatement.DividendByAsset.builder()
                    .assetId(assetId)
                    .ticker(ticker)
                    .instrument(instrument)
                    .grossAmount(gross.setScale(4, RoundingMode.HALF_UP))
                    .taxAmount(tax.setScale(4, RoundingMode.HALF_UP))
                    .netAmount(net.setScale(4, RoundingMode.HALF_UP))
                    .paymentCount(count)
                    .yieldOnCost(yieldOnCost)
                    .build());
        }

        return result;
    }

    /**
     * Constrói resumo de cash flows agrupado por tipo.
     */
    private List<IncomeStatement.CashFlowSummary> buildCashFlowSummary(Long portfolioId) {

        List<Object[]> rows = cashFlowRepository.getCashFlowSummaryByType(portfolioId);
        List<IncomeStatement.CashFlowSummary> result = new ArrayList<>();

        for (Object[] row : rows) {
            String type = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();

            result.add(IncomeStatement.CashFlowSummary.builder()
                    .type(type)
                    .totalAmount(amount != null
                            ? amount.setScale(4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO)
                    .count(count)
                    .build());
        }

        return result;
    }
}

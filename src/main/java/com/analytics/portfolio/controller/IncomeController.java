package com.analytics.portfolio.controller;

import com.analytics.portfolio.dto.IncomeStatement;
import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.CashFlow;
import com.analytics.portfolio.model.Transaction;
import com.analytics.portfolio.repository.CashFlowRepository;
import com.analytics.portfolio.repository.TransactionRepository;
import com.analytics.portfolio.service.IncomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de rendimentos (income) de um portfólio.
 * <p>
 * GET /api/portfolios/{id}/income              → IncomeStatement completo
 * GET /api/portfolios/{id}/income/dividends    → apenas transações de dividendos
 * GET /api/portfolios/{id}/income/interest     → apenas transações de juros
 */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/income")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Income", description = "Dividends, interest and cash flow statements")
public class IncomeController {

    private final IncomeService incomeService;
    private final TransactionRepository transactionRepository;
    private final CashFlowRepository cashFlowRepository;

    // ── Statement completo ────────────────────────────────────────

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(
            summary = "Full income statement",
            description = """
                    Returns a complete income breakdown for the portfolio:
                    - Dividends: gross, withholding tax, net — per asset and total
                    - Interest: gross, tax, net
                    - Total net income (dividends + interest)
                    - Cash flow summary (deposits, withdrawals, transfers) grouped by type
                    """
    )
    public ResponseEntity<IncomeStatement> getIncomeStatement(
            @PathVariable Long portfolioId) {

        log.info("Income statement solicitado para portfolio {}", portfolioId);
        IncomeStatement statement = incomeService.getIncomeStatement(portfolioId);
        return ResponseEntity.ok(statement);
    }

    // ── Detalhe de transações por categoria ───────────────────────

    @GetMapping("/dividends")
    @Transactional(readOnly = true)
    @Operation(
            summary = "Dividend transactions",
            description = "Returns all DIVIDEND and WITHHOLDING_TAX transactions, ordered by date desc"
    )
    public ResponseEntity<List<Transaction>> getDividendTransactions(
            @PathVariable Long portfolioId) {

        List<Transaction> txs = transactionRepository.findByPortfolioIdAndTypes(
                portfolioId,
                List.of(TransactionType.DIVIDEND, TransactionType.WITHHOLDING_TAX)
        );
        return ResponseEntity.ok(txs);
    }

    @GetMapping("/interest")
    @Transactional(readOnly = true)
    @Operation(
            summary = "Interest transactions",
            description = "Returns all INTEREST and INTEREST_TAX transactions, ordered by date desc"
    )
    public ResponseEntity<List<CashFlow>> getInterestTransactions(
            @PathVariable Long portfolioId) {

        List<CashFlow> txs = cashFlowRepository.findByPortfolioIdAndTypes(
                portfolioId,
                List.of(TransactionType.INTEREST, TransactionType.INTEREST_TAX)
        );
        return ResponseEntity.ok(txs);
    }

    @GetMapping("/dividends/by-asset")
    @Transactional(readOnly = true)
    @Operation(
            summary = "Dividends grouped by asset",
            description = "Returns the dividend summary per asset (gross, WHT, net, yield on cost)"
    )
    public ResponseEntity<List<IncomeStatement.DividendByAsset>> getDividendsByAsset(
            @PathVariable Long portfolioId) {

        IncomeStatement statement = incomeService.getIncomeStatement(portfolioId);
        return ResponseEntity.ok(statement.getDividendsByAsset());
    }
}
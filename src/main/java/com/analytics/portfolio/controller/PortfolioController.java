package com.analytics.portfolio.controller;

import com.analytics.portfolio.dto.*;
import com.analytics.portfolio.integration.BinanceImportService;
import com.analytics.portfolio.integration.XTBImportService;
import com.analytics.portfolio.model.*;
import com.analytics.portfolio.repository.CashFlowRepository;
import com.analytics.portfolio.repository.DividendRepository;
import com.analytics.portfolio.repository.PositionRepository;
import com.analytics.portfolio.repository.TransactionRepository;
import com.analytics.portfolio.security.CurrentUserResolver;
import com.analytics.portfolio.service.MetricsCalculationService;
import com.analytics.portfolio.service.PortfolioService;
import com.analytics.portfolio.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio", description = "Portfolio management — user-scoped")
public class PortfolioController {

    private final PortfolioService        portfolioService;
    private final PositionRepository      positionRepository;
    private final TransactionRepository   transactionRepository;
    private final DividendRepository      dividendRepository;
    private final CashFlowRepository      cashFlowRepository;
    private final MetricsCalculationService metricsService;
    private final XTBImportService        xtbImportService;
    private final BinanceImportService    binanceImportService;
    private final PositionService         positionService;
    private final CurrentUserResolver userResolver;


    // ════════════════════════════════════════════════════════════
    // CRUD — todos os endpoints filtrados pelo user autenticado
    // ════════════════════════════════════════════════════════════

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List all portfolios for the authenticated user")
    public ResponseEntity<List<PortfolioSummary>> listPortfolios(
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        return ResponseEntity.ok(portfolioService.listPortfolios(user));
    }

    @PostMapping
    @Operation(summary = "Create a new portfolio for the authenticated user")
    public ResponseEntity<Portfolio> createPortfolio(
            @Valid @RequestBody CreatePortfolioRequest request,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        Portfolio created = portfolioService.createPortfolio(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get portfolio by ID — must belong to authenticated user")
    public ResponseEntity<Portfolio> getPortfolio(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        return ResponseEntity.ok(portfolioService.getPortfolio(id, user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update portfolio — must belong to authenticated user")
    public ResponseEntity<Portfolio> updatePortfolio(
            @PathVariable Long id,
            @Valid @RequestBody CreatePortfolioRequest request,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        return ResponseEntity.ok(portfolioService.updatePortfolio(id, request, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete portfolio and all its data")
    public ResponseEntity<Void> deletePortfolio(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.deletePortfolio(id, user);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════════════════════
    // Métricas individuais
    // ════════════════════════════════════════════════════════════

    @GetMapping("/{id}/metrics")
    @Transactional(readOnly = true)
    @Operation(summary = "Get portfolio metrics and analytics")
    public ResponseEntity<PortfolioMetrics> getPortfolioMetrics(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        Portfolio portfolio = portfolioService.getPortfolio(id, user);
        List<Position> positions = positionRepository.findByPortfolioId(id);
        PortfolioMetrics metrics = metricsService.calculatePortfolioMetrics(portfolio, positions);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/{id}/positions/metrics")
    @Transactional(readOnly = true)
    @Operation(summary = "Get metrics for all positions in a portfolio")
    public ResponseEntity<List<AssetMetrics>> getPositionsMetrics(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.getPortfolio(id, user); // ownership check

        List<Position> positions = positionRepository.findByPortfolioId(id);
        List<AssetMetrics> metricsList = positions.stream()
                .map(position -> {
                    List<Transaction> trades = transactionRepository
                            .findByPortfolioIdAndAssetId(id, position.getAsset().getId());
                    List<Dividend> dividends = dividendRepository
                            .findByPortfolioIdAndAssetId(id, position.getAsset().getId());
                    return metricsService.calculateAssetMetrics(position, trades, dividends);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(metricsList);
    }

    // ════════════════════════════════════════════════════════════
    // Métricas agregadas (cross-portfolio)
    // ════════════════════════════════════════════════════════════

    @GetMapping("/aggregate")
    @Transactional(readOnly = true)
    @Operation(
            summary = "Aggregate metrics across all user portfolios",
            description = """
            Returns consolidated metrics for all portfolios with includeInAggregate=true.
            Includes: total net worth, asset allocation by type and exchange,
            concentration risk, weighted CAGR, and per-portfolio breakdown.
            """
    )
    public ResponseEntity<AggregatePortfolioMetrics> getAggregateMetrics(
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        return ResponseEntity.ok(portfolioService.getAggregateMetrics(user));
    }

    // ════════════════════════════════════════════════════════════
    // Dados do portfolio
    // ════════════════════════════════════════════════════════════

    @GetMapping("/{id}/positions")
    @Transactional(readOnly = true)
    @Operation(summary = "Get all positions in portfolio")
    public ResponseEntity<List<Position>> getPortfolioPositions(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.getPortfolio(id, user); // ownership check
        return ResponseEntity.ok(positionRepository.findByPortfolioId(id));
    }

    @GetMapping("/{id}/transactions")
    @Transactional(readOnly = true)
    @Operation(summary = "Get all transactions")
    public ResponseEntity<List<Transaction>> getTransactions(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.getPortfolio(id, user); // ownership check
        return ResponseEntity.ok(
                transactionRepository.findByPortfolioIdOrderByTransactionDateDesc(id));
    }

    @GetMapping("/{id}/dividends")
    @Transactional(readOnly = true)
    @Operation(summary = "Get all dividends")
    public ResponseEntity<List<Dividend>> getDividends(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.getPortfolio(id, user); // ownership check
        return ResponseEntity.ok(
                dividendRepository.findByPortfolioIdOrderByPaymentDateDesc(id));
    }

    @GetMapping("/{id}/cash-flows")
    @Transactional(readOnly = true)
    @Operation(summary = "Get all cash flows")
    public ResponseEntity<List<CashFlow>> getPortfolioCashFlows(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.getPortfolio(id, user); // ownership check
        return ResponseEntity.ok(cashFlowRepository.findByPortfolioId(id));
    }

    // ════════════════════════════════════════════════════════════
    // Importação
    // ════════════════════════════════════════════════════════════

    @PostMapping(value = "/{id}/import/xtb", consumes = "multipart/form-data")
    @Operation(summary = "Import transactions from XTB Excel file")
    public ResponseEntity<XTBImportService.ImportResult> importFromXTB(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.getPortfolio(id, user); // ownership check

        try {
            XTBImportService.ImportResult result = xtbImportService.importFromExcel(file, id);
            if (result.getTransactionsImported() > 0) {
                positionService.recalculatePositions(id);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error importing XTB data for portfolio {}", id, e);
            return ResponseEntity.badRequest()
                    .body(XTBImportService.ImportResult.builder()
                            .transactionsImported(0).transactionsDuplicate(0)
                            .cashFlowsImported(0).totalProcessed(0).build());
        }
    }

    @PostMapping(value = "/{id}/import/binance", consumes = "multipart/form-data")
    @Operation(summary = "Import transactions from Binance CSV")
    public ResponseEntity<ImportResult> importFromBinance(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.getPortfolio(id, user); // ownership check

        try {
            List<Transaction> trades = binanceImportService.importFromCSV(file, id);
            transactionRepository.saveAll(trades);
            return ResponseEntity.ok(new ImportResult(trades.size(), 0, "Imported successfully"));
        } catch (Exception e) {
            log.error("Error importing Binance data for portfolio {}", id, e);
            return ResponseEntity.badRequest()
                    .body(new ImportResult(0, 0, "Error: " + e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════
    // Utilitários
    // ════════════════════════════════════════════════════════════

    @PostMapping("/{id}/recalculate-positions")
    @Operation(summary = "Recalculate all positions based on transactions")
    public ResponseEntity<RecalculateResult> recalculatePositions(
            @PathVariable Long id,
            @AuthenticationPrincipal User authUser) {

        User user = userResolver.resolve(authUser);
        portfolioService.getPortfolio(id, user); // ownership check

        try {
            List<Position> positions = positionService.recalculatePositions(id);
            return ResponseEntity.ok(new RecalculateResult(
                    positions.size(), "Positions recalculated successfully"));
        } catch (Exception e) {
            log.error("Error recalculating positions for portfolio {}", id, e);
            return ResponseEntity.badRequest()
                    .body(new RecalculateResult(0, "Error: " + e.getMessage()));
        }
    }

    // ── Response records ──────────────────────────────────────────

    public record RecalculateResult(int positionsCalculated, String message) {}

    public record ImportResult(
            int transactionsImported,
            int transactionsDuplicates,
            String message) {}
}
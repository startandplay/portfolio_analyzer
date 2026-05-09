package com.analytics.portfolio.controller;

import com.analytics.portfolio.dto.AssetMetrics;
import com.analytics.portfolio.dto.PortfolioMetrics;
import com.analytics.portfolio.integration.BinanceImportService;
import com.analytics.portfolio.integration.XTBImportService;
import com.analytics.portfolio.model.Dividend;
import com.analytics.portfolio.model.Portfolio;
import com.analytics.portfolio.model.Position;
import com.analytics.portfolio.model.Transaction;
import com.analytics.portfolio.repository.DividendRepository;
import com.analytics.portfolio.repository.PortfolioRepository;
import com.analytics.portfolio.repository.PositionRepository;
import com.analytics.portfolio.repository.TransactionRepository;
import com.analytics.portfolio.service.MetricsCalculationService;
import com.analytics.portfolio.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio", description = "Portfolio management APIs")
public class PortfolioController {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;
    private final MetricsCalculationService metricsService;
    private final XTBImportService xtbImportService;
    private final BinanceImportService binanceImportService;
    private final PositionService positionService;

    @GetMapping
    @Operation(summary = "Get all portfolios")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Portfolio>> getAllPortfolios() {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        return ResponseEntity.ok(portfolios);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get portfolio by ID")
    public ResponseEntity<Portfolio> getPortfolioById(@PathVariable Long id) {
        return portfolioRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new portfolio")
    public ResponseEntity<Portfolio> createPortfolio(@RequestBody Portfolio portfolio) {
        Portfolio saved = portfolioRepository.save(portfolio);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update portfolio")
    public ResponseEntity<Portfolio> updatePortfolio(
            @PathVariable Long id,
            @RequestBody Portfolio portfolio) {

        return portfolioRepository.findById(id)
                .map(existing -> {
                    existing.setName(portfolio.getName());
                    existing.setDescription(portfolio.getDescription());
                    existing.setCurrency(portfolio.getCurrency());
                    existing.setInitialCapital(portfolio.getInitialCapital());
                    Portfolio updated = portfolioRepository.save(existing);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete portfolio")
    public ResponseEntity<Void> deletePortfolio(@PathVariable Long id) {
        if (portfolioRepository.existsById(id)) {
            portfolioRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/metrics")
    @Transactional(readOnly = true)
    @Operation(summary = "Get portfolio metrics and analytics")
    public ResponseEntity<PortfolioMetrics> getPortfolioMetrics(@PathVariable Long id) {
        return portfolioRepository.findByIdWithPositions(id)
                .map(portfolio -> {
                    List<Position> positions = positionRepository.findByPortfolioId(id);
                    PortfolioMetrics metrics = metricsService.calculatePortfolioMetrics(
                            portfolio, positions);
                    return ResponseEntity.ok(metrics);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/positions")
    @Transactional(readOnly = true)
    @Operation(summary = "Get all positions in portfolio")
    public ResponseEntity<List<Position>> getPortfolioPositions(@PathVariable Long id) {
        List<Position> positions = positionRepository.findByPortfolioId(id);
        return ResponseEntity.ok(positions);
    }

    @GetMapping("/{id}/positions/metrics")
    @Transactional(readOnly = true)
    @Operation(summary = "Get metrics for all positions")
    public ResponseEntity<List<AssetMetrics>> getPositionsMetrics(@PathVariable Long id) {
        List<Position> positions = positionRepository.findByPortfolioId(id);

        List<AssetMetrics> metricslist = positions.stream()
                .map(position -> {
                    List<Transaction> trades = transactionRepository
                            .findByPortfolioIdAndAssetId(id, position.getAsset().getId());
                    List<Dividend> dividends = dividendRepository
                            .findByPortfolioIdAndAssetId(id, position.getAsset().getId());

                    return metricsService.calculateAssetMetrics(
                            position, trades, dividends);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(metricslist);
    }

    @GetMapping("/{id}/transactions")
    @Transactional(readOnly = true)
    @Operation(summary = "Get all transactions")
    public ResponseEntity<List<Transaction>> getTransactions(@PathVariable Long id) {
        List<Transaction> trades = transactionRepository
                .findByPortfolioIdOrderByTransactionDateDesc(id);
        return ResponseEntity.ok(trades);
    }

    @GetMapping("/{id}/dividends")
    @Transactional(readOnly = true)
    @Operation(summary = "Get all dividends")
    public ResponseEntity<List<Dividend>> getDividends(@PathVariable Long id) {
        List<Dividend> dividends = dividendRepository
                .findByPortfolioIdOrderByPaymentDateDesc(id);
        return ResponseEntity.ok(dividends);
    }


    @PostMapping(value = "/{id}/import/xtb", consumes = "multipart/form-data")
    @Operation(
            summary = "Import transactions and cash flows from XTB Excel file",
            description = "Upload an Excel file exported from XTB. Automatically separates Transactions (with ticker) and CashFlows (without ticker). Filters duplicates.")
    public ResponseEntity<XTBImportService.ImportResult> importFromXTB(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {

        try {
            XTBImportService.ImportResult result = xtbImportService.importFromExcel(file, id);

            if (result.getTransactionsImported() > 0) {
                positionService.recalculatePositions(id);
                log.info("Positions recalculated for portfolio {} after XTB import", id);
            }

            log.info("XTB import completed: {}", result.getSummary());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error importing XTB data", e);
            return ResponseEntity.badRequest()
                    .body(XTBImportService.ImportResult.builder()
                            .transactionsImported(0)
                            .transactionsDuplicate(0)
                            .cashFlowsImported(0)
                            .totalProcessed(0)
                            .build());
        }
    }

    @PostMapping(value = "/{id}/import/binance", consumes = "multipart/form-data")
    @Operation(summary = "Import transactions from Binance CSV")
    public ResponseEntity<ImportResult> importFromBinance(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {

        try {
            List<Transaction> trades = binanceImportService.importFromCSV(file, id);

            // Salvar transações
            transactionRepository.saveAll(trades);

            ImportResult result = new ImportResult(
                    trades.size(), 0, "Binance transactions imported successfully");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error importing Binance data", e);
            return ResponseEntity.badRequest()
                    .body(new ImportResult(0, 0, "Error: " + e.getMessage()));
        }
    }

    /**
     * Recalcula posições baseado nas transações
     */
    @PostMapping("/{id}/recalculate-positions")
    @Operation(
            summary = "Recalculate positions for a portfolio",
            description = "Recalculates all positions based on transactions. Useful after manual data changes."
    )
    public ResponseEntity<RecalculateResult> recalculatePositions(@PathVariable Long id) {
        try {
            List<Position> positions = positionService.recalculatePositions(id);

            return ResponseEntity.ok(new RecalculateResult(
                    positions.size(),
                    "Positions recalculated successfully"
            ));
        } catch (Exception e) {
            log.error("Error recalculating positions", e);
            return ResponseEntity.badRequest()
                    .body(new RecalculateResult(0, "Error: " + e.getMessage()));
        }
    }


    public record RecalculateResult(
            int positionsCalculated,
            String message
    ) {}



    // DTO para resultado de importação
    public record ImportResult(
            int transactionsImported,
            int transactionsDuplicates,
            String message
    ) {
    }
}

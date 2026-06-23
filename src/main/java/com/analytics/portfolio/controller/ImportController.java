package com.analytics.portfolio.controller;

import com.analytics.portfolio.dto.ImportResult;
import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.integration.ImportOrchestrator;
import com.analytics.portfolio.model.RawImportRecord;
import com.analytics.portfolio.model.User;
import com.analytics.portfolio.repository.RawImportRecordRepository;
import com.analytics.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Endpoint único de importação para todas as corretoras.
 *
 * POST /api/portfolios/{id}/import?source=XTB
 * POST /api/portfolios/{id}/import?source=BINANCE
 *
 * Para adicionar uma nova corretora não é necessário tocar neste
 * controller — basta criar um novo BrokerImportAdapter.
 */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Import", description = "Importação de ficheiros de corretoras")
public class ImportController {

    private final ImportOrchestrator        orchestrator;
    private final PortfolioService          portfolioService;
    private final RawImportRecordRepository rawRepo;

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @Operation(
            summary = "Import broker file",
            description = "Accepts any supported broker file. Pass ?source=XTB, ?source=BINANCE, etc."
    )
    public ResponseEntity<ImportResult> importFile(
            @PathVariable Long portfolioId,
            @RequestParam PortfolioSource source,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user); // ownership check

        try {
            ImportResult result = orchestrator.importFile(file, portfolioId, source);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Import rejeitado: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ImportResult.builder()
                            .source(source.name())
                            .failedRecords(1)
                            .build());
        } catch (Exception e) {
            log.error("Erro na importação portfolio={} source={}", portfolioId, source, e);
            return ResponseEntity.internalServerError()
                    .body(ImportResult.builder()
                            .source(source.name())
                            .failedRecords(1)
                            .build());
        }
    }

    @GetMapping("/import/batches")
    @Operation(summary = "List import batches",
               description = "Returns all batch IDs for this portfolio, most recent first")
    public ResponseEntity<List<String>> listBatches(
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        return ResponseEntity.ok(rawRepo.findDistinctBatchIdsByPortfolioId(portfolioId));
    }

    @GetMapping("/import/batches/{batchId}")
    @Operation(summary = "Get raw records for a batch",
               description = "Returns all raw records of an import batch — useful for debugging")
    public ResponseEntity<List<RawImportRecord>> getBatch(
            @PathVariable Long portfolioId,
            @PathVariable String batchId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        return ResponseEntity.ok(rawRepo.findByBatchId(batchId));
    }
}

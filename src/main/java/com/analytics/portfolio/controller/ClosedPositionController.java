package com.analytics.portfolio.controller;

import com.analytics.portfolio.dto.ClosedPositionStats;
import com.analytics.portfolio.model.ClosedPosition;
import com.analytics.portfolio.repository.ClosedPositionRepository;
import com.analytics.portfolio.service.ClosedPositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/closed-positions")
@RequiredArgsConstructor
@Tag(name = "Closed Positions", description = "Historical closed trades with real cost analysis")
public class ClosedPositionController {

    private final ClosedPositionRepository repo;
    private final ClosedPositionService    service;

    // ── Listagem ─────────────────────────────────────────────────────────

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List all closed positions for a portfolio")
    public ResponseEntity<List<ClosedPosition>> getAll(@PathVariable Long portfolioId) {
        return ResponseEntity.ok(repo.findByPortfolioIdOrderByCloseTimeDesc(portfolioId));
    }

    @GetMapping("/ticker/{ticker}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get closed positions for a specific ticker")
    public ResponseEntity<List<ClosedPosition>> getByTicker(@PathVariable Long portfolioId,
                                                             @PathVariable String ticker) {
        return ResponseEntity.ok(repo.findByPortfolioIdAndTicker(portfolioId, ticker.toUpperCase()));
    }

    @GetMapping("/period")
    @Transactional(readOnly = true)
    @Operation(summary = "Get closed positions within a date range")
    public ResponseEntity<List<ClosedPosition>> getByPeriod(
            @PathVariable Long portfolioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(
            repo.findByPortfolioIdAndCloseTimeBetweenOrderByCloseTimeDesc(portfolioId, from, to));
    }

    // ── Estatísticas ─────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    @Operation(
        summary = "Full performance statistics",
        description = """
            Calculates: P&L realizado, Win Rate, Profit Factor, Risk/Reward Ratio,
            custos reais (comissão + swap + rollover), ROI realizado, custo médio %
            (para ajustar break-even de posições abertas), melhor/pior trade, P&L por ticker.
            """
    )
    public ResponseEntity<ClosedPositionStats> getStats(@PathVariable Long portfolioId) {
        return ResponseEntity.ok(service.getStats(portfolioId));
    }

    @GetMapping("/stats/pl-by-ticker")
    @Transactional(readOnly = true)
    @Operation(summary = "P&L grouped by ticker")
    public ResponseEntity<List<ClosedPositionStats.TickerStats>> getPLByTicker(
            @PathVariable Long portfolioId) {
        ClosedPositionStats stats = service.getStats(portfolioId);
        return ResponseEntity.ok(stats.getByTicker());
    }

    @GetMapping("/stats/realized-pl")
    @Transactional(readOnly = true)
    @Operation(summary = "Total realized P&L (net, after all costs)")
    public ResponseEntity<?> getRealizedPL(@PathVariable Long portfolioId) {
        return ResponseEntity.ok(java.util.Map.of(
            "totalRealizedPL",  repo.getTotalRealizedPL(portfolioId),
            "totalCommissions", repo.getTotalCommissions(portfolioId),
            "totalSwapCosts",   repo.getTotalSwapCosts(portfolioId),
            "totalAllCosts",    repo.getTotalAllCosts(portfolioId)
        ));
    }

    @GetMapping("/stats/best-trade")
    @Transactional(readOnly = true)
    @Operation(summary = "Best trade (highest profit)")
    public ResponseEntity<ClosedPosition> getBestTrade(@PathVariable Long portfolioId) {
        return repo.getBestTrade(portfolioId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats/worst-trade")
    @Transactional(readOnly = true)
    @Operation(summary = "Worst trade (highest loss)")
    public ResponseEntity<ClosedPosition> getWorstTrade(@PathVariable Long portfolioId) {
        return repo.getWorstTrade(portfolioId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Custo real do investimento actual ────────────────────────────────

    @GetMapping("/stats/real-cost-factor")
    @Transactional(readOnly = true)
    @Operation(
        summary = "Real cost factor for open positions",
        description = """
            Retorna o custo médio % baseado em posições fechadas históricas.
            Usar este valor para ajustar o break-even real de posições abertas:
            breakEven = avgBuyPrice × (1 + realCostFactor/100)
            """
    )
    public ResponseEntity<?> getRealCostFactor(@PathVariable Long portfolioId) {
        ClosedPositionStats stats = service.getStats(portfolioId);
        return ResponseEntity.ok(java.util.Map.of(
            "avgCostPercentage",        stats.getAvgCostPercentage(),
            "avgCommissionPerTrade",    stats.getAvgCommissionPerTrade(),
            "totalAllCosts",            stats.getTotalAllCosts(),
            "totalInvestedInClosed",    stats.getTotalInvestedInClosed(),
            "realizedROI",              stats.getRealizedROI(),
            "interpretation",           "Use avgCostPercentage to adjust open position break-even prices"
        ));
    }
}

package com.analytics.portfolio.controller;

import com.analytics.portfolio.repository.TransactionRepository;
import com.analytics.portfolio.service.HoldingsCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/holdings")
@RequiredArgsConstructor
@Tag(name = "Holdings", description = "Portfolio holdings APIs")
public class HoldingsController {

    private final HoldingsCalculationService holdingsService;
    private final TransactionRepository transactionRepository;

    @GetMapping("/simple")
    @Operation(summary = "Get simple holdings (symbol -> quantity)")
    public ResponseEntity<Map<String, BigDecimal>> getSimpleHoldings(
            @PathVariable Long portfolioId) {

        Map<String, BigDecimal> holdings = holdingsService.calculateSimpleHoldings(portfolioId);
        return ResponseEntity.ok(holdings);
    }

    @GetMapping
    @Operation(summary = "Get holdings using optimized SQL query")
    public ResponseEntity<List<TransactionRepository.HoldingSummary>> getHoldings(
            @PathVariable Long portfolioId) {

        List<TransactionRepository.HoldingSummary> holdings =
                transactionRepository.calculateHoldings(portfolioId);

        return ResponseEntity.ok(holdings);
    }
}
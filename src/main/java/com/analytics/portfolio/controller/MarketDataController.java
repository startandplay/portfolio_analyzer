package com.analytics.portfolio.controller;

import com.analytics.portfolio.dto.MarketQuoteDto;
import com.analytics.portfolio.service.YahooFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller para integração com Yahoo Finance API via RapidAPI
 * Endpoints para consulta e atualização de preços
 */
@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Market Data", description = "Cotações e dados de mercado via Yahoo Finance")
public class MarketDataController {

    private final YahooFinanceService yahooFinanceService;

    /**
     * Busca cotação completa de um ativo
     */
    @GetMapping("/quote/{symbol}")
    @Operation(
            summary = "Get real-time quote for a stock",
            description = "Returns complete quote data including price, volume, changes, etc."
    )
    public ResponseEntity<MarketQuoteDto.Quote> getQuote(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "STOCKS") String type) {
        try {
            MarketQuoteDto.Quote quote = yahooFinanceService.getQuote(symbol);

            if (quote == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(quote);
        } catch (Exception e) {
            log.error("Erro ao buscar cotação de {}: {}", symbol, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Busca cotações de múltiplos ativos
     */
    @GetMapping("/stock/quotes")
    @Operation(
            summary = "Get quotes for multiple stocks",
            description = "Returns quotes for multiple symbols in a single request"
    )
    public ResponseEntity<List<MarketQuoteDto.Quote>> getBatchQuotes(
            @RequestParam List<String> symbols) {
        try {
            List<MarketQuoteDto.Quote> quotes = yahooFinanceService.getBatchQuotes(symbols);
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            log.error("Erro ao buscar cotações: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

//    /**
//     * Busca cotação de criptomoeda
//     */
//    @GetMapping("/crypto/{symbol}")
//    @Operation(summary = "Get crypto quote", description = "Returns quote for a cryptocurrency")
//    public ResponseEntity<YahooFinanceDTO.Quote> getCryptoQuote(@PathVariable String symbol) {
//        try {
//            YahooFinanceDTO.Quote quote = yahooFinanceService.getQuote(symbol);
//            return quote != null ? ResponseEntity.ok(quote) : ResponseEntity.notFound().build();
//        } catch (Exception e) {
//            log.error("Erro ao buscar crypto {}: {}", symbol, e.getMessage());
//            return ResponseEntity.badRequest().build();
//        }
//    }


    /**
     * Busca apenas o preço atual
     */
    @GetMapping("/price/{symbol}")
    @Operation(
            summary = "Get current price for a stock",
            description = "Returns only the current price (faster than full quote)"
    )
    public ResponseEntity<PriceResponse> getPrice(@PathVariable String symbol) {
        try {
            BigDecimal price = yahooFinanceService.getCurrentPrice(symbol);

            if (price == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(new PriceResponse(symbol, price));
        } catch (Exception e) {
            log.error("Erro ao buscar preço de {}: {}", symbol, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Atualiza o preço de um ativo específico
     */
    @PostMapping("/update-price/{ticker}")
    @Operation(
            summary = "Update price for a specific asset",
            description = "Fetches latest price from API and updates all positions containing this asset"
    )
    public ResponseEntity<UpdatePriceResponse> updatePrice(@PathVariable String ticker) {
        try {

            BigDecimal price = yahooFinanceService.updateAssetPrice(ticker);

            if (price != null) {
                return ResponseEntity.ok(
                        new UpdatePriceResponse(ticker, price, "Price updated successfully")
                );
            } else {
                return ResponseEntity.ok(
                        new UpdatePriceResponse(ticker, null, "Price not available")
                );
            }
        } catch (Exception e) {
            log.error("Erro ao atualizar preço de {}: {}", ticker, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new UpdatePriceResponse(ticker, null, "Error: " + e.getMessage()));
        }
    }

    /**
     * Atualiza preços de todas as posições de um portfolio
     */
    @PostMapping("/update-portfolio/{portfolioId}")
    @Operation(
            summary = "Update all prices for a portfolio",
            description = "Updates prices for all active positions in the portfolio using batch request"
    )
    public ResponseEntity<PortfolioUpdateResponse> updatePortfolioPrices(
            @PathVariable Long portfolioId) {
        try {
            int updated = yahooFinanceService.updatePortfolioPrices(portfolioId);

            return ResponseEntity.ok(
                    new PortfolioUpdateResponse(
                            portfolioId,
                            updated,
                            "Prices updated successfully"
                    )
            );
        } catch (Exception e) {
            log.error("Erro ao atualizar preços do portfolio {}: {}", portfolioId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new PortfolioUpdateResponse(portfolioId, 0, "Error: " + e.getMessage()));
        }
    }

    // ===== DTOs de Resposta =====

    public record PriceResponse(
            String symbol,
            BigDecimal price
    ) {
    }

    public record UpdatePriceResponse(
            String symbol,
            BigDecimal price,
            String message
    ) {
    }

    public record PortfolioUpdateResponse(
            Long portfolioId,
            int positionsUpdated,
            String message
    ) {
    }
}
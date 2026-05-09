package com.analytics.portfolio.controller;

import com.analytics.portfolio.enums.AssetType;
import com.analytics.portfolio.model.Asset;
import com.analytics.portfolio.repository.AssetRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controller para operações com Assets
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Assets", description = "Operações com ativos")
public class AssetController {

    private final AssetRepository assetRepository;

    /**
     * Busca todos os assets
     */
    @GetMapping
    @Operation(summary = "Get all assets", description = "Returns all assets in the database")
    public ResponseEntity<List<Asset>> getAllAssets() {
        List<Asset> assets = assetRepository.findAll();
        return ResponseEntity.ok(assets);
    }

    /**
     * Busca asset por ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get asset by ID", description = "Returns a single asset by ID")
    public ResponseEntity<Asset> getAssetById(@PathVariable Long id) {
        return assetRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Busca asset por símbolo
     */
    @GetMapping("/symbol/{symbol}")
    @Operation(summary = "Get asset by symbol", description = "Returns a single asset by symbol/ticker")
    public ResponseEntity<Asset> getAssetBySymbol(@PathVariable String symbol) {
        return assetRepository.findBySymbol(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Busca todos os símbolos (tickers)
     *
     * GET /api/assets/symbols
     *
     * Exemplo: ["AAPL", "MSFT", "GOOGL", "AMZN", "TSLA"]
     */
    @GetMapping("/symbols")
    @Operation(
            summary = "Get all symbols/tickers",
            description = "Returns a list of all unique symbols (tickers) in the assets table"
    )
    public ResponseEntity<List<String>> getAllSymbols() {
        log.info("Buscando todos os símbolos");
        List<String> symbols = assetRepository.findAllSymbols();
        log.info("Encontrados {} símbolos", symbols.size());
        return ResponseEntity.ok(symbols);
    }

    /**
     * Busca símbolos com positions ativas
     *
     * GET /api/assets/symbols/active
     *
     * Retorna apenas símbolos que têm positions com quantity > 0
     */
    @GetMapping("/symbols/active")
    @Operation(
            summary = "Get active symbols",
            description = "Returns symbols that have active positions (quantity > 0)"
    )
    public ResponseEntity<List<String>> getActiveSymbols() {
        log.info("Buscando símbolos com positions ativas");
        List<String> symbols = assetRepository.findSymbolsWithActivePositions();
        log.info("Encontrados {} símbolos ativos", symbols.size());
        return ResponseEntity.ok(symbols);
    }

    /**
     * Busca símbolos de um portfolio específico
     *
     * GET /api/assets/symbols/portfolio/{portfolioId}
     *
     * Retorna símbolos das positions ativas de um portfolio
     */
    @GetMapping("/symbols/portfolio/{portfolioId}")
    @Operation(
            summary = "Get symbols for a portfolio",
            description = "Returns symbols of active positions in a specific portfolio"
    )
    public ResponseEntity<List<String>> getPortfolioSymbols(
            @PathVariable Long portfolioId) {
        log.info("Buscando símbolos do portfolio {}", portfolioId);
        List<String> symbols = assetRepository.findSymbolsByPortfolioId(portfolioId);
        log.info("Portfolio {} tem {} símbolos", portfolioId, symbols.size());
        return ResponseEntity.ok(symbols);
    }

    /**
     * Busca estatísticas de símbolos
     *
     * GET /api/assets/symbols/stats
     *
     * Retorna contagens e estatísticas sobre símbolos
     */
    @GetMapping("/symbols/stats")
    @Operation(
            summary = "Get symbol statistics",
            description = "Returns statistics about symbols (total, active, inactive)"
    )
    public ResponseEntity<SymbolStats> getSymbolStats() {
        log.info("Calculando estatísticas de símbolos");

        List<String> allSymbols = assetRepository.findAllSymbols();
        List<String> activeSymbols = assetRepository.findSymbolsWithActivePositions();

        SymbolStats stats = new SymbolStats(
                allSymbols.size(),
                activeSymbols.size(),
                allSymbols.size() - activeSymbols.size()
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * Busca símbolos formatados para dropdown/select
     *
     * GET /api/assets/symbols/dropdown
     *
     * Retorna formato ideal para componentes de UI
     */
    @GetMapping("/symbols/dropdown")
    @Operation(
            summary = "Get symbols for dropdown",
            description = "Returns symbols formatted for UI dropdowns/selects"
    )
    public ResponseEntity<List<SymbolOption>> getSymbolsForDropdown() {
        log.info("Buscando símbolos para dropdown");

        List<String> symbols = assetRepository.findAllSymbols();

        List<SymbolOption> options = symbols.stream()
                .map(symbol -> new SymbolOption(symbol, symbol))
                .toList();

        return ResponseEntity.ok(options);
    }

    /**
     * Verifica se um símbolo existe
     *
     * GET /api/assets/symbols/{symbol}/exists
     */
    @GetMapping("/symbols/{symbol}/exists")
    @Operation(
            summary = "Check if symbol exists",
            description = "Returns true if the symbol exists in the database"
    )
    public ResponseEntity<Map<String, Boolean>> symbolExists(@PathVariable String symbol) {
        boolean exists = assetRepository.findBySymbol(symbol).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists, "symbol", symbol.equals(symbol)));
    }

    /**
     * Busca assets por tipo
     */
    @GetMapping("/type/{type}")
    @Operation(summary = "Get assets by type", description = "Returns assets filtered by type")
    public ResponseEntity<List<Asset>> getAssetsByType(@PathVariable AssetType type) {
        List<Asset> assets = assetRepository.findByType(type);
        return ResponseEntity.ok(assets);
    }

    // ===== DTOs de Resposta =====

    /**
     * Estatísticas de símbolos
     */
    public record SymbolStats(
            int total,
            int active,
            int inactive
    ) {}

    /**
     * Opção para dropdown/select
     */
    public record SymbolOption(
            String value,
            String label
    ) {}
}

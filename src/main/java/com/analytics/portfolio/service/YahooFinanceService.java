package com.analytics.portfolio.service;

import com.analytics.portfolio.clients.YahooFinanceClient;
import com.analytics.portfolio.dto.MarketQuoteDto;
import com.analytics.portfolio.enums.AssetType;
import com.analytics.portfolio.model.Asset;
import com.analytics.portfolio.model.Position;
import com.analytics.portfolio.model.PriceHistory;
import com.analytics.portfolio.repository.AssetRepository;
import com.analytics.portfolio.repository.PositionRepository;
import com.analytics.portfolio.repository.PriceHistoryRepository;
import com.analytics.portfolio.utils.PortfolioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Serviço para integração com Yahoo Finance API via RapidAPI
 * Atualiza preços de ativos e posições
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class YahooFinanceService {

    private final YahooFinanceClient yahooFinanceClient;
    private final PositionRepository positionRepository;
    private final AssetRepository assetRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    /**
     * Atualiza o preço atual de um ativo específico
     *
     * @param ticker Símbolo do ativo (ex: "AAPL", "MSFT")
     * @return Preço atualizado
     */
    @Transactional
    public BigDecimal updateAssetPrice(String ticker) {
        log.info("Atualizando preço para: {}", ticker);

        try {
            // Buscar cotação da API
            MarketQuoteDto.Quote quote = yahooFinanceClient.getQuote(PortfolioUtils.mapToLS(ticker));

            if (quote == null || quote.regularMarketPrice() == null) {
                log.warn("Cotação não encontrada para: {}", ticker);
                return null;
            }

            BigDecimal price = quote.regularMarketPrice();

            // Atualizar asset se existir
            Optional<Asset> assetOpt = assetRepository.findByTicker(ticker);
            if (assetOpt.isPresent()) {
                Asset asset = assetOpt.get();
                asset.setCurrentPrice(price);
                asset.setExchange(quote.exchange());
                asset.setType(AssetType.valueOf(quote.quoteType()));
                asset.setLastPriceUpdate(LocalDateTime.now());
                assetRepository.save(asset);

                log.info("Preço do ativo {} atualizado: {}", ticker, price);
            }

            // Salvar histórico de preço
            updatePriceHistory(ticker, quote);

            // Atualizar todas as positions desse ativo
            updatePositionsForAsset(ticker, price);

            return price;

        } catch (YahooFinanceClient.YahooFinanceException e) {
            log.error("Erro ao atualizar preço de {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    /**
     * Atualiza preços de todas as posições ativas de um portfolio
     *
     * @param portfolioId ID do portfolio
     * @return Número de posições atualizadas
     */
    @Transactional
    public int updatePortfolioPrices(Long portfolioId) {
        log.info("Atualizando preços do portfolio: {}", portfolioId);

        List<Position> positions = positionRepository.findByPortfolioId(portfolioId);

        if (positions.isEmpty()) {
            log.info("Nenhuma posição encontrada para portfolio {}", portfolioId);
            return 0;
        }

        // Coletar símbolos únicos
        List<String> tickers = positions.stream()
                .map(p -> PortfolioUtils.mapToLS(p.getAsset().getTicker()))
                .distinct()
                .toList();

        log.info("Atualizando {} ativos únicos via batch", tickers.size());

        try {
            // Buscar todas as cotações de uma vez (mais eficiente!)
            List<MarketQuoteDto.Quote> quotes = yahooFinanceClient.getBatchQuotes(tickers);

            int updated = 0;
            for (MarketQuoteDto.Quote quote : quotes) {
                if (quote != null && quote.regularMarketPrice() != null) {
                    String symbol = PortfolioUtils.mapToPT(quote.symbol());
                    BigDecimal price = quote.regularMarketPrice();

                    updateAssets(symbol, price);

                    updatePriceHistory(symbol, quote);

                    updatePositionsForAsset(symbol, price);

                    updated++;
                }
            }

            log.info("Preços atualizados: {} de {} ativos", updated, tickers.size());
            return updated;

        } catch (Exception e) {
            log.error("Erro ao atualizar preços do portfolio: {}", e.getMessage());
            return 0;
        }
    }

    private void updateAssets(String symbol, BigDecimal price) {

        Optional<Asset> assetOpt = assetRepository.findBySymbol(symbol);

        if (assetOpt.isPresent()) {
            Asset asset = assetOpt.get();
            asset.setCurrentPrice(price);
            asset.setLastPriceUpdate(LocalDateTime.now());

            assetRepository.save(asset);
            log.info("Asset {} updated.", symbol);
        }
    }

    /**
     * Atualiza todas as posições que contêm um ativo específico
     */
    @Transactional
    public void updatePositionsForAsset(String ticker, BigDecimal price) {
        Optional<Asset> assetOpt = assetRepository.findByTicker(ticker);

        if (assetOpt.isEmpty()) {
            return;
        }

        Asset asset = assetOpt.get();

        // Buscar todas as positions desse ativo
        List<Position> positions = positionRepository.findAll().stream()
                .filter(p -> p.getAsset().getId().equals(asset.getId()))
                .peek(p -> p.setCurrentPrice(price)).toList();

        if (!positions.isEmpty()) {
            positionRepository.saveAll(positions);
            log.info("Atualizadas {} posições do ativo {}", positions.size(), ticker);
        }
    }

    /**
     * Salva o histórico de preços
     */
    private void updatePriceHistory(String symbol, MarketQuoteDto.Quote quote) {
        Optional<Asset> assetOpt = assetRepository.findBySymbol(symbol);

        if (assetOpt.isEmpty()) {
            return;
        }

        Asset asset = assetOpt.get();

        PriceHistory history = PriceHistory.builder()
                .asset(asset)
                .price(quote.regularMarketPrice())
                .open(quote.regularMarketOpen())
                .high(quote.regularMarketDayHigh())
                .low(quote.regularMarketDayLow())
                .volume(quote.regularMarketVolume())
                .priceDate(convertTimestamp(quote.regularMarketTime()))
                .source("YAHOO_FINANCE")
                .build();

        priceHistoryRepository.save(history);
        log.debug("Histórico de preço salvo para {}", symbol);
    }

//    /**
//     * Busca cotação completa de um ativo
//     */
//    public YahooFinanceDTO.Quote getQuote(String symbol) {
//        return yahooFinanceClient.getQuote(symbol);
//    }

    /**
     * Busca cotação completa de um ativo
     */
    public MarketQuoteDto.Quote getQuote(String symbol) {
        return yahooFinanceClient.getQuote(symbol);
    }

    /**
     * Busca apenas o preço atual
     */
    public BigDecimal getCurrentPrice(String symbol) {
        return yahooFinanceClient.getPrice(symbol);
    }

    /**
     * Busca cotações de múltiplos ativos
     */
    public List<MarketQuoteDto.Quote> getBatchQuotes(List<String> symbols) {
        return yahooFinanceClient.getBatchQuotes(symbols);
    }

    /**
     * Converte timestamp Unix para LocalDateTime
     */
    private LocalDateTime convertTimestamp(Long timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
        );
    }
}

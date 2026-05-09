package com.analytics.portfolio.clients;

import com.analytics.portfolio.config.YahooFinanceConfig;
import com.analytics.portfolio.dto.MarketQuoteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cliente HTTP para comunicação com Yahoo Finance API via RapidAPI
 * Documentação: https://rapidapi.com/sparior/api/yahoo-finance15
 * <p>
 * Endpoint: GET /api/v1/markets/quote?ticker=AAPL&type=STOCKS
 */
@Component
@Slf4j
public class YahooFinanceClient {

    private final YahooFinanceConfig config;
    private final RestClient restClient;

    public YahooFinanceClient(YahooFinanceConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("x-rapidapi-key", config.getApiKey())
                .defaultHeader("x-rapidapi-host", config.getApiHost())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Busca cotação de uma ou mais ações
     * Endpoint: GET /api/v1/markets/quote
     *
     * @param ticker Símbolo da ação (ex: "AAPL") ou múltiplos separados por vírgula (ex: "AAPL,MSFT,GOOGL")
     * @return Dados das cotações
     */
    public MarketQuoteDto.MarketQuoteResponse getQuotes(String ticker) {
        try {
            log.debug("Buscando cotações para: {} (type: {})", ticker);

            MarketQuoteDto.MarketQuoteResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        return uriBuilder
                                .path("/api/v1/markets/stock/quotes")
                                .queryParam("ticker", ticker)
                                .build();
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (request, resp) -> {
                        throw new YahooFinanceException("Rate limit excedido. Aguarde antes de fazer mais requisições.");
                    })
                    .onStatus(status -> status.value() == 403, (request, resp) -> {
                        throw new YahooFinanceException("API key inválida ou sem permissão. Verifique sua chave RapidAPI.");
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                        throw new YahooFinanceException("Erro ao buscar cotação: " + resp.getStatusText());
                    })
                    .body(MarketQuoteDto.MarketQuoteResponse.class);

            if (response != null && response.body() != null && !response.body().isEmpty()) {
                log.debug("Cotações obtidas com sucesso para: {}", ticker);
            } else {
                log.warn("Resposta vazia para: {}", ticker);
            }

            return response;

        } catch (RestClientException e) {
            log.error("Erro ao buscar cotações para {}: {}", ticker, e.getMessage());
            throw new YahooFinanceException("Erro ao buscar cotação para " + ticker, e);
        }
    }

    /**
     * Busca cotação de uma única ação com tipo específico
     *
     * @param ticker Símbolo da ação
     * @return Dados da cotação
     */
    public MarketQuoteDto.Quote getQuote(String ticker) {
        MarketQuoteDto.MarketQuoteResponse response = getQuotes(ticker);

        if (response == null || response.body() == null ||
                response.body().isEmpty()) {
            return null;
        }

        return response.body().getFirst();
    }


    /**
     * Busca cotações de múltiplas ações com tipo específico
     *
     * @param symbols Lista de símbolos
     * @return Lista de cotações
     */
    public List<MarketQuoteDto.Quote> getBatchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        try {
            // Juntar símbolos com vírgula: AAPL,MSFT,GOOGL
            String tickers = String.join(",", symbols);
            log.debug("Buscando batch quotes para {} símbolos", symbols.size());

            MarketQuoteDto.MarketQuoteResponse response = getQuotes(tickers);

            if (response == null || response.body() == null) {
                return List.of();
            }

            return response.body();

        } catch (Exception e) {
            log.error("Erro ao buscar batch quotes: {}", e.getMessage());
            throw new YahooFinanceException("Erro ao buscar cotações em lote", e);
        }
    }

    /**
     * Busca apenas o preço atual de uma ação
     *
     * @param symbol Símbolo da ação
     * @return Resposta simplificada com preço
     */
    public BigDecimal getPrice(String symbol) {
        MarketQuoteDto.Quote quote = getQuote(symbol);
        return quote.regularMarketPrice();
    }



    /**
     * Exception customizada para erros da Yahoo Finance API
     */
    public static class YahooFinanceException extends RuntimeException {
        public YahooFinanceException(String message) {
            super(message);
        }

        public YahooFinanceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
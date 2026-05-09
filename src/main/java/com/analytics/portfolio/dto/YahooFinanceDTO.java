package com.analytics.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs para respostas da Yahoo Finance API via RapidAPI
 * Endpoint: https://yahoo-finance15.p.rapidapi.com/api/v1/markets/stock/quotes
 */
public class YahooFinanceDTO {

    /**
     * Resposta principal do endpoint /quotes
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteResponse {
        private String status;
        private Body body;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Body {
            private List<Quote> quotes;
        }
    }

    /**
     * Dados de cotação de uma ação
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {
        // Informações básicas
        private String symbol;

        @JsonProperty("shortName")
        private String shortName;

        @JsonProperty("longName")
        private String longName;

        private String exchange;
        private String currency;

        // Preços
        @JsonProperty("regularMarketPrice")
        private BigDecimal regularMarketPrice;

        @JsonProperty("regularMarketOpen")
        private BigDecimal regularMarketOpen;

        @JsonProperty("regularMarketDayHigh")
        private BigDecimal regularMarketDayHigh;

        @JsonProperty("regularMarketDayLow")
        private BigDecimal regularMarketDayLow;

        @JsonProperty("regularMarketPreviousClose")
        private BigDecimal regularMarketPreviousClose;

        // Variações
        @JsonProperty("regularMarketChange")
        private BigDecimal regularMarketChange;

        @JsonProperty("regularMarketChangePercent")
        private BigDecimal regularMarketChangePercent;

        // Volume
        @JsonProperty("regularMarketVolume")
        private Long regularMarketVolume;

        @JsonProperty("averageDailyVolume3Month")
        private Long averageDailyVolume3Month;

        // Market cap e outros
        @JsonProperty("marketCap")
        private Long marketCap;

        @JsonProperty("fiftyTwoWeekLow")
        private BigDecimal fiftyTwoWeekLow;

        @JsonProperty("fiftyTwoWeekHigh")
        private BigDecimal fiftyTwoWeekHigh;

        @JsonProperty("fiftyDayAverage")
        private BigDecimal fiftyDayAverage;

        @JsonProperty("twoHundredDayAverage")
        private BigDecimal twoHundredDayAverage;

        // Timestamps
        @JsonProperty("regularMarketTime")
        private Long regularMarketTime;

        @JsonProperty("postMarketPrice")
        private BigDecimal postMarketPrice;

        @JsonProperty("postMarketChange")
        private BigDecimal postMarketChange;

        @JsonProperty("postMarketChangePercent")
        private BigDecimal postMarketChangePercent;

        @JsonProperty("postMarketTime")
        private Long postMarketTime;

        // Estado do mercado
        @JsonProperty("marketState")
        private String marketState; // "REGULAR", "CLOSED", "PRE", "POST"

        // Dados financeiros adicionais
        @JsonProperty("trailingPE")
        private BigDecimal trailingPE;

        @JsonProperty("forwardPE")
        private BigDecimal forwardPE;

        @JsonProperty("dividendYield")
        private BigDecimal dividendYield;

        @JsonProperty("epsTrailingTwelveMonths")
        private BigDecimal epsTrailingTwelveMonths;

        @JsonProperty("epsForward")
        private BigDecimal epsForward;

        @JsonProperty("bookValue")
        private BigDecimal bookValue;

        @JsonProperty("priceToBook")
        private BigDecimal priceToBook;
    }

    /**
     * Resposta simplificada para preço
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceResponse {
        private String symbol;
        private BigDecimal price;
        private BigDecimal change;
        private BigDecimal changePercent;

        public static PriceResponse from(Quote quote) {
            if (quote == null) return null;

            PriceResponse response = new PriceResponse();
            response.setSymbol(quote.getSymbol());
            response.setPrice(quote.getRegularMarketPrice());
            response.setChange(quote.getRegularMarketChange());
            response.setChangePercent(quote.getRegularMarketChangePercent());
            return response;
        }
    }

    /**
     * Resposta de erro
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorResponse {
        private String status;
        private String message;
        private Integer code;
    }
}

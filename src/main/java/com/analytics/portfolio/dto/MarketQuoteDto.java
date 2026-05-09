package com.analytics.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;


public class MarketQuoteDto {

    // ============================
    // Root Record
    // ============================
    public record MarketQuoteResponse(
            @JsonProperty("meta") Meta meta,
            @JsonProperty("body") List<Quote> body
    ) {}

    // ============================
    // Meta Record
    // ============================
    public record Meta(
            @JsonProperty("version") String version,
            @JsonProperty("status") Integer status,
            @JsonProperty("copywrite") String copywrite,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("processedTime") String processedTime
    ) {}

    // ============================
    // Quote Record
    // ============================
    public record Quote(

            @JsonProperty("language") String language,
            @JsonProperty("region") String region,
            @JsonProperty("quoteType") String quoteType,
            @JsonProperty("typeDisp") String typeDisp,
            @JsonProperty("quoteSourceName") String quoteSourceName,
            @JsonProperty("triggerable") Boolean triggerable,
            @JsonProperty("customPriceAlertConfidence") String customPriceAlertConfidence,
            @JsonProperty("currency") String currency,

            @JsonProperty("earningsTimestamp") Long earningsTimestamp,
            @JsonProperty("earningsTimestampStart") Long earningsTimestampStart,
            @JsonProperty("earningsTimestampEnd") Long earningsTimestampEnd,
            @JsonProperty("earningsCallTimestampStart") Long earningsCallTimestampStart,
            @JsonProperty("earningsCallTimestampEnd") Long earningsCallTimestampEnd,
            @JsonProperty("isEarningsDateEstimate") Boolean isEarningsDateEstimate,

            @JsonProperty("trailingAnnualDividendRate") BigDecimal trailingAnnualDividendRate,
            @JsonProperty("trailingPE") BigDecimal trailingPE,
            @JsonProperty("dividendRate") BigDecimal dividendRate,
            @JsonProperty("trailingAnnualDividendYield") BigDecimal trailingAnnualDividendYield,
            @JsonProperty("dividendYield") BigDecimal dividendYield,
            @JsonProperty("epsTrailingTwelveMonths") BigDecimal epsTrailingTwelveMonths,
            @JsonProperty("epsForward") BigDecimal epsForward,
            @JsonProperty("epsCurrentYear") BigDecimal epsCurrentYear,
            @JsonProperty("priceEpsCurrentYear") BigDecimal priceEpsCurrentYear,

            @JsonProperty("sharesOutstanding") Long sharesOutstanding,
            @JsonProperty("bookValue") BigDecimal bookValue,

            @JsonProperty("fiftyDayAverage") BigDecimal fiftyDayAverage,
            @JsonProperty("fiftyDayAverageChange") BigDecimal fiftyDayAverageChange,
            @JsonProperty("fiftyDayAverageChangePercent") BigDecimal fiftyDayAverageChangePercent,

            @JsonProperty("twoHundredDayAverage") BigDecimal twoHundredDayAverage,
            @JsonProperty("twoHundredDayAverageChange") BigDecimal twoHundredDayAverageChange,
            @JsonProperty("twoHundredDayAverageChangePercent") BigDecimal twoHundredDayAverageChangePercent,

            @JsonProperty("marketCap") Long marketCap,
            @JsonProperty("corporateActions") List<Object> corporateActions,

            @JsonProperty("forwardPE") BigDecimal forwardPE,
            @JsonProperty("priceToBook") BigDecimal priceToBook,

            @JsonProperty("sourceInterval") Integer sourceInterval,
            @JsonProperty("exchangeDataDelayedBy") Integer exchangeDataDelayedBy,

            @JsonProperty("tradeable") Boolean tradeable,
            @JsonProperty("cryptoTradeable") Boolean cryptoTradeable,

            @JsonProperty("regularMarketTime") Long regularMarketTime,

            @JsonProperty("exchange") String exchange,
            @JsonProperty("messageBoardId") String messageBoardId,
            @JsonProperty("exchangeTimezoneName") String exchangeTimezoneName,
            @JsonProperty("exchangeTimezoneShortName") String exchangeTimezoneShortName,
            @JsonProperty("gmtOffSetMilliseconds") Long gmtOffSetMilliseconds,
            @JsonProperty("market") String market,
            @JsonProperty("esgPopulated") Boolean esgPopulated,

            @JsonProperty("longName") String longName,
            @JsonProperty("marketState") String marketState,
            @JsonProperty("shortName") String shortName,
            @JsonProperty("hasPrePostMarketData") Boolean hasPrePostMarketData,

            @JsonProperty("firstTradeDateMilliseconds") Long firstTradeDateMilliseconds,
            @JsonProperty("priceHint") Integer priceHint,

            @JsonProperty("regularMarketChange") BigDecimal regularMarketChange,
            @JsonProperty("regularMarketDayHigh") BigDecimal regularMarketDayHigh,
            @JsonProperty("regularMarketDayRange") String regularMarketDayRange,
            @JsonProperty("regularMarketDayLow") BigDecimal regularMarketDayLow,
            @JsonProperty("regularMarketVolume") Long regularMarketVolume,
            @JsonProperty("regularMarketPreviousClose") BigDecimal regularMarketPreviousClose,

            @JsonProperty("bid") BigDecimal bid,
            @JsonProperty("ask") BigDecimal ask,
            @JsonProperty("bidSize") Integer bidSize,
            @JsonProperty("askSize") Integer askSize,

            @JsonProperty("fullExchangeName") String fullExchangeName,
            @JsonProperty("financialCurrency") String financialCurrency,

            @JsonProperty("regularMarketOpen") BigDecimal regularMarketOpen,

            @JsonProperty("averageDailyVolume3Month") Long averageDailyVolume3Month,
            @JsonProperty("averageDailyVolume10Day") Long averageDailyVolume10Day,

            @JsonProperty("fiftyTwoWeekLowChange") BigDecimal fiftyTwoWeekLowChange,
            @JsonProperty("fiftyTwoWeekLowChangePercent") BigDecimal fiftyTwoWeekLowChangePercent,
            @JsonProperty("fiftyTwoWeekRange") String fiftyTwoWeekRange,
            @JsonProperty("fiftyTwoWeekHighChange") BigDecimal fiftyTwoWeekHighChange,
            @JsonProperty("fiftyTwoWeekHighChangePercent") BigDecimal fiftyTwoWeekHighChangePercent,
            @JsonProperty("fiftyTwoWeekLow") BigDecimal fiftyTwoWeekLow,
            @JsonProperty("fiftyTwoWeekHigh") BigDecimal fiftyTwoWeekHigh,
            @JsonProperty("fiftyTwoWeekChangePercent") BigDecimal fiftyTwoWeekChangePercent,

            @JsonProperty("regularMarketChangePercent") BigDecimal regularMarketChangePercent,
            @JsonProperty("regularMarketPrice") BigDecimal regularMarketPrice,

            @JsonProperty("symbol") String symbol,

            @JsonProperty("preMarketChange") BigDecimal preMarketChange,
            @JsonProperty("preMarketChangePercent") BigDecimal preMarketChangePercent,
            @JsonProperty("preMarketPrice") BigDecimal preMarketPrice,
            @JsonProperty("preMarketTime") Long preMarketTime,

            @JsonProperty("postMarketChange") BigDecimal postMarketChange,
            @JsonProperty("postMarketChangePercent") BigDecimal postMarketChangePercent,
            @JsonProperty("postMarketPrice") BigDecimal postMarketPrice,
            @JsonProperty("postMarketTime") Long postMarketTime
    ) {}
}

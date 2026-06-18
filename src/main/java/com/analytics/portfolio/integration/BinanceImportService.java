package com.analytics.portfolio.integration;

import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.enums.AssetType;
import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.Asset;
import com.analytics.portfolio.model.Portfolio;
import com.analytics.portfolio.model.Transaction;
import com.analytics.portfolio.repository.AssetRepository;
import com.analytics.portfolio.repository.PortfolioRepository;
import com.analytics.portfolio.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço para integração com Binance API e importação de dados
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceImportService {

    @Value("${binance.api.url:https://api.binance.com}")
    private String binanceApiUrl;

    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final com.analytics.portfolio.service.DuplicateDetectionService duplicateDetectionService;

    /**
     * Importa histórico de trades da Binance via API
     */
    public List<Transaction> importTradesFromAPI(String apiKey, String apiSecret,
                                                 String symbol, Long portfolioId)
            throws IOException {

        // Buscar portfolio
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio não encontrado: " + portfolioId));

        long timestamp = System.currentTimeMillis();
        String queryString = "symbol=" + symbol + "&timestamp=" + timestamp;
        String signature = generateSignature(queryString, apiSecret);

        String url = binanceApiUrl + "/api/v3/myTrades?" + queryString + "&signature=" + signature;

        Request request = new Request.Builder()
                .url(url)
                .header("X-MBX-APIKEY", apiKey)
                .build();

        List<Transaction> trades = new ArrayList<>();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro ao buscar trades da Binance: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode tradesArray = objectMapper.readTree(responseBody);

            for (JsonNode trade : tradesArray) {
                Transaction transaction = parseBinanceTrade(trade, portfolio);
//                transaction.add(transaction);
            }
        }

        return trades;
    }

    /**
     * Importa transações de arquivo CSV exportado da Binance
     */
    public List<Transaction> importFromCSV(MultipartFile file, Long portfolioId)
            throws IOException {

        // Buscar portfolio
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio não encontrado: " + portfolioId));

        List<Transaction> trades = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                try {
                    Transaction transaction = parseBinanceCSVLine(line, portfolio);
                    if (transaction != null) {
//                        transaction.add(transaction);
                    }
                } catch (Exception e) {
                    log.warn("Erro ao processar linha CSV Binance: {}", e.getMessage());
                }
            }
        }

        // Filtrar duplicatas
        List<Transaction> newTrades = duplicateDetectionService.filterDuplicates(trades, transactionRepository::existsByImportFingerprint);

        int duplicatesCount = trades.size() - newTrades.size();
        if (duplicatesCount > 0) {
            log.info("Binance Import: {} transações analisadas, {} novas, {} duplicatas ignoradas",
                    trades.size(), newTrades.size(), duplicatesCount);
        } else {
            log.info("Binance Import: {} transações novas importadas", newTrades.size());
        }

        return newTrades;
    }

    /**
     * Parse de trade da API Binance para Transaction
     */
    private Transaction parseBinanceTrade(JsonNode trade, Portfolio portfolio) {
        String symbol = trade.get("symbol").asText();
        String tradeId = trade.get("id").asText();
        BigDecimal price = new BigDecimal(trade.get("price").asText());
        BigDecimal quantity = new BigDecimal(trade.get("qty").asText());
        BigDecimal commission = new BigDecimal(trade.get("commission").asText());
        long timestamp = trade.get("time").asLong();
        boolean isBuyer = trade.get("isBuyer").asBoolean();

        LocalDateTime transactionDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

        // Buscar ou criar o Asset
        Asset asset = findOrCreateAsset(symbol, AssetType.CRYPTO, PortfolioSource.BINANCE);

        return Transaction.builder()
                .portfolio(portfolio)
                .asset(asset)
                .type(isBuyer ? TransactionType.BUY : TransactionType.SELL)
                .quantity(quantity)
                .price(price)
                .fees(commission)
                .transactionDate(transactionDate)
                .importSource("BINANCE")
                .externalId(tradeId)
                .build();
    }

    /**
     * Parse de linha CSV da Binance
     */
    private Transaction parseBinanceCSVLine(String line, Portfolio portfolio) {
        String[] parts = line.split(",");

        if (parts.length < 9) {
            return null;
        }

        String dateStr = parts[0].trim();
        String pair = parts[1].trim();
        String type = parts[2].trim().toUpperCase();
        BigDecimal avgPrice = new BigDecimal(parts[5].trim());
        BigDecimal filled = new BigDecimal(parts[6].trim());
        String feeStr = parts[8].trim();

        // Parse da fee (formato: "0.045 USDT")
        String[] feeParts = feeStr.split(" ");
        BigDecimal fee = new BigDecimal(feeParts[0]);

        LocalDateTime transactionDate = LocalDateTime.parse(
                dateStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        TransactionType transactionType;
        if (type.equals("BUY")) {
            transactionType = TransactionType.BUY;
        } else if (type.equals("SELL")) {
            transactionType = TransactionType.SELL;
        } else {
            return null;
        }

        // Buscar ou criar o Asset
        Asset asset = findOrCreateAsset(pair, AssetType.CRYPTO, PortfolioSource.BINANCE);

        return Transaction.builder()
                .portfolio(portfolio)
                .asset(asset)
                .type(transactionType)
                .quantity(filled)
                .price(avgPrice)
                .fees(fee)
                .transactionDate(transactionDate)
                .importSource("BINANCE")
                .build();
    }

    /**
     * Busca preço atual de um par na Binance
     */
    public BigDecimal getCurrentPrice(String symbol) throws IOException {
        String url = binanceApiUrl + "/api/v3/ticker/price?symbol=" + symbol;

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro ao buscar preço da Binance: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode priceData = objectMapper.readTree(responseBody);

            return new BigDecimal(priceData.get("price").asText());
        }
    }

    /**
     * Busca histórico de preços (candlestick data) da Binance
     */
    public List<BigDecimal[]> getHistoricalPrices(String symbol, String interval,
                                                  int limit) throws IOException {
        String url = String.format("%s/api/v3/klines?symbol=%s&interval=%s&limit=%d",
                binanceApiUrl, symbol, interval, limit);

        Request request = new Request.Builder()
                .url(url)
                .build();

        List<BigDecimal[]> prices = new ArrayList<>();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro ao buscar histórico de preços: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode candlesArray = objectMapper.readTree(responseBody);

            for (JsonNode candle : candlesArray) {
                BigDecimal[] ohlc = new BigDecimal[5];
                ohlc[0] = new BigDecimal(candle.get(1).asText()); // open
                ohlc[1] = new BigDecimal(candle.get(2).asText()); // high
                ohlc[2] = new BigDecimal(candle.get(3).asText()); // low
                ohlc[3] = new BigDecimal(candle.get(4).asText()); // close
                ohlc[4] = new BigDecimal(candle.get(5).asText()); // volume

                prices.add(ohlc);
            }
        }

        return prices;
    }

    /**
     * Gera assinatura HMAC SHA256 para requisições autenticadas
     */
    private String generateSignature(String data, String secret) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar assinatura", e);
        }
    }

    /**
     * Busca um Asset existente ou cria um novo
     */
    private Asset findOrCreateAsset(String symbol, AssetType type, PortfolioSource source) {
        return assetRepository.findBySymbol(symbol)
                .orElseGet(() -> {
                    Asset newAsset = Asset.builder()
                            .symbol(symbol)
                            .instrument(symbol)
                            .type(type)
                            .source(source)
                            .exchange(source == PortfolioSource.BINANCE ? "BINANCE" : null)
                            .build();

                    return assetRepository.save(newAsset);
                });
    }

    /**
     * Importa staking rewards da Binance
     */
    public List<Transaction> importStakingRewards(String apiKey, String apiSecret,
                                                  Long portfolioId) throws IOException {

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio não encontrado: " + portfolioId));

        long timestamp = System.currentTimeMillis();
        String queryString = "timestamp=" + timestamp;
        String signature = generateSignature(queryString, apiSecret);

        String url = binanceApiUrl + "/sapi/v1/staking/stakingRecord?" +
                queryString + "&signature=" + signature;

        Request request = new Request.Builder()
                .url(url)
                .header("X-MBX-APIKEY", apiKey)
                .build();

        List<Transaction> rewards = new ArrayList<>();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro ao buscar staking rewards: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode rewardsArray = objectMapper.readTree(responseBody);

            for (JsonNode reward : rewardsArray) {
                // Parse staking rewards como Transaction do tipo DIVIDEND
                // Implementação específica depende do formato da resposta da API
            }
        }

        return rewards;
    }
}
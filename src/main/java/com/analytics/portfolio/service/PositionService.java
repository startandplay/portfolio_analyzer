package com.analytics.portfolio.service;

import com.analytics.portfolio.dto.IPositionSummaryAggregation;
import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.*;
import com.analytics.portfolio.repository.DividendRepository;
import com.analytics.portfolio.repository.PositionRepository;
import com.analytics.portfolio.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço para calcular e gerenciar posições (holdings) do portfólio
 * Preenche TODOS os campos da Position baseado em transactions e dividends
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositionService {

    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;

    /**
     * Recalcula TODAS as posições de um portfólio baseado nas transações
     * Preenche todos os campos: quantity, averageBuyPrice, totalInvested,
     * realizedPL, totalDividendsReceived, firstPurchaseDate
     */
    @Transactional
    public List<Position> recalculatePositions(Long portfolioId) {
        log.info("Recalculando positions para portfolio {}", portfolioId);

        // 1. Get all transactions
        List<Transaction> transactions = transactionRepository
                .findByPortfolioIdOrderByTransactionDateDesc(portfolioId);

        // 2. Get some data from transactions
        Map<Asset, PositionData> positionsMap = calculatePositionsFromTransactions(transactions);

        // 3. Get calculated data from an aggregation between transactions and closed position
        List<IPositionSummaryAggregation> aggregatedPositions = positionRepository.recalculatePortfolioPositions(portfolioId);

        // 4. fill positionsMap with calculated data
        positionsMap.forEach((asset, positionData) -> {

            aggregatedPositions.stream()//
                    .filter(p -> p.getAssetId().equals(asset.getId()))//
                    .forEach(p -> {
                        positionData.averageBuyPrice = p.getAveragePurchasePrice();
                        positionData.quantity = p.getCurrentQuantity();
                        positionData.totalInvested = p.getCurrentTotalPurchaseValue().abs();
                        positionData.realizedPL = p.getTotalRealizedPnl();
                    });

        });

        // 5. Delete posições antigas
        positionRepository.deleteByPortfolioId(portfolioId);

        // 6. create new positions
        List<Position> newPositions = positionsMap.entrySet().stream()
                //.filter(entry -> entry.getValue().quantity.compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> createPosition(entry.getKey(), entry.getValue()))
                .toList();



        // 6. Save to DB
        List<Position> saved = positionRepository.saveAll(newPositions);

        log.info("Recalculadas {} posições para portfolio {}", saved.size(), portfolioId);

        return saved;
    }

    /**
     * Calcula posições a partir de transações e dividendos
     * Preenche TODOS os campos calculáveis
     */
    private Map<Asset, PositionData> calculatePositionsFromTransactions(
            List<Transaction> transactions) {

        Map<Asset, PositionData> positions = new HashMap<>();

        for (Transaction t : transactions) {
            Asset asset = t.getAsset();
            Portfolio portfolio = t.getPortfolio();
            TransactionType type = t.getType(); // Cache para evitar múltiplas chamadas
        
            PositionData data = positions.computeIfAbsent(asset,
                    a -> new PositionData(portfolio, asset));

            // Otimizado: switch em vez de if/else (mais eficiente em Java moderno)
            switch (type) {
                case BUY -> processBuyTransaction(t, data);
                case DIVIDEND -> processDividendTransaction(t, data);
                //case SELL -> processSellTransaction(t, data);
               // case WITHHOLDING_TAX -> processWithholdingTaxTransaction(t, data);
            }
        }

        // ===== CALCULAR PREÇO MÉDIO DE COMPRA =====
        positions.values().forEach(data -> {
            if (data.quantity.compareTo(BigDecimal.ZERO) > 0) {
                data.averageBuyPrice = data.totalInvested.divide(
                        data.quantity, 4, RoundingMode.HALF_UP);
            }
        });


        return positions;
    }

    private void processWithholdingTaxTransaction(Transaction t, PositionData data) {
    }

    /**
     * Processa transação de COMPRA
     * Consolidado: registra primeira compra + adiciona quantidade e custo
     */
    private void processBuyTransaction(Transaction t, PositionData data) {
        // Registrar data da primeira compra
        if (data.firstPurchaseDate == null ||
                t.getTransactionDate().isBefore(data.firstPurchaseDate)) {
            data.firstPurchaseDate = t.getTransactionDate();
        }
    }


    /**
     * Processa transação de VENDA
     * Calcula lucro/prejuízo realizado usando método FIFO
     */
    private void processSellTransaction(Transaction t, PositionData data) {
        // Calcular valor de venda (preço - fees)
        BigDecimal sellValue = t.getQuantity().multiply(t.getPrice());
        if (t.getFees() != null) {
            sellValue = sellValue.subtract(t.getFees());
        }

        // Calcular custo médio das ações vendidas (FIFO simplificado)
        BigDecimal avgCostPerShare = data.quantity.compareTo(BigDecimal.ZERO) > 0
                ? data.totalInvested.divide(data.quantity, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal costOfSold = t.getQuantity().multiply(avgCostPerShare);
        BigDecimal realizedPL = sellValue.subtract(costOfSold);

        // Atualizar dados
        data.realizedPL = data.realizedPL.add(realizedPL);
        data.quantity = data.quantity.subtract(t.getQuantity());
        data.totalInvested = data.totalInvested.subtract(costOfSold);
    }


    private void processDividendTransaction(Transaction t, PositionData data) {
        // PROCESSAR DIVIDENDOS
        if (data != null && t.getTotalAmount() != null) {
            data.totalDividendsReceived = data.totalDividendsReceived.add(t.getTotalAmount());
        }
    }


    /**
     * Cria uma Position a partir dos dados calculados
     * Preenche TODOS os campos disponíveis
     */
    private Position createPosition(Asset asset, PositionData data) {
        return Position.builder()
                .portfolio(data.portfolio)
                .asset(asset)
                .quantity(data.quantity)
                .averageBuyPrice(data.averageBuyPrice)
                .totalInvested(data.totalInvested)
                .realizedPL(data.realizedPL)
                .totalDividendsReceived(data.totalDividendsReceived)
                .firstPurchaseDate(data.firstPurchaseDate)
                // The current price is obtained from the Asset that already read the Yahoo Finance API
                // verify if currentPrice is null and call the YahooFinanceService to get the current prices
                .currentPrice(asset.getCurrentPrice())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * Busca position específica
     */
    public Optional<Position> getPosition(Long portfolioId, Long assetId) {
        return positionRepository.findByPortfolioIdAndAssetId(portfolioId, assetId);
    }

    /**
     * Lista todas as posições de um portfólio
     */
    public List<Position> getPositions(Long portfolioId) {
        return positionRepository.findByPortfolioId(portfolioId);
    }

    /**
     * Dados temporários para cálculo de position
     */
    private static class PositionData {
        Portfolio portfolio;
        Asset asset;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal averageBuyPrice = BigDecimal.ZERO;
        BigDecimal realizedPL = BigDecimal.ZERO;
        BigDecimal totalDividendsReceived = BigDecimal.ZERO;
        LocalDateTime firstPurchaseDate = null;

        PositionData(Portfolio portfolio, Asset asset) {
            this.portfolio = portfolio;
            this.asset = asset;
        }
    }
}
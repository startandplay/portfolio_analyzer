package com.analytics.portfolio.service;

import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.Transaction;
import com.analytics.portfolio.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HoldingsCalculationService {

    private final TransactionRepository transactionRepository;

    /**
     * Método SIMPLES - Loop básico
     */
    public Map<String, BigDecimal> calculateSimpleHoldings(Long portfolioId) {

        List<Transaction> trades = transactionRepository
                .findByPortfolioIdOrderByTransactionDateDesc(portfolioId);

        Map<String, BigDecimal> holdings = new HashMap<>();

        for (Transaction transaction : trades) {
            String symbol = transaction.getAsset().getSymbol();
            BigDecimal quantity = transaction.getQuantity();

            BigDecimal currentQuantity = holdings.getOrDefault(symbol, BigDecimal.ZERO);

            if (transaction.getType() == TransactionType.BUY) {
                holdings.put(symbol, currentQuantity.add(quantity));
            } else if (transaction.getType() == TransactionType.SELL) {
                holdings.put(symbol, currentQuantity.subtract(quantity));
            }
        }

        // Remover holdings zerados
        holdings.entrySet().removeIf(entry ->
                entry.getValue().compareTo(BigDecimal.ZERO) <= 0);

        return holdings;
    }
}

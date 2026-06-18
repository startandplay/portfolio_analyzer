package com.analytics.portfolio.service;

import com.analytics.portfolio.dto.AssetMetrics;
import com.analytics.portfolio.dto.PortfolioMetrics;
import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.Dividend;
import com.analytics.portfolio.model.Portfolio;
import com.analytics.portfolio.model.Position;
import com.analytics.portfolio.model.Transaction;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MetricsCalculationService {

    private static final int SCALE = 4;
    private static final int PERCENTAGE_SCALE = 2;
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365.25);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Calcula todas as métricas do portfólio
     */
    public PortfolioMetrics calculatePortfolioMetrics(Portfolio portfolio, List<Position> positions) {
        
        BigDecimal totalInvested = positions.stream()
            .map(Position::getTotalInvested)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentValue = positions.stream()
            .map(p -> p.getCurrentPrice() != null ? 
                p.getCurrentPrice().multiply(p.getQuantity()) : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedGains = currentValue.subtract(totalInvested);
        
        BigDecimal realizedGains = portfolio.getTransactions().stream()
            .filter(t -> t.getType() == TransactionType.SELL)
            .map(this::calculateRealizedPL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDividends = positions.stream()
            .map(Position::getTotalDividendsReceived)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcula retorno total
        BigDecimal totalReturn = unrealizedGains.add(realizedGains).add(totalDividends);
        BigDecimal totalReturnPercentage = totalInvested.compareTo(BigDecimal.ZERO) > 0 ?
            totalReturn.divide(totalInvested, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED) :
            BigDecimal.ZERO;

        // Calcula dias investidos
        LocalDateTime firstInvestment = portfolio.getTransactions().stream()
            .filter(t -> t.getType() == TransactionType.BUY)
            .map(Transaction::getTransactionDate)
            .min(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());

        long daysInvested = ChronoUnit.DAYS.between(firstInvestment, LocalDateTime.now());
        
        // Retorno anualizado: ((1 + totalReturn)^(365.25/days) - 1) * 100
        BigDecimal annualizedReturn = calculateAnnualizedReturn(
            totalReturnPercentage, BigDecimal.valueOf(daysInvested));

        // Dividend Yield anualizado
        BigDecimal dividendYield = totalInvested.compareTo(BigDecimal.ZERO) > 0 ?
            totalDividends.divide(totalInvested, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED) :
            BigDecimal.ZERO;

        BigDecimal annualizedDividendYield = calculateAnnualizedReturn(
            dividendYield, BigDecimal.valueOf(daysInvested));

        // Total fees
//        BigDecimal totalFees = portfolio.getTransactions().stream()
//            .map(t -> t.getFees() != null ? t.getFees() : BigDecimal.ZERO)
//            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PortfolioMetrics.builder()
            .portfolioId(portfolio.getId())
            .portfolioName(portfolio.getName())
            .totalInvested(totalInvested)
            .currentValue(currentValue)
            .totalValue(currentValue)
            .totalReturn(totalReturn)
            .totalReturnPercentage(totalReturnPercentage)
            .annualizedReturn(annualizedReturn)
            .totalDividendsReceived(totalDividends)
            .dividendYield(dividendYield)
            .annualizedDividendYield(annualizedDividendYield)
            .realizedGains(realizedGains)
            .unrealizedGains(unrealizedGains)
            .totalGains(unrealizedGains.add(realizedGains))
//            .totalFees(totalFees)
            .daysInvested((int) daysInvested)
            .firstInvestmentDate(firstInvestment)
            .lastUpdateDate(LocalDateTime.now())
            .numberOfPositions(positions.size())
            .numberOfAssets((int) positions.stream().map(Position::getAsset).distinct().count())
            .build();
    }

    /**
     * Calcula métricas individuais de um ativo
     */
    public AssetMetrics calculateAssetMetrics(Position position, List<Transaction> trades,
                                             List<Dividend> dividends) {
        
        BigDecimal totalDividends = dividends.stream()
            .map(Dividend::getNetAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal realizedPL = trades.stream()
            .filter(t -> t.getType() == TransactionType.SELL)
            .map(this::calculateRealizedPL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPL = position.getUnrealizedPL().add(realizedPL);
        BigDecimal totalPLPercentage = position.getTotalInvested().compareTo(BigDecimal.ZERO) > 0 ?
            totalPL.divide(position.getTotalInvested(), SCALE, RoundingMode.HALF_UP).multiply(HUNDRED) :
            BigDecimal.ZERO;

        // Retorno total com dividendos
        BigDecimal totalReturnWithDividends = totalPL.add(totalDividends);
        BigDecimal totalReturnWithDividendsPercentage = position.getTotalInvested().compareTo(BigDecimal.ZERO) > 0 ?
            totalReturnWithDividends.divide(position.getTotalInvested(), SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED) :
            BigDecimal.ZERO;

        // Dias mantidos
        long daysHeld = position.getFirstPurchaseDate() != null ?
            ChronoUnit.DAYS.between(position.getFirstPurchaseDate(), LocalDateTime.now()) : 0;

        // Retorno anualizado
        BigDecimal annualizedReturn = calculateAnnualizedReturn(
            totalReturnWithDividendsPercentage, BigDecimal.valueOf(daysHeld));

        // Dividend Yield
        BigDecimal dividendYield = position.getTotalInvested().compareTo(BigDecimal.ZERO) > 0 ?
            totalDividends.divide(position.getTotalInvested(), SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED) :
            BigDecimal.ZERO;

        BigDecimal annualizedDividendYield = calculateAnnualizedReturn(
            dividendYield, BigDecimal.valueOf(daysHeld));

        // Total fees
//        BigDecimal totalFees = transactions.stream()
//            .map(t -> t.getFees() != null ? t.getFees() : BigDecimal.ZERO)
//            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AssetMetrics.builder()
            .assetId(position.getAsset().getId())
            .symbol(position.getAsset().getSymbol())
            .ticker(position.getAsset().getTicker())
            .instrument(position.getAsset().getInstrument())
            .assetType(position.getAsset().getType().name())
            .quantity(position.getQuantity())
            .averageBuyPrice(position.getAverageBuyPrice())
            .currentPrice(position.getCurrentPrice())
            .totalInvested(position.getTotalInvested())
            .currentValue(position.getCurrentValue())
            .unrealizedPL(position.getUnrealizedPL())
            .unrealizedPLPercentage(position.getUnrealizedPLPercentage())
            .realizedPL(realizedPL)
            .totalPL(totalPL)
            .totalPLPercentage(totalPLPercentage)
            .annualizedReturn(annualizedReturn)
            .annualizedReturnPercentage(annualizedReturn)
            .totalDividends(totalDividends)
            .dividendYield(dividendYield)
            .annualizedDividendYield(annualizedDividendYield)
            .totalReturnWithDividends(totalReturnWithDividends)
            .totalReturnWithDividendsPercentage(totalReturnWithDividendsPercentage)
            .firstPurchaseDate(position.getFirstPurchaseDate())
            .daysHeld((int) daysHeld)
//            .totalFees(totalFees)
            .build();
    }

    /**
     * Calcula retorno anualizado usando fórmula composta
     * Fórmula: ((1 + r)^(365.25/days) - 1) * 100
     */
    private BigDecimal calculateAnnualizedReturn(BigDecimal returnPercentage, BigDecimal days) {
        if (days.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Converte percentagem para decimal
        BigDecimal returnDecimal = returnPercentage.divide(HUNDRED, 6, RoundingMode.HALF_UP);
        
        // (1 + r)
        BigDecimal onePlusReturn = BigDecimal.ONE.add(returnDecimal);
        
        // 365.25 / days
        BigDecimal exponent = DAYS_IN_YEAR.divide(days, 6, RoundingMode.HALF_UP);
        
        // (1 + r)^(365.25/days)
        double annualizedFactor = Math.pow(onePlusReturn.doubleValue(), exponent.doubleValue());
        
        // Subtrai 1 e converte para percentagem
        BigDecimal result = BigDecimal.valueOf(annualizedFactor)
            .subtract(BigDecimal.ONE)
            .multiply(HUNDRED);

        return result.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calcula volatilidade (desvio padrão anualizado dos retornos)
     */
    public BigDecimal calculateVolatility(List<BigDecimal> dailyReturns) {
        if (dailyReturns == null || dailyReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        DescriptiveStatistics stats = new DescriptiveStatistics();
        dailyReturns.forEach(r -> stats.addValue(r.doubleValue()));

        double stdDev = stats.getStandardDeviation();
        
        // Anualiza multiplicando por raiz quadrada de 252 (dias de trading)
        double annualizedVolatility = stdDev * Math.sqrt(252);

        return BigDecimal.valueOf(annualizedVolatility)
            .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calcula Sharpe Ratio
     * Sharpe = (Retorno do Portfolio - Taxa Livre de Risco) / Volatilidade
     */
    public BigDecimal calculateSharpeRatio(BigDecimal portfolioReturn, 
                                          BigDecimal riskFreeRate, 
                                          BigDecimal volatility) {
        if (volatility.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal excessReturn = portfolioReturn.subtract(riskFreeRate);
        return excessReturn.divide(volatility, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calcula o P&L realizado de uma transação de venda
     */
    private BigDecimal calculateRealizedPL(Transaction sellTransaction) {
        // Simplificado - em produção seria necessário usar FIFO/LIFO
        // e considerar o custo médio correto
        return BigDecimal.ZERO; // Placeholder
    }

    /**
     * Calcula o Maximum Drawdown
     */
    public BigDecimal calculateMaxDrawdown(List<BigDecimal> portfolioValues) {
        if (portfolioValues == null || portfolioValues.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal maxValue = portfolioValues.get(0);
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (BigDecimal value : portfolioValues) {
            if (value.compareTo(maxValue) > 0) {
                maxValue = value;
            }

            BigDecimal drawdown = maxValue.subtract(value)
                .divide(maxValue, SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);

            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }
}

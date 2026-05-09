package com.analytics.portfolio.service;

import com.analytics.portfolio.dto.ClosedPositionStats;
import com.analytics.portfolio.model.ClosedPosition;
import com.analytics.portfolio.repository.ClosedPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClosedPositionService {

    private final ClosedPositionRepository repo;

    /**
     * Calcula todas as métricas de performance de posições fechadas.
     *
     * Métricas calculadas:
     *  1. P&L total realizado
     *  2. Win Rate
     *  3. Profit Factor (Total Gains / Total Losses)
     *  4. Risk/Reward Ratio (Avg Win / Avg Loss)
     *  5. Custos reais totais (comissão + swap + rollover)
     *  6. ROI realizado considerando custos reais
     *  7. Custo médio % por trade → usado para ajustar break-even de posições abertas
     *  8. P&L por instrumento
     *  9. Melhor e pior trade
     */
    @Transactional(readOnly = true)
    public ClosedPositionStats getStats(Long portfolioId) {

        // ── Totais base ────────────────────────────────────────────
        BigDecimal totalPL       = repo.getTotalRealizedPL(portfolioId);
        BigDecimal totalInvested = repo.getTotalInvested(portfolioId);
        long totalTrades         = repo.countAllTrades(portfolioId);
        long winners             = repo.countWinners(portfolioId);
        long losers              = repo.countLosers(portfolioId);
        long breakEven           = totalTrades - winners - losers;

        // ── Win Rate ───────────────────────────────────────────────
        double winRate  = totalTrades > 0 ? (double) winners / totalTrades * 100 : 0;
        double lossRate = totalTrades > 0 ? (double) losers  / totalTrades * 100 : 0;

        // ── Médias ─────────────────────────────────────────────────
        BigDecimal avgWin  = repo.getAverageWin(portfolioId);
        BigDecimal avgLoss = repo.getAverageLoss(portfolioId).abs();

        BigDecimal avgPLPerTrade = totalTrades > 0
            ? totalPL.divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // ── Profit Factor = Sum(Wins) / Sum(Losses) ────────────────
        BigDecimal sumWins   = repo.getSumOfWins(portfolioId);
        BigDecimal sumLosses = repo.getSumOfLosses(portfolioId);  // já positivo

        BigDecimal profitFactor = sumLosses.compareTo(BigDecimal.ZERO) > 0
            ? sumWins.divide(sumLosses, 4, RoundingMode.HALF_UP)
            : sumWins.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("999") : BigDecimal.ZERO;

        // ── Risk/Reward Ratio = Avg Win / Avg Loss ─────────────────
        BigDecimal rrRatio = avgLoss.compareTo(BigDecimal.ZERO) > 0
            ? avgWin.divide(avgLoss, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // ── Custos reais ───────────────────────────────────────────
        BigDecimal totalCommissions = repo.getTotalCommissions(portfolioId);
        BigDecimal totalSwap        = repo.getTotalSwapCosts(portfolioId);
        BigDecimal totalRollover    = repo.getTotalRolloverCosts(portfolioId);
        BigDecimal totalAllCosts    = repo.getTotalAllCosts(portfolioId);
        BigDecimal avgCommission    = repo.getAvgCommissionPerTrade(portfolioId);

        // ── Custo % médio (útil para ajustar posições abertas) ─────
        // avgCostPct = totalAllCosts / totalInvested * 100
        BigDecimal avgCostPct = totalInvested.compareTo(BigDecimal.ZERO) > 0
            ? totalAllCosts.divide(totalInvested, 6, RoundingMode.HALF_UP)
                           .multiply(new BigDecimal("100"))
            : BigDecimal.ZERO;

        // ── ROI realizado = totalPL / totalInvested * 100 ──────────
        BigDecimal realizedROI = totalInvested.compareTo(BigDecimal.ZERO) > 0
            ? totalPL.divide(totalInvested, 6, RoundingMode.HALF_UP)
                     .multiply(new BigDecimal("100"))
            : BigDecimal.ZERO;

        // ── Melhor e pior trade ────────────────────────────────────
        ClosedPositionStats.TradeInfo bestTrade  = repo.getBestTrade(portfolioId).map(this::toTradeInfo).orElse(null);
        ClosedPositionStats.TradeInfo worstTrade = repo.getWorstTrade(portfolioId).map(this::toTradeInfo).orElse(null);

        // ── Por ticker ─────────────────────────────────────────────
        List<ClosedPositionStats.TickerStats> byTicker = repo.getPLGroupedByTicker(portfolioId)
            .stream()
            .map(row -> {
                String ticker      = (String) row[0];
                String instrument  = (String) row[1];
                long   count       = ((Number) row[2]).longValue();
                BigDecimal pl      = (BigDecimal) row[3];
                BigDecimal avgPl   = (BigDecimal) row[4];

                BigDecimal tickerCommissions = totalTrades > 0
                    ? totalCommissions.multiply(BigDecimal.valueOf(count))
                                      .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                // Win rate por ticker
                List<ClosedPosition> tickerPositions =
                    repo.findByPortfolioIdAndTicker(portfolioId, ticker);
                long tickerWins = tickerPositions.stream().filter(ClosedPosition::isWinner).count();
                BigDecimal tickerWinRate = count > 0
                    ? BigDecimal.valueOf(tickerWins * 100.0 / count).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                return ClosedPositionStats.TickerStats.builder()
                    .ticker(ticker)
                    .instrument(instrument)
                    .tradeCount(count)
                    .totalPL(pl != null ? pl.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .avgPL(avgPl != null ? avgPl.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .totalCommissions(tickerCommissions)
                    .winRate(tickerWinRate)
                    .build();
            })
            .collect(Collectors.toList());

        return ClosedPositionStats.builder()
            .totalRealizedPL(totalPL)
            .totalCommissions(totalCommissions)
            .totalSwapCosts(totalSwap)
            .totalRolloverCosts(totalRollover)
            .totalAllCosts(totalAllCosts)
            .avgCommissionPerTrade(avgCommission)
            .totalTrades(totalTrades)
            .winners(winners)
            .losers(losers)
            .breakEven(breakEven)
            .winRate(Math.round(winRate * 10.0) / 10.0)
            .lossRate(Math.round(lossRate * 10.0) / 10.0)
            .profitFactor(profitFactor)
            .riskRewardRatio(rrRatio)
            .averageWin(avgWin)
            .averageLoss(avgLoss)
            .averagePLPerTrade(avgPLPerTrade)
            .bestTrade(bestTrade)
            .worstTrade(worstTrade)
            .avgCostPercentage(avgCostPct)
            .totalInvestedInClosed(totalInvested)
            .realizedROI(realizedROI)
            .byTicker(byTicker)
            .build();
    }

    private ClosedPositionStats.TradeInfo toTradeInfo(ClosedPosition cp) {
        return ClosedPositionStats.TradeInfo.builder()
            .ticker(cp.getTicker())
            .instrument(cp.getInstrument())
            .type(cp.getType())
            .profitLoss(cp.getProfitLoss())
            .openPrice(cp.getOpenPrice())
            .closePrice(cp.getClosePrice())
            .volume(cp.getVolume())
            .openTime(cp.getOpenTime() != null ? cp.getOpenTime().toString() : null)
            .closeTime(cp.getCloseTime() != null ? cp.getCloseTime().toString() : null)
            .holdingHours(cp.getHoldingHours())
            .closeOrigin(cp.getCloseOrigin())
            .build();
    }
}

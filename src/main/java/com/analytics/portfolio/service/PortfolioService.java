package com.analytics.portfolio.service;

import com.analytics.portfolio.dto.*;
import com.analytics.portfolio.enums.AssetType;
import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.model.Portfolio;
import com.analytics.portfolio.model.Position;
import com.analytics.portfolio.model.User;
import com.analytics.portfolio.repository.PortfolioRepository;
import com.analytics.portfolio.repository.PositionRepository;
import com.analytics.portfolio.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço de portfolios com:
 * - Validação de ownership (user só acede aos seus portfolios)
 * - CRUD com user implícito (não passado pelo cliente)
 * - Cálculo de métricas agregadas cross-portfolio
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final MetricsCalculationService metricsService;

    // ── CRUD ──────────────────────────────────────────────────────

    /**
     * Lista todos os portfolios do utilizador como PortfolioSummary.
     */
    @Transactional(readOnly = true)
    public List<PortfolioSummary> listPortfolios(User user) {
        List<Portfolio> portfolios = portfolioRepository
                .findByUserIdOrderByNameAsc(user.getId());

        return portfolios.stream()
                .map(p -> buildSummary(p, user))
                .collect(Collectors.toList());
    }

    /**
     * Cria um novo portfolio para o utilizador autenticado.
     */
    @Transactional
    public Portfolio createPortfolio(CreatePortfolioRequest request, User user) {

        // Validar nome único por user
        if (portfolioRepository.existsByUserIdAndName(user.getId(), request.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have a portfolio named '" + request.getName() + "'");
        }

        Portfolio portfolio = Portfolio.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .source(request.getSource())
                .currency(request.getCurrency() != null ? request.getCurrency() : "EUR")
                .initialCapital(request.getInitialCapital())
                .includeInAggregate(request.isIncludeInAggregate())
                .build();

        Portfolio saved = portfolioRepository.save(portfolio);
        log.info("Portfolio '{}' (source={}) criado para user {}", saved.getName(), saved.getSource(), user.getId());
        return saved;
    }

    /**
     * Devolve um portfolio verificando que pertence ao utilizador.
     * Lança 404 se não existir ou 403 se não for do user.
     */
    @Transactional(readOnly = true)
    public Portfolio getPortfolio(Long portfolioId, User user) {
        return portfolioRepository.findByIdAndUserId(portfolioId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Portfolio not found or access denied"));
    }

    /**
     * Atualiza campos editáveis de um portfolio.
     */
    @Transactional
    public Portfolio updatePortfolio(Long portfolioId, CreatePortfolioRequest request, User user) {
        Portfolio portfolio = getPortfolio(portfolioId, user);

        // Validar nome único se foi alterado
        if (!portfolio.getName().equals(request.getName()) &&
                portfolioRepository.existsByUserIdAndName(user.getId(), request.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have a portfolio named '" + request.getName() + "'");
        }

        portfolio.setName(request.getName());
        portfolio.setDescription(request.getDescription());
        portfolio.setSource(request.getSource());
        portfolio.setCurrency(request.getCurrency() != null ? request.getCurrency() : portfolio.getCurrency());
        portfolio.setInitialCapital(request.getInitialCapital());
        portfolio.setIncludeInAggregate(request.isIncludeInAggregate());

        return portfolioRepository.save(portfolio);
    }

    /**
     * Remove um portfolio e todos os seus dados (cascade).
     */
    @Transactional
    public void deletePortfolio(Long portfolioId, User user) {
        Portfolio portfolio = getPortfolio(portfolioId, user);
        portfolioRepository.delete(portfolio);
        log.info("Portfolio {} eliminado pelo user {}", portfolioId, user.getId());
    }

    /**
     * Verifica ownership sem lançar exceção.
     * Util para guards internos.
     */
    public boolean isOwner(Long portfolioId, User user) {
        return portfolioRepository.existsByIdAndUserId(portfolioId, user.getId());
    }

    // ── Métricas agregadas ────────────────────────────────────────

    /**
     * Calcula a visão consolidada de todos os portfolios do utilizador
     * que têm includeInAggregate = true.
     */
    @Transactional(readOnly = true)
    public AggregatePortfolioMetrics getAggregateMetrics(User user) {

        List<Portfolio> portfolios = portfolioRepository
                .findByUserIdAndIncludeInAggregateTrue(user.getId());

        if (portfolios.isEmpty()) {
            return buildEmptyAggregate(user);
        }

        // Calcular métricas individuais de cada portfolio
        List<PortfolioMetrics> allMetrics = portfolios.stream()
                .map(p -> {
                    List<Position> positions = positionRepository.findByPortfolioId(p.getId());
                    try {
                        return metricsService.calculatePortfolioMetrics(p, positions);
                    } catch (Exception e) {
                        log.warn("Erro ao calcular métricas do portfolio {}: {}", p.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return buildAggregateMetrics(user, portfolios, allMetrics);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private AggregatePortfolioMetrics buildAggregateMetrics(
            User user,
            List<Portfolio> portfolios,
            List<PortfolioMetrics> allMetrics) {

        // Totais
        BigDecimal totalNetWorth = sum(allMetrics, PortfolioMetrics::getCurrentValue);
        BigDecimal totalInvested = sum(allMetrics, PortfolioMetrics::getTotalInvested);
        BigDecimal totalReturn = sum(allMetrics, PortfolioMetrics::getTotalReturn);
        BigDecimal totalDividends = sum(allMetrics, PortfolioMetrics::getTotalDividendsReceived);
        BigDecimal totalUnrealized = sum(allMetrics, PortfolioMetrics::getUnrealizedGains);
        BigDecimal totalRealized = sum(allMetrics, PortfolioMetrics::getRealizedGains);

        // Retorno % total
        BigDecimal totalReturnPct = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? totalReturn.divide(totalInvested, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // CAGR ponderado — média ponderada pelo valor investido
        BigDecimal weightedCagr = calculateWeightedCagr(allMetrics, totalInvested);

        // Alocação por source
        Map<PortfolioSource, BigDecimal> valueBySource = new EnumMap<>(PortfolioSource.class);
        Map<PortfolioSource, BigDecimal> allocBySource = new EnumMap<>(PortfolioSource.class);
        for (int i = 0; i < portfolios.size(); i++) {
            PortfolioSource src = portfolios.get(i).getSource();
            BigDecimal val = allMetrics.get(i).getCurrentValue() != null
                    ? allMetrics.get(i).getCurrentValue() : BigDecimal.ZERO;
            valueBySource.merge(src, val, BigDecimal::add);
        }
        if (totalNetWorth.compareTo(BigDecimal.ZERO) > 0) {
            valueBySource.forEach((src, val) ->
                    allocBySource.put(src, val.divide(totalNetWorth, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))));
        }

        // Alocação por asset type — usando posições de todos os portfolios
        Map<AssetType, BigDecimal> valueByAsset = new EnumMap<>(AssetType.class);
        Map<AssetType, BigDecimal> allocByAsset = new EnumMap<>(AssetType.class);
        portfolios.forEach(p -> {
            positionRepository.findByPortfolioId(p.getId()).forEach(pos -> {
                if (pos.getCurrentValue() != null && pos.getAsset() != null) {
                    AssetType type = pos.getAsset().getType();
                    valueByAsset.merge(type, pos.getCurrentValue(), BigDecimal::add);
                }
            });
        });
        if (totalNetWorth.compareTo(BigDecimal.ZERO) > 0) {
            valueByAsset.forEach((type, val) ->
                    allocByAsset.put(type, val.divide(totalNetWorth, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))));
        }

        // Concentration risk — maior posição individual
        String[] largestTicker = {null};
        BigDecimal[] largestValue = {BigDecimal.ZERO};
        portfolios.forEach(p ->
                positionRepository.findByPortfolioId(p.getId()).forEach(pos -> {
                    if (pos.getCurrentValue() != null &&
                            pos.getCurrentValue().compareTo(largestValue[0]) > 0) {
                        largestValue[0] = pos.getCurrentValue();
                        largestTicker[0] = pos.getAsset() != null ? pos.getAsset().getTicker() : "–";
                    }
                })
        );
        BigDecimal concentrationRisk = totalNetWorth.compareTo(BigDecimal.ZERO) > 0
                ? largestValue[0].divide(totalNetWorth, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Distinct assets
        Set<Long> distinctAssets = new HashSet<>();
        portfolios.forEach(p ->
                positionRepository.findByPortfolioId(p.getId())
                        .forEach(pos -> {
                            if (pos.getAsset() != null) distinctAssets.add(pos.getAsset().getId());
                        })
        );

        // Portfolio breakdown
        int inProfit = 0, inLoss = 0;
        List<AggregatePortfolioMetrics.PortfolioContribution> breakdown = new ArrayList<>();
        for (int i = 0; i < portfolios.size(); i++) {
            Portfolio p = portfolios.get(i);
            PortfolioMetrics m = allMetrics.get(i);
            BigDecimal val = m.getCurrentValue() != null ? m.getCurrentValue() : BigDecimal.ZERO;
            BigDecimal weight = totalNetWorth.compareTo(BigDecimal.ZERO) > 0
                    ? val.divide(totalNetWorth, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            boolean profit = m.getTotalReturn() != null &&
                    m.getTotalReturn().compareTo(BigDecimal.ZERO) >= 0;
            if (profit) inProfit++;
            else inLoss++;
            breakdown.add(AggregatePortfolioMetrics.PortfolioContribution.builder()
                    .portfolioId(p.getId())
                    .portfolioName(p.getName())
                    .source(p.getSource())
                    .currency(p.getCurrency())
                    .currentValue(val)
                    .totalInvested(m.getTotalInvested())
                    .totalReturn(m.getTotalReturn())
                    .totalReturnPercentage(m.getTotalReturnPercentage())
                    .weightPercentage(weight)
                    .numberOfPositions(m.getNumberOfPositions() != null ? m.getNumberOfPositions() : 0)
                    .inProfit(profit)
                    .build());
        }

        return AggregatePortfolioMetrics.builder()
                .userId(user.getId())
                .baseCurrency("EUR")
                .calculatedAt(LocalDateTime.now())
                .portfoliosIncluded(portfolios.size())
                .totalNetWorth(totalNetWorth)
                .totalInvested(totalInvested)
                .totalReturn(totalReturn)
                .totalReturnPercentage(totalReturnPct)
                .weightedAnnualizedReturn(weightedCagr)
                .totalDividendsNet(totalDividends)
                .totalInterestNet(BigDecimal.ZERO) // enriquecido pelo IncomeService se necessário
                .totalIncomeNet(totalDividends)
                .totalUnrealizedGains(totalUnrealized)
                .totalRealizedGains(totalRealized)
                .allocationByAssetType(allocByAsset)
                .valueByAssetType(valueByAsset)
                .allocationBySource(allocBySource)
                .valueBySource(valueBySource)
                .concentrationRisk(concentrationRisk)
                .largestHoldingTicker(largestTicker[0])
                .largestHoldingPercentage(concentrationRisk)
                .numberOfDistinctAssets(distinctAssets.size())
                .portfoliosInProfit(inProfit)
                .portfoliosInLoss(inLoss)
                .portfolioBreakdown(breakdown)
                .build();
    }

    /**
     * CAGR ponderado = soma(CAGR_i * investido_i) / total_investido
     */
    private BigDecimal calculateWeightedCagr(List<PortfolioMetrics> metrics, BigDecimal totalInvested) {
        if (totalInvested.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal weightedSum = metrics.stream()
                .filter(m -> m.getAnnualizedReturn() != null && m.getTotalInvested() != null)
                .map(m -> m.getAnnualizedReturn().multiply(m.getTotalInvested()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return weightedSum.divide(totalInvested, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<PortfolioMetrics> metrics,
                           java.util.function.Function<PortfolioMetrics, BigDecimal> getter) {
        return metrics.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PortfolioSummary buildSummary(Portfolio p, User user) {
        List<Position> positions = positionRepository.findByPortfolioId(p.getId());

        BigDecimal totalInvested = positions.stream()
                .map(Position::getTotalInvested)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentValue = positions.stream()
                .map(pos -> pos.getCurrentPrice() != null
                        ? pos.getCurrentPrice().multiply(pos.getQuantity())
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReturn = currentValue.subtract(totalInvested);
        BigDecimal totalReturnPct = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? totalReturn.divide(totalInvested, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        int txCount = transactionRepository.findByPortfolioId(p.getId()).size();

        return PortfolioSummary.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .source(p.getSource())
                .currency(p.getCurrency())
                .includeInAggregate(p.isIncludeInAggregate())
                .totalInvested(totalInvested)
                .currentValue(currentValue)
                .totalReturn(totalReturn)
                .totalReturnPercentage(totalReturnPct)
                .numberOfPositions(positions.size())
                .numberOfTransactions(txCount)
                .createdAt(p.getCreatedAt())
                .build();
    }

    private AggregatePortfolioMetrics buildEmptyAggregate(User user) {
        return AggregatePortfolioMetrics.builder()
                .userId(user.getId())
                .baseCurrency("EUR")
                .calculatedAt(LocalDateTime.now())
                .portfoliosIncluded(0)
                .totalNetWorth(BigDecimal.ZERO)
                .totalInvested(BigDecimal.ZERO)
                .totalReturn(BigDecimal.ZERO)
                .totalReturnPercentage(BigDecimal.ZERO)
                .portfolioBreakdown(List.of())
                .build();
    }
}

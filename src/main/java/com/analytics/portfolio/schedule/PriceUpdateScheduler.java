package com.analytics.portfolio.schedule;

import com.analytics.portfolio.config.YahooFinanceConfig;
import com.analytics.portfolio.model.Position;
import com.analytics.portfolio.repository.PositionRepository;
import com.analytics.portfolio.service.YahooFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serviço para atualização automática de preços via Yahoo Finance
 * Executa em intervalos configuráveis
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "yahoo-finance",
        name = "auto-update-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class PriceUpdateScheduler {

    private final YahooFinanceService yahooFinanceService;
    private final PositionRepository positionRepository;
    private final YahooFinanceConfig config;

    /**
     * Atualiza preços de todos os ativos em posições ativas
     * Executado conforme configuração (default: 1 hora)
     *
     * Cron: ${yahoo-finance.update-interval-minutes} minutos
     */
    @Scheduled(
            fixedDelayString = "${yahoo-finance.update-interval-minutes:60}",
            initialDelay = 60000 // Aguarda 1 minuto após startup
    )
    public void updateAllActivePrices() {
        log.info("Iniciando atualização automática de preços via Yahoo Finance...");

        try {
            // Buscar todos os símbolos únicos de posições ativas
            List<Position> positions = positionRepository.findAll();

            Set<String> symbols = positions.stream()
                    .map(p -> p.getAsset().getSymbol())
                    .collect(Collectors.toSet());

            if (symbols.isEmpty()) {
                log.info("Nenhum ativo para atualizar");
                return;
            }

            log.info("Atualizando preços de {} ativos únicos", symbols.size());

            int updated = 0;
            int failed = 0;

            for (String symbol : symbols) {
                try {
                    var price = yahooFinanceService.updateAssetPrice(symbol);
                    if (price != null) {
                        updated++;
                        log.debug("Atualizado: {} = {}", symbol, price);
                    } else {
                        failed++;
                    }

                    // Rate limiting - 2 segundos entre chamadas (RapidAPI free: 500 req/mês)
                    Thread.sleep(2000);

                } catch (Exception e) {
                    log.error("Erro ao atualizar {}: {}", symbol, e.getMessage());
                    failed++;
                }
            }

            log.info("Atualização concluída: {} sucesso, {} falhas", updated, failed);

        } catch (Exception e) {
            log.error("Erro na atualização automática de preços", e);
        }
    }

    /**
     * Executa limpeza de histórico antigo (opcional)
     * Executado 1x por dia às 3h da manhã
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOldPriceHistory() {
        log.info("Limpeza de histórico de preços não implementada ainda");
        // TODO: implementar limpeza de registros > 1 ano
    }
}
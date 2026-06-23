package com.analytics.portfolio.integration;

import com.analytics.portfolio.model.*;
import com.analytics.portfolio.enums.PortfolioSource;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Contrato para o mapeamento raw JSON → entidades canónicas.
 *
 * Cada BrokerImportAdapter implementa também esta interface,
 * tornando o ImportOrchestrator completamente agnóstico ao
 * formato de cada corretora.
 *
 * O método map() devolve um Result com as entidades criadas
 * (pode conter zero ou mais de cada tipo por linha raw).
 */
public interface BrokerCanonicalMapper {

    /** Mesma source que o adaptador — garante que o mapper certo é escolhido */
    PortfolioSource getSource();

    /**
     * Converte o payload JSON de um RawImportRecord nas entidades canónicas.
     *
     * @param payload   JSON do raw_payload
     * @param portfolio portfolio de destino
     * @return Result com as listas de entidades criadas (podem estar vazias)
     */
    MappingResult map(JsonNode payload, Portfolio portfolio);

    record MappingResult(
            List<Transaction>    transactions,
            List<CashFlow>       cashFlows,
            List<ClosedPosition> closedPositions
    ) {
        public static MappingResult empty() {
            return new MappingResult(List.of(), List.of(), List.of());
        }

        public boolean isEmpty() {
            return transactions.isEmpty() && cashFlows.isEmpty() && closedPositions.isEmpty();
        }
    }
}

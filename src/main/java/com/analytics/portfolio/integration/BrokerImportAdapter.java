package com.analytics.portfolio.integration;

import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.model.Portfolio;
import com.analytics.portfolio.model.RawImportRecord;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Contrato para adaptadores de importação de corretoras.
 *
 * Cada corretora implementa esta interface. A responsabilidade
 * do adaptador é APENAS:
 *   1. Ler o ficheiro
 *   2. Converter cada linha num RawImportRecord com o payload JSON
 *
 * A orquestração (dedup, mapeamento canónico, persistência) é
 * responsabilidade do ImportOrchestrator — não do adaptador.
 *
 * Para adicionar uma nova corretora:
 *   1. Criar XyzImportAdapter implements BrokerImportAdapter
 *   2. Anotá-la com @Component
 *   3. O ImportOrchestrator deteta-a automaticamente via Spring
 */
public interface BrokerImportAdapter {

    /** Identifica a corretora que este adaptador suporta */
    PortfolioSource getSource();

    /**
     * Lê o ficheiro e devolve uma lista de registos brutos.
     * Cada registo contém o payload JSON da linha original.
     *
     * @param file        ficheiro enviado pelo utilizador
     * @param portfolio   portfolio de destino
     * @param batchId     UUID do batch — deve ser injetado em cada record
     * @return lista de RawImportRecord com status=PENDING
     */
    List<RawImportRecord> parse(MultipartFile file, Portfolio portfolio, String batchId)
            throws IOException;
}

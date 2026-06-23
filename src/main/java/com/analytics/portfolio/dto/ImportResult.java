package com.analytics.portfolio.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO de resposta unificado para qualquer importação de broker.
 * Substitui o ImportResult inner-class do XTBImportService.
 */
@Data
@Builder
public class ImportResult {

    private String batchId;
    private String source;

    private int totalRawRecords;       // linhas gravadas em raw
    private int transactionsImported;
    private int transactionsDuplicate;
    private int cashFlowsImported;
    private int cashFlowsDuplicate;
    private int closedPositionsImported;
    private int closedPositionsDuplicate;
    private int failedRecords;         // linhas que falharam o parse/mapeamento

    public int getTotalImported() {
        return transactionsImported + cashFlowsImported + closedPositionsImported;
    }

    public String getSummary() {
        return String.format(
            "[%s] batch=%s raw=%d importados=%d duplicatas=%d erros=%d",
            source, batchId, totalRawRecords,
            getTotalImported(),
            transactionsDuplicate + cashFlowsDuplicate + closedPositionsDuplicate,
            failedRecords
        );
    }
}

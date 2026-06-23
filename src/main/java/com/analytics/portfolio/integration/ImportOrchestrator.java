package com.analytics.portfolio.integration;

import com.analytics.portfolio.dto.ImportResult;
import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.enums.RawImportStatus;
import com.analytics.portfolio.model.*;
import com.analytics.portfolio.repository.*;
import com.analytics.portfolio.service.DuplicateDetectionService;
import com.analytics.portfolio.service.PositionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Orquestra o pipeline de importação para qualquer corretora.
 *
 * Passos fixos (independente da corretora):
 *   1. BrokerImportAdapter.parse()  → lista de RawImportRecord (payload JSON)
 *   2. Gravar todos os raw records na BD
 *   3. BrokerCanonicalMapper.map() → entidades canónicas por record
 *   4. DuplicateDetectionService   → filtrar duplicatas
 *   5. Persistir entidades novas + atualizar status dos raw records
 *
 * Para adicionar uma nova corretora:
 *   1. Criar XyzImportAdapter implements BrokerImportAdapter, BrokerCanonicalMapper
 *   2. Anotar com @Component
 *   3. Não é necessário tocar no ImportOrchestrator.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImportOrchestrator {

    private final List<BrokerImportAdapter>   adapters;
    private final List<BrokerCanonicalMapper> mappers;
    private final RawImportRecordRepository   rawRepo;
    private final TransactionRepository       transactionRepo;
    private final CashFlowRepository          cashFlowRepo;
    private final ClosedPositionRepository    closedPositionRepo;
    private final PortfolioRepository         portfolioRepo;
    private final DuplicateDetectionService   duplicateService;
    private final PositionService             positionService;
    private final ObjectMapper                objectMapper;

    // ════════════════════════════════════════════════════════════
    // Entry point
    // ════════════════════════════════════════════════════════════

    @Transactional
    public ImportResult importFile(MultipartFile file, Long portfolioId, PortfolioSource source)
            throws Exception {

        Portfolio portfolio = portfolioRepo.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio não encontrado: " + portfolioId));

        BrokerImportAdapter adapter = adapters.stream()
                .filter(a -> a.getSource() == source)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sem adaptador para source: " + source));

        BrokerCanonicalMapper mapper = mappers.stream()
                .filter(m -> m.getSource() == source)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sem mapper canónico para source: " + source));

        String batchId = UUID.randomUUID().toString();
        log.info("[{}] Iniciando importação batch={} portfolio={}", source, batchId, portfolioId);

        // ── Passo 1 & 2: parse + gravar raw ──────────────────────
        List<RawImportRecord> rawRecords = adapter.parse(file, portfolio, batchId);
        rawRepo.saveAll(rawRecords);
        log.info("[{}] {} registos raw gravados", source, rawRecords.size());

        // ── Passo 3: mapear raw → canónico ───────────────────────
        List<Transaction>    transactions    = new ArrayList<>();
        List<CashFlow>       cashFlows       = new ArrayList<>();
        List<ClosedPosition> closedPositions = new ArrayList<>();

        for (RawImportRecord record : rawRecords) {
            try {
                JsonNode payload = objectMapper.readTree(record.getRawPayload());
                BrokerCanonicalMapper.MappingResult result = mapper.map(payload, portfolio);

                if (!result.isEmpty()) {
                    transactions.addAll(result.transactions());
                    cashFlows.addAll(result.cashFlows());
                    closedPositions.addAll(result.closedPositions());

                    // Marcar tipo para depois atualizar o status
                    if (!result.transactions().isEmpty())    record.setMappedEntityType("TRANSACTION");
                    else if (!result.cashFlows().isEmpty())  record.setMappedEntityType("CASH_FLOW");
                    else if (!result.closedPositions().isEmpty()) record.setMappedEntityType("CLOSED_POSITION");
                }

            } catch (Exception e) {
                record.setStatus(RawImportStatus.FAILED);
                record.setErrorMessage(e.getMessage());
                log.warn("[{}] Falha no mapeamento linha {}: {}", source, record.getRowNumber(), e.getMessage());
            }
        }

        // ── Passo 4: dedup ───────────────────────────────────────
        List<Transaction>    newTx = duplicateService.filterDuplicates(transactions,  transactionRepo::existsByImportFingerprint);
        List<CashFlow>       newCf = duplicateService.filterDuplicates(cashFlows,      cashFlowRepo::existsByImportFingerprint);
        List<ClosedPosition> newCp = duplicateService.filterDuplicates(closedPositions, closedPositionRepo::existsByImportFingerprint);

        int dupTx = transactions.size()    - newTx.size();
        int dupCf = cashFlows.size()       - newCf.size();
        int dupCp = closedPositions.size() - newCp.size();

        // ── Passo 5: persistir ───────────────────────────────────
        if (!newCp.isEmpty()) closedPositionRepo.saveAll(newCp);
        if (!newTx.isEmpty()) transactionRepo.saveAll(newTx);
        if (!newCf.isEmpty()) cashFlowRepo.saveAll(newCf);

        markRawRecords(rawRecords);
        rawRepo.saveAll(rawRecords);

        if (!newTx.isEmpty()) {
            positionService.recalculatePositions(portfolioId);
        }

        long failed = rawRecords.stream().filter(r -> r.getStatus() == RawImportStatus.FAILED).count();

        ImportResult result = ImportResult.builder()
                .batchId(batchId)
                .source(source.name())
                .totalRawRecords(rawRecords.size())
                .transactionsImported(newTx.size())
                .transactionsDuplicate(dupTx)
                .cashFlowsImported(newCf.size())
                .cashFlowsDuplicate(dupCf)
                .closedPositionsImported(newCp.size())
                .closedPositionsDuplicate(dupCp)
                .failedRecords((int) failed)
                .build();

        log.info("[{}] {}", source, result.getSummary());
        return result;
    }

    // ════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════

    private void markRawRecords(List<RawImportRecord> records) {
        records.forEach(r -> {
            if (r.getStatus() == RawImportStatus.FAILED) return;
            if (r.getMappedEntityType() != null) {
                r.setStatus(RawImportStatus.MAPPED);
            } else {
                r.setStatus(RawImportStatus.DUPLICATE);
            }
        });
    }
}

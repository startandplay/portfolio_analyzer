package com.analytics.portfolio.integration;

import com.analytics.portfolio.dto.ImportResult;
import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.enums.RawImportStatus;
import com.analytics.portfolio.enums.TransactionType;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orquestra o pipeline de importação para qualquer corretora.
 *
 * Passos fixos (independente da corretora):
 *   1. adapter.parse()  → lista de RawImportRecord (payload JSON)
 *   2. Gravar todos os raw records na BD
 *   3. Mapear raw → entidades canónicas (Transaction / CashFlow / ClosedPosition)
 *   4. DuplicateDetectionService → filtrar duplicatas
 *   5. Persistir entidades novas + atualizar status dos raw records
 *
 * Para adicionar uma nova corretora: criar um @Component que implemente
 * BrokerImportAdapter — o orchestrator deteta-o automaticamente.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImportOrchestrator {

    private final List<BrokerImportAdapter>    adapters;
    private final RawImportRecordRepository    rawRepo;
    private final TransactionRepository        transactionRepo;
    private final CashFlowRepository           cashFlowRepo;
    private final ClosedPositionRepository     closedPositionRepo;
    private final PortfolioRepository          portfolioRepo;
    private final AssetRepository              assetRepo;
    private final DuplicateDetectionService    duplicateService;
    private final PositionService              positionService;
    private final ObjectMapper                 objectMapper;
    private final XTBImportAdapter             xtbAdapter;   // acesso aos helpers de parse

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

        String batchId = UUID.randomUUID().toString();
        log.info("[{}] Iniciando importação batch={} portfolio={}", source, batchId, portfolioId);

        // ── Passo 1 & 2: parse + gravar raw ──────────────────────
        List<RawImportRecord> rawRecords = adapter.parse(file, portfolio, batchId);
        rawRepo.saveAll(rawRecords);
        log.info("[{}] {} registos raw gravados", source, rawRecords.size());

        // ── Passo 3 & 4 & 5: mapear, dedup, persistir ────────────
        List<Transaction>    transactions    = new ArrayList<>();
        List<CashFlow>       cashFlows       = new ArrayList<>();
        List<ClosedPosition> closedPositions = new ArrayList<>();

        for (RawImportRecord record : rawRecords) {
            try {
                mapRecord(record, portfolio, transactions, cashFlows, closedPositions);
            } catch (Exception e) {
                record.setStatus(RawImportStatus.FAILED);
                record.setErrorMessage(e.getMessage());
                log.warn("[{}] Falha no mapeamento linha {}: {}", source, record.getRowNumber(), e.getMessage());
            }
        }

        // Dedup
        List<Transaction>    newTx  = duplicateService.filterDuplicates(transactions,  transactionRepo::existsByImportFingerprint);
        List<CashFlow>       newCf  = duplicateService.filterDuplicates(cashFlows,      cashFlowRepo::existsByImportFingerprint);
        List<ClosedPosition> newCp  = duplicateService.filterDuplicates(closedPositions, closedPositionRepo::existsByImportFingerprint);

        int dupTx = transactions.size()    - newTx.size();
        int dupCf = cashFlows.size()       - newCf.size();
        int dupCp = closedPositions.size() - newCp.size();

        // Persistir
        if (!newCp.isEmpty())  closedPositionRepo.saveAll(newCp);
        if (!newTx.isEmpty())  transactionRepo.saveAll(newTx);
        if (!newCf.isEmpty())  cashFlowRepo.saveAll(newCf);

        // Atualizar status dos raw records para MAPPED/DUPLICATE
        markRawRecords(rawRecords, newTx, newCf, newCp);
        rawRepo.saveAll(rawRecords);

        // Recalcular posições se houve novas transações
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
    // Mapeamento raw → canónico (XTB)
    // ════════════════════════════════════════════════════════════

    private void mapRecord(RawImportRecord record, Portfolio portfolio,
                           List<Transaction> txOut,
                           List<CashFlow> cfOut,
                           List<ClosedPosition> cpOut) throws Exception {

        JsonNode payload = objectMapper.readTree(record.getRawPayload());
        String sheetType = payload.path("sheetType").asText();

        switch (sheetType) {
            case "CASH_OPERATIONS"  -> mapCashOps(payload, portfolio, record, txOut, cfOut);
            case "CLOSED_POSITIONS" -> mapClosedPos(payload, portfolio, record, cpOut);
            default -> throw new IllegalArgumentException("sheetType desconhecido: " + sheetType);
        }
    }

    private void mapCashOps(JsonNode p, Portfolio portfolio, RawImportRecord record,
                             List<Transaction> txOut, List<CashFlow> cfOut) {

        String ticker       = p.path("ticker").asText("");
        String type         = p.path("type").asText("");
        String comment      = p.path("comment").asText("");
        String transactionId= p.path("transactionId").asText("");
        BigDecimal amount   = bd(p.path("amount").asText(null));
        LocalDateTime time  = dt(p.path("time").asText(null));

        boolean hasAsset = ticker != null && !ticker.isBlank();

        if (hasAsset) {
            // Transaction (BUY/SELL/DIVIDEND/WHT)
            TransactionType txType = xtbAdapter.resolveTransactionType(type);
            String instrument = p.path("instrument").asText("");
            Asset asset = xtbAdapter.findOrCreateAsset(ticker, instrument);

            BigDecimal quantity = BigDecimal.ZERO;
            BigDecimal price    = BigDecimal.ZERO;
            BigDecimal taxPct   = BigDecimal.ZERO;
            String currency     = "EUR";

            if (txType == TransactionType.BUY || txType == TransactionType.SELL) {
                XTBImportAdapter.ParsedBuySell parsed = xtbAdapter.parseBuySell(comment);
                quantity = XTBImportAdapter.quantityConverter(parsed.quantity());
                price    = parsed.price();
            } else if (txType == TransactionType.DIVIDEND || txType == TransactionType.WITHHOLDING_TAX) {
                XTBImportAdapter.ParsedDividend parsed = xtbAdapter.parseDividend(comment);
                currency = parsed.currency();
                price    = parsed.pricePerShare();
                taxPct   = parsed.taxPercentage();
            }

            txOut.add(Transaction.builder()
                    .portfolio(portfolio)
                    .asset(asset)
                    .externalId(transactionId)
                    .type(txType)
                    .quantity(quantity)
                    .price(price)
                    .totalAmount(amount)
                    .transactionDate(time)
                    .currency(currency)
                    .taxPercentage(taxPct)
                    .importSource(PortfolioSource.XTB.name())
                    .notes(comment)
                    .build());

            record.setMappedEntityType("TRANSACTION");

        } else if (!transactionId.isBlank()) {
            // CashFlow (DEPOSIT/WITHDRAWAL/INTEREST etc.)
            TransactionType cfType;
            try {
                cfType = xtbAdapter.resolveTransactionType(type);
            } catch (IllegalArgumentException e) {
                cfType = com.analytics.portfolio.enums.TransactionType.OTHER;
            }

            cfOut.add(CashFlow.builder()
                    .portfolio(portfolio)
                    .externalId(transactionId)
                    .importSource(PortfolioSource.XTB.name())
                    .flowDate(time)
                    .amount(amount)
                    .type(cfType)
                    .currency("EUR")
                    .comment(comment)
                    .build());

            record.setMappedEntityType("CASH_FLOW");
        }
    }

    private void mapClosedPos(JsonNode p, Portfolio portfolio,
                               RawImportRecord record, List<ClosedPosition> cpOut) {

        String ticker = p.path("ticker").asText("");
        Asset asset = assetRepo.findByTicker(ticker)
                .orElseGet(() -> assetRepo.save(Asset.builder()
                        .ticker(ticker).symbol(ticker)
                        .instrument(p.path("instrument").asText(""))
                        .type(com.analytics.portfolio.enums.AssetType.STOCK)
                        .source(PortfolioSource.XTB)
                        .build()));

        cpOut.add(ClosedPosition.builder()
                .portfolio(portfolio)
                .asset(asset)
                .instrument(p.path("instrument").asText(""))
                .category(p.path("category").asText(""))
                .ticker(ticker)
                .type(p.path("type").asText(""))
                .volume(bd(p.path("volume").asText(null)))
                .openPrice(bd(p.path("openPrice").asText(null)))
                .closePrice(bd(p.path("closePrice").asText(null)))
                .openTime(dt(p.path("openTime").asText(null)))
                .closeTime(dt(p.path("closeTime").asText(null)))
                .product(p.path("product").asText(""))
                .profitLoss(bd(p.path("profitLoss").asText(null)))
                .grossProfit(bd(p.path("grossProfit").asText(null)))
                .purchaseValue(bd(p.path("purchaseValue").asText(null)))
                .saleValue(bd(p.path("saleValue").asText(null)))
                .stopLoss(bd(p.path("stopLoss").asText(null)))
                .takeProfit(bd(p.path("takeProfit").asText(null)))
                .commission(bd(p.path("commission").asText(null)))
                .margin(bd(p.path("margin").asText(null)))
                .swap(bd(p.path("swap").asText(null)))
                .rollover(bd(p.path("rollover").asText(null)))
                .openConversionRate(bd(p.path("openConversionRate").asText(null)))
                .closeConversionRate(bd(p.path("closeConversionRate").asText(null)))
                .closeOrigin(p.path("closeOrigin").asText(""))
                .positionId(p.path("positionId").asText(""))
                .comment(p.path("comment").asText(""))
                .importSource("XTB")
                .build());

        record.setMappedEntityType("CLOSED_POSITION");
    }

    // ════════════════════════════════════════════════════════════
    // Atualizar status dos raw records após persistência
    // ════════════════════════════════════════════════════════════

    private void markRawRecords(List<RawImportRecord> all,
                                 List<Transaction> newTx,
                                 List<CashFlow> newCf,
                                 List<ClosedPosition> newCp) {
        // Os que foram mapeados e não são duplicata → MAPPED
        // Os que não chegaram às listas finais (foram filtrados como duplicata) → DUPLICATE
        // Os que ficaram FAILED já foram marcados no loop anterior
        all.forEach(r -> {
            if (r.getStatus() == RawImportStatus.FAILED) return;
            if (r.getMappedEntityType() != null) {
                r.setStatus(RawImportStatus.MAPPED);
            } else {
                r.setStatus(RawImportStatus.DUPLICATE);
            }
        });
    }

    // ── Helpers de conversão ─────────────────────────────────────

    private BigDecimal bd(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private LocalDateTime dt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s); } catch (Exception e) { return null; }
    }
}

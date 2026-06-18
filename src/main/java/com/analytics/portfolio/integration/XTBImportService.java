package com.analytics.portfolio.integration;

import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.enums.AssetType;
import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.*;
import com.analytics.portfolio.repository.*;
import com.analytics.portfolio.service.DuplicateDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.NotAcceptableStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;


/**
 * Serviço para importar dados do XTB
 * Suporta arquivos CSV e Excel exportados da plataforma XTB
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class XTBImportService {

    // ── Date formats XTB ──────────────────────────────────────────────────
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    );

    // Cabeçalho XTB: TypeTicker | Instrument | Time | Amount | ID | Comment
    private static final int COL_TYPE = 0;
    private static final int COL_TICKER = 1;
    private static final int COL_INSTRUMENT = 2;
    private static final int COL_TIME = 3;
    private static final int COL_AMOUNT = 4;
    private static final int COL_ID = 5;
    private static final int COL_COMMENT = 6;

    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final CashFlowRepository cashFlowRepository;
    private final ClosedPositionRepository closedPositionRepository;
    private final DuplicateDetectionService duplicateService;

    public static BigDecimal quantityConverter(String value) {

        if (value == null)
            throw new IllegalArgumentException("The parameter cant be null");

        if (!value.contains("/"))
            return new BigDecimal(value);

        // Split usando regex para ignorar possíveis espaços: " 2 / 6 "
        String[] parts = value.trim().split("\\s*/\\s*");

        BigDecimal numerator = new BigDecimal(parts[0]);
        BigDecimal denominador = new BigDecimal(parts[1]);

        // Division by zero validation
        if (denominador.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Divisão por zero.");
        }

        int places = 4; // decimal places;
        // HALF_UP é o arredondamento padrão (0.5 vai para cima)
        return numerator.divide(denominador, places, RoundingMode.HALF_UP);
    }

    /**
     * Importa transações de Excel do XTB
     */
    @Transactional
    public ImportResult importFromExcel(MultipartFile file, Long portfolioId)
            throws IOException {

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio não encontrado: " + portfolioId));

        List<Transaction> allTransactions = new ArrayList<>();
        List<CashFlow> allCashFlows = new ArrayList<>();
        List<ClosedPosition> allClosedPositions = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            int sheetCount = workbook.getNumberOfSheets();
            log.info("XTB Import: {} sheet(s) encontradas", sheetCount);

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);

                if (sheet == null || sheet.getLastRowNum() < 1) continue;

                String sheetType = detectSheetType(sheet);
                log.info("Sheet '{}' detectada como: {}", sheet.getSheetName(), sheetType);

                switch (sheetType) {
                    case "CASH_OPERATIONS" ->
                            processCashOperationsSheet(sheet, portfolio, allTransactions, allCashFlows);
                    case "CLOSED_POSITIONS" -> processClosedPositionsSheet(sheet, portfolio, allClosedPositions);
                    default -> log.warn("Sheet '{}' não reconhecida — ignorada", sheet.getSheetName());
                }
            }
        }

        // Filtrar duplicatas de transactions
        List<ClosedPosition> newClosedPositions = duplicateService.filterDuplicates(allClosedPositions, closedPositionRepository::existsByImportFingerprint);
        List<Transaction> newTransactions = duplicateService.filterDuplicates(allTransactions, transactionRepository::existsByImportFingerprint);
        List<CashFlow> newCashFlows = duplicateService.filterDuplicates(allCashFlows, cashFlowRepository::existsByImportFingerprint);

        int closePositionsDuplicates = allClosedPositions.size() - newClosedPositions.size();
        int transactionsDuplicates = allTransactions.size() - newTransactions.size();
        int cashFlowDuplicates = allCashFlows.size() - newCashFlows.size();

        // SALVAR NO BANCO DE DADOS
        log.info("Salvando {} transactions, {} cash flows e {} close positions no banco...",
                newTransactions.size(), allCashFlows.size(), allClosedPositions.size());

        if (!newClosedPositions.isEmpty()) {
            closedPositionRepository.saveAll(newClosedPositions);
            log.info("{} close positions salvas com sucesso", newClosedPositions.size());
        }

        // Salvar transactions (apenas as novas, sem duplicatas)
        if (!newTransactions.isEmpty()) {
            transactionRepository.saveAll(newTransactions);
            log.info("{} transactions salvas com sucesso", newTransactions.size());
        }

        // Salvar cash flows
        if (!newCashFlows.isEmpty()) {
            cashFlowRepository.saveAll(newCashFlows);
            log.info("{} cash flows salvos com sucesso", newCashFlows.size());
        }

        log.info("XTB Import concluído — {} transactions, {} cashflows, {} closed positions, ({} duplicatas ignoradas)",
                newTransactions.size(), newCashFlows.size(), newClosedPositions.size(),
                transactionsDuplicates + closePositionsDuplicates);

        return ImportResult.builder()
                .transactionsImported(newTransactions.size())
                .transactionsDuplicate(transactionsDuplicates)
                .cashFlowsImported(newCashFlows.size())
                .cashFlowsDuplicate(cashFlowDuplicates)
                .closedPositionsImported(newClosedPositions.size())
                .closedPositionsDuplicate(closePositionsDuplicates)
                .totalProcessed(allTransactions.size() + allCashFlows.size() + allClosedPositions.size())
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Detecção automática do tipo de sheet pelo cabeçalho
    // ═════════════════════════════════════════════════════════════════════
    private String detectSheetType(Sheet sheet) {
        Row header = sheet.getRow(4);
        if (header == null) return "UNKNOWN";

        // Recolher todos os valores do cabeçalho
        Set<String> headerCells = new HashSet<>();
        for (Cell cell : header) {
            String val = getCellValueAsString(cell).toLowerCase().trim();
            if (!val.isEmpty()) headerCells.add(val);
        }

        // Closed Positions tem "open price", "close price", "profit/loss"
        if (headerCells.stream().anyMatch(h -> h.contains("open price") || h.contains("close price") || h.contains("profit/loss"))) {
            return "CLOSED_POSITIONS";
        }
        // Cash Operations tem "ticker", "instrument", "amount", "id"
        if (headerCells.stream().anyMatch(h -> h.equals("id") || h.equals("amount"))) {
            return "CASH_OPERATIONS";
        }
        return "UNKNOWN";
    }

    // ═════════════════════════════════════════════════════════════════════
    // Tabela 2 — Closed Positions
    // ═════════════════════════════════════════════════════════════════════

    private void processCashOperationsSheet(Sheet sheet, Portfolio portfolio,
                                            List<Transaction> transactions,
                                            List<CashFlow> cashFlows) {
        int startDataRowIndex = 5;

        for (int i = startDataRowIndex; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isInvalidRow(row)) continue;

            try {
                XTBRowData rowData = parseXTBRowData(row);

                if (rowData.isAssetTransaction()) {
                    // Tem ticker/asset → Transaction
                    Transaction transaction = createTransaction(rowData, portfolio);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                } else if (rowData.isCashFlowTransaction()) {
                    // Sem ticker → CashFlow
                    CashFlow cashflow = createCashFlow(rowData, portfolio);
                    if (cashflow != null) {
                        cashFlows.add(cashflow);
                    }
                }

            } catch (Exception e) {
                log.warn("Erro ao processar linha {}: {}", i, e.getMessage());
            }
        }
    }

    private void processClosedPositionsSheet(Sheet sheet, Portfolio portfolio,
                                             List<ClosedPosition> result) {
        // Mapear colunas pelo cabeçalho real (posição pode variar)
        Map<String, Integer> colIdx = buildColumnIndex(sheet.getRow(4));

        int startDataRowIndex = 5;
        for (int i = startDataRowIndex; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isInvalidRow(row)) continue;
            try {
                ClosedPosition cp = buildClosedPosition(row, colIdx, portfolio);
                if (cp != null) result.add(cp);
            } catch (Exception e) {
                log.warn("Closed Positions linha {}: {}", i, e.getMessage());
            }
        }
    }

    private ClosedPosition buildClosedPosition(Row row, Map<String, Integer> cols, Portfolio portfolio) {
        Optional<Asset> asset = assetRepository.findByTicker(getByCol(row, cols, "ticker"));

        return ClosedPosition.builder()
                .asset(asset.get())
                .portfolio(portfolio)
                .instrument(getByCol(row, cols, "instrument"))
                .category(getByCol(row, cols, "category"))
                .ticker(getByCol(row, cols, "ticker"))
                .type(getByCol(row, cols, "type"))
                .volume(getBDByCol(row, cols, "volume"))
                .openPrice(getBDByCol(row, cols, "open price"))
                .closePrice(getBDByCol(row, cols, "close price"))
                .openTime(getDateByCol(row, cols, "open time (utc)"))
                .closeTime(getDateByCol(row, cols, "close time (utc)"))
                .product(getByCol(row, cols, "product"))
                .profitLoss(getBDByCol(row, cols, "profit/loss"))
                .grossProfit(getBDByCol(row, cols, "gross profit"))
                .purchaseValue(getBDByCol(row, cols, "purchase value"))
                .saleValue(getBDByCol(row, cols, "sale value"))
                .stopLoss(getBDByCol(row, cols, "stop loss"))
                .takeProfit(getBDByCol(row, cols, "take profit"))
                .commission(getBDByCol(row, cols, "commission"))
                .margin(getBDByCol(row, cols, "margin"))
                .swap(getBDByCol(row, cols, "swap"))
                .rollover(getBDByCol(row, cols, "rollover"))
                .openConversionRate(getBDByCol(row, cols, "open conversion rate"))
                .closeConversionRate(getBDByCol(row, cols, "close conversion rate"))
                .closeOrigin(getByCol(row, cols, "close origin"))
                .positionId(getByCol(row, cols, "position id"))
                .comment(getByCol(row, cols, "comment"))
                .importSource("XTB")
                .build();
    }

    /**
     * Mapeia nome da coluna (lowercase) → índice
     */
    private Map<String, Integer> buildColumnIndex(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;
        for (Cell cell : headerRow) {
            String key = getCellValueAsString(cell).toLowerCase().trim();
            if (!key.isEmpty()) map.put(key, cell.getColumnIndex());
        }
        return map;
    }

    /**
     * Parse de uma linha do Excel XTB
     * Cabeçalho: Type |Ticker | Instrument | Time | Amount | ID | Comment
     */
    private XTBRowData parseXTBRowData(Row row) {
        String type = getCellValueAsString(row.getCell(COL_TYPE));
        String ticker = getCellValueAsString(row.getCell(COL_TICKER));
        String instrument = getCellValueAsString(row.getCell(COL_INSTRUMENT));
        LocalDateTime time = getCellDateTime(row.getCell(COL_TIME));
        BigDecimal amount = getCellBigDecimal(row.getCell(COL_AMOUNT));
        String id = getCellValueAsString(row.getCell(COL_ID));
        String comment = getCellValueAsString(row.getCell(COL_COMMENT));

        return XTBRowData.builder()
                .type(type)
                .ticker(ticker)
                .instrument(instrument)
                .time(time)
                .amount(amount)
                .transactionId(id)
                .comment(comment)
                .build();
    }

    /**
     * Cria CashFlow a partir dos dados da linha
     * Usado quando a linha NÃO tem Ticker (movimentação de caixa)
     */
    private CashFlow createCashFlow(XTBRowData data, Portfolio portfolio) {

        TransactionType cashFlowType = determineCashFlowType(data);
        String currency = "EUR";

        if (cashFlowType == null) {
            log.warn("Tipo de cash flow desconhecido: {}", data.getComment());
            return null;
        }

        return CashFlow.builder()
                .externalId(data.getTransactionId())
                .importSource(PortfolioSource.XTB.name())
                .portfolio(portfolio)
                .flowDate(data.getTime())
                .amount(data.getAmount())
                .type(cashFlowType)
                .currency(currency)
                .comment(data.getComment())// keeps the original comment
                .build();
    }

    /**
     * Cria Transaction a partir dos dados da linha
     * Usado quando a linha tem Ticker (ativo)
     */
    private Transaction createTransaction(XTBRowData data, Portfolio portfolio) {
        // Determinar tipo baseado no comment ou amount
        TransactionType type = getType(data.getType().toLowerCase());
        TransactionDto transactionDto = transactionParser(data.getComment(), type);
        BigDecimal totalAmount = data.getAmount();

        // Buscar ou criar Asset
        Asset asset = findOrCreateAsset(data.getTicker(), data.getInstrument());

        return Transaction.builder()
                .portfolio(portfolio)
                .asset(asset)
                .externalId(data.getTransactionId())
                .type(type)
                .quantity(quantityConverter(transactionDto.quantity()))
                .price(transactionDto.price())
                .totalAmount(totalAmount)
                .transactionDate(data.getTime())
                .currency(transactionDto.currency())
                .taxPercentage(transactionDto.percentage())
                .importSource(PortfolioSource.XTB.name())
                .notes(data.getComment())// keeps the original comment
                .build();
    }

    /**
     * Determina o tipo de Transaction baseado no comment e amount
     */
    private TransactionType determineCashFlowType(XTBRowData data) {
        String type = data.getType().toLowerCase();

        if (type.contains("deposit") || type.contains("free funds interest")
                || type.contains("free funds interest tax") || type.contains("transfer")) {

            return getType(type);
        }

        return TransactionType.OTHER;
    }

    /**
     * Try to find an existent Asset build one
     */
    private Asset findOrCreateAsset(String ticker, String instrument) {
        return assetRepository.findByTicker(ticker)
                .filter(a -> a.getTicker() != null && a.getInstrument() != null)
                .orElseGet(() -> {
                    Asset newAsset = Asset.builder()
                            .ticker(ticker)
                            .symbol(ticker)
                            .instrument(instrument)
                            .type(AssetType.STOCK)
                            .source(PortfolioSource.XTB)
                            .build();

                    return assetRepository.save(newAsset);
                });
    }

    private TransactionDto transactionParser(String comment, TransactionType type) {

        if (type == TransactionType.SELL || type == TransactionType.BUY) {
            return buySellParser(comment, type);
        }

        if (type == TransactionType.DIVIDEND || type == TransactionType.WITHHOLDING_TAX) {
            return dividendTaxParser(comment, type);
        }

        log.warn("Transaction Type {} not match return default values: ", type);

        return new TransactionDto("", BigDecimal.ZERO, "", BigDecimal.ZERO);
    }

    private TransactionDto buySellParser(String cellValue, TransactionType type) {
        // OPEN BUY 20 @ 1.210 -> Buy
        // OPEN BUY 19/20 @ 1.224 -> Buy
        // CLOSE BUY 88/150 @ 2.990 -> Sell

        if (cellValue == null) {
            throw new NotAcceptableStatusException("The cellValue can't be null");
        }

        String[] part = cellValue.trim().split("\\s+");

        String qty = part[2];
        if (qty.contains("/")) {
            qty = qty.split("/")[0]; // Get the qty before /
        }

        BigDecimal price = new BigDecimal(part[4]).setScale(3, RoundingMode.HALF_UP);

        return new TransactionDto(qty, price, "", BigDecimal.ZERO);
    }

    private TransactionDto dividendTaxParser(String cellValue, TransactionType type) {
        // RENE.PT EUR WHT 35%
        // RENE.PT EUR 0.0930/ SHR

        if (cellValue == null) {
            throw new NotAcceptableStatusException("The cellValue can't be null");
        }

        String[] part = cellValue.trim().split("\\s+");

        String currency = part[1];

        String price = part[2];
        if (price.contains("/")) {
            price = price.split("/")[0]; // Get the qty before /
        } else {
            price = "0";
        }

        String lastField = part[3];
        double percentField = 0;
        if (lastField.contains("%")) {
            percentField = Double.parseDouble(lastField.split("%")[0]); // Get the num before /
        }

        BigDecimal percentage = BigDecimal.valueOf(percentField / 100).setScale(3, RoundingMode.UNNECESSARY);

        return new TransactionDto("0", new BigDecimal(price), currency, percentage);
    }

    private TransactionType getType(String fileType) {
        return switch (fileType) {
            case "stock sell" -> TransactionType.SELL;
            case "buy", "stock purchase" -> TransactionType.BUY;
            case "deposit" -> TransactionType.DEPOSIT;
            case "withdrawal" -> TransactionType.WITHDRAWAL;
            case "transfer" -> TransactionType.TRANSFER;
            case "dividend" -> TransactionType.DIVIDEND;
            case "withholding tax" -> TransactionType.WITHHOLDING_TAX;
            case "free funds interest" -> TransactionType.INTEREST;
            case "free funds interest tax" -> TransactionType.INTEREST_TAX;
            default -> throw new IllegalArgumentException("Transaction type unknown: " + fileType);
        };
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }


    // ===== Utilitários para leitura de células =====
    // ═════════════════════════════════════════════════════════════════════
    // Helpers — leitura de células
    // ═════════════════════════════════════════════════════════════════════

    private String getByCol(Row row, Map<String, Integer> cols, String colName) {
        Integer idx = cols.get(colName.toLowerCase());
        return idx != null ? getCellValueAsString(row.getCell(idx)) : "";
    }

    private BigDecimal getBDByCol(Row row, Map<String, Integer> cols, String colName) {
        Integer idx = cols.get(colName.toLowerCase());
        return idx != null ? getCellBigDecimal(row.getCell(idx)) : null;
    }

    private LocalDateTime getDateByCol(Row row, Map<String, Integer> cols, String colName) {
        Integer idx = cols.get(colName.toLowerCase());
        return idx != null ? getCellDateTime(row.getCell(idx)) : null;
    }

    private LocalDateTime getCellDateTime(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            for (DateTimeFormatter fmt : DATE_FORMATS) {
                try {
                    return LocalDateTime.parse(s, fmt);
                } catch (DateTimeParseException ignored) {
                }
            }
        }
        return null;
    }

    private BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;

        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        } else if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim().replace(",", ".");
            try {
                return !value.isBlank() ? new BigDecimal(value) : BigDecimal.ZERO;
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }

        return BigDecimal.ZERO;
    }

    /*
     * Returns False is a row is null or has more than 2 empty cells
     * */
    private boolean isInvalidRow(Row row) {
        if (row == null) return true;

        int lastCell = 6;
        int emptyCells = 0;

        for (int i = 0; i < lastCell; i++) {
            Cell cell = row.getCell(i);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                emptyCells++;
            }
        }

        return emptyCells > 2;
    }


    // ===== Classes de Dados =====

    private record TransactionDto(String quantity, BigDecimal price, String currency, BigDecimal percentage) {
    }

    /**
     * Dados de uma linha do Excel XTB
     */
    @lombok.Data
    @lombok.Builder
    private static class XTBRowData {
        private String type;           // tipo do ativo
        private String ticker;         // Símbolo do ativo (vazio para cash flows)
        private String instrument;     // Nome/descrição (vazio para cash flows)
        private LocalDateTime time;    // Data/hora
        private BigDecimal amount;     // Valor
        private String transactionId;  // ID da transação
        private String comment;        // Comentário

        /**
         * Verifica se é uma transação de ativo (tem ticker)
         */
        public boolean isAssetTransaction() {
            return ticker != null && !ticker.trim().isEmpty();
        }

        public boolean isCashFlowTransaction() {
            return !isAssetTransaction() && (transactionId != null && !transactionId.isEmpty());
        }

    }

    /**
     * Resultado da importação
     */
    @lombok.Data
    @lombok.Builder
    public static class ImportResult {
        private int transactionsImported;   // Transactions novas
        private int transactionsDuplicate;  // Transactions duplicadas
        private int cashFlowsImported;      // CashFlows importados
        private int cashFlowsDuplicate;      // CashFlows importados
        private int closedPositionsImported;      // closed Positions importados
        private int closedPositionsDuplicate;      // closed Positions importados
        private int totalProcessed;         // Total de linhas processadas

        public String getSummary() {
            return String.format("Processadas %d linhas " +
                            "transactions (%d novas, %d duplicatas), " +
                            "cashFlows (%d novas, %d duplicatas) cash flows" +
                            "closePositions (%d novas, %d duplicatas) close positions" +
                            "dividends (%d novas, %d duplicatas) dividends",
                    totalProcessed, transactionsImported, transactionsDuplicate,
                    cashFlowsImported, cashFlowsDuplicate, closedPositionsImported,
                    closedPositionsDuplicate);
        }
    }

}

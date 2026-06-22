package com.analytics.portfolio.integration;

import com.analytics.portfolio.enums.AssetType;
import com.analytics.portfolio.enums.PortfolioSource;
import com.analytics.portfolio.enums.RawImportStatus;
import com.analytics.portfolio.enums.TransactionType;
import com.analytics.portfolio.model.*;
import com.analytics.portfolio.repository.AssetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
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
 * Adaptador de importação para ficheiros Excel do XTB.
 *
 * Responsabilidade: ler o Excel, converter cada linha num
 * RawImportRecord com o payload JSON. Nada mais.
 *
 * A lógica de parse do XTBImportService original foi preservada
 * na íntegra — apenas a orquestração foi removida.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class XTBImportAdapter implements BrokerImportAdapter {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    );

    private static final int COL_TYPE       = 0;
    private static final int COL_TICKER     = 1;
    private static final int COL_INSTRUMENT = 2;
    private static final int COL_TIME       = 3;
    private static final int COL_AMOUNT     = 4;
    private static final int COL_ID         = 5;
    private static final int COL_COMMENT    = 6;

    private final AssetRepository assetRepository;
    private final ObjectMapper    objectMapper;

    @Override
    public PortfolioSource getSource() {
        return PortfolioSource.XTB;
    }

    // ════════════════════════════════════════════════════════════
    // Entry point
    // ════════════════════════════════════════════════════════════

    @Override
    public List<RawImportRecord> parse(MultipartFile file, Portfolio portfolio, String batchId)
            throws IOException {

        List<RawImportRecord> records = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                if (sheet == null || sheet.getLastRowNum() < 1) continue;

                String sheetType = detectSheetType(sheet);
                log.info("XTB sheet '{}' → {}", sheet.getSheetName(), sheetType);

                switch (sheetType) {
                    case "CASH_OPERATIONS" ->
                            parseCashOperationsSheet(sheet, portfolio, batchId, file.getOriginalFilename(), records);
                    case "CLOSED_POSITIONS" ->
                            parseClosedPositionsSheet(sheet, portfolio, batchId, file.getOriginalFilename(), records);
                    default -> log.warn("Sheet '{}' não reconhecida — ignorada", sheet.getSheetName());
                }
            }
        }

        log.info("XTB parse concluído: {} registos raw", records.size());
        return records;
    }

    // ════════════════════════════════════════════════════════════
    // Cash Operations sheet
    // ════════════════════════════════════════════════════════════

    private void parseCashOperationsSheet(Sheet sheet, Portfolio portfolio,
                                          String batchId, String fileName,
                                          List<RawImportRecord> out) {
        for (int i = 5; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isInvalidRow(row)) continue;

            try {
                XTBRowData data = readRow(row);
                ObjectNode payload = buildCashOpsPayload(data);

                out.add(RawImportRecord.builder()
                        .batchId(batchId)
                        .portfolioId(portfolio.getId())
                        .importSource(PortfolioSource.XTB.name())
                        .fileName(fileName)
                        .rowNumber(i)
                        .rawPayload(objectMapper.writeValueAsString(payload))
                        .status(RawImportStatus.PENDING)
                        .build());

            } catch (Exception e) {
                log.warn("XTB CashOps linha {}: {}", i, e.getMessage());
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Closed Positions sheet
    // ════════════════════════════════════════════════════════════

    private void parseClosedPositionsSheet(Sheet sheet, Portfolio portfolio,
                                           String batchId, String fileName,
                                           List<RawImportRecord> out) {
        Map<String, Integer> cols = buildColumnIndex(sheet.getRow(4));

        for (int i = 5; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isInvalidRow(row)) continue;

            try {
                ObjectNode payload = buildClosedPosPayload(row, cols);

                out.add(RawImportRecord.builder()
                        .batchId(batchId)
                        .portfolioId(portfolio.getId())
                        .importSource(PortfolioSource.XTB.name())
                        .fileName(fileName)
                        .rowNumber(i)
                        .rawPayload(objectMapper.writeValueAsString(payload))
                        .status(RawImportStatus.PENDING)
                        .build());

            } catch (Exception e) {
                log.warn("XTB ClosedPos linha {}: {}", i, e.getMessage());
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Payload builders  (raw → JSON)
    // ════════════════════════════════════════════════════════════

    private ObjectNode buildCashOpsPayload(XTBRowData d) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("sheetType",    "CASH_OPERATIONS");
        n.put("type",         d.type);
        n.put("ticker",       d.ticker);
        n.put("instrument",   d.instrument);
        n.put("time",         d.time != null ? d.time.toString() : null);
        n.put("amount",       d.amount != null ? d.amount.toPlainString() : null);
        n.put("transactionId",d.transactionId);
        n.put("comment",      d.comment);
        return n;
    }

    private ObjectNode buildClosedPosPayload(Row row, Map<String, Integer> cols) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("sheetType",           "CLOSED_POSITIONS");
        n.put("instrument",          getByCol(row, cols, "instrument"));
        n.put("category",            getByCol(row, cols, "category"));
        n.put("ticker",              getByCol(row, cols, "ticker"));
        n.put("type",                getByCol(row, cols, "type"));
        n.put("volume",              bdStr(getBDByCol(row, cols, "volume")));
        n.put("openPrice",           bdStr(getBDByCol(row, cols, "open price")));
        n.put("closePrice",          bdStr(getBDByCol(row, cols, "close price")));
        n.put("openTime",            dtStr(getDateByCol(row, cols, "open time (utc)")));
        n.put("closeTime",           dtStr(getDateByCol(row, cols, "close time (utc)")));
        n.put("product",             getByCol(row, cols, "product"));
        n.put("profitLoss",          bdStr(getBDByCol(row, cols, "profit/loss")));
        n.put("grossProfit",         bdStr(getBDByCol(row, cols, "gross profit")));
        n.put("purchaseValue",       bdStr(getBDByCol(row, cols, "purchase value")));
        n.put("saleValue",           bdStr(getBDByCol(row, cols, "sale value")));
        n.put("stopLoss",            bdStr(getBDByCol(row, cols, "stop loss")));
        n.put("takeProfit",          bdStr(getBDByCol(row, cols, "take profit")));
        n.put("commission",          bdStr(getBDByCol(row, cols, "commission")));
        n.put("margin",              bdStr(getBDByCol(row, cols, "margin")));
        n.put("swap",                bdStr(getBDByCol(row, cols, "swap")));
        n.put("rollover",            bdStr(getBDByCol(row, cols, "rollover")));
        n.put("openConversionRate",  bdStr(getBDByCol(row, cols, "open conversion rate")));
        n.put("closeConversionRate", bdStr(getBDByCol(row, cols, "close conversion rate")));
        n.put("closeOrigin",         getByCol(row, cols, "close origin"));
        n.put("positionId",          getByCol(row, cols, "position id"));
        n.put("comment",             getByCol(row, cols, "comment"));
        return n;
    }

    // ════════════════════════════════════════════════════════════
    // Helpers públicos (reutilizados pelo ImportOrchestrator)
    // ════════════════════════════════════════════════════════════

    public static BigDecimal quantityConverter(String value) {
        if (value == null) throw new IllegalArgumentException("value cannot be null");
        if (!value.contains("/")) return new BigDecimal(value);
        String[] parts = value.trim().split("\\s*/\\s*");
        BigDecimal num = new BigDecimal(parts[0]);
        BigDecimal den = new BigDecimal(parts[1]);
        if (den.compareTo(BigDecimal.ZERO) == 0) throw new ArithmeticException("Division by zero");
        return num.divide(den, 4, RoundingMode.HALF_UP);
    }

    public TransactionType resolveTransactionType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "stock sell"              -> TransactionType.SELL;
            case "buy", "stock purchase"   -> TransactionType.BUY;
            case "deposit"                 -> TransactionType.DEPOSIT;
            case "withdrawal"              -> TransactionType.WITHDRAWAL;
            case "transfer"                -> TransactionType.TRANSFER;
            case "dividend"                -> TransactionType.DIVIDEND;
            case "withholding tax"         -> TransactionType.WITHHOLDING_TAX;
            case "free funds interest"     -> TransactionType.INTEREST;
            case "free funds interest tax" -> TransactionType.INTEREST_TAX;
            default -> throw new IllegalArgumentException("Unknown XTB type: " + fileType);
        };
    }

    public Asset findOrCreateAsset(String ticker, String instrument) {
        return assetRepository.findByTicker(ticker)
                .filter(a -> a.getTicker() != null && a.getInstrument() != null)
                .orElseGet(() -> assetRepository.save(Asset.builder()
                        .ticker(ticker)
                        .symbol(ticker)
                        .instrument(instrument)
                        .type(AssetType.STOCK)
                        .source(PortfolioSource.XTB)
                        .build()));
    }

    // ── Parse helpers (comment field) ────────────────────────────

    public record ParsedBuySell(String quantity, BigDecimal price) {}
    public record ParsedDividend(String currency, BigDecimal pricePerShare, BigDecimal taxPercentage) {}

    public ParsedBuySell parseBuySell(String comment) {
        if (comment == null) throw new NotAcceptableStatusException("comment is null");
        String[] p = comment.trim().split("\\s+");
        String qty = p[2].contains("/") ? p[2].split("/")[0] : p[2];
        return new ParsedBuySell(qty, new BigDecimal(p[4]).setScale(3, RoundingMode.HALF_UP));
    }

    public ParsedDividend parseDividend(String comment) {
        if (comment == null) throw new NotAcceptableStatusException("comment is null");
        String[] p = comment.trim().split("\\s+");
        String currency = p[1];
        String priceStr = p[2].contains("/") ? p[2].split("/")[0] : "0";
        double pct = p[3].contains("%")
                ? Double.parseDouble(p[3].split("%")[0])
                : 0.0;
        return new ParsedDividend(
                currency,
                new BigDecimal(priceStr),
                BigDecimal.valueOf(pct / 100).setScale(3, RoundingMode.UNNECESSARY)
        );
    }

    // ════════════════════════════════════════════════════════════
    // Sheet-level helpers
    // ════════════════════════════════════════════════════════════

    private String detectSheetType(Sheet sheet) {
        Row header = sheet.getRow(4);
        if (header == null) return "UNKNOWN";
        Set<String> cells = new HashSet<>();
        for (Cell cell : header)
            cells.add(getCellValueAsString(cell).toLowerCase().trim());
        if (cells.stream().anyMatch(h -> h.contains("open price") || h.contains("profit/loss")))
            return "CLOSED_POSITIONS";
        if (cells.stream().anyMatch(h -> h.equals("id") || h.equals("amount")))
            return "CASH_OPERATIONS";
        return "UNKNOWN";
    }

    private Map<String, Integer> buildColumnIndex(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;
        for (Cell cell : headerRow) {
            String key = getCellValueAsString(cell).toLowerCase().trim();
            if (!key.isEmpty()) map.put(key, cell.getColumnIndex());
        }
        return map;
    }

    private XTBRowData readRow(Row row) {
        return new XTBRowData(
                getCellValueAsString(row.getCell(COL_TYPE)),
                getCellValueAsString(row.getCell(COL_TICKER)),
                getCellValueAsString(row.getCell(COL_INSTRUMENT)),
                getCellDateTime(row.getCell(COL_TIME)),
                getCellBigDecimal(row.getCell(COL_AMOUNT)),
                getCellValueAsString(row.getCell(COL_ID)),
                getCellValueAsString(row.getCell(COL_COMMENT))
        );
    }

    private record XTBRowData(String type, String ticker, String instrument,
                               LocalDateTime time, BigDecimal amount,
                               String transactionId, String comment) {}

    // ── Cell helpers ─────────────────────────────────────────────

    private String getByCol(Row row, Map<String, Integer> cols, String col) {
        Integer idx = cols.get(col.toLowerCase());
        return idx != null ? getCellValueAsString(row.getCell(idx)) : "";
    }

    private BigDecimal getBDByCol(Row row, Map<String, Integer> cols, String col) {
        Integer idx = cols.get(col.toLowerCase());
        return idx != null ? getCellBigDecimal(row.getCell(idx)) : null;
    }

    private LocalDateTime getDateByCol(Row row, Map<String, Integer> cols, String col) {
        Integer idx = cols.get(col.toLowerCase());
        return idx != null ? getCellDateTime(row.getCell(idx)) : null;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;
        if (cell.getCellType() == CellType.NUMERIC)
            return BigDecimal.valueOf(cell.getNumericCellValue());
        if (cell.getCellType() == CellType.STRING) {
            String v = cell.getStringCellValue().trim().replace(",", ".");
            try { return !v.isBlank() ? new BigDecimal(v) : BigDecimal.ZERO; }
            catch (NumberFormatException e) { return BigDecimal.ZERO; }
        }
        return BigDecimal.ZERO;
    }

    private LocalDateTime getCellDateTime(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
            return cell.getLocalDateTimeCellValue();
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            for (DateTimeFormatter fmt : DATE_FORMATS) {
                try { return LocalDateTime.parse(s, fmt); }
                catch (DateTimeParseException ignored) {}
            }
        }
        return null;
    }

    private boolean isInvalidRow(Row row) {
        int empty = 0;
        for (int i = 0; i < 6; i++) {
            Cell c = row.getCell(i);
            if (c == null || c.getCellType() == CellType.BLANK) empty++;
        }
        return empty > 2;
    }

    private String bdStr(BigDecimal v) { return v != null ? v.toPlainString() : null; }
    private String dtStr(LocalDateTime v) { return v != null ? v.toString() : null; }
}

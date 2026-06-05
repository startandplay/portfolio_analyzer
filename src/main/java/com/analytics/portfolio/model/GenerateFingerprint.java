package com.analytics.portfolio.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record GenerateFingerprint(String externalId,
                                  BigDecimal amount,
                                  LocalDateTime date1,
                                  LocalDateTime date2,
                                  Long portfolioId) {

    public String generate() {
        // 1. Normalizar ID
        String normId = (externalId() != null) ? externalId().trim().toLowerCase() : "no-id";

        // 2. Normalizar Valor (149,16 -> 149.16)
        String normAmount = (amount() != null)
                ? amount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "0.00";

        // 3. Normalizar Data (ISO 8601)
        String normDate1 = normalizeDate(date1);
        String normDate2 = normalizeDate(date2);

        // 4. String Final
        String raw = String.format("%s|%s|%s|%s|%d",
                normId, normAmount, normDate1, normDate2, portfolioId());

        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String normalizeDate(LocalDateTime date){
        return  (date != null)
                ? date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : "0000-00-00";
    }
}

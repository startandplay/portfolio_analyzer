package com.analytics.portfolio.service;

import com.analytics.portfolio.model.Fingerprintable;
import com.analytics.portfolio.model.Transaction;
import com.analytics.portfolio.repository.CashFlowRepository;
import com.analytics.portfolio.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateDetectionService {

    private final TransactionRepository transactionRepository;
    private final CashFlowRepository cashFlowRepository;

    /**
     * Verifica se uma transação já existe
     */
    public boolean isDuplicate(Transaction transaction) {     // Garantir que o fingerprint está gerado

        String fingerprint = transaction.getImportFingerprint();

        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }

        return transactionRepository.existsByImportFingerprint(fingerprint);
    }


    public <T extends Fingerprintable> List<T> filterDuplicates(List<T> items, Function<String, Boolean> existsDb) {

        items.forEach(item -> {
            if (item.getImportFingerprint() == null) {
                item.setImportFingerprint(item.generateFingerprint());
            }
        });

        Set<String> seenInThisImport = new HashSet<>();  // ← protecção duplicatas internas

        return items.stream().filter(item -> {
            String fp = item.getImportFingerprint();

            if (seenInThisImport.contains(fp)) { // ← novo: duplicata no mesmo ficheiro
                log.debug("[{}] Duplicata interna: {}", item.getClass().getSimpleName(), fp);
                return false;
            }

            Boolean isDup = existsDb.apply(fp); // ← função generica

            if (isDup) {
                log.debug("[{}] Duplicata no banco: {}", item.getClass().getSimpleName(), fp);
            }
            seenInThisImport.add(fp);
            return !isDup;
        }).toList();
    }

    /**
     * Encontra transação existente por fingerprint
     */
    public Optional<Transaction> findByFingerprint(String fingerprint) {
        return transactionRepository.findByImportFingerprint(fingerprint);
    }

    /**
     * Estatísticas de importação
     */
    public ImportStats analyzeImport(List<Transaction> trades) {
        long total = trades.size();
        long duplicates = trades.stream().filter(this::isDuplicate).count();
        long newRecords = total - duplicates;

        return new ImportStats(total, newRecords, duplicates);
    }

    /**
     * DTO com estatísticas de importação
     */
    public record ImportStats(long total, long newRecords, long duplicates) {
        public double duplicatePercentage() {
            return total > 0 ? (duplicates * 100.0 / total) : 0;
        }

        public String summary() {
            return String.format("Total: %d, Novas: %d, Duplicatas: %d (%.1f%%)", total, newRecords, duplicates, duplicatePercentage());
        }
    }
}
package com.analytics.portfolio.model;

/**
 * Contrato para entidades que suportam detecção de duplicatas por fingerprint.
 *
 * Qualquer entidade que implemente esta interface pode usar o
 * DuplicateDetectionService.filterDuplicates() de forma genérica,
 * sem código duplicado.
 *
 * Implementações:
 *  - Transaction
 *  - CashFlow
 *  - ClosedPosition
 */
public interface Fingerprintable {

    /** Devolve o fingerprint actual (pode ser null antes de persistir) */
    String getImportFingerprint();

    /** Define o fingerprint */
    void setImportFingerprint(String fingerprint);

    /**
     * Gera um fingerprint único e determinístico para esta entidade.
     * Deve ser idempotente — a mesma entidade gera sempre o mesmo fingerprint.
     */
    String generateFingerprint();
}

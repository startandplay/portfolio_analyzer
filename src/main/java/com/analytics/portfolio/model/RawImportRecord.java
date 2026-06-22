package com.analytics.portfolio.model;

import com.analytics.portfolio.enums.RawImportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Landing table para dados brutos de importação.
 *
 * Cada linha representa uma linha/registo do ficheiro original,
 * guardada em JSON antes de qualquer mapeamento canónico.
 * Isto permite:
 *  - re-processar importações sem o ficheiro original
 *  - debug de erros de parse por broker
 *  - desfazer uma importação inteira por batchId
 */
@Entity
@Table(name = "raw_import_records", indexes = {
        @Index(name = "idx_raw_batch",     columnList = "batch_id"),
        @Index(name = "idx_raw_portfolio", columnList = "portfolio_id"),
        @Index(name = "idx_raw_status",    columnList = "status")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RawImportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID gerado por importação — agrupa todos os registos de um único upload */
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    /** XTB, BINANCE, TRADING212, etc. */
    @Column(name = "import_source", nullable = false, length = 30)
    private String importSource;

    /** Nome do ficheiro original */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** Número da linha no ficheiro (útil para debug) */
    @Column(name = "row_number")
    private Integer rowNumber;

    /**
     * Payload bruto da linha serializado em JSON.
     * Tipo JSONB no PostgreSQL — TEXT em H2 (dev).
     */
    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RawImportStatus status = RawImportStatus.PENDING;

    /** Tipo da entidade canónica criada: TRANSACTION, CASH_FLOW, CLOSED_POSITION */
    @Column(name = "mapped_entity_type", length = 30)
    private String mappedEntityType;

    /** ID da entidade canónica criada (null se PENDING/FAILED/DUPLICATE) */
    @Column(name = "mapped_entity_id")
    private Long mappedEntityId;

    /** Mensagem de erro quando status = FAILED */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

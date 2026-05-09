package com.analytics.portfolio.model;

import com.analytics.portfolio.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.codec.digest.DigestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimentações de caixa do portfolio que NÃO envolvem assets
 * <p>
 * Exemplos:
 * - Depósitos de dinheiro
 * - Saques (withdrawals)
 * - Transferências entre contas
 * - Impostos pagos
 * - Taxas administrativas
 * - Juros recebidos
 * <p>
 * Diferente de Transaction que sempre tem um Asset associado,
 * CashFlow representa movimentações puras de caixa.
 */
@Entity
@Table(name = "cash_flows", indexes = {
        @Index(name = "idx_cash_flows_portfolio", columnList = "portfolio_id"),
        @Index(name = "idx_cash_flows_type", columnList = "type"),
        @Index(name = "idx_cash_flows_date", columnList = "flow_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlow implements Fingerprintable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    @JsonBackReference
    private Portfolio portfolio;

    /**
     * Tipo de movimentação de caixa
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransactionType type;

    /**
     * Valor da movimentação
     * Positivo = entrada de dinheiro (depósito, juros)
     * Negativo = saída de dinheiro (saque, imposto, taxa)
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Moeda da movimentação
     */
    @Column(length = 10)
    private String currency;

    /**
     * Data da movimentação
     */
    @Column(name = "flow_date", nullable = false)
    private LocalDateTime flowDate;

    @Column(name = "import_source")
    private String importSource; // XTB, BINANCE, MANUAL

    /**
     * Descrição/observações
     */
    @Column(length = 1000)
    private String comment;


    /**
     * Referência externa (ID do banco, número da transação, etc)
     */
    @Column(name = "external_id")
    private String externalId; // ID from XTB or Binance

    /**
     * Data de criação do registro
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "import_fingerprint", unique = true, length = 64)
    private String importFingerprint;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (importFingerprint == null) {
            importFingerprint = generateFingerprint();
        }
    }

    /**
     * Gera fingerprint único para a transação
     * Usado para prevenir duplicatas na importação
     */
    public String generateFingerprint() {
        // Estratégia 1: Se tem external ID, usar
        if (externalId != null && !externalId.isBlank() &&
                importSource != null && !importSource.isBlank()) {

            return String.format("%d_%s_%s",
                    portfolio.getId(), importSource, externalId);
        }

        // Estratégia 2: Hash dos dados principais
        String data = String.format("%d_%s_%s_%s",
                portfolio.getId(),
                externalId,
                amount.toString(),
                type.name()
        );

        return DigestUtils.sha256Hex(data);
    }

    /**
     * Garante que fingerprint existe após carregar do banco ou criar
     */
    @PostLoad
    @PostPersist
    @PostUpdate
    protected void ensureFingerprintExists() {
        if (importFingerprint == null && portfolio != null) {
            importFingerprint = generateFingerprint();
        }
    }


}

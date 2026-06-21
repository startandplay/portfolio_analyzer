package com.analytics.portfolio.model;

import com.analytics.portfolio.enums.RealEstateTransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ledger de movimentos de caixa de um imóvel.
 * Convenção: amount positivo = entrada (RENT, OTHER_INCOME),
 *            amount negativo = saída (todas as despesas).
 */
@Entity
@Table(name = "real_estate_transactions", indexes = {
        @Index(name = "idx_re_tx_property", columnList = "property_id"),
        @Index(name = "idx_re_tx_date", columnList = "transaction_date")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RealEstateTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private RealEstateProperty property;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RealEstateTransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

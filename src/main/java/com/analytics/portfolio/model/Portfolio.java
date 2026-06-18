package com.analytics.portfolio.model;

import com.analytics.portfolio.enums.PortfolioSource;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolios", uniqueConstraints = {
        // Um utilizador não pode ter dois portfolios com o mesmo nome
        @UniqueConstraint(name = "uk_portfolio_user_name", columnNames = {"user_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Dono do portfolio ─────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore // nunca serializar o user completo
    private User user;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String description;

    /**
     * Fonte/exchange deste portfolio.
     * Cada portfolio representa tipicamente uma corretora ou exchange.
     * Pode ser MANUAL ou REAL_ESTATE para portfolios criados manualmente.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @NotNull
    @Builder.Default
    private PortfolioSource source = PortfolioSource.MANUAL;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "initial_capital")
    private BigDecimal initialCapital;

    /**
     * Flag para incluir ou excluir este portfolio da agregação global.
     * Útil quando se quer excluir temporariamente um portfolio da visão consolidada.
     */
    @Column(name = "include_in_aggregate", nullable = false)
    @Builder.Default
    private boolean includeInAggregate = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Position> positions = new ArrayList<>();

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"portfolio", "asset", "hibernateLazyInitializer"})
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Dividend> dividends = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Helper ────────────────────────────────────────────────────
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }
}

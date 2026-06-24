package com.analytics.portfolio.dto;

import com.analytics.portfolio.enums.RealEstateTransactionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para registar um movimento de caixa num imóvel.
 *
 * Convenção de sinal:
 *   Receitas (RENT, OTHER_INCOME)        → amount positivo
 *   Despesas (INSURANCE, MORTGAGE, etc.) → amount negativo
 *
 * O frontend pode sempre enviar o valor absoluto — o controller
 * aplica o sinal correto com base no tipo.
 */
@Data
public class CreateReTransactionRequest {

    @NotNull(message = "Transaction type is required")
    private RealEstateTransactionType type;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @NotNull(message = "Transaction date is required")
    private LocalDateTime transactionDate;

    private String description;
}

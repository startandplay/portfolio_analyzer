package com.analytics.portfolio.dto;

import com.analytics.portfolio.enums.PortfolioSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO para criação de um novo portfolio.
 * Separa o payload da entidade JPA, evitando que o cliente
 * envie campos que não deve controlar (user, createdAt, etc.).
 */
@Data
public class CreatePortfolioRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Source is required")
    private PortfolioSource source;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency = "EUR";

    private BigDecimal initialCapital;

    private boolean includeInAggregate = true;
}

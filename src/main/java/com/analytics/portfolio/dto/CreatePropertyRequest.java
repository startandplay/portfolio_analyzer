package com.analytics.portfolio.dto;

import com.analytics.portfolio.enums.PropertyStrategy;
import com.analytics.portfolio.enums.PropertyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreatePropertyRequest {

    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "Property type is required")
    private PropertyType propertyType;

    @NotNull(message = "Strategy is required")
    private PropertyStrategy strategy;

    @NotNull(message = "Purchase price is required")
    @Positive(message = "Purchase price must be positive")
    private BigDecimal purchasePrice;

    @NotNull(message = "Purchase date is required")
    private LocalDateTime purchaseDate;

    // Financiamento — opcionais
    private BigDecimal downPaymentAmount;
    private BigDecimal loanAmount;
    private BigDecimal interestRatePercentage;

    // Avaliação inicial — opcional (default = purchasePrice)
    private BigDecimal currentEstimatedValue;

    private String currency = "EUR";
    private String notes;
}

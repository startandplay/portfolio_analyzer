package com.analytics.portfolio.controller;

import com.analytics.portfolio.dto.CreatePropertyRequest;
import com.analytics.portfolio.dto.CreateReTransactionRequest;
import com.analytics.portfolio.dto.RealEstateMetrics;
import com.analytics.portfolio.enums.PropertyStatus;
import com.analytics.portfolio.enums.RealEstateTransactionType;
import com.analytics.portfolio.model.Portfolio;
import com.analytics.portfolio.model.RealEstateProperty;
import com.analytics.portfolio.model.RealEstateTransaction;
import com.analytics.portfolio.model.User;
import com.analytics.portfolio.repository.RealEstatePropertyRepository;
import com.analytics.portfolio.repository.RealEstateTransactionRepository;
import com.analytics.portfolio.service.PortfolioService;
import com.analytics.portfolio.service.RealEstateMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * CRUD de imóveis e respetivos movimentos de caixa.
 *
 * Base path: /api/portfolios/{portfolioId}/properties
 *
 * Estrutura:
 *   Imóvel     → RealEstateProperty   (cabeçalho)
 *   Movimentos → RealEstateTransaction (ledger)
 *   Métricas   → RealEstateMetricsService
 */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/properties")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Real Estate", description = "Gestão de imóveis e respetivos cash flows")
public class RealEstateController {

    // Tipos que representam receitas — sinal positivo
    private static final Set<RealEstateTransactionType> INCOME_TYPES =
            Set.of(RealEstateTransactionType.RENT, RealEstateTransactionType.OTHER_INCOME);

    private final PortfolioService                portfolioService;
    private final RealEstatePropertyRepository    propertyRepo;
    private final RealEstateTransactionRepository txRepo;
    private final RealEstateMetricsService        metricsService;

    // ════════════════════════════════════════════
    // Imóveis — CRUD
    // ════════════════════════════════════════════

    @PostMapping
    @Operation(summary = "Create property",
               description = "Creates a new real estate property linked to the portfolio")
    public ResponseEntity<RealEstateProperty> createProperty(
            @PathVariable Long portfolioId,
            @Valid @RequestBody CreatePropertyRequest req,
            @AuthenticationPrincipal User user) {

        Portfolio portfolio = portfolioService.getPortfolio(portfolioId, user);

        BigDecimal estimatedValue = req.getCurrentEstimatedValue() != null
                ? req.getCurrentEstimatedValue()
                : req.getPurchasePrice();

        RealEstateProperty property = RealEstateProperty.builder()
                .portfolio(portfolio)
                .address(req.getAddress())
                .propertyType(req.getPropertyType())
                .strategy(req.getStrategy())
                .purchasePrice(req.getPurchasePrice())
                .purchaseDate(req.getPurchaseDate())
                .downPaymentAmount(req.getDownPaymentAmount())
                .loanAmount(req.getLoanAmount())
                .interestRatePercentage(req.getInterestRatePercentage())
                .currentEstimatedValue(estimatedValue)
                .currency(req.getCurrency() != null ? req.getCurrency() : "EUR")
                .notes(req.getNotes())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(propertyRepo.save(property));
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List properties", description = "Returns all properties for the portfolio")
    public ResponseEntity<List<RealEstateProperty>> listProperties(
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        return ResponseEntity.ok(propertyRepo.findByPortfolioId(portfolioId));
    }

    @GetMapping("/{propertyId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get property by ID")
    public ResponseEntity<RealEstateProperty> getProperty(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        return ResponseEntity.ok(findProperty(propertyId, portfolioId));
    }

    @PutMapping("/{propertyId}")
    @Operation(summary = "Update property",
               description = "Updates editable fields of a property")
    public ResponseEntity<RealEstateProperty> updateProperty(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @Valid @RequestBody CreatePropertyRequest req,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        RealEstateProperty property = findProperty(propertyId, portfolioId);

        property.setAddress(req.getAddress());
        property.setPropertyType(req.getPropertyType());
        property.setStrategy(req.getStrategy());
        property.setPurchasePrice(req.getPurchasePrice());
        property.setPurchaseDate(req.getPurchaseDate());
        property.setDownPaymentAmount(req.getDownPaymentAmount());
        property.setLoanAmount(req.getLoanAmount());
        property.setInterestRatePercentage(req.getInterestRatePercentage());
        if (req.getCurrentEstimatedValue() != null)
            property.setCurrentEstimatedValue(req.getCurrentEstimatedValue());
        if (req.getNotes() != null)
            property.setNotes(req.getNotes());

        return ResponseEntity.ok(propertyRepo.save(property));
    }

    @DeleteMapping("/{propertyId}")
    @Operation(summary = "Delete property and all its transactions")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        propertyRepo.delete(findProperty(propertyId, portfolioId));
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════
    // Ações de estado
    // ════════════════════════════════════════════

    @PatchMapping("/{propertyId}/valuation")
    @Operation(summary = "Update current valuation",
               description = "Updates the current estimated value and sets lastValuationDate to now")
    public ResponseEntity<RealEstateProperty> updateValuation(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @RequestParam BigDecimal value,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        RealEstateProperty property = findProperty(propertyId, portfolioId);
        property.setCurrentEstimatedValue(value);
        property.setLastValuationDate(LocalDateTime.now());
        return ResponseEntity.ok(propertyRepo.save(property));
    }

    @PatchMapping("/{propertyId}/sale")
    @Operation(summary = "Register sale",
               description = "Marks the property as SOLD with sale price and date")
    public ResponseEntity<RealEstateProperty> registerSale(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @RequestParam BigDecimal salePrice,
            @RequestParam(required = false) LocalDateTime saleDate,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        RealEstateProperty property = findProperty(propertyId, portfolioId);
        property.setSalePrice(salePrice);
        property.setSaleDate(saleDate != null ? saleDate : LocalDateTime.now());
        property.setStatus(PropertyStatus.SOLD);
        return ResponseEntity.ok(propertyRepo.save(property));
    }

    // ════════════════════════════════════════════
    // Movimentos de caixa (ledger)
    // ════════════════════════════════════════════

    @PostMapping("/{propertyId}/transactions")
    @Operation(summary = "Add cash flow transaction",
               description = "Records a cash movement (rent, expense, mortgage, etc.). "
                           + "Income types are stored as positive, expenses as negative.")
    public ResponseEntity<RealEstateTransaction> addTransaction(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @Valid @RequestBody CreateReTransactionRequest req,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        RealEstateProperty property = findProperty(propertyId, portfolioId);

        // Garantir sinal correto independente do que o cliente enviou
        BigDecimal amount = req.getAmount().abs();
        if (!INCOME_TYPES.contains(req.getType())) {
            amount = amount.negate();
        }

        RealEstateTransaction tx = RealEstateTransaction.builder()
                .property(property)
                .type(req.getType())
                .amount(amount)
                .transactionDate(req.getTransactionDate())
                .description(req.getDescription())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(txRepo.save(tx));
    }

    @GetMapping("/{propertyId}/transactions")
    @Transactional(readOnly = true)
    @Operation(summary = "List transactions",
               description = "Returns all cash flow transactions for the property, most recent first")
    public ResponseEntity<List<RealEstateTransaction>> listTransactions(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        findProperty(propertyId, portfolioId);
        return ResponseEntity.ok(txRepo.findByPropertyIdOrderByTransactionDateDesc(propertyId));
    }

    @DeleteMapping("/{propertyId}/transactions/{txId}")
    @Operation(summary = "Delete transaction")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @PathVariable Long txId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        findProperty(propertyId, portfolioId);

        RealEstateTransaction tx = txRepo.findById(txId)
                .filter(t -> t.getProperty().getId().equals(propertyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found"));

        txRepo.delete(tx);
        return ResponseEntity.noContent().build();
    }

    // ════════════════════════════════════════════
    // Métricas
    // ════════════════════════════════════════════

    @GetMapping("/{propertyId}/metrics/rental")
    @Operation(summary = "Rental metrics",
               description = "NOI, cap rate, gross yield, DSCR, cash-on-cash return, total return "
                           + "based on trailing 12 months of transactions")
    public ResponseEntity<RealEstateMetrics.RentalMetrics> getRentalMetrics(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        findProperty(propertyId, portfolioId);
        return ResponseEntity.ok(metricsService.calculateRentalMetrics(propertyId));
    }

    @GetMapping("/{propertyId}/metrics/flip")
    @Operation(summary = "Fix & flip metrics",
               description = "Total project cost, profit, ROI and annualized ROI. "
                           + "If not yet sold, uses currentEstimatedValue as projected sale price.")
    public ResponseEntity<RealEstateMetrics.FlipMetrics> getFlipMetrics(
            @PathVariable Long portfolioId,
            @PathVariable Long propertyId,
            @AuthenticationPrincipal User user) {

        portfolioService.getPortfolio(portfolioId, user);
        findProperty(propertyId, portfolioId);
        return ResponseEntity.ok(metricsService.calculateFlipMetrics(propertyId));
    }

    // ════════════════════════════════════════════
    // Helper
    // ════════════════════════════════════════════

    private RealEstateProperty findProperty(Long propertyId, Long portfolioId) {
        return propertyRepo.findById(propertyId)
                .filter(p -> p.getPortfolio().getId().equals(portfolioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Property not found or does not belong to this portfolio"));
    }
}

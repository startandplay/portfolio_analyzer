package com.analytics.portfolio.enums;

public enum RealEstateTransactionType {
    // ── Receitas (amount positivo) ──
    RENT,
    OTHER_INCOME,

    // ── Despesas operacionais (entram no NOI) ──
    INSURANCE,
    PROPERTY_TAX,
    MAINTENANCE,
    MANAGEMENT_FEE,
    UTILITIES,
    HOA,
    OTHER_EXPENSE,

    // ── Serviço da dívida (fora do NOI) ──
    MORTGAGE_PAYMENT,

    // ── Custos de aquisição/disposição (one-off) ──
    RENOVATION,
    CLOSING_COST_PURCHASE,
    CLOSING_COST_SALE
}

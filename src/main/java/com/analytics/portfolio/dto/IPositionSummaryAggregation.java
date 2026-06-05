package com.analytics.portfolio.dto;

import java.math.BigDecimal;

public interface IPositionSummaryAggregation {

    Long getAssetId();

    String getTicker();

    BigDecimal getCurrentQuantity();

    BigDecimal getCurrentTotalPurchaseValue();

    BigDecimal getAveragePurchasePrice();

    BigDecimal getTotalRealizedPnl();
}

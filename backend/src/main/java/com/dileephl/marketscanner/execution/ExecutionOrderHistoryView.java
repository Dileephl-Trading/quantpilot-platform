package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One readable lifecycle row for an entry and its protective exit orders. */
public record ExecutionOrderHistoryView(
        String orderId,
        String symbol,
        String entrySide,
        long entryQuantity,
        long entryFilledQuantity,
        String entryStatus,
        String entryOrderType,
        BigDecimal entryRequestedPrice,
        BigDecimal entryAveragePrice,
        String stopOrderId,
        String stopStatus,
        long stopFilledQuantity,
        BigDecimal stopTriggerPrice,
        BigDecimal stopAveragePrice,
        String targetOrderId,
        String targetStatus,
        long targetFilledQuantity,
        BigDecimal targetRequestedPrice,
        BigDecimal targetAveragePrice,
        long excessExitQuantity,
        long exitFilledQuantity,
        BigDecimal exitAveragePrice,
        BigDecimal realisedPnl,
        BigDecimal brokerRealisedPnl,
        String pnlSource,
        OffsetDateTime exitCompletedAt,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}

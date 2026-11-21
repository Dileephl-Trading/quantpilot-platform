package com.dileephl.marketscanner.journal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FillPaperTradeRequest(
        @NotNull @DecimalMin(value = "0.0001") BigDecimal actualEntryPrice,
        @Min(1) long filledQuantity,
        OffsetDateTime filledAt) {
}

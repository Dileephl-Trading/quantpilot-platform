package com.dileephl.marketscanner.journal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ClosePaperTradeRequest(
        @NotNull @DecimalMin(value = "0.0001") BigDecimal actualExitPrice,
        @DecimalMin(value = "0") BigDecimal brokerage,
        @NotNull ExitReason exitReason,
        OffsetDateTime closedAt) {
}

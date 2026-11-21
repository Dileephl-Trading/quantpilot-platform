package com.dileephl.marketscanner.journal;

import com.dileephl.marketscanner.execution.OrderSide;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreatePaperTradeRequest(
        @NotBlank @Size(max = 32) String symbol,
        @NotNull OrderSide side,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal intendedEntryPrice,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal stopPrice,
        @Min(1) long intendedQuantity,
        @Size(max = 80) String strategy,
        @Size(max = 500) String notes) {
}

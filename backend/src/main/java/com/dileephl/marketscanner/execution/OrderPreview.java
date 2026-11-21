package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;
import java.util.List;

public record OrderPreview(boolean allowed, long maxAllowedQuantity, BigDecimal proposedRisk,
                           List<String> reasons, String executionMode) { }

package com.example.bookreview.dto.internal;

import java.math.BigDecimal;

public record CostResult(long totalTokens, BigDecimal usdCost) {

    public BigDecimal totalCost() {
        return usdCost;
    }
}

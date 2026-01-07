package com.example.bookreview.dto.internal;

import java.math.BigDecimal;

public record TokenPrice(BigDecimal promptPricePerThousand, BigDecimal completionPricePerThousand) {}

package com.example.bookreview.service.model;

import java.math.BigDecimal;

public record AiReviewResult(String improvedContent, long tokenCount, BigDecimal usdCost) {
}

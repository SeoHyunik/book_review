package com.example.macronews.dto;

import java.math.BigDecimal;
import java.util.List;

public record OpenAiUsageDashboardDto(
        List<OpenAiUsageRecordViewDto> recentRecords,
        List<OpenAiUsageAggregateDto> dailyAggregates,
        List<OpenAiUsageAggregateDto> monthlyAggregates,
        BigDecimal recentUsdTotal,
        BigDecimal recentKrwTotal,
        BigDecimal exchangeRate,
        String exchangeRateStatusMessageKey,
        boolean exchangeRateFallback,
        boolean hasUnpricedRecords
) {
}

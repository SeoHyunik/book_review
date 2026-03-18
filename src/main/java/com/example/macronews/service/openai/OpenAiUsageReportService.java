package com.example.macronews.service.openai;

import com.example.macronews.domain.OpenAiUsageFeatureType;
import com.example.macronews.domain.OpenAiUsageRecord;
import com.example.macronews.dto.OpenAiUsageAggregateDto;
import com.example.macronews.dto.OpenAiUsageDashboardDto;
import com.example.macronews.dto.OpenAiUsageRecordViewDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.repository.OpenAiUsageRecordRepository;
import com.example.macronews.service.market.MarketDataFacade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAiUsageReportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final OpenAiUsageRecordRepository openAiUsageRecordRepository;
    private final MarketDataFacade marketDataFacade;

    @Value("${openai.pricing.interpretation-model-name:${openai.interpretation-model:${openai.model:gpt-4o-mini}}}")
    private String interpretationModel;

    @Value("${openai.pricing.interpretation-model.prompt-per-1k-usd:0.00015}")
    private BigDecimal interpretationPromptPer1kUsd;

    @Value("${openai.pricing.interpretation-model.completion-per-1k-usd:0.0006}")
    private BigDecimal interpretationCompletionPer1kUsd;

    @Value("${openai.pricing.summary-model-name:${app.featured.market-summary.ai-model:gpt-5}}")
    private String summaryModel;

    @Value("${openai.pricing.summary-model.prompt-per-1k-usd:0.00125}")
    private BigDecimal summaryPromptPer1kUsd;

    @Value("${openai.pricing.summary-model.completion-per-1k-usd:0.01}")
    private BigDecimal summaryCompletionPer1kUsd;

    @Value("${openai.pricing.primary-model-name:}")
    private String legacyPrimaryModel;

    @Value("${openai.pricing.primary-model.prompt-per-1k-usd:0.005}")
    private BigDecimal legacyPrimaryPromptPer1kUsd;

    @Value("${openai.pricing.primary-model.completion-per-1k-usd:0.015}")
    private BigDecimal legacyPrimaryCompletionPer1kUsd;

    @Value("${openai.pricing.default.prompt-per-1k-usd:0.005}")
    private BigDecimal defaultPromptPer1kUsd;

    @Value("${openai.pricing.default.completion-per-1k-usd:0.015}")
    private BigDecimal defaultCompletionPer1kUsd;

    @Value("${openai.cost.krw-fallback-rate:1350.0}")
    private BigDecimal fallbackKrwRate;

    @Value("${openai.cost.daily-days:7}")
    private int dailyDays;

    @Value("${openai.cost.monthly-months:6}")
    private int monthlyMonths;

    public OpenAiUsageDashboardDto getDashboard() {
        Optional<FxSnapshotDto> liveFxSnapshot = marketDataFacade.getUsdKrw();
        BigDecimal exchangeRate = liveFxSnapshot
                .map(FxSnapshotDto::rate)
                .map(BigDecimal::valueOf)
                .orElse(fallbackKrwRate);
        boolean exchangeFallback = liveFxSnapshot.isEmpty();
        String exchangeRateStatusMessageKey = exchangeFallback
                ? "admin.openai.exchange.fallback"
                : "admin.openai.exchange.live";

        List<OpenAiUsageRecord> recentRecords = openAiUsageRecordRepository.findTop50ByOrderByTimestampDesc();
        List<OpenAiUsageRecordViewDto> recentViews = recentRecords.stream()
                .map(record -> toRecordView(record, exchangeRate))
                .toList();

        Instant monthlyCutoff = YearMonth.now(BUSINESS_ZONE)
                .minusMonths(Math.max(monthlyMonths, 1) - 1L)
                .atDay(1)
                .atStartOfDay(BUSINESS_ZONE)
                .toInstant();
        List<OpenAiUsageRecord> aggregateWindowRecords =
                openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(monthlyCutoff);

        List<OpenAiUsageAggregateDto> dailyAggregates = buildDailyAggregates(aggregateWindowRecords, exchangeRate);
        List<OpenAiUsageAggregateDto> monthlyAggregates = buildMonthlyAggregates(aggregateWindowRecords, exchangeRate);

        BigDecimal recentUsdTotal = recentViews.stream()
                .map(OpenAiUsageRecordViewDto::estimatedUsdCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recentKrwTotal = recentViews.stream()
                .map(OpenAiUsageRecordViewDto::estimatedKrwCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OpenAiUsageDashboardDto(
                recentViews,
                dailyAggregates,
                monthlyAggregates,
                scaleUsd(recentUsdTotal),
                scaleKrw(recentKrwTotal),
                exchangeRate,
                exchangeRateStatusMessageKey,
                exchangeFallback
        );
    }

    BigDecimal estimateUsdCost(OpenAiUsageRecord record) {
        Pricing pricing = resolvePricing(record.model());
        BigDecimal promptCost = BigDecimal.valueOf(record.promptTokens())
                .multiply(pricing.promptPer1kUsd())
                .divide(BigDecimal.valueOf(1000L), 8, RoundingMode.HALF_UP);
        BigDecimal completionCost = BigDecimal.valueOf(record.completionTokens())
                .multiply(pricing.completionPer1kUsd())
                .divide(BigDecimal.valueOf(1000L), 8, RoundingMode.HALF_UP);
        return scaleUsd(promptCost.add(completionCost));
    }

    private List<OpenAiUsageAggregateDto> buildDailyAggregates(List<OpenAiUsageRecord> records, BigDecimal exchangeRate) {
        LocalDate cutoffDay = LocalDate.now(BUSINESS_ZONE).minusDays(Math.max(dailyDays, 1) - 1L);
        Map<LocalDate, List<OpenAiUsageRecord>> grouped = records.stream()
                .filter(record -> !record.timestamp().atZone(BUSINESS_ZONE).toLocalDate().isBefore(cutoffDay))
                .collect(java.util.stream.Collectors.groupingBy(
                        record -> record.timestamp().atZone(BUSINESS_ZONE).toLocalDate()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<OpenAiUsageRecord>>comparingByKey(Comparator.reverseOrder()))
                .limit(Math.max(dailyDays, 1))
                .map(entry -> toAggregate(entry.getKey().format(DAY_FORMATTER), entry.getValue(), exchangeRate))
                .toList();
    }

    private List<OpenAiUsageAggregateDto> buildMonthlyAggregates(List<OpenAiUsageRecord> records, BigDecimal exchangeRate) {
        YearMonth cutoffMonth = YearMonth.now(BUSINESS_ZONE).minusMonths(Math.max(monthlyMonths, 1) - 1L);
        Map<YearMonth, List<OpenAiUsageRecord>> grouped = records.stream()
                .filter(record -> !YearMonth.from(record.timestamp().atZone(BUSINESS_ZONE)).isBefore(cutoffMonth))
                .collect(java.util.stream.Collectors.groupingBy(
                        record -> YearMonth.from(record.timestamp().atZone(BUSINESS_ZONE))));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<YearMonth, List<OpenAiUsageRecord>>comparingByKey(Comparator.reverseOrder()))
                .limit(Math.max(monthlyMonths, 1))
                .map(entry -> toAggregate(entry.getKey().format(MONTH_FORMATTER), entry.getValue(), exchangeRate))
                .toList();
    }

    private OpenAiUsageAggregateDto toAggregate(String label, List<OpenAiUsageRecord> records, BigDecimal exchangeRate) {
        long requestCount = records.size();
        long promptTokens = records.stream().mapToLong(OpenAiUsageRecord::promptTokens).sum();
        long completionTokens = records.stream().mapToLong(OpenAiUsageRecord::completionTokens).sum();
        long totalTokens = records.stream().mapToLong(OpenAiUsageRecord::totalTokens).sum();
        BigDecimal estimatedUsdCost = records.stream()
                .map(this::estimateUsdCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedKrwCost = estimatedUsdCost.multiply(exchangeRate);
        return new OpenAiUsageAggregateDto(
                label,
                requestCount,
                promptTokens,
                completionTokens,
                totalTokens,
                scaleUsd(estimatedUsdCost),
                scaleKrw(estimatedKrwCost)
        );
    }

    private OpenAiUsageRecordViewDto toRecordView(OpenAiUsageRecord record, BigDecimal exchangeRate) {
        BigDecimal estimatedUsdCost = estimateUsdCost(record);
        return new OpenAiUsageRecordViewDto(
                record.timestamp(),
                record.model(),
                featureMessageKey(record.featureType()),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                estimatedUsdCost,
                scaleKrw(estimatedUsdCost.multiply(exchangeRate))
        );
    }

    private String featureMessageKey(OpenAiUsageFeatureType featureType) {
        return "admin.openai.feature." + featureType.name().toLowerCase(Locale.ROOT);
    }

    private Pricing resolvePricing(String model) {
        if (model != null && model.equalsIgnoreCase(summaryModel)) {
            return new Pricing(summaryPromptPer1kUsd, summaryCompletionPer1kUsd);
        }
        if (model != null && model.equalsIgnoreCase(interpretationModel)) {
            return new Pricing(interpretationPromptPer1kUsd, interpretationCompletionPer1kUsd);
        }
        if (model != null && model.equalsIgnoreCase(legacyPrimaryModel)) {
            return new Pricing(legacyPrimaryPromptPer1kUsd, legacyPrimaryCompletionPer1kUsd);
        }
        return new Pricing(defaultPromptPer1kUsd, defaultCompletionPer1kUsd);
    }

    private BigDecimal scaleUsd(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleKrw(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private record Pricing(BigDecimal promptPer1kUsd, BigDecimal completionPer1kUsd) {
    }
}

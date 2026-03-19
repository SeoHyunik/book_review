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
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OpenAiUsageReportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Pattern DATED_MODEL_PATTERN = Pattern.compile("^(.*)-\\d{4}-\\d{2}-\\d{2}$");

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
        ExchangeRateResolution exchangeRateResolution = resolveExchangeRate();
        BigDecimal exchangeRate = exchangeRateResolution.exchangeRate();

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

        BigDecimal rawRecentUsdTotal = recentRecords.stream()
                .map(record -> calculateUsdCost(record, resolvePricing(record)))
                .flatMap(Optional::stream)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasUnpricedRecords = recentViews.stream().anyMatch(record -> !record.estimatedCostAvailable())
                || aggregateWindowRecords.stream().anyMatch(record -> resolvePricing(record).isEmpty());

        return new OpenAiUsageDashboardDto(
                recentViews,
                dailyAggregates,
                monthlyAggregates,
                scaleUsd(rawRecentUsdTotal),
                scaleKrw(rawRecentUsdTotal.multiply(exchangeRate)),
                exchangeRate,
                exchangeRateResolution.messageKey(),
                exchangeRateResolution.fallback(),
                hasUnpricedRecords
        );
    }

    BigDecimal estimateUsdCost(OpenAiUsageRecord record) {
        Pricing pricing = resolvePricing(record)
                .orElseThrow(() -> new IllegalStateException("Pricing unavailable for usage record model=" + record.model()));
        return scaleUsd(calculateUsdCost(record, Optional.of(pricing))
                .orElseThrow(() -> new IllegalStateException("Pricing unavailable for usage record model=" + record.model())));
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
                .map(record -> calculateUsdCost(record, resolvePricing(record)))
                .flatMap(Optional::stream)
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
        Optional<Pricing> pricing = resolvePricing(record);
        BigDecimal estimatedUsdCost = calculateUsdCost(record, pricing)
                .map(this::scaleUsd)
                .orElse(BigDecimal.ZERO);
        return new OpenAiUsageRecordViewDto(
                record.timestamp(),
                record.model(),
                featureMessageKey(record.featureType()),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                pricing.isPresent(),
                estimatedUsdCost,
                scaleKrw(estimatedUsdCost.multiply(exchangeRate))
        );
    }

    private String featureMessageKey(OpenAiUsageFeatureType featureType) {
        return "admin.openai.feature." + featureType.name().toLowerCase(Locale.ROOT);
    }

    private Optional<Pricing> resolvePricing(OpenAiUsageRecord record) {
        if (record == null) {
            return Optional.empty();
        }
        String normalizedModel = normalizeModel(record.model());

        Optional<Pricing> featureSpecific = resolveFeatureSpecificPricing(record.featureType(), normalizedModel);
        if (featureSpecific.isPresent()) {
            return featureSpecific;
        }

        Optional<Pricing> explicitModelMatch = resolveConfiguredModelPricing(normalizedModel);
        if (explicitModelMatch.isPresent()) {
            return explicitModelMatch;
        }

        if (!StringUtils.hasText(normalizedModel) || "unknown".equals(normalizedModel)) {
            Optional<Pricing> featureFallback = resolveFeatureDefaultPricing(record.featureType());
            if (featureFallback.isPresent()) {
                return featureFallback;
            }
            return Optional.of(new Pricing(defaultPromptPer1kUsd, defaultCompletionPer1kUsd));
        }

        return Optional.empty();
    }

    private Optional<Pricing> resolveFeatureSpecificPricing(OpenAiUsageFeatureType featureType, String normalizedModel) {
        if (!StringUtils.hasText(normalizedModel)) {
            return Optional.empty();
        }
        return switch (featureType) {
            case MACRO_INTERPRETATION -> matchesConfiguredModel(normalizedModel, interpretationModel)
                    ? Optional.of(new Pricing(interpretationPromptPer1kUsd, interpretationCompletionPer1kUsd))
                    : Optional.empty();
            case MARKET_SUMMARY -> matchesConfiguredModel(normalizedModel, summaryModel)
                    ? Optional.of(new Pricing(summaryPromptPer1kUsd, summaryCompletionPer1kUsd))
                    : Optional.empty();
            case MARKET_FORECAST -> matchesConfiguredModel(normalizedModel, legacyPrimaryModel)
                    ? Optional.of(new Pricing(legacyPrimaryPromptPer1kUsd, legacyPrimaryCompletionPer1kUsd))
                    : Optional.empty();
        };
    }

    private Optional<Pricing> resolveConfiguredModelPricing(String normalizedModel) {
        if (!StringUtils.hasText(normalizedModel)) {
            return Optional.empty();
        }
        if (matchesConfiguredModel(normalizedModel, summaryModel)) {
            return Optional.of(new Pricing(summaryPromptPer1kUsd, summaryCompletionPer1kUsd));
        }
        if (matchesConfiguredModel(normalizedModel, interpretationModel)) {
            return Optional.of(new Pricing(interpretationPromptPer1kUsd, interpretationCompletionPer1kUsd));
        }
        if (matchesConfiguredModel(normalizedModel, legacyPrimaryModel)) {
            return Optional.of(new Pricing(legacyPrimaryPromptPer1kUsd, legacyPrimaryCompletionPer1kUsd));
        }
        return Optional.empty();
    }

    private Optional<Pricing> resolveFeatureDefaultPricing(OpenAiUsageFeatureType featureType) {
        if (featureType == null) {
            return Optional.empty();
        }
        return switch (featureType) {
            case MACRO_INTERPRETATION -> Optional.of(new Pricing(interpretationPromptPer1kUsd, interpretationCompletionPer1kUsd));
            case MARKET_SUMMARY -> Optional.of(new Pricing(summaryPromptPer1kUsd, summaryCompletionPer1kUsd));
            case MARKET_FORECAST -> Optional.of(new Pricing(legacyPrimaryPromptPer1kUsd, legacyPrimaryCompletionPer1kUsd));
        };
    }

    private Optional<BigDecimal> calculateUsdCost(OpenAiUsageRecord record, Optional<Pricing> pricing) {
        if (record == null || pricing.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal promptCost = BigDecimal.valueOf(record.promptTokens())
                .multiply(pricing.get().promptPer1kUsd())
                .divide(BigDecimal.valueOf(1000L), 8, RoundingMode.HALF_UP);
        BigDecimal completionCost = BigDecimal.valueOf(record.completionTokens())
                .multiply(pricing.get().completionPer1kUsd())
                .divide(BigDecimal.valueOf(1000L), 8, RoundingMode.HALF_UP);
        return Optional.of(promptCost.add(completionCost));
    }

    private ExchangeRateResolution resolveExchangeRate() {
        Optional<BigDecimal> liveRate = marketDataFacade.getUsdKrw()
                .map(FxSnapshotDto::rate)
                .map(BigDecimal::valueOf)
                .filter(this::isPositive);
        if (liveRate.isPresent()) {
            return new ExchangeRateResolution(liveRate.get(), "admin.openai.exchange.live", false);
        }

        BigDecimal safeFallbackRate = isPositive(fallbackKrwRate) ? fallbackKrwRate : BigDecimal.ZERO;
        return new ExchangeRateResolution(safeFallbackRate, "admin.openai.exchange.fallback", true);
    }

    private boolean matchesConfiguredModel(String normalizedRecordedModel, String configuredModel) {
        String normalizedConfiguredModel = normalizeModel(configuredModel);
        return StringUtils.hasText(normalizedRecordedModel)
                && StringUtils.hasText(normalizedConfiguredModel)
                && normalizedRecordedModel.equals(normalizedConfiguredModel);
    }

    private String normalizeModel(String model) {
        if (!StringUtils.hasText(model)) {
            return "";
        }
        String normalized = model.trim().toLowerCase(Locale.ROOT);
        var matcher = DATED_MODEL_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return normalized;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BigDecimal scaleUsd(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleKrw(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private record Pricing(BigDecimal promptPer1kUsd, BigDecimal completionPer1kUsd) {
    }

    private record ExchangeRateResolution(BigDecimal exchangeRate, String messageKey, boolean fallback) {
    }
}

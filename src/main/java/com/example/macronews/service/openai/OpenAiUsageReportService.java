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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OpenAiUsageReportService {

    private static final int RECENT_RECORD_PAGE_SIZE = 10;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Pattern DATED_MODEL_PATTERN = Pattern.compile("^(.*)-\\d{4}-\\d{2}-\\d{2}$");
    private static final BigDecimal ONE_MILLION_TOKENS = BigDecimal.valueOf(1_000_000L);
    private static final String FEATURE_DEFAULT_FALLBACK = "default_fallback";

    private final OpenAiUsageRecordRepository openAiUsageRecordRepository;
    private final MarketDataFacade marketDataFacade;
    private final OpenAiPricingSnapshotLoader pricingSnapshotLoader;

    @Value("${openai.cost.krw-fallback-rate:1350.0}")
    private BigDecimal fallbackKrwRate;

    @Value("${openai.cost.daily-days:7}")
    private int dailyDays;

    @Value("${openai.cost.monthly-months:6}")
    private int monthlyMonths;

    public OpenAiUsageDashboardDto getDashboard() {
        return getDashboard(1);
    }

    public OpenAiUsageDashboardDto getDashboard(int page) {
        ExchangeRateResolution exchangeRateResolution = resolveExchangeRate();
        BigDecimal exchangeRate = exchangeRateResolution.exchangeRate();

        int requestedPage = resolveRecentRecordPage(page);
        Page<OpenAiUsageRecord> recentRecordPage = fetchRecentRecordPage(requestedPage);
        List<OpenAiUsageRecord> recentRecords = recentRecordPage.getContent();
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
        int recentRecordTotalPages = Math.max(recentRecordPage.getTotalPages(), 1);
        int recentRecordCurrentPage = recentRecordPage.getTotalPages() == 0
                ? 1
                : recentRecordPage.getNumber() + 1;

        BigDecimal rawRecentUsdTotal = recentRecords.stream()
                .map(record -> calculateUsdCost(record, resolvePricing(record)))
                .flatMap(Optional::stream)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasUnpricedRecords = recentViews.stream().anyMatch(record -> !record.estimatedCostAvailable())
                || aggregateWindowRecords.stream().anyMatch(record -> resolvePricing(record).isEmpty());

        return new OpenAiUsageDashboardDto(
                recentViews,
                recentRecordPage.getTotalElements(),
                recentRecordCurrentPage,
                recentRecordTotalPages,
                recentRecordPage.getTotalPages() > 0 && recentRecordPage.hasPrevious(),
                recentRecordPage.getTotalPages() > 0 && recentRecordPage.hasNext(),
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

    private int resolveRecentRecordPage(int page) {
        return Math.max(page, 1);
    }

    private Page<OpenAiUsageRecord> fetchRecentRecordPage(int page) {
        Page<OpenAiUsageRecord> recentRecordPage = openAiUsageRecordRepository.findAllByOrderByTimestampDesc(
                recentRecordPageable(page));
        int totalPages = recentRecordPage.getTotalPages();
        if (totalPages > 0 && page > totalPages) {
            return openAiUsageRecordRepository.findAllByOrderByTimestampDesc(recentRecordPageable(totalPages));
        }
        return recentRecordPage;
    }

    private PageRequest recentRecordPageable(int page) {
        return PageRequest.of(page - 1, RECENT_RECORD_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "timestamp"));
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

        Optional<Pricing> explicitModelMatch = resolveModelPricing(normalizedModel);
        if (explicitModelMatch.isPresent()) {
            return explicitModelMatch;
        }

        if (!StringUtils.hasText(normalizedModel) || "unknown".equals(normalizedModel)) {
            Optional<Pricing> featureFallback = resolveFeatureDefaultPricing(record.featureType());
            if (featureFallback.isPresent()) {
                return featureFallback;
            }
            return resolveNamedFeatureProfile(FEATURE_DEFAULT_FALLBACK);
        }

        return Optional.empty();
    }

    private Optional<Pricing> resolveFeatureSpecificPricing(OpenAiUsageFeatureType featureType, String normalizedModel) {
        if (!StringUtils.hasText(normalizedModel)) {
            return Optional.empty();
        }
        return resolveNamedFeatureProfile(featureProfileName(featureType))
                .filter(pricing -> matchesConfiguredModel(normalizedModel, pricing.modelAlias()));
    }

    private Optional<Pricing> resolveModelPricing(String normalizedModel) {
        if (!StringUtils.hasText(normalizedModel)) {
            return Optional.empty();
        }
        return Optional.ofNullable(pricingSnapshot().models())
                .map(models -> models.get(normalizedModel))
                .flatMap(this::toPricing);
    }

    private Optional<Pricing> resolveFeatureDefaultPricing(OpenAiUsageFeatureType featureType) {
        if (featureType == null) {
            return Optional.empty();
        }
        return resolveNamedFeatureProfile(featureProfileName(featureType));
    }

    private Optional<BigDecimal> calculateUsdCost(OpenAiUsageRecord record, Optional<Pricing> pricing) {
        if (record == null || pricing.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal promptCost = BigDecimal.valueOf(record.promptTokens())
                .multiply(pricing.get().inputPer1mUsd())
                .divide(ONE_MILLION_TOKENS, 8, RoundingMode.HALF_UP);
        BigDecimal completionCost = BigDecimal.valueOf(record.completionTokens())
                .multiply(pricing.get().outputPer1mUsd())
                .divide(ONE_MILLION_TOKENS, 8, RoundingMode.HALF_UP);
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

    private OpenAiPricingSnapshot pricingSnapshot() {
        return pricingSnapshotLoader.snapshot();
    }

    private String featureProfileName(OpenAiUsageFeatureType featureType) {
        return featureType.name().toLowerCase(Locale.ROOT);
    }

    private Optional<Pricing> resolveNamedFeatureProfile(String profileName) {
        if (!StringUtils.hasText(profileName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(pricingSnapshot().featureProfiles())
                .map(profiles -> profiles.get(profileName))
                .flatMap(this::toPricing);
    }

    private Optional<Pricing> toPricing(OpenAiPricingSnapshot.ModelPricing modelPricing) {
        if (modelPricing == null || modelPricing.input() == null || modelPricing.output() == null) {
            return Optional.empty();
        }
        return Optional.of(new Pricing(modelPricing.input(), modelPricing.output(), null));
    }

    private Optional<Pricing> toPricing(OpenAiPricingSnapshot.FeaturePricingProfile profile) {
        if (profile == null || profile.input() == null || profile.output() == null) {
            return Optional.empty();
        }
        return Optional.of(new Pricing(profile.input(), profile.output(), profile.model()));
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

    private record Pricing(BigDecimal inputPer1mUsd, BigDecimal outputPer1mUsd, String modelAlias) {
    }

    private record ExchangeRateResolution(BigDecimal exchangeRate, String messageKey, boolean fallback) {
    }
}

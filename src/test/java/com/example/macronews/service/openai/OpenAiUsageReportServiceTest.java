package com.example.macronews.service.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.OpenAiUsageFeatureType;
import com.example.macronews.domain.OpenAiUsageRecord;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.repository.OpenAiUsageRecordRepository;
import com.example.macronews.service.market.MarketDataFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OpenAiUsageReportServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-03T12:00:00Z");

    @Mock
    private OpenAiUsageRecordRepository openAiUsageRecordRepository;

    @Mock
    private MarketDataFacade marketDataFacade;

    private OpenAiPricingSnapshotLoader pricingSnapshotLoader;
    private OpenAiUsageReportService openAiUsageReportService;

    @BeforeEach
    void setUp() {
        pricingSnapshotLoader = new OpenAiPricingSnapshotLoader(
                new ObjectMapper(),
                new ClassPathResource("pricing/openai-pricing.json")
        );
        openAiUsageReportService = new OpenAiUsageReportService(
                openAiUsageRecordRepository,
                marketDataFacade,
                pricingSnapshotLoader
        );
        ReflectionTestUtils.setField(openAiUsageReportService, "clock", Clock.fixed(FIXED_NOW, BUSINESS_ZONE));
        ReflectionTestUtils.setField(openAiUsageReportService, "reportingStartDate", LocalDate.of(2026, 4, 1));
        ReflectionTestUtils.setField(openAiUsageReportService, "fallbackKrwRate", BigDecimal.valueOf(1350d));
        ReflectionTestUtils.setField(openAiUsageReportService, "dailyDays", 7);
        ReflectionTestUtils.setField(openAiUsageReportService, "monthlyMonths", 6);
    }

    @Test
    @DisplayName("dashboard should exclude records before the reporting cutoff")
    void getDashboard_excludesRecordsBeforeReportingCutoff() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 1000, 500,
                        LocalDate.of(2026, 3, 31).atTime(23, 0).atZone(BUSINESS_ZONE).toInstant()),
                record("gpt-5.4-mini", OpenAiUsageFeatureType.MARKET_SUMMARY, 1000, 500,
                        LocalDate.of(2026, 4, 1).atTime(10, 0).atZone(BUSINESS_ZONE).toInstant())
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, FIXED_NOW)));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentRecords()).hasSize(1);
        assertThat(dashboard.recentRecordTotalCount()).isEqualTo(1);
        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0030");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("4");
        assertThat(dashboard.dailyAggregates()).hasSize(1);
        assertThat(dashboard.dailyAggregates().get(0).estimatedUsdCost()).isEqualByComparingTo("0.0030");
        assertThat(dashboard.monthlyAggregates()).hasSize(1);
        assertThat(dashboard.monthlyAggregates().get(0).estimatedKrwCost()).isEqualByComparingTo("4");
        assertThat(dashboard.exchangeRateStatusMessageKey()).isEqualTo("admin.openai.exchange.live");
        assertThat(dashboard.hasUnpricedRecords()).isFalse();
    }

    @Test
    @DisplayName("dashboard should keep records on and after the reporting cutoff")
    void getDashboard_keepsRecordsOnAndAfterReportingCutoff() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 1000, 500,
                        LocalDate.of(2026, 4, 1).atTime(9, 0).atZone(BUSINESS_ZONE).toInstant()),
                record("gpt-5.4-mini", OpenAiUsageFeatureType.MARKET_SUMMARY, 1000, 500, FIXED_NOW)
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, FIXED_NOW)));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentRecords()).hasSize(2);
        assertThat(dashboard.recentRecordTotalCount()).isEqualTo(2);
        assertThat(dashboard.dailyAggregates()).isNotEmpty();
        assertThat(dashboard.monthlyAggregates()).isNotEmpty();
    }

    @Test
    @DisplayName("dashboard should keep daily, monthly, and total costs aligned for reporting records")
    void getDashboard_keepsDailyMonthlyAndTotalCostsAligned() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 1000, 500,
                        LocalDate.of(2026, 4, 2).atTime(11, 0).atZone(BUSINESS_ZONE).toInstant()),
                record("gpt-5.4-mini", OpenAiUsageFeatureType.MARKET_SUMMARY, 1000, 500,
                        LocalDate.of(2026, 4, 2).atTime(10, 0).atZone(BUSINESS_ZONE).toInstant())
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, FIXED_NOW)));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentRecords()).hasSize(2);
        assertThat(dashboard.recentRecordTotalCount()).isEqualTo(2);
        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0035");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("5");
        assertThat(dashboard.dailyAggregates()).hasSize(1);
        assertThat(dashboard.dailyAggregates().get(0).estimatedUsdCost()).isEqualByComparingTo("0.0035");
        assertThat(dashboard.monthlyAggregates()).hasSize(1);
        assertThat(dashboard.monthlyAggregates().get(0).estimatedKrwCost()).isEqualByComparingTo("5");
        assertThat(dashboard.exchangeRateStatusMessageKey()).isEqualTo("admin.openai.exchange.live");
        assertThat(dashboard.hasUnpricedRecords()).isFalse();
    }

    @Test
    @DisplayName("dashboard should remain safe when only pre-cutoff records exist")
    void getDashboard_remainsSafeWhenOnlyPreReportingCutoffRecordsExist() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 100, 20,
                        LocalDate.of(2026, 3, 31).atTime(23, 0).atZone(BUSINESS_ZONE).toInstant())
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentRecordTotalCount()).isZero();
        assertThat(dashboard.recentRecords()).isEmpty();
        assertThat(dashboard.dailyAggregates()).isEmpty();
        assertThat(dashboard.monthlyAggregates()).isEmpty();
        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0000");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("dashboard should estimate missing prompt and completion tokens from total tokens")
    void getDashboard_usesTotalTokensWhenPromptAndCompletionAreMissing() {
        Instant now = FIXED_NOW;
        List<OpenAiUsageRecord> records = List.of(
                new OpenAiUsageRecord(
                        "missing-usage",
                        now,
                        "gpt-5.4",
                        OpenAiUsageFeatureType.MARKET_FORECAST,
                        0,
                        0,
                        1_000_000
                )
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, now)));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentRecords()).hasSize(1);
        assertThat(dashboard.recentRecords().get(0).estimatedUsdCost()).isEqualByComparingTo("2.5000");
        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("2.5000");
        assertThat(dashboard.hasUnpricedRecords()).isFalse();
    }

    @Test
    @DisplayName("dashboard should fall back to configured KRW rate when live FX is unavailable")
    void getDashboard_usesFallbackFxRate() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 1000, 0, FIXED_NOW)
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.exchangeRateStatusMessageKey()).isEqualTo("admin.openai.exchange.fallback");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("dashboard should price dated model slugs using normalized configured aliases")
    void getDashboard_pricesDatedModelAliases() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini-2026-03-01", OpenAiUsageFeatureType.MARKET_FORECAST, 1000, 500, FIXED_NOW)
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, FIXED_NOW)));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0125");
        assertThat(dashboard.hasUnpricedRecords()).isFalse();
        assertThat(dashboard.recentRecords().get(0).estimatedCostAvailable()).isTrue();
    }

    @Test
    @DisplayName("estimateUsdCost should use repository JSON model pricing for gpt-5.4 family")
    void estimateUsdCost_usesJsonModelPricing() {
        BigDecimal gpt54Cost = openAiUsageReportService.estimateUsdCost(
                record("gpt-5.4", OpenAiUsageFeatureType.MARKET_FORECAST, 1_000_000, 1_000_000, FIXED_NOW)
        );
        BigDecimal gpt54MiniCost = openAiUsageReportService.estimateUsdCost(
                record("gpt-5.4-mini", OpenAiUsageFeatureType.MARKET_FORECAST, 1_000_000, 1_000_000, FIXED_NOW)
        );
        BigDecimal gpt54NanoCost = openAiUsageReportService.estimateUsdCost(
                record("gpt-5.4-nano", OpenAiUsageFeatureType.MARKET_FORECAST, 1_000_000, 1_000_000, FIXED_NOW)
        );

        assertThat(gpt54Cost).isEqualByComparingTo("17.5000");
        assertThat(gpt54MiniCost).isEqualByComparingTo("5.2500");
        assertThat(gpt54NanoCost).isEqualByComparingTo("1.4500");
    }

    @Test
    @DisplayName("dashboard should exclude unrecognized model slugs from estimated cost totals")
    void getDashboard_excludesUnrecognizedModelsFromCostTotals() {
        List<OpenAiUsageRecord> records = List.of(
                record("unknown-experimental-model", OpenAiUsageFeatureType.MARKET_FORECAST, 1000, 500, FIXED_NOW)
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, FIXED_NOW)));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0000");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("0");
        assertThat(dashboard.hasUnpricedRecords()).isTrue();
        assertThat(dashboard.recentRecords().get(0).estimatedCostAvailable()).isFalse();
    }

    @Test
    @DisplayName("dashboard should use feature fallback pricing when model is blank")
    void getDashboard_usesFeatureFallbackWhenModelBlank() {
        List<OpenAiUsageRecord> records = List.of(
                record("", OpenAiUsageFeatureType.MARKET_FORECAST, 1000, 500, FIXED_NOW)
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, FIXED_NOW)));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0125");
        assertThat(dashboard.recentRecords().get(0).estimatedCostAvailable()).isTrue();
        assertThat(dashboard.hasUnpricedRecords()).isFalse();
    }

    @Test
    @DisplayName("dashboard should request reporting cutoff records using the Seoul boundary")
    void getDashboard_requestsReportingCutoffForRecentRecords() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 100, 20, FIXED_NOW)
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        openAiUsageReportService.getDashboard(2);

        ArgumentCaptor<Instant> timestampCaptor = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(openAiUsageRecordRepository, org.mockito.Mockito.times(1))
                .findByTimestampGreaterThanEqualOrderByTimestampDesc(
                timestampCaptor.capture());
        assertThat(timestampCaptor.getAllValues().get(0)).isEqualTo(
                LocalDate.of(2026, 4, 1).atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    @Test
    @DisplayName("dashboard should expose recent record pagination metadata")
    void getDashboard_exposesRecentRecordPaginationMetadata() {
        List<OpenAiUsageRecord> reportingRecords = java.util.stream.IntStream.rangeClosed(0, 20)
                .mapToObj(index -> record(
                        "gpt-4o-mini",
                        OpenAiUsageFeatureType.MACRO_INTERPRETATION,
                        100,
                        20,
                        LocalDate.of(2026, 4, 2).atTime(11, 0).minusMinutes(index).atZone(BUSINESS_ZONE).toInstant()))
                .toList();
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(reportingRecords);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        var dashboard = openAiUsageReportService.getDashboard(2);

        assertThat(dashboard.recentRecordTotalCount()).isEqualTo(21);
        assertThat(dashboard.recentRecordCurrentPage()).isEqualTo(2);
        assertThat(dashboard.recentRecordTotalPages()).isEqualTo(3);
        assertThat(dashboard.recentRecordHasPreviousPage()).isTrue();
        assertThat(dashboard.recentRecordHasNextPage()).isTrue();
        assertThat(dashboard.recentRecords()).hasSize(10);
    }

    @Test
    @DisplayName("dashboard should keep summaries while the detailed table stays on reporting records")
    void getDashboard_keepsSummariesWhileRecentTableShowsReportingRecords() {
        List<OpenAiUsageRecord> reportingRecords = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 100, 20,
                        LocalDate.of(2026, 4, 2).atTime(11, 0).atZone(BUSINESS_ZONE).toInstant()),
                record("gpt-4o-mini", OpenAiUsageFeatureType.MARKET_SUMMARY, 100, 20,
                        LocalDate.of(2026, 4, 2).atTime(10, 0).atZone(BUSINESS_ZONE).toInstant())
        );
        List<OpenAiUsageRecord> summaryWindowRecords = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 100, 20, FIXED_NOW),
                record("gpt-4o-mini", OpenAiUsageFeatureType.MARKET_SUMMARY, 100, 20,
                        FIXED_NOW.minus(2, ChronoUnit.DAYS))
        );
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(reportingRecords, summaryWindowRecords);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentRecordTotalCount()).isEqualTo(2);
        assertThat(dashboard.recentRecords()).hasSize(2);
        assertThat(dashboard.dailyAggregates()).isNotEmpty();
        assertThat(dashboard.monthlyAggregates()).isNotEmpty();
    }

    @Test
    @DisplayName("dashboard should clamp oversized page requests to the last available page")
    void getDashboard_clampsOversizedPageRequests() {
        List<OpenAiUsageRecord> reportingRecords = java.util.stream.IntStream.rangeClosed(0, 20)
                .mapToObj(index -> record(
                        "gpt-4o-mini",
                        OpenAiUsageFeatureType.MACRO_INTERPRETATION,
                        100,
                        20,
                        LocalDate.of(2026, 4, 2).atTime(12, 0).minusMinutes(index).atZone(BUSINESS_ZONE).toInstant()))
                .toList();
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(reportingRecords);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        var dashboard = openAiUsageReportService.getDashboard(5);

        assertThat(dashboard.recentRecordCurrentPage()).isEqualTo(3);
        assertThat(dashboard.recentRecordTotalPages()).isEqualTo(3);
        assertThat(dashboard.recentRecordHasPreviousPage()).isTrue();
        assertThat(dashboard.recentRecordHasNextPage()).isFalse();
        assertThat(dashboard.recentRecords()).hasSize(1);
    }

    @Test
    @DisplayName("dashboard should normalize empty recent record pages to first page metadata")
    void getDashboard_normalizesEmptyRecentRecordPageMetadata() {
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        var dashboard = openAiUsageReportService.getDashboard(5);

        assertThat(dashboard.recentRecordTotalCount()).isZero();
        assertThat(dashboard.recentRecordCurrentPage()).isEqualTo(1);
        assertThat(dashboard.recentRecordTotalPages()).isEqualTo(1);
        assertThat(dashboard.recentRecordHasPreviousPage()).isFalse();
        assertThat(dashboard.recentRecordHasNextPage()).isFalse();
        assertThat(dashboard.recentRecords()).isEmpty();
    }

    private OpenAiUsageRecord record(String model, OpenAiUsageFeatureType featureType, int promptTokens,
            int completionTokens, Instant timestamp) {
        return new OpenAiUsageRecord(
                null,
                timestamp,
                model,
                featureType,
                promptTokens,
                completionTokens,
                promptTokens + completionTokens
        );
    }
}

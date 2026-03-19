package com.example.macronews.service.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.OpenAiUsageFeatureType;
import com.example.macronews.domain.OpenAiUsageRecord;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.repository.OpenAiUsageRecordRepository;
import com.example.macronews.service.market.MarketDataFacade;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OpenAiUsageReportServiceTest {

    @Mock
    private OpenAiUsageRecordRepository openAiUsageRecordRepository;

    @Mock
    private MarketDataFacade marketDataFacade;

    private OpenAiUsageReportService openAiUsageReportService;

    @BeforeEach
    void setUp() {
        openAiUsageReportService = new OpenAiUsageReportService(openAiUsageRecordRepository, marketDataFacade);
        ReflectionTestUtils.setField(openAiUsageReportService, "interpretationModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(openAiUsageReportService, "interpretationPromptPer1kUsd", BigDecimal.valueOf(0.00015d));
        ReflectionTestUtils.setField(openAiUsageReportService, "interpretationCompletionPer1kUsd", BigDecimal.valueOf(0.0006d));
        ReflectionTestUtils.setField(openAiUsageReportService, "summaryModel", "gpt-5");
        ReflectionTestUtils.setField(openAiUsageReportService, "summaryPromptPer1kUsd", BigDecimal.valueOf(0.00125d));
        ReflectionTestUtils.setField(openAiUsageReportService, "summaryCompletionPer1kUsd", BigDecimal.valueOf(0.01d));
        ReflectionTestUtils.setField(openAiUsageReportService, "legacyPrimaryModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(openAiUsageReportService, "legacyPrimaryPromptPer1kUsd", BigDecimal.valueOf(0.005d));
        ReflectionTestUtils.setField(openAiUsageReportService, "legacyPrimaryCompletionPer1kUsd", BigDecimal.valueOf(0.015d));
        ReflectionTestUtils.setField(openAiUsageReportService, "defaultPromptPer1kUsd", BigDecimal.valueOf(0.010d));
        ReflectionTestUtils.setField(openAiUsageReportService, "defaultCompletionPer1kUsd", BigDecimal.valueOf(0.030d));
        ReflectionTestUtils.setField(openAiUsageReportService, "fallbackKrwRate", BigDecimal.valueOf(1350d));
        ReflectionTestUtils.setField(openAiUsageReportService, "dailyDays", 7);
        ReflectionTestUtils.setField(openAiUsageReportService, "monthlyMonths", 6);
    }

    @Test
    @DisplayName("dashboard should calculate estimated costs using live FX when available")
    void getDashboard_calculatesUsdAndKrwWithLiveFx() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 1000, 500, now),
                record("gpt-5", OpenAiUsageFeatureType.MARKET_SUMMARY, 1000, 500, now.minus(2, ChronoUnit.DAYS))
        );
        given(openAiUsageRecordRepository.findTop50ByOrderByTimestampDesc()).willReturn(records);
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, now)));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentRecords()).hasSize(2);
        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0067");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("9");
        assertThat(dashboard.exchangeRateStatusMessageKey()).isEqualTo("admin.openai.exchange.live");
        assertThat(dashboard.hasUnpricedRecords()).isFalse();
        assertThat(dashboard.dailyAggregates()).isNotEmpty();
        assertThat(dashboard.monthlyAggregates()).isNotEmpty();
    }

    @Test
    @DisplayName("dashboard should fall back to configured KRW rate when live FX is unavailable")
    void getDashboard_usesFallbackFxRate() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 1000, 0, Instant.now())
        );
        given(openAiUsageRecordRepository.findTop50ByOrderByTimestampDesc()).willReturn(records);
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
                record("gpt-4o-mini-2026-03-01", OpenAiUsageFeatureType.MARKET_FORECAST, 1000, 500, Instant.now())
        );
        given(openAiUsageRecordRepository.findTop50ByOrderByTimestampDesc()).willReturn(records);
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, Instant.now())));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0125");
        assertThat(dashboard.hasUnpricedRecords()).isFalse();
        assertThat(dashboard.recentRecords().get(0).estimatedCostAvailable()).isTrue();
    }

    @Test
    @DisplayName("dashboard should exclude unrecognized model slugs from estimated cost totals")
    void getDashboard_excludesUnrecognizedModelsFromCostTotals() {
        List<OpenAiUsageRecord> records = List.of(
                record("unknown-experimental-model", OpenAiUsageFeatureType.MARKET_FORECAST, 1000, 500, Instant.now())
        );
        given(openAiUsageRecordRepository.findTop50ByOrderByTimestampDesc()).willReturn(records);
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, Instant.now())));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0000");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("0");
        assertThat(dashboard.hasUnpricedRecords()).isTrue();
        assertThat(dashboard.recentRecords().get(0).estimatedCostAvailable()).isFalse();
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

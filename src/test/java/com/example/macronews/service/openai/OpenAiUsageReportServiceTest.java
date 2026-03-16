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
        ReflectionTestUtils.setField(openAiUsageReportService, "primaryModel", "gpt-4o");
        ReflectionTestUtils.setField(openAiUsageReportService, "primaryPromptPer1kUsd", BigDecimal.valueOf(0.005d));
        ReflectionTestUtils.setField(openAiUsageReportService, "primaryCompletionPer1kUsd", BigDecimal.valueOf(0.015d));
        ReflectionTestUtils.setField(openAiUsageReportService, "defaultPromptPer1kUsd", BigDecimal.valueOf(0.010d));
        ReflectionTestUtils.setField(openAiUsageReportService, "defaultCompletionPer1kUsd", BigDecimal.valueOf(0.030d));
        ReflectionTestUtils.setField(openAiUsageReportService, "fallbackKrwRate", BigDecimal.valueOf(1350d));
        ReflectionTestUtils.setField(openAiUsageReportService, "dailyDays", 7);
        ReflectionTestUtils.setField(openAiUsageReportService, "monthlyMonths", 6);
    }

    @Test
    @DisplayName("dashboard should calculate estimated costs using live FX when available")
    void getDashboard_calculatesUsdAndKrwWithLiveFx() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 1000, 500, Instant.parse("2026-03-16T00:00:00Z")),
                record("other-model", OpenAiUsageFeatureType.MARKET_FORECAST, 1000, 500, Instant.parse("2026-03-01T00:00:00Z"))
        );
        given(openAiUsageRecordRepository.findTop50ByOrderByTimestampDesc()).willReturn(records);
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, Instant.now())));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentRecords()).hasSize(2);
        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0375");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("53");
        assertThat(dashboard.exchangeRateStatusMessageKey()).isEqualTo("admin.openai.exchange.live");
        assertThat(dashboard.dailyAggregates()).isNotEmpty();
        assertThat(dashboard.monthlyAggregates()).isNotEmpty();
    }

    @Test
    @DisplayName("dashboard should fall back to configured KRW rate when live FX is unavailable")
    void getDashboard_usesFallbackFxRate() {
        List<OpenAiUsageRecord> records = List.of(record("gpt-4o", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 1000, 0, Instant.parse("2026-03-16T00:00:00Z")));
        given(openAiUsageRecordRepository.findTop50ByOrderByTimestampDesc()).willReturn(records);
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.exchangeRateStatusMessageKey()).isEqualTo("admin.openai.exchange.fallback");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("7");
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



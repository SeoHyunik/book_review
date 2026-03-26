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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
        given(openAiUsageRecordRepository.findAllByOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(records));
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
        given(openAiUsageRecordRepository.findAllByOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(records));
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
        given(openAiUsageRecordRepository.findAllByOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(records));
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
        given(openAiUsageRecordRepository.findAllByOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(records));
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.of(new FxSnapshotDto("USD", "KRW", 1400d, Instant.now())));

        var dashboard = openAiUsageReportService.getDashboard();

        assertThat(dashboard.recentUsdTotal()).isEqualByComparingTo("0.0000");
        assertThat(dashboard.recentKrwTotal()).isEqualByComparingTo("0");
        assertThat(dashboard.hasUnpricedRecords()).isTrue();
        assertThat(dashboard.recentRecords().get(0).estimatedCostAvailable()).isFalse();
    }

    @Test
    @DisplayName("dashboard should request recent records with page size 10 and newest-first order")
    void getDashboard_requestsRecentRecordsWithFixedPagination() {
        List<OpenAiUsageRecord> records = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 100, 20, Instant.now())
        );
        given(openAiUsageRecordRepository.findAllByOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(records, org.springframework.data.domain.PageRequest.of(1, 10), 11));
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(records);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        openAiUsageReportService.getDashboard(2);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(openAiUsageRecordRepository).findAllByOrderByTimestampDesc(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("timestamp")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("timestamp").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }

    @Test
    @DisplayName("dashboard should expose recent record pagination metadata")
    void getDashboard_exposesRecentRecordPaginationMetadata() {
        Instant now = Instant.now();
        List<OpenAiUsageRecord> secondPageRecords = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 100, 20, now.minus(20, ChronoUnit.MINUTES))
        );
        given(openAiUsageRecordRepository.findAllByOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(secondPageRecords, org.springframework.data.domain.PageRequest.of(1, 10), 21));
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(secondPageRecords);
        given(marketDataFacade.getUsdKrw()).willReturn(Optional.empty());

        var dashboard = openAiUsageReportService.getDashboard(2);

        assertThat(dashboard.recentRecordTotalCount()).isEqualTo(21);
        assertThat(dashboard.recentRecordCurrentPage()).isEqualTo(2);
        assertThat(dashboard.recentRecordTotalPages()).isEqualTo(3);
        assertThat(dashboard.recentRecordHasPreviousPage()).isTrue();
        assertThat(dashboard.recentRecordHasNextPage()).isTrue();
        assertThat(dashboard.recentRecords()).hasSize(1);
    }

    @Test
    @DisplayName("dashboard should clamp oversized page requests to the last available page")
    void getDashboard_clampsOversizedPageRequests() {
        Instant now = Instant.now();
        List<OpenAiUsageRecord> lastPageRecords = List.of(
                record("gpt-4o-mini", OpenAiUsageFeatureType.MACRO_INTERPRETATION, 100, 20, now.minus(40, ChronoUnit.MINUTES))
        );
        given(openAiUsageRecordRepository.findAllByOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(4, 10), 21),
                        new PageImpl<>(lastPageRecords, org.springframework.data.domain.PageRequest.of(2, 10), 21));
        given(openAiUsageRecordRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(lastPageRecords);
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
        given(openAiUsageRecordRepository.findAllByOrderByTimestampDesc(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(4, 10), 0));
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

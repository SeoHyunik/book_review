package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.MarketType;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.openai.OpenAiUsageLoggingService;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiMarketSummaryServiceTest {

    @Mock
    private RecentMarketSummaryService recentMarketSummaryService;

    @Mock
    private ExternalApiUtils externalApiUtils;

    @Mock
    private MarketForecastQueryService marketForecastQueryService;

    @Mock
    private OpenAiUsageLoggingService openAiUsageLoggingService;

    private AiMarketSummaryService aiMarketSummaryService;

    @BeforeEach
    void setUp() {
        aiMarketSummaryService = new AiMarketSummaryService(
                recentMarketSummaryService,
                marketForecastQueryService,
                externalApiUtils,
                new ObjectMapper(),
                openAiUsageLoggingService
        );
        ReflectionTestUtils.setField(aiMarketSummaryService, "clock",
                Clock.fixed(Instant.parse("2026-03-17T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        ReflectionTestUtils.setField(aiMarketSummaryService, "aiEnabled", true);
        ReflectionTestUtils.setField(aiMarketSummaryService, "aiModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(aiMarketSummaryService, "aiWindowHours", 3);
        ReflectionTestUtils.setField(aiMarketSummaryService, "aiMaxItems", 10);
        ReflectionTestUtils.setField(aiMarketSummaryService, "aiMinItems", 3);
        ReflectionTestUtils.setField(aiMarketSummaryService, "aiMaxInputChars", 12000);
        ReflectionTestUtils.setField(aiMarketSummaryService, "aiCacheMinutes", 15);
        ReflectionTestUtils.setField(aiMarketSummaryService, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(aiMarketSummaryService, "openAiUrl", "https://api.openai.com/v1/chat/completions");
        ReflectionTestUtils.setField(aiMarketSummaryService, "openAiMaxTokens", 800);
        ReflectionTestUtils.setField(aiMarketSummaryService, "openAiTemperature", 0.2d);
        ReflectionTestUtils.setField(aiMarketSummaryService, "promptFile", new ByteArrayResource("""
                {
                  "messages": [
                    {"role": "system", "content": "synthesize"},
                    {"role": "user", "template": "Items:\\n{{newsItemsJson}}"}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("enough recent analyzed items with AI success should produce synthesized summary")
    void getCurrentSummary_returnsSynthesizedSummaryWhenAiSucceeds() {
        given(recentMarketSummaryService.loadRecentAnalyzedNews(3, 10))
                .willReturn(List.of(
                        newsEvent("news-1", "2026-03-01T02:30:00Z", "2026-03-17T02:30:00Z"),
                        newsEvent("news-2", "2026-03-02T02:30:00Z", "2026-03-17T02:20:00Z"),
                        newsEvent("news-3", "2026-03-03T02:30:00Z", "2026-03-17T02:10:00Z")
                ));
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"headlineKo\\":\\"\\uCD5C\\uADFC \\uAC70\\uC2DC \\uC2E0\\uD638\\uB294 \\uBC29\\uC5B4\\uC801\\uC73C\\uB85C \\uAE30\\uC6B8\\uACE0 \\uC788\\uC2B5\\uB2C8\\uB2E4\\",\\"headlineEn\\":\\"Recent macro signals lean defensive\\",\\"summaryKo\\":\\"\\uCD5C\\uADFC \\uD5E4\\uB4DC\\uB77C\\uC778\\uC744 \\uC885\\uD569\\uD558\\uBA74 \\uB2EC\\uB7EC\\uC640 \\uBCC0\\uB3D9\\uC131 \\uBD80\\uB2F4\\uC774 \\uB3CB\\uBCF4\\uC785\\uB2C8\\uB2E4.\\",\\"summaryEn\\":\\"Across the recent cluster, USD and volatility are reinforcing a more defensive tone.\\",\\"dominantSentiment\\":\\"NEGATIVE\\",\\"keyDrivers\\":[\\"USD\\",\\"Volatility\\"],\\"marketViewKo\\":\\"\\uB2E8\\uAE30\\uC801\\uC73C\\uB85C\\uB294 \\uBC29\\uC5B4\\uC801 \\uD574\\uC11D\\uC774 \\uC720\\uB9AC\\uD569\\uB2C8\\uB2E4.\\",\\"marketViewEn\\":\\"Near term, a defensive interpretation remains appropriate.\\",\\"supportingNewsIds\\":[\\"news-1\\",\\"news-2\\"],\\"confidence\\":0.78}"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 100,
                    "completion_tokens": 50,
                    "total_tokens": 150
                  }
                }
                """));

        Optional<com.example.macronews.dto.FeaturedMarketSummaryDto> result = aiMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().aiSynthesized()).isTrue();
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.NEGATIVE);
        assertThat(result.get().marketViewEn()).isEqualTo("Near term, a defensive interpretation remains appropriate.");
        verify(openAiUsageLoggingService).recordUsage(
                eq(com.example.macronews.domain.OpenAiUsageFeatureType.MARKET_SUMMARY),
                eq("gpt-4o-mini"),
                any());
    }

    @Test
    @DisplayName("AI disabled should keep synthesized summary unavailable")
    void getCurrentSummary_returnsEmptyWhenAiDisabled() {
        ReflectionTestUtils.setField(aiMarketSummaryService, "aiEnabled", false);

        assertThat(aiMarketSummaryService.getCurrentSummary()).isEmpty();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("invalid AI JSON should keep synthesized summary unavailable")
    void getCurrentSummary_returnsEmptyWhenParsingFails() {
        given(recentMarketSummaryService.loadRecentAnalyzedNews(3, 10))
                .willReturn(List.of(
                        newsEvent("news-1", "2026-03-01T02:30:00Z", "2026-03-17T02:30:00Z"),
                        newsEvent("news-2", "2026-03-02T02:30:00Z", "2026-03-17T02:20:00Z"),
                        newsEvent("news-3", "2026-03-03T02:30:00Z", "2026-03-17T02:10:00Z")
                ));
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "not-json"
                      }
                    }
                  ]
                }
                """));

        assertThat(aiMarketSummaryService.getCurrentSummary()).isEmpty();
    }

    @Test
    @DisplayName("not enough recent items should keep synthesized summary unavailable")
    void getCurrentSummary_returnsEmptyWhenRecentItemsInsufficient() {
        given(recentMarketSummaryService.loadRecentAnalyzedNews(3, 10))
                .willReturn(List.of(
                        newsEvent("news-1", "2026-03-01T02:30:00Z", "2026-03-17T02:30:00Z"),
                        newsEvent("news-2", "2026-03-02T02:30:00Z", "2026-03-17T02:20:00Z")
                ));

        assertThat(aiMarketSummaryService.getCurrentSummary()).isEmpty();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("getCurrentSummary should return empty when the OpenAI request times out")
    void givenTimeoutResponse_whenGetCurrentSummary_thenReturnEmptySummary() {
        given(recentMarketSummaryService.loadRecentAnalyzedNews(3, 10))
                .willReturn(List.of(
                        newsEvent("news-1", "2026-03-01T02:30:00Z", "2026-03-17T02:30:00Z"),
                        newsEvent("news-2", "2026-03-02T02:30:00Z", "2026-03-17T02:20:00Z"),
                        newsEvent("news-3", "2026-03-03T02:30:00Z", "2026-03-17T02:10:00Z")
                ));
        given(marketForecastQueryService.getCurrentSummaryHandoff()).willReturn(Optional.empty());
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(504, "External API request timed out"));

        assertThat(aiMarketSummaryService.getCurrentSummary()).isEmpty();
        verify(openAiUsageLoggingService, never()).recordUsage(any(), any(), any());
    }

    private NewsEvent newsEvent(String id, String publishedAt, String analyzedAt) {
        return new NewsEvent(
                id,
                null,
                "Title " + id,
                "Summary " + id,
                "Source",
                "https://example.com/" + id,
                Instant.parse(publishedAt),
                Instant.parse(analyzedAt),
                com.example.macronews.domain.NewsStatus.ANALYZED,
                new AnalysisResult(
                        "test-model",
                        Instant.parse(analyzedAt),
                        "headline ko",
                        "headline en",
                        "summary ko",
                        "summary en",
                        List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d)),
                        List.of(new MarketImpact(MarketType.KOSPI, ImpactDirection.UP, 0.6d))
                ),
                null,
                null
        );
    }
}

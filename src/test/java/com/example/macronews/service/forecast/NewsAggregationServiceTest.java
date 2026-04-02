package com.example.macronews.service.forecast;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.openai.OpenAiUsageLoggingService;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NewsAggregationServiceTest {

    @Mock
    private NewsEventRepository newsEventRepository;

    @Mock
    private ExternalApiUtils externalApiUtils;

    @Mock
    private OpenAiUsageLoggingService openAiUsageLoggingService;

    @Mock
    private MarketDataFacade marketDataFacade;

    private NewsAggregationService newsAggregationService;

    @BeforeEach
    void setUp() {
        newsAggregationService = new NewsAggregationService(
                newsEventRepository,
                externalApiUtils,
                new ObjectMapper(),
                openAiUsageLoggingService,
                marketDataFacade
        );
        ReflectionTestUtils.setField(newsAggregationService, "forecastEnabled", true);
        ReflectionTestUtils.setField(newsAggregationService, "windowHours", 3);
        ReflectionTestUtils.setField(newsAggregationService, "maxNewsItems", 20);
        ReflectionTestUtils.setField(newsAggregationService, "cacheMinutes", 15);
        ReflectionTestUtils.setField(newsAggregationService, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(newsAggregationService, "openAiUrl", "https://api.openai.com/v1/chat/completions");
        ReflectionTestUtils.setField(newsAggregationService, "openAiModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(newsAggregationService, "openAiMaxTokens", 800);
        ReflectionTestUtils.setField(newsAggregationService, "openAiTemperature", 0.2d);
        ReflectionTestUtils.setField(newsAggregationService, "forecastPromptFile", new ByteArrayResource("""
                {
                  "messages": [
                    {"role": "system", "content": "aggregate"},
                    {"role": "user", "template": "Items:\\n{{newsItemsJson}}"}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8)));
        lenient().when(marketDataFacade.getUs10y()).thenReturn(java.util.Optional.empty());
        lenient().when(marketDataFacade.getDxy()).thenReturn(java.util.Optional.empty());
    }

    @Test
    @DisplayName("buildPayload should include recent analyzed news context")
    void buildPayload_includesRecentNewsContext() {
        String payload = newsAggregationService.buildPayload(List.of(
                newsEvent("news-1", "KOSPI rises"),
                newsEvent("news-2", "Dollar eases")
        ), "");

        assertThat(payload).contains("KOSPI rises");
        assertThat(payload).contains("Dollar eases");
        assertThat(payload).contains("summaryKo");
        assertThat(payload).contains("macroImpacts");
        assertThat(payload).doesNotContain("Current market context:");
    }

    @Test
    @DisplayName("parseSnapshot should default invalid mood to CLOUD")
    void parseSnapshot_defaultsInvalidMoodToCloud() {
        MarketForecastSnapshotDto snapshot = newsAggregationService.parseSnapshot("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"mood\\":\\"storm\\",\\"headlineKo\\":\\"Headline KO\\",\\"headlineEn\\":\\"Headline EN\\",\\"summaryKo\\":\\"Summary KO\\",\\"summaryEn\\":\\"Summary EN\\",\\"keyDrivers\\":[\\"driver-1\\"],\\"relatedNewsIds\\":[\\"news-1\\"],\\"relatedNewsTitles\\":[\\"KOSPI rises\\"],\\"macroDirections\\":{\\"USD\\":\\"DOWN\\"}}"
                      }
                    }
                  ]
                }
                """, List.of(newsEvent("news-1", "KOSPI rises")));

        assertThat(snapshot.mood().name()).isEqualTo("CLOUD");
        assertThat(snapshot.relatedNewsIds()).containsExactly("news-1");
        assertThat(snapshot.macroDirections().get(MacroVariable.USD)).isEqualTo(ImpactDirection.DOWN);
    }

    @Test
    @DisplayName("getCurrentSnapshot should fall back to empty when recent analyzed news is insufficient")
    void getCurrentSnapshot_returnsEmptyWhenInsufficientNews() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(newsEvent("news-1", "Only one item")));

        assertThat(newsAggregationService.getCurrentSnapshot()).isEmpty();
        verifyNoInteractions(externalApiUtils);
    }

    @Test
    @DisplayName("getCurrentSnapshot should keep news-only payload when all market data is missing")
    void getCurrentSnapshot_usesNewsOnlyPayloadWhenAllMarketDataMissing() throws Exception {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(recentNews());
        given(marketDataFacade.getUsdKrw()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getGold()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getOil()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getKospi()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getUs10y()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getDxy()).willReturn(java.util.Optional.empty());
        given(externalApiUtils.callAPI(any(ExternalApiRequest.class)))
                .willReturn(successfulForecastResponse());

        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);

        assertThat(newsAggregationService.getCurrentSnapshot()).isPresent();

        org.mockito.Mockito.verify(externalApiUtils).callAPI(requestCaptor.capture());
        JsonNode payload = new ObjectMapper().readTree(requestCaptor.getValue().body());
        assertThat(payload.path("messages")).hasSize(2);
        assertThat(payload.path("messages").get(1).path("content").asText()).contains("Items:");
        assertThat(payload.path("messages").get(1).path("content").asText()).doesNotContain("Current market context:");
    }

    @Test
    @DisplayName("getCurrentSnapshot should append only available market values")
    void getCurrentSnapshot_appendsOnlyAvailableMarketValues() throws Exception {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(recentNews());
        given(marketDataFacade.getUsdKrw())
                .willReturn(java.util.Optional.of(new FxSnapshotDto("USD", "KRW", 1350.2d, Instant.now())));
        given(marketDataFacade.getGold()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getOil())
                .willReturn(java.util.Optional.of(new OilSnapshotDto(78.3d, null, Instant.now())));
        given(marketDataFacade.getKospi())
                .willReturn(java.util.Optional.of(new IndexSnapshotDto("KOSPI", 2685.4d, Instant.now())));
        given(marketDataFacade.getUs10y())
                .willReturn(java.util.Optional.of(new Us10ySnapshotDto(
                        4.21d,
                        Instant.parse("2026-03-17T00:00:00Z").atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                        "FRED",
                        "DGS10"
                )));
        given(marketDataFacade.getDxy())
                .willReturn(java.util.Optional.of(new DxySnapshotDto(
                        103.45d,
                        Instant.parse("2026-03-17T00:00:00Z"),
                        "TWELVE_DATA_SYNTHETIC",
                        "ICE_DXY_BASKET",
                        true
                )));
        given(externalApiUtils.callAPI(any(ExternalApiRequest.class)))
                .willReturn(successfulForecastResponse());

        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);

        assertThat(newsAggregationService.getCurrentSnapshot()).isPresent();

        org.mockito.Mockito.verify(externalApiUtils).callAPI(requestCaptor.capture());
        JsonNode payload = new ObjectMapper().readTree(requestCaptor.getValue().body());
        JsonNode root = payload;
        String userContent = root.path("messages").get(1).path("content").asText();
        assertThat(root.path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(root.path("max_tokens").asInt()).isEqualTo(800);
        assertThat(root.path("temperature").asDouble()).isEqualTo(0.2d);
        assertThat(root.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(userContent).contains("Items:");
        assertThat(userContent).contains("Current market context:");
        assertThat(userContent).contains("- USD/KRW: 1350.2");
        assertThat(userContent).contains("- WTI: 78.3");
        assertThat(userContent).contains("- KOSPI: 2685.4");
        assertThat(userContent).contains("- US 10Y: 4.2% (FRED DGS10)");
        assertThat(userContent).contains("- DXY: 103.5 (TWELVE_DATA_SYNTHETIC synthetic, ICE_DXY_BASKET)");
        assertThat(userContent).doesNotContain("- Gold:");
        assertThat(userContent).doesNotContain("- Brent:");
        verify(openAiUsageLoggingService).recordUsage(
                eq(com.example.macronews.domain.OpenAiUsageFeatureType.MARKET_FORECAST),
                eq("gpt-4o-mini"),
                any());
    }

    @Test
    @DisplayName("getCurrentSnapshot should return empty when the OpenAI request times out")
    void givenTimeoutResponse_whenGetCurrentSnapshot_thenReturnEmptySnapshot() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(recentNews());
        given(marketDataFacade.getUsdKrw()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getGold()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getOil()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getKospi()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getUs10y()).willReturn(java.util.Optional.empty());
        given(marketDataFacade.getDxy()).willReturn(java.util.Optional.empty());
        given(externalApiUtils.callAPI(any(ExternalApiRequest.class)))
                .willReturn(new ExternalApiResult(504, "External API request timed out"));

        assertThat(newsAggregationService.getCurrentSnapshot()).isEmpty();
        verifyNoInteractions(openAiUsageLoggingService);
    }

    @Test
    @DisplayName("getCurrentSnapshot should fail open when marketDataFacade throws")
    void getCurrentSnapshot_continuesWhenMarketDataFacadeThrows() throws Exception {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(recentNews());
        given(marketDataFacade.getUsdKrw()).willThrow(new RuntimeException("market api down"));
        given(externalApiUtils.callAPI(any(ExternalApiRequest.class)))
                .willReturn(successfulForecastResponse());

        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);

        assertThatCode(() -> newsAggregationService.getCurrentSnapshot())
                .doesNotThrowAnyException();
        assertThat(newsAggregationService.getCurrentSnapshot()).isPresent();

        org.mockito.Mockito.verify(externalApiUtils, org.mockito.Mockito.atLeastOnce()).callAPI(requestCaptor.capture());
        assertThat(requestCaptor.getValue().body()).doesNotContain("Current market context:");
    }

    private List<NewsEvent> recentNews() {
        return List.of(
                newsEvent("news-1", "KOSPI rises"),
                newsEvent("news-2", "Dollar eases")
        );
    }

    private ExternalApiResult successfulForecastResponse() {
        return new ExternalApiResult(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"mood\\":\\"sun\\",\\"headlineKo\\":\\"Headline KO\\",\\"headlineEn\\":\\"Headline EN\\",\\"summaryKo\\":\\"Summary KO\\",\\"summaryEn\\":\\"Summary EN\\",\\"keyDrivers\\":[\\"driver-1\\"],\\"relatedNewsIds\\":[\\"news-1\\"],\\"relatedNewsTitles\\":[\\"KOSPI rises\\"],\\"macroDirections\\":{\\"USD\\":\\"DOWN\\"}}"
                      }
                    }
                  ]
                }
                """);
    }

    private NewsEvent newsEvent(String id, String title) {
        return new NewsEvent(
                id,
                "external-" + id,
                title,
                "Summary",
                "Reuters",
                "https://example.com/" + id,
                Instant.now(),
                Instant.now(),
                NewsStatus.ANALYZED,
                new AnalysisResult(
                        "test-model",
                        Instant.now(),
                        "Headline KO",
                        "Headline EN",
                        "Summary KO",
                        "Summary EN",
                        List.of(new MacroImpact(MacroVariable.KOSPI, ImpactDirection.UP, 0.8d)),
                        List.of()
                ),
                null,
                null
        );
    }
}

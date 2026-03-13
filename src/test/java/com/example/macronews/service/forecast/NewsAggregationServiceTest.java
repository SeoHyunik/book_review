package com.example.macronews.service.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private NewsAggregationService newsAggregationService;

    @BeforeEach
    void setUp() {
        newsAggregationService = new NewsAggregationService(newsEventRepository, externalApiUtils, new ObjectMapper());
        ReflectionTestUtils.setField(newsAggregationService, "forecastEnabled", true);
        ReflectionTestUtils.setField(newsAggregationService, "windowHours", 3);
        ReflectionTestUtils.setField(newsAggregationService, "maxNewsItems", 20);
        ReflectionTestUtils.setField(newsAggregationService, "cacheMinutes", 15);
        ReflectionTestUtils.setField(newsAggregationService, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(newsAggregationService, "openAiUrl", "https://api.openai.com/v1/chat/completions");
        ReflectionTestUtils.setField(newsAggregationService, "openAiModel", "gpt-4o");
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
    }

    @Test
    @DisplayName("buildPayload should include recent analyzed news context")
    void buildPayload_includesRecentNewsContext() {
        String payload = newsAggregationService.buildPayload(List.of(
                newsEvent("news-1", "KOSPI rises"),
                newsEvent("news-2", "Dollar eases")
        ));

        assertThat(payload).contains("KOSPI rises");
        assertThat(payload).contains("Dollar eases");
        assertThat(payload).contains("summaryKo");
        assertThat(payload).contains("macroImpacts");
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
        org.mockito.BDDMockito.given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(newsEvent("news-1", "Only one item")));

        assertThat(newsAggregationService.getCurrentSnapshot()).isEmpty();
        verifyNoInteractions(externalApiUtils);
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
                        "Summary KO",
                        "Summary EN",
                        List.of(new MacroImpact(MacroVariable.KOSPI, ImpactDirection.UP, 0.8d)),
                        List.of()
                )
        );
    }
}


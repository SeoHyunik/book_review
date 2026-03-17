package com.example.macronews.service.news.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GNewsSourceProviderTest {

    @Mock
    private ExternalApiUtils externalApiUtils;

    private GNewsSourceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GNewsSourceProvider(externalApiUtils, new ObjectMapper());
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "baseUrl", "https://gnews.io/api/v4/search");
        ReflectionTestUtils.setField(provider, "apiKey", "gnews-key");
        ReflectionTestUtils.setField(provider, "query", "market OR stocks");
        ReflectionTestUtils.setField(provider, "maxAgeHours", 24L);
        ReflectionTestUtils.setField(provider, "fallbackMaxAgeHours", 36L);
        ReflectionTestUtils.setField(provider, "clock",
                Clock.fixed(Instant.parse("2026-03-17T03:30:00Z"), ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("GNews provider should parse a basic response")
    void fetchTopHeadlines_parsesBasicResponse() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "articles": [
                    {
                      "title": "Market tone firms",
                      "description": "Fed and oil remain in focus",
                      "url": "https://gnews.example.com/1",
                      "publishedAt": "2026-03-17T02:45:00Z",
                      "source": { "name": "GNews Source" }
                    }
                  ]
                }
                """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).source()).isEqualTo("GNews Source");
        assertThat(results.get(0).url()).isEqualTo("https://gnews.example.com/1");
    }

    @Test
    @DisplayName("GNews semi-fresh bucket should allow items outside the fresh window but inside the fallback window")
    void fetchTopHeadlines_allowsSemiFreshItemsInsideFallbackWindow() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "articles": [
                    {
                      "title": "Earlier market recap",
                      "description": "Still usable for fallback",
                      "url": "https://gnews.example.com/2",
                      "publishedAt": "2026-03-15T20:00:00Z",
                      "source": { "name": "GNews Source" }
                    }
                  ]
                }
                """));

        assertThat(provider.fetchTopHeadlines(5)).isEmpty();
        assertThat(provider.fetchTopHeadlines(5, NewsFreshnessBucket.SEMI_FRESH))
                .extracting(ExternalNewsItem::url)
                .containsExactly("https://gnews.example.com/2");
    }
}

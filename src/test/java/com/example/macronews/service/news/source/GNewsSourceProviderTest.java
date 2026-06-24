package com.example.macronews.service.news.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Test
    @DisplayName("GNews provider should fail open when the external request times out")
    void givenTimeoutResponse_whenFetchTopHeadlines_thenReturnEmptyList() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(504, "External API request timed out"));

        assertThat(provider.fetchTopHeadlines(5)).isEmpty();
    }

    @Test
    @DisplayName("GNews request URL should use a single simple query and never a boolean OR expression")
    void fetchTopHeadlines_usesSimpleQueryWithoutBooleanOrExpression() {
        ReflectionTestUtils.setField(provider, "query",
                "stock market, federal reserve, inflation, oil prices, semiconductors");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                { "articles": [] }
                """));

        provider.fetchTopHeadlines(5);

        // The very first attempt must be the first simple query, and no captured URL may contain an
        // " OR " token in its q parameter (the prior broad expression that GNews rejected with 400).
        List<String> urls = decodedRequestUrls();
        assertThat(urls.get(0)).contains("q=stock market");
        assertThat(urls).allSatisfy(url -> assertThat(url).doesNotContain(" OR "));
    }

    @Test
    @DisplayName("GNews provider should split a legacy boolean OR query into simple queries")
    void fetchTopHeadlines_splitsLegacyOrQueryIntoSimpleQueries() {
        ReflectionTestUtils.setField(provider, "query", "market OR stocks OR inflation");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                { "articles": [] }
                """));

        provider.fetchTopHeadlines(5);

        List<String> urls = decodedRequestUrls();
        assertThat(urls).allSatisfy(url -> assertThat(url).doesNotContain(" OR "));
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("q=market"));
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("q=stocks"));
        assertThat(urls).anySatisfy(url -> assertThat(url).contains("q=inflation"));
    }

    @Test
    @DisplayName("GNews provider should skip without any remote call when the query config is blank")
    void fetchTopHeadlines_skipsWhenQueryBlank() {
        ReflectionTestUtils.setField(provider, "query", "   ");

        assertThat(provider.fetchTopHeadlines(5)).isEmpty();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("GNews provider should return empty on an HTTP 400 upstream rejection")
    void fetchTopHeadlines_returnsEmptyOnHttp400() {
        ReflectionTestUtils.setField(provider, "query", "stock market");
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(400, "Bad Request"));

        assertThat(provider.fetchTopHeadlines(5)).isEmpty();
        verify(externalApiUtils, times(1)).callAPI(any());
    }

    @Test
    @DisplayName("GNews provider should try the next query when the first one is rejected with 400")
    void fetchTopHeadlines_triesNextQueryAfterHttp400() {
        ReflectionTestUtils.setField(provider, "query", "stock market, federal reserve");
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(400, "Bad Request"))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "articles": [
                            {
                              "title": "Fed holds rates",
                              "description": "Policy update",
                              "url": "https://gnews.example.com/fed",
                              "publishedAt": "2026-03-17T02:45:00Z",
                              "source": { "name": "GNews Source" }
                            }
                          ]
                        }
                        """));

        List<ExternalNewsItem> results = provider.fetchTopHeadlines(5);

        assertThat(results)
                .extracting(ExternalNewsItem::url)
                .containsExactly("https://gnews.example.com/fed");
        // The first (rejected) query and the second (successful) query are both attempted.
        verify(externalApiUtils, times(2)).callAPI(any());
        assertThat(decodedRequestUrls())
                .anySatisfy(url -> assertThat(url).contains("q=stock market"))
                .anySatisfy(url -> assertThat(url).contains("q=federal reserve"));
    }

    @Test
    @DisplayName("GNews provider should stop immediately on HTTP 429 without trying more queries")
    void fetchTopHeadlines_stopsOnHttp429() {
        ReflectionTestUtils.setField(provider, "query", "stock market, federal reserve");
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(429, "Too Many Requests"));

        assertThat(provider.fetchTopHeadlines(5)).isEmpty();
        // A 429 is terminal: the remaining candidate queries must not be attempted.
        verify(externalApiUtils, times(1)).callAPI(any());
    }

    @Test
    @DisplayName("GNews request URL should keep the API key masked in the sanitized log form")
    void sanitizeUrl_masksApiKey() {
        String sanitized = (String) ReflectionTestUtils.invokeMethod(provider, "sanitizeUrl",
                "https://gnews.io/api/v4/search?q=stock%20market&apikey=gnews-key");

        assertThat(sanitized).contains("apikey=***");
        assertThat(sanitized).doesNotContain("gnews-key");
    }

    private List<String> decodedRequestUrls() {
        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);
        verify(externalApiUtils, atLeastOnce()).callAPI(requestCaptor.capture());
        return requestCaptor.getAllValues().stream()
                .map(request -> URLDecoder.decode(request.url(), StandardCharsets.UTF_8))
                .toList();
    }
}

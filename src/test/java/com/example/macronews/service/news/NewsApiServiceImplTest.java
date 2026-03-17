package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NewsApiServiceImplTest {

    @Mock
    private ExternalApiUtils externalApiUtils;

    @InjectMocks
    private NewsApiServiceImpl newsApiService;

    @Test
    @DisplayName("fetchForeignTopHeadlines should return recent search results first when enough articles exist")
    void fetchForeignTopHeadlines_prefersRecentSearchWhenEnoughResultsExist() {
        setUpDefaults();

        Instant newest = Instant.now().minusSeconds(60 * 60);
        Instant fresh = Instant.now().minusSeconds(60 * 90);
        String body = "{" +
                "\"articles\":[" +
                articleJson("Fresh Source 1", "Newest fed article", "Latest summary", "https://example.com/newest", newest.toString()) + "," +
                articleJson("Fresh Source 2", "Fresh market article", "Fresh summary", "https://example.com/fresh", fresh.toString()) +
                "]}";

        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, body));

        var results = newsApiService.fetchForeignTopHeadlines(2);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(item -> item.url())
                .containsExactly("https://example.com/newest", "https://example.com/fresh");
        verify(externalApiUtils, times(1)).callAPI(any());
    }

    @Test
    @DisplayName("fetchTopHeadlines should encode the recent query before calling NewsAPI")
    void fetchTopHeadlines_encodesRecentQueryParameter() {
        setUpDefaults();
        ReflectionTestUtils.setField(newsApiService, "recentQuery", "market OR stocks OR economy");

        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, "{\"articles\":[]}"));

        newsApiService.fetchTopHeadlines(1);

        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);
        verify(externalApiUtils, atLeastOnce()).callAPI(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(ExternalApiRequest::url)
                .anySatisfy(url -> assertThat(url).contains("q=market%20OR%20stocks%20OR%20economy"));
    }

    @Test
    @DisplayName("fetchForeignTopHeadlines should merge top headlines only when recent search underfills")
    void fetchForeignTopHeadlines_mergesRecentAndHeadlineResults() {
        setUpDefaults();

        Instant recent = Instant.now().minusSeconds(60 * 60);
        Instant headline = Instant.now().minusSeconds(60 * 70);
        String searchBody = "{" +
                "\"articles\":[" +
                articleJson("Search Source", "Recent market article", "Summary", "https://example.com/search", recent.toString()) +
                "]}";
        String headlineBody = "{" +
                "\"articles\":[" +
                articleJson("Headline Source", "Headline fed article", "Summary", "https://example.com/headline", headline.toString()) +
                "]}";

        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, searchBody))
                .willReturn(new ExternalApiResult(200, headlineBody));

        var results = newsApiService.fetchForeignTopHeadlines(2);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(item -> item.url())
                .containsExactly("https://example.com/search", "https://example.com/headline");
    }

    @Test
    @DisplayName("parseArticles should keep only fresh articles that match configured macro keywords")
    void fetchForeignTopHeadlines_filtersArticlesByKeywordInTitleOrDescription() {
        setUpDefaults();

        Instant recent = Instant.now().minusSeconds(60 * 60);
        String body = "{" +
                "\"articles\":[" +
                articleJson("Relevant Title", "Fed signals patience", "General summary", "https://example.com/title", recent.toString()) + "," +
                articleJson("Relevant Description", "Company update", "KOSPI volatility rises", "https://example.com/description", recent.toString()) + "," +
                articleJson("Irrelevant", "Celebrity profile", "Entertainment roundup", "https://example.com/irrelevant", recent.toString()) +
                "]}";

        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, body));

        var results = newsApiService.fetchForeignTopHeadlines(5);

        assertThat(results).extracting(item -> item.url())
                .containsExactly("https://example.com/title", "https://example.com/description");
    }

    @Test
    @DisplayName("parseArticles should fail open when filter keywords configuration is blank")
    void fetchForeignTopHeadlines_doesNotFilterEverythingWhenKeywordsAreBlank() {
        setUpDefaults();
        ReflectionTestUtils.setField(newsApiService, "filterKeywords", " ,  ");

        Instant recent = Instant.now().minusSeconds(60 * 60);
        String body = "{" +
                "\"articles\":[" +
                articleJson("General", "Company update", "Entertainment roundup", "https://example.com/general", recent.toString()) +
                "]}";

        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, body));

        var results = newsApiService.fetchForeignTopHeadlines(1);

        assertThat(results).extracting(item -> item.url())
                .containsExactly("https://example.com/general");
    }

    @Test
    @DisplayName("parseArticles should skip stale global articles beyond configured max age")
    void fetchForeignTopHeadlines_skipsStaleGlobalArticles() {
        setUpDefaults();

        Instant stale = Instant.now().minusSeconds(60L * 60L * 30L);
        String body = "{" +
                "\"articles\":[" +
                articleJson("Old Source", "Fed recap", "Old summary", "https://example.com/old", stale.toString()) +
                "]}";

        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, body));

        var results = newsApiService.fetchForeignTopHeadlines(5);

        assertThat(results).isEmpty();
    }

    private void setUpDefaults() {
        ReflectionTestUtils.setField(newsApiService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(newsApiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(newsApiService, "defaultLimit", 10);
        ReflectionTestUtils.setField(newsApiService, "recencyHours", 48L);
        ReflectionTestUtils.setField(newsApiService, "globalMaxAgeHours", 24L);
        ReflectionTestUtils.setField(newsApiService, "recentQuery", "market OR stocks");
        ReflectionTestUtils.setField(newsApiService, "filterKeywords", "fed, kospi, inflation, market");
        ReflectionTestUtils.setField(newsApiService, "searchUrl", "https://newsapi.org/v2/everything");
        ReflectionTestUtils.setField(newsApiService, "baseUrl", "https://newsapi.org/v2/top-headlines");
        ReflectionTestUtils.setField(newsApiService, "country", "us");
        ReflectionTestUtils.setField(newsApiService, "category", "business");
        ReflectionTestUtils.setField(newsApiService, "enabled", true);
    }

    private String articleJson(String source, String title, String description, String url, String publishedAt) {
        return "{" +
                "\"source\":{\"name\":\"" + source + "\"}," +
                "\"title\":\"" + title + "\"," +
                "\"description\":\"" + description + "\"," +
                "\"url\":\"" + url + "\"," +
                "\"publishedAt\":\"" + publishedAt + "\"" +
                "}";
    }
}

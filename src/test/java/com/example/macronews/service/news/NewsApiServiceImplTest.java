package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
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
    @DisplayName("fetchTopHeadlines should prefer fresh recent articles and exclude old ones")
    void fetchTopHeadlines_filtersOutOldArticles() {
        ReflectionTestUtils.setField(newsApiService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(newsApiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(newsApiService, "defaultLimit", 10);
        ReflectionTestUtils.setField(newsApiService, "recencyHours", 48L);
        ReflectionTestUtils.setField(newsApiService, "recentQuery", "market OR stocks");
        ReflectionTestUtils.setField(newsApiService, "searchUrl", "https://newsapi.org/v2/everything");
        ReflectionTestUtils.setField(newsApiService, "baseUrl", "https://newsapi.org/v2/top-headlines");
        ReflectionTestUtils.setField(newsApiService, "country", "us");
        ReflectionTestUtils.setField(newsApiService, "category", "business");

        Instant newest = Instant.now().minusSeconds(60 * 60);
        Instant fresh = Instant.now().minusSeconds(60 * 90);
        Instant old = Instant.now().minusSeconds(60 * 60 * 100);
        String body = "{" +
                "\"articles\":[" +
                articleJson("Fresh Source 1", "Newest article", "Latest summary", "https://example.com/newest", newest.toString()) + "," +
                articleJson("Fresh Source 2", "Fresh article", "Fresh summary", "https://example.com/fresh", fresh.toString()) + "," +
                articleJson("Old Source", "Old article", "Old summary", "https://example.com/old", old.toString()) +
                "]}";

        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, body));

        var results = newsApiService.fetchTopHeadlines(2);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(item -> item.url())
                .containsExactly("https://example.com/newest", "https://example.com/fresh");
    }

    @Test
    @DisplayName("fetchTopHeadlines should encode the recent query before calling NewsAPI")
    void fetchTopHeadlines_encodesRecentQueryParameter() {
        ReflectionTestUtils.setField(newsApiService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(newsApiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(newsApiService, "defaultLimit", 10);
        ReflectionTestUtils.setField(newsApiService, "recencyHours", 48L);
        ReflectionTestUtils.setField(newsApiService, "recentQuery", "market OR stocks OR economy");
        ReflectionTestUtils.setField(newsApiService, "searchUrl", "https://newsapi.org/v2/everything");
        ReflectionTestUtils.setField(newsApiService, "baseUrl", "https://newsapi.org/v2/top-headlines");
        ReflectionTestUtils.setField(newsApiService, "country", "us");
        ReflectionTestUtils.setField(newsApiService, "category", "business");

        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, "{\"articles\":[]}"));

        newsApiService.fetchTopHeadlines(1);

        ArgumentCaptor<ExternalApiRequest> requestCaptor = ArgumentCaptor.forClass(ExternalApiRequest.class);
        verify(externalApiUtils, atLeastOnce()).callAPI(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(ExternalApiRequest::url)
                .anySatisfy(url -> assertThat(url).contains("q=market%20OR%20stocks%20OR%20economy"));
    }

    @Test
    @DisplayName("fetchTopHeadlines should merge recent search with top headlines when recent search underfills")
    void fetchTopHeadlines_mergesRecentAndHeadlineResults() {
        ReflectionTestUtils.setField(newsApiService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(newsApiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(newsApiService, "defaultLimit", 10);
        ReflectionTestUtils.setField(newsApiService, "recencyHours", 48L);
        ReflectionTestUtils.setField(newsApiService, "recentQuery", "market OR stocks");
        ReflectionTestUtils.setField(newsApiService, "searchUrl", "https://newsapi.org/v2/everything");
        ReflectionTestUtils.setField(newsApiService, "baseUrl", "https://newsapi.org/v2/top-headlines");
        ReflectionTestUtils.setField(newsApiService, "country", "us");
        ReflectionTestUtils.setField(newsApiService, "category", "business");

        Instant recent = Instant.now().minusSeconds(60 * 60);
        Instant headline = Instant.now().minusSeconds(60 * 70);
        String searchBody = "{" +
                "\"articles\":[" +
                articleJson("Search Source", "Recent search article", "Summary", "https://example.com/search", recent.toString()) +
                "]}";
        String headlineBody = "{" +
                "\"articles\":[" +
                articleJson("Headline Source", "Headline article", "Summary", "https://example.com/headline", headline.toString()) +
                "]}";

        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, searchBody))
                .willReturn(new ExternalApiResult(200, headlineBody));

        var results = newsApiService.fetchTopHeadlines(2);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(item -> item.url())
                .containsExactly("https://example.com/search", "https://example.com/headline");
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
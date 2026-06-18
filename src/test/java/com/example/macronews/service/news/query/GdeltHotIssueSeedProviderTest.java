package com.example.macronews.service.news.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GdeltHotIssueSeedProviderTest {

    // First two deterministic local seeds, used to assert that any failure path degrades to the
    // stable fallback list defined in the provider.
    private static final String FIRST_FALLBACK_SEED = "Federal Reserve interest rate decision";
    private static final String SECOND_FALLBACK_SEED = "US CPI inflation report";

    @Mock
    private ExternalApiUtils externalApiUtils;

    private GdeltHotIssueSeedProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GdeltHotIssueSeedProvider(externalApiUtils, new ObjectMapper());
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "baseUrl",
                "https://api.gdeltproject.org/api/v2/doc/doc");
        ReflectionTestUtils.setField(provider, "query", "(market OR inflation)");
        ReflectionTestUtils.setField(provider, "timespan", "24h");
        ReflectionTestUtils.setField(provider, "maxRecords", 25);
    }

    @Test
    @DisplayName("Parses GDELT article titles into bounded remote seeds on success")
    void resolveHotIssueSeeds_parsesRemoteResponse() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "articles": [
                    { "title": "Fed signals patience on rate cuts" },
                    { "title": "Oil climbs on supply worries" },
                    { "title": "Chip stocks rally after earnings" }
                  ]
                }
                """));

        List<String> seeds = provider.resolveHotIssueSeeds(3);

        assertThat(seeds).containsExactly(
                "Fed signals patience on rate cuts",
                "Oil climbs on supply worries",
                "Chip stocks rally after earnings");
    }

    @Test
    @DisplayName("Falls back to deterministic local seeds when the upstream times out")
    void resolveHotIssueSeeds_fallsBackOnTimeout() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(504, "External API request timed out after PT30S"));

        List<String> seeds = provider.resolveHotIssueSeeds(5);

        assertThat(seeds).hasSize(5);
        assertThat(seeds).startsWith(FIRST_FALLBACK_SEED, SECOND_FALLBACK_SEED);
    }

    @Test
    @DisplayName("Falls back to deterministic local seeds when the upstream is unavailable")
    void resolveHotIssueSeeds_fallsBackOnFailureStatus() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(503, "service unavailable"));

        List<String> seeds = provider.resolveHotIssueSeeds(4);

        assertThat(seeds).hasSize(4);
        assertThat(seeds).startsWith(FIRST_FALLBACK_SEED);
    }

    @Test
    @DisplayName("Falls back to deterministic local seeds when the response body is malformed")
    void resolveHotIssueSeeds_fallsBackOnMalformedBody() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, "{ this is not valid json"));

        List<String> seeds = provider.resolveHotIssueSeeds(2);

        assertThat(seeds).containsExactly(FIRST_FALLBACK_SEED, SECOND_FALLBACK_SEED);
    }

    @Test
    @DisplayName("Falls back to deterministic local seeds when the article list is empty")
    void resolveHotIssueSeeds_fallsBackOnEmptyArticles() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, "{ \"articles\": [] }"));

        List<String> seeds = provider.resolveHotIssueSeeds(3);

        assertThat(seeds).hasSize(3);
        assertThat(seeds).startsWith(FIRST_FALLBACK_SEED);
    }

    @Test
    @DisplayName("Timeout and failure paths produce identical deterministic fallback seeds")
    void resolveHotIssueSeeds_fallbackIsDeterministicAcrossFailureModes() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(504, "timed out"))
                .willReturn(new ExternalApiResult(503, "unavailable"));

        List<String> timeoutSeeds = provider.resolveHotIssueSeeds(6);
        List<String> failureSeeds = provider.resolveHotIssueSeeds(6);

        assertThat(timeoutSeeds).isEqualTo(failureSeeds);
    }
}

package com.example.macronews.service.news.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private static final String FIRST_FALLBACK_SEED = "Bank of Korea base rate decision";
    private static final String SECOND_FALLBACK_SEED = "US Federal Reserve FOMC rate decision";

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

    @Test
    @DisplayName("Returns deterministic fallback seeds without any remote call when disabled")
    void resolveHotIssueSeeds_whenDisabled_returnsFallbackWithoutRemoteCall() {
        // Mirrors the production not-configured state (app.news.gdelt.enabled=false): the provider
        // must short-circuit to deterministic fallback seeds and never touch the remote endpoint.
        ReflectionTestUtils.setField(provider, "enabled", false);

        List<String> seeds = provider.resolveHotIssueSeeds(5);

        assertThat(seeds).isNotEmpty();
        assertThat(seeds).hasSize(5);
        assertThat(seeds).startsWith(FIRST_FALLBACK_SEED, SECOND_FALLBACK_SEED);
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("Returns not-configured fallback seeds without any remote call when base URL is blank")
    void resolveHotIssueSeeds_whenBaseUrlBlank_returnsNotConfiguredFallback() {
        // A blank base-url is treated as not-configured even when enabled=true, so the provider
        // degrades to fallback seeds without issuing any remote request.
        ReflectionTestUtils.setField(provider, "baseUrl", "");

        List<String> seeds = provider.resolveHotIssueSeeds(3);

        assertThat(seeds).isNotEmpty();
        assertThat(seeds).hasSize(3);
        assertThat(seeds).startsWith(FIRST_FALLBACK_SEED);
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("Extracts article titles as remote seeds from a realistic GDELT DOC 2.0 artlist response")
    void resolveHotIssueSeeds_extractsTitlesFromRealisticGdeltResponse() {
        // Realistic GDELT DOC 2.0 mode=artlist&format=json payload, including the full field set
        // (url, url_mobile, seendate, socialimage, domain, language, sourcecountry) so this test would
        // catch any drift in the expected `articles`/`title` extraction keys.
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "articles": [
                    {
                      "url": "https://www.example.com/fed-holds-rates",
                      "url_mobile": "",
                      "title": "Fed holds interest rates steady amid inflation concerns",
                      "seendate": "20260623T101500Z",
                      "socialimage": "https://www.example.com/a.jpg",
                      "domain": "example.com",
                      "language": "English",
                      "sourcecountry": "United States"
                    },
                    {
                      "url": "https://www.example.org/oil-prices-climb",
                      "url_mobile": "",
                      "title": "Oil prices climb as central bank signals caution",
                      "seendate": "20260623T100000Z",
                      "socialimage": "https://www.example.org/b.jpg",
                      "domain": "example.org",
                      "language": "English",
                      "sourcecountry": "United Kingdom"
                    }
                  ]
                }
                """));

        List<String> seeds = provider.resolveHotIssueSeeds(5);

        assertThat(seeds).containsExactly(
                "Fed holds interest rates steady amid inflation concerns",
                "Oil prices climb as central bank signals caution");
    }

    @Test
    @DisplayName("Falls back when articles are present but every title is blank")
    void resolveHotIssueSeeds_fallsBackWhenArticlesHaveBlankTitles() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "articles": [
                    { "url": "https://www.example.com/a", "title": "" },
                    { "url": "https://www.example.com/b", "title": "   " }
                  ]
                }
                """));

        List<String> seeds = provider.resolveHotIssueSeeds(4);

        assertThat(seeds).hasSize(4);
        assertThat(seeds).startsWith(FIRST_FALLBACK_SEED, SECOND_FALLBACK_SEED);
    }

    @Test
    @DisplayName("Falls back when GDELT returns HTTP 200 with a non-JSON plain-text error body")
    void resolveHotIssueSeeds_fallsBackWhenBodyIsNonJsonTextError() {
        // GDELT commonly answers a rejected/too-broad query with HTTP 200 and a plain-text error
        // instead of JSON; this must degrade to fallback seeds rather than surface zero seeds.
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200,
                "Your query was too short or too broad. Please refine and try again."));

        List<String> seeds = provider.resolveHotIssueSeeds(6);

        assertThat(seeds).hasSize(6);
        assertThat(seeds).startsWith(FIRST_FALLBACK_SEED, SECOND_FALLBACK_SEED);
    }

    @Test
    @DisplayName("Serves cached remote seeds within the success TTL without a second remote call")
    void returnsCachedRemoteSeedsWithinSuccessTtl() {
        // The success cache must short-circuit a closely spaced second resolve so the same ingestion
        // flow does not re-query GDELT for an answer it already has.
        ReflectionTestUtils.setField(provider, "successTtl", "30m");
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, """
                {
                  "articles": [
                    { "title": "Fed signals patience on rate cuts" },
                    { "title": "Oil climbs on supply worries" }
                  ]
                }
                """));

        List<String> first = provider.resolveHotIssueSeeds(3);
        List<String> second = provider.resolveHotIssueSeeds(3);

        assertThat(first).containsExactly(
                "Fed signals patience on rate cuts",
                "Oil climbs on supply worries");
        assertThat(second).isEqualTo(first);
        // Exactly one remote call: the second resolve was served from the success cache.
        verify(externalApiUtils, times(1)).callAPI(any());
    }

    @Test
    @DisplayName("HTTP 429 arms a rate-limit cooldown and skips the remote call while it is active")
    void rateLimitsAfterHttp429AndSkipsRemoteDuringCooldown() {
        // GDELT rate-limits its DOC API with HTTP 429; the first 429 must classify distinctly and arm
        // a cooldown so the next resolve in the same window returns fallback without another call.
        ReflectionTestUtils.setField(provider, "rateLimitCooldown", "60m");
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(429, "rate limit exceeded"));

        List<String> first = provider.resolveHotIssueSeeds(5);
        List<String> second = provider.resolveHotIssueSeeds(5);

        assertThat(first).hasSize(5);
        assertThat(first).startsWith(FIRST_FALLBACK_SEED, SECOND_FALLBACK_SEED);
        assertThat(second).isEqualTo(first);
        // Only the first resolve hit the upstream; the cooldown skipped the remote call on the second.
        verify(externalApiUtils, times(1)).callAPI(any());
    }

    @Test
    @DisplayName("Uses the default rate-limit cooldown because Retry-After headers are not exposed")
    void usesDefaultRateLimitCooldownWhenRetryAfterUnavailable() {
        // The shared ExternalApiResult abstraction exposes only status + body, never response headers,
        // so Retry-After cannot be honoured; the configured default cooldown is the back-off instead.
        // Here a zero-length cooldown proves the window is driven purely by the configured duration:
        // with no cooldown armed, the second resolve issues a fresh remote call.
        ReflectionTestUtils.setField(provider, "rateLimitCooldown", "0s");
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(429, "rate limit exceeded"));

        List<String> first = provider.resolveHotIssueSeeds(4);
        List<String> second = provider.resolveHotIssueSeeds(4);

        assertThat(first).startsWith(FIRST_FALLBACK_SEED);
        assertThat(second).startsWith(FIRST_FALLBACK_SEED);
        // A zero cooldown arms no window, so the second resolve calls the upstream again.
        verify(externalApiUtils, times(2)).callAPI(any());
    }

    @Test
    @DisplayName("Retries the remote call once the rate-limit cooldown has expired")
    void expiresCooldownAndRetriesRemote() {
        ReflectionTestUtils.setField(provider, "rateLimitCooldown", "60m");
        Instant start = Instant.parse("2026-06-24T00:00:00Z");
        ReflectionTestUtils.setField(provider, "clock", Clock.fixed(start, ZoneOffset.UTC));
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(429, "rate limit exceeded"))
                .willReturn(new ExternalApiResult(200, """
                        {
                          "articles": [
                            { "title": "Fed resumes signalling after cooldown" }
                          ]
                        }
                        """));

        List<String> duringCooldown = provider.resolveHotIssueSeeds(3);
        assertThat(duringCooldown).startsWith(FIRST_FALLBACK_SEED);

        // Advance the clock past the 60m cooldown; the next resolve must re-enter the remote call.
        ReflectionTestUtils.setField(provider, "clock",
                Clock.fixed(start.plus(Duration.ofMinutes(61)), ZoneOffset.UTC));
        List<String> afterExpiry = provider.resolveHotIssueSeeds(3);

        assertThat(afterExpiry).containsExactly("Fed resumes signalling after cooldown");
        verify(externalApiUtils, times(2)).callAPI(any());
    }

    @Test
    @DisplayName("Malformed and empty responses keep returning safe fallback seeds")
    void keepsFallbackForMalformedAndEmptyResponses() {
        // A short fallback-ttl window is armed after the first malformed body, so the second resolve in
        // the same cycle returns fallback without re-entering the remote call.
        ReflectionTestUtils.setField(provider, "fallbackTtl", "10m");
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, "Your query was too short or too broad."));

        List<String> first = provider.resolveHotIssueSeeds(6);
        List<String> second = provider.resolveHotIssueSeeds(6);

        assertThat(first).hasSize(6);
        assertThat(first).startsWith(FIRST_FALLBACK_SEED, SECOND_FALLBACK_SEED);
        assertThat(second).isEqualTo(first);
        verify(externalApiUtils, times(1)).callAPI(any());
    }
}

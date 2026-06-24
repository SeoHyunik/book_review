package com.example.macronews.service.news.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit tests for {@link OpenAiMarketIssueSeedProvider}. Every OpenAI interaction is mocked
 * through {@link ExternalApiUtils}; no live OpenAI/web call is ever made. Fixtures cover both the
 * Responses API {@code output_text} shape and the {@code output[].content[].text} shape.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiMarketIssueSeedProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2026-06-24T00:00:00Z");

    @Mock
    private ExternalApiUtils externalApiUtils;

    private OpenAiMarketIssueSeedProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OpenAiMarketIssueSeedProvider(externalApiUtils, new ObjectMapper());
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "apiKey", "openai-key");
        ReflectionTestUtils.setField(provider, "responsesUrl", "https://api.openai.com/v1/responses");
        ReflectionTestUtils.setField(provider, "model", "gpt-5.5");
        ReflectionTestUtils.setField(provider, "maxQueries", 12);
        ReflectionTestUtils.setField(provider, "confidenceThreshold", 0.5);
        ReflectionTestUtils.setField(provider, "successTtl", "45m");
        ReflectionTestUtils.setField(provider, "failureCooldown", "45m");
        ReflectionTestUtils.setField(provider, "dailyCallLimit", 24);
        ReflectionTestUtils.setField(provider, "clock", Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Disabled by default makes no OpenAI call and returns OPENAI_DISABLED")
    void disabledByDefaultDoesNotCallOpenAi() {
        ReflectionTestUtils.setField(provider, "enabled", false);

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_DISABLED);
        assertThat(result.isDynamic()).isFalse();
        assertThat(result.naverQueries()).isEmpty();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("Parses a valid web-search seed response (output_text shape) into dynamic seeds")
    void parsesValidWebSearchSeedResponse() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, outputTextEnvelope(validSingleSeedJson())));

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_WEB_SEARCH);
        assertThat(result.isDynamic()).isTrue();
        assertThat(result.seeds()).hasSize(1);
        assertThat(result.naverQueries()).containsExactly("삼성전자 HBM", "SK하이닉스 실적");
        assertThat(result.evidenceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Parses the Responses API output[].content[].text shape as well")
    void parsesOutputArrayShape() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, outputArrayEnvelope(validSingleSeedJson())));

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_WEB_SEARCH);
        assertThat(result.naverQueries()).containsExactly("삼성전자 HBM", "SK하이닉스 실적");
    }

    @Test
    @DisplayName("Drops a seed that has no source URLs / evidence titles")
    void dropsSeedWithoutEvidence() {
        String inner = """
                {
                  "seeds": [
                    {
                      "topicFamily": "반도체",
                      "issue": "HBM 수요 증가",
                      "naverQueries": ["삼성전자 HBM"],
                      "confidence": 0.8,
                      "evidenceTitles": ["근거 제목"],
                      "sourceUrls": ["https://news.example.com/a"]
                    },
                    {
                      "topicFamily": "환율",
                      "issue": "출처 없는 이슈",
                      "naverQueries": ["원달러 환율"],
                      "confidence": 0.9,
                      "evidenceTitles": [],
                      "sourceUrls": []
                    }
                  ]
                }
                """;
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, outputTextEnvelope(inner)));

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.seeds()).hasSize(1);
        assertThat(result.naverQueries()).containsExactly("삼성전자 HBM");
    }

    @Test
    @DisplayName("Drops a seed whose confidence is below the threshold")
    void dropsLowConfidenceSeed() {
        String inner = """
                {
                  "seeds": [
                    {
                      "topicFamily": "반도체",
                      "issue": "신뢰 높은 이슈",
                      "naverQueries": ["삼성전자 HBM"],
                      "confidence": 0.8,
                      "evidenceTitles": ["근거"],
                      "sourceUrls": ["https://news.example.com/a"]
                    },
                    {
                      "topicFamily": "금리",
                      "issue": "신뢰 낮은 이슈",
                      "naverQueries": ["기준금리 인하"],
                      "confidence": 0.2,
                      "evidenceTitles": ["근거"],
                      "sourceUrls": ["https://news.example.com/b"]
                    }
                  ]
                }
                """;
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, outputTextEnvelope(inner)));

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.seeds()).hasSize(1);
        assertThat(result.naverQueries()).containsExactly("삼성전자 HBM");
    }

    @Test
    @DisplayName("Rejects an OR-syntax query while keeping the valid queries")
    void removesOrRejectsOrQuery() {
        String inner = """
                {
                  "seeds": [
                    {
                      "topicFamily": "반도체",
                      "issue": "반도체 이슈",
                      "naverQueries": ["삼성전자 OR SK하이닉스", "삼성전자 실적"],
                      "confidence": 0.8,
                      "evidenceTitles": ["근거"],
                      "sourceUrls": ["https://news.example.com/a"]
                    }
                  ]
                }
                """;
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, outputTextEnvelope(inner)));

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.naverQueries()).containsExactly("삼성전자 실적");
    }

    @Test
    @DisplayName("Caps and de-duplicates the flattened Naver query list")
    void capsAndDeduplicatesNaverQueries() {
        ReflectionTestUtils.setField(provider, "maxQueries", 3);
        String inner = """
                {
                  "seeds": [
                    {
                      "topicFamily": "증시",
                      "issue": "시장 이슈",
                      "naverQueries": ["삼성전자 실적", "삼성전자 실적", "SK하이닉스 실적", "현대차 판매", "기아 판매"],
                      "confidence": 0.8,
                      "evidenceTitles": ["근거"],
                      "sourceUrls": ["https://news.example.com/a"]
                    }
                  ]
                }
                """;
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, outputTextEnvelope(inner)));

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.naverQueries()).hasSize(3);
        assertThat(result.naverQueries()).doesNotHaveDuplicates();
        assertThat(result.naverQueries()).containsExactly("삼성전자 실적", "SK하이닉스 실적", "현대차 판매");
    }

    @Test
    @DisplayName("Serves a cached successful result within the TTL without a second call")
    void cachesSuccessfulResultWithinTtl() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, outputTextEnvelope(validSingleSeedJson())));

        MarketIssueSeedResult first = provider.resolveMarketIssueSeeds();
        MarketIssueSeedResult second = provider.resolveMarketIssueSeeds();

        assertThat(first.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_WEB_SEARCH);
        assertThat(second.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_CACHED);
        assertThat(second.isDynamic()).isTrue();
        assertThat(second.naverQueries()).isEqualTo(first.naverQueries());
        verify(externalApiUtils, times(1)).callAPI(any());
    }

    @Test
    @DisplayName("Arms a cooldown after a failure and skips the next call")
    void returnsCooldownAfterFailureWithoutCallingAgain() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(500, "Internal Server Error"));

        MarketIssueSeedResult first = provider.resolveMarketIssueSeeds();
        MarketIssueSeedResult second = provider.resolveMarketIssueSeeds();

        assertThat(first.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_FAILED);
        assertThat(second.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_COOLDOWN);
        assertThat(second.isDynamic()).isFalse();
        verify(externalApiUtils, times(1)).callAPI(any());
    }

    @Test
    @DisplayName("A malformed HTTP 200 body returns OPENAI_FAILED")
    void malformedResponseReturnsFailedResult() {
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(200, "this is not json at all {"));

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_FAILED);
        assertThat(result.isDynamic()).isFalse();
        assertThat(result.naverQueries()).isEmpty();
    }

    @Test
    @DisplayName("A missing API key returns a non-dynamic result without any call")
    void missingApiKeyReturnsDisabledOrFailedWithoutCall() {
        ReflectionTestUtils.setField(provider, "apiKey", "");

        MarketIssueSeedResult result = provider.resolveMarketIssueSeeds();

        assertThat(result.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_DISABLED);
        assertThat(result.isDynamic()).isFalse();
        verify(externalApiUtils, never()).callAPI(any());
    }

    @Test
    @DisplayName("No live external call path exists when the provider is disabled")
    void noLiveExternalCall() {
        ReflectionTestUtils.setField(provider, "enabled", false);

        provider.resolveMarketIssueSeeds();
        provider.resolveMarketIssueSeeds();

        verifyNoInteractions(externalApiUtils);
    }

    @Test
    @DisplayName("The daily call budget blocks further calls once the failure cooldown has expired")
    void dailyLimitBlocksFurtherCallsAfterCooldownExpiry() {
        ReflectionTestUtils.setField(provider, "dailyCallLimit", 1);
        ReflectionTestUtils.setField(provider, "failureCooldown", "1m");
        given(externalApiUtils.callAPI(any()))
                .willReturn(new ExternalApiResult(500, "Internal Server Error"));

        // First resolve consumes the single daily call and arms a 1-minute failure cooldown.
        MarketIssueSeedResult first = provider.resolveMarketIssueSeeds();
        assertThat(first.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_FAILED);

        // After the cooldown expires (same UTC day, no cached success), the exhausted daily budget must
        // still prevent another call.
        ReflectionTestUtils.setField(provider, "clock",
                Clock.fixed(FIXED_NOW.plus(Duration.ofMinutes(2)), ZoneOffset.UTC));
        MarketIssueSeedResult second = provider.resolveMarketIssueSeeds();

        assertThat(second.origin()).isEqualTo(MarketIssueSeedOrigin.OPENAI_COOLDOWN);
        assertThat(second.reason()).isEqualTo("daily-limit");
        verify(externalApiUtils, times(1)).callAPI(any());
    }

    private static String validSingleSeedJson() {
        return """
                {
                  "seeds": [
                    {
                      "topicFamily": "반도체",
                      "issue": "HBM 수요 증가",
                      "naverQueries": ["삼성전자 HBM", "SK하이닉스 실적"],
                      "confidence": 0.8,
                      "evidenceTitles": ["삼성전자 HBM 공급 확대"],
                      "sourceUrls": ["https://news.example.com/a"]
                    }
                  ]
                }
                """;
    }

    private static String outputTextEnvelope(String innerJson) {
        try {
            return "{\"output_text\":" + OBJECT_MAPPER.writeValueAsString(innerJson) + "}";
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String outputArrayEnvelope(String innerJson) {
        try {
            String text = OBJECT_MAPPER.writeValueAsString(innerJson);
            return "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":" + text + "}]}]}";
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

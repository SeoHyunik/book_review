package com.example.macronews.service.news.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
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

/**
 * Unit tests for the GDELT -> OpenAI -> curated priority chain. All collaborators are mocked; no live
 * GDELT/OpenAI/Naver call is made.
 */
@ExtendWith(MockitoExtension.class)
class MarketIssueSeedServiceTest {

    private static final Instant SEED_TIME = Instant.parse("2026-03-13T03:30:00Z");

    @Mock
    private GdeltHotIssueSeedProvider gdeltHotIssueSeedProvider;

    @Mock
    private DeterministicQueryGenerator deterministicQueryGenerator;

    @Mock
    private OpenAiMarketIssueSeedProvider openAiMarketIssueSeedProvider;

    private MarketIssueSeedService service;

    @BeforeEach
    void setUp() {
        service = new MarketIssueSeedService(
                gdeltHotIssueSeedProvider, deterministicQueryGenerator, openAiMarketIssueSeedProvider);
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-03-13T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("GDELT REMOTE drives the generator and never consults OpenAI")
    void usesGdeltRemoteBeforeOpenAi() {
        given(gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(10))
                .willReturn(gdeltResult(HotIssueSeedOrigin.REMOTE, "ok", 200));
        given(deterministicQueryGenerator.generateQueries(List.of("federal reserve rate decision"), 10))
                .willReturn(List.of("연준 금리", "코스피 지수"));

        ResolvedMarketIssueQueries resolved = service.resolve();

        assertThat(resolved.source()).isEqualTo("gdelt-dynamic");
        assertThat(resolved.seedOrigin()).isEqualTo("REMOTE");
        assertThat(resolved.generatedQueryCount()).isEqualTo(2);
        assertThat(resolved.queries().get(0)).isEqualTo("연준 금리");
        verifyNoInteractions(openAiMarketIssueSeedProvider);
    }

    @Test
    @DisplayName("GDELT CACHED_REMOTE drives the generator and never consults OpenAI")
    void usesGdeltCachedRemoteBeforeOpenAi() {
        given(gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(10))
                .willReturn(gdeltResult(HotIssueSeedOrigin.CACHED_REMOTE, "cached-remote", -1));
        given(deterministicQueryGenerator.generateQueries(List.of("federal reserve rate decision"), 10))
                .willReturn(List.of("연준 금리"));

        ResolvedMarketIssueQueries resolved = service.resolve();

        assertThat(resolved.source()).isEqualTo("gdelt-cached-dynamic");
        assertThat(resolved.seedOrigin()).isEqualTo("CACHED_REMOTE");
        assertThat(resolved.queries().get(0)).isEqualTo("연준 금리");
        verifyNoInteractions(openAiMarketIssueSeedProvider);
    }

    @Test
    @DisplayName("When GDELT is not dynamic, OpenAI web-search dynamic seeds are used")
    void usesOpenAiWhenGdeltIsNotDynamic() {
        given(gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(10))
                .willReturn(nonDynamicGdelt(HotIssueSeedOrigin.RATE_LIMIT_COOLDOWN, "rate-limit-cooldown"));
        given(openAiMarketIssueSeedProvider.resolveMarketIssueSeeds())
                .willReturn(MarketIssueSeedResult.webSearch(
                        List.of("삼성전자 HBM", "SK하이닉스 실적"), List.of(seed()), SEED_TIME));

        ResolvedMarketIssueQueries resolved = service.resolve();

        assertThat(resolved.source()).isEqualTo("openai-web-search-dynamic");
        assertThat(resolved.seedOrigin()).isEqualTo("OPENAI_WEB_SEARCH");
        assertThat(resolved.evidenceCount()).isEqualTo(1);
        assertThat(resolved.queries().get(0)).isEqualTo("삼성전자 HBM");
        // GDELT was not dynamic, so the deterministic generator must not be consulted.
        verifyNoInteractions(deterministicQueryGenerator);
    }

    @Test
    @DisplayName("OpenAI disabled falls back to the curated Korea-market pack")
    void fallsBackToCuratedWhenOpenAiDisabled() {
        given(gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(10))
                .willReturn(nonDynamicGdelt(HotIssueSeedOrigin.UPSTREAM_FAILURE_COOLDOWN, "upstream-cooldown"));
        given(openAiMarketIssueSeedProvider.resolveMarketIssueSeeds())
                .willReturn(MarketIssueSeedResult.disabled("disabled", SEED_TIME));

        ResolvedMarketIssueQueries resolved = service.resolve();

        assertThat(resolved.source()).isEqualTo("naver-curated-fallback");
        assertThat(resolved.seedOrigin()).isEqualTo("CURATED_FALLBACK");
        assertThat(resolved.generatedQueryCount()).isZero();
        assertThat(resolved.curatedQueryCount()).isEqualTo(NaverCuratedFallbackQueries.QUERIES.size());
        assertThat(resolved.queries()).isEqualTo(NaverCuratedFallbackQueries.QUERIES);
        verifyNoInteractions(deterministicQueryGenerator);
    }

    @Test
    @DisplayName("OpenAI failure falls back to the curated Korea-market pack")
    void fallsBackToCuratedWhenOpenAiFails() {
        given(gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(10))
                .willReturn(nonDynamicGdelt(HotIssueSeedOrigin.FALLBACK, "malformed-body"));
        given(openAiMarketIssueSeedProvider.resolveMarketIssueSeeds())
                .willReturn(MarketIssueSeedResult.failed("upstream-status-500", SEED_TIME));

        ResolvedMarketIssueQueries resolved = service.resolve();

        assertThat(resolved.source()).isEqualTo("naver-curated-fallback");
        assertThat(resolved.reason()).isEqualTo("upstream-status-500");
        assertThat(resolved.queries()).isEqualTo(NaverCuratedFallbackQueries.QUERIES);
    }

    @Test
    @DisplayName("The curated fallback pack excludes polluted queries and includes Korea-market queries")
    void preservesCuratedFallbackPack() {
        given(gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(10))
                .willReturn(nonDynamicGdelt(HotIssueSeedOrigin.RATE_LIMIT_COOLDOWN, "rate-limit-cooldown"));
        given(openAiMarketIssueSeedProvider.resolveMarketIssueSeeds())
                .willReturn(MarketIssueSeedResult.cooldown("failure-cooldown", SEED_TIME));

        ResolvedMarketIssueQueries resolved = service.resolve();

        assertThat(resolved.queries())
                // Bare single-noun queries and the old polluted defaults must be gone.
                .doesNotContain("삼성전자", "SK하이닉스", "원달러", "반도체", "2차전지", "방산",
                        "코스피 지수", "코스닥 지수", "한국은행 기준금리", "미국 연준 금리")
                // Market-context queries must be present.
                .contains("삼성전자 주가", "SK하이닉스 주가", "원달러 환율", "뉴욕증시 마감", "나스닥 마감", "반도체 주가");
    }

    @Test
    @DisplayName("The final merged query list is capped and de-duplicated")
    void capsAndDeduplicatesFinalQueries() {
        given(gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(10))
                .willReturn(gdeltResult(HotIssueSeedOrigin.REMOTE, "ok", 200));
        given(deterministicQueryGenerator.generateQueries(List.of("federal reserve rate decision"), 10))
                .willReturn(List.of("a1", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10"));

        ResolvedMarketIssueQueries resolved = service.resolve();

        assertThat(resolved.queries()).hasSize(24);
        assertThat(resolved.queries()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Resolved result carries enough provenance diagnostics")
    void logsOrReturnsEnoughDiagnostics() {
        given(gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(10))
                .willReturn(nonDynamicGdelt(HotIssueSeedOrigin.RATE_LIMIT_COOLDOWN, "rate-limit-cooldown"));
        given(openAiMarketIssueSeedProvider.resolveMarketIssueSeeds())
                .willReturn(MarketIssueSeedResult.webSearch(List.of("삼성전자 HBM"), List.of(seed()), SEED_TIME));

        ResolvedMarketIssueQueries resolved = service.resolve();

        assertThat(resolved.source()).isNotBlank();
        assertThat(resolved.seedOrigin()).isNotBlank();
        assertThat(resolved.reason()).isNotBlank();
        assertThat(resolved.generatedQueryCount()).isGreaterThan(0);
        assertThat(resolved.curatedQueryCount()).isGreaterThanOrEqualTo(0);
        assertThat(resolved.evidenceCount()).isEqualTo(1);
        verify(openAiMarketIssueSeedProvider).resolveMarketIssueSeeds();
    }

    private static HotIssueSeedResult gdeltResult(HotIssueSeedOrigin origin, String reason, int status) {
        return new HotIssueSeedResult(List.of("federal reserve rate decision"), origin, reason, status,
                false, true, 1, SEED_TIME);
    }

    private static HotIssueSeedResult nonDynamicGdelt(HotIssueSeedOrigin origin, String reason) {
        return new HotIssueSeedResult(
                List.of("Bank of Korea base rate decision"), origin, reason, -1, true, true, 0, SEED_TIME);
    }

    private static MarketIssueSeed seed() {
        return new MarketIssueSeed("반도체", "HBM 수요 증가", List.of("삼성전자 HBM"), 0.8,
                List.of("삼성전자 HBM 공급"), List.of("https://news.example.com/a"));
    }
}

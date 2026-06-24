package com.example.macronews.service.news.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link DeterministicQueryGenerator}. No external calls, mocking, OpenAI usage,
 * or Naver query-path wiring: the generator is exercised directly to confirm deterministic Korean
 * event-phrase mapping, stable defaults, reproducible ordering, limit bounding, and de-duplication.
 */
class DeterministicQueryGeneratorTest {

    // The first stable Korean default query returned whenever no seed keyword matches.
    private static final String FIRST_DEFAULT_QUERY = "한국은행 기준금리 동결";

    // Broad/abstract phrases that previously surfaced stale-and-irrelevant Naver results. The generator
    // must never emit these; specific event/entity phrases replace them.
    private static final List<String> BANNED_BROAD_QUERIES = List.of(
            "금융 시장 동향", "물가 상승률", "기준금리 발표", "국제 유가 동향",
            "주식 시장 전망", "글로벌 증시 동향", "통화정책 방향", "소비자물가 지수",
            "채권 시장 동향", "반도체 업황 전망", "중앙은행 정책", "환율 동향");

    private DeterministicQueryGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DeterministicQueryGenerator();
    }

    @Test
    @DisplayName("Maps a matching English seed keyword to specific Korean event phrases (mapped mode)")
    void generateQueries_mapsKeywordToKoreanEventPhrase() {
        List<String> queries = generator.generateQueries(List.of("US interest rate decision"), 10);

        // "interest rate" now yields concrete central-bank-decision phrases, not the broad "기준금리 발표".
        assertThat(queries).containsExactly("한국은행 기준금리 동결", "금통위 기준금리 결정");
        assertThat(queries).doesNotContainAnyElementsOf(BANNED_BROAD_QUERIES);
    }

    @Test
    @DisplayName("Maps oil and semiconductor seeds to entity-specific Korean queries")
    void generateQueries_mapsOilAndSemiconductorToEntitySpecificQueries() {
        List<String> oilQueries = generator.generateQueries(List.of("WTI Brent crude oil price"), 10);
        assertThat(oilQueries).contains("WTI 유가 상승", "브렌트유 유가 상승");
        assertThat(oilQueries).doesNotContain("국제 유가 동향");

        List<String> chipQueries =
                generator.generateQueries(List.of("Samsung Electronics SK Hynix semiconductor"), 10);
        assertThat(chipQueries).contains("삼성전자 주가 전망", "SK하이닉스 주가 전망", "반도체 수출 증가");
        assertThat(chipQueries).doesNotContain("반도체 업황 전망");
    }

    @Test
    @DisplayName("Orders event-specific phrases ahead of generic index phrases for one seed")
    void generateQueries_eventPhrasePriorityOverGenericNouns() {
        // A single seed matches the specific "inflation" keyword and the generic "market" keyword.
        // Keyword iteration order is stable, so the event-specific phrases must precede the generic one.
        List<String> queries = generator.generateQueries(List.of("inflation roils the market"), 10);

        assertThat(queries).containsExactly("미국 CPI 물가 발표", "미국 PCE 물가 발표", "코스피 마감 시황");
        assertThat(queries.indexOf("미국 CPI 물가 발표")).isLessThan(queries.indexOf("코스피 마감 시황"));
        assertThat(queries).doesNotContainAnyElementsOf(BANNED_BROAD_QUERIES);
    }

    @Test
    @DisplayName("Returns the stable Korean default list when seeds are null")
    void generateQueries_nullSeedsReturnsDefaults() {
        List<String> queries = generator.generateQueries(null, 10);

        assertThat(queries).isNotEmpty();
        assertThat(queries).startsWith(FIRST_DEFAULT_QUERY);
    }

    @Test
    @DisplayName("Returns the stable Korean default list when seeds are empty")
    void generateQueries_emptySeedsReturnsDefaults() {
        List<String> queries = generator.generateQueries(List.of(), 10);

        assertThat(queries).isNotEmpty();
        assertThat(queries).startsWith(FIRST_DEFAULT_QUERY);
    }

    @Test
    @DisplayName("Returns the stable Korean default list when no seed keyword matches")
    void generateQueries_noMatchSeedsReturnsDefaults() {
        List<String> blankOrUnmatched = new ArrayList<>(Arrays.asList(null, "", "   ", "weather forecast"));

        List<String> queries = generator.generateQueries(blankOrUnmatched, 10);

        assertThat(queries).isNotEmpty();
        assertThat(queries).startsWith(FIRST_DEFAULT_QUERY);
    }

    @Test
    @DisplayName("Produces identical ordered output across repeated calls (reproducible)")
    void generateQueries_isDeterministicAcrossRepeatedCalls() {
        List<String> seeds = List.of("Federal Reserve raises interest rate", "oil prices climb");

        List<String> first = generator.generateQueries(seeds, 10);
        List<String> second = generator.generateQueries(seeds, 10);

        assertThat(first).isEqualTo(second);
        // Mapped mode keeps the stable keyword iteration order, independent of seed ordering: the
        // "interest rate" phrases lead, then "federal reserve", then "oil".
        assertThat(first).containsExactly(
                "한국은행 기준금리 동결", "금통위 기준금리 결정",
                "미국 연준 금리 결정", "FOMC 금리 동결",
                "WTI 유가 상승", "브렌트유 유가 상승");
    }

    @Test
    @DisplayName("Bounds the result to the requested limit when many keywords match")
    void generateQueries_boundsResultToLimit() {
        List<String> seeds = List.of("interest rate federal reserve inflation oil dollar");

        List<String> queries = generator.generateQueries(seeds, 2);

        assertThat(queries).hasSize(2);
        // The two leading "interest rate" phrases fill the limit, even mid-keyword-list.
        assertThat(queries).containsExactly("한국은행 기준금리 동결", "금통위 기준금리 결정");
    }

    @Test
    @DisplayName("De-duplicates queries when multiple seeds map to the same Korean phrases")
    void generateQueries_deduplicatesRepeatedPhrases() {
        List<String> seeds = List.of("inflation surges", "more inflation data");

        List<String> queries = generator.generateQueries(seeds, 10);

        // Both seeds map to the same two inflation-release phrases; the result stays de-duplicated.
        assertThat(queries).containsExactly("미국 CPI 물가 발표", "미국 PCE 물가 발표");
    }
}

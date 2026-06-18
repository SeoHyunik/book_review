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
    private static final String FIRST_DEFAULT_QUERY = "미국 기준금리 발표";

    private DeterministicQueryGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DeterministicQueryGenerator();
    }

    @Test
    @DisplayName("Maps a matching English seed keyword to its Korean event phrase (mapped mode)")
    void generateQueries_mapsKeywordToKoreanEventPhrase() {
        List<String> queries = generator.generateQueries(List.of("US interest rate decision"), 10);

        assertThat(queries).containsExactly("기준금리 발표");
    }

    @Test
    @DisplayName("Orders event-specific phrases ahead of generic noun-list phrases for one seed")
    void generateQueries_eventPhrasePriorityOverGenericNouns() {
        // A single seed matches the specific "inflation" keyword and the generic "market" keyword.
        // Keyword iteration order is stable, so the event-specific phrase must precede the generic one.
        List<String> queries = generator.generateQueries(List.of("inflation roils the market"), 10);

        assertThat(queries).containsExactly("물가 상승률", "금융 시장 동향");
        assertThat(queries.indexOf("물가 상승률")).isLessThan(queries.indexOf("금융 시장 동향"));
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
        // Mapped mode keeps the stable keyword iteration order, independent of seed ordering.
        assertThat(first).containsExactly("기준금리 발표", "연준 통화정책", "국제 유가 동향");
    }

    @Test
    @DisplayName("Bounds the result to the requested limit when many keywords match")
    void generateQueries_boundsResultToLimit() {
        List<String> seeds = List.of("interest rate federal reserve inflation oil dollar");

        List<String> queries = generator.generateQueries(seeds, 2);

        assertThat(queries).hasSize(2);
        assertThat(queries).containsExactly("기준금리 발표", "연준 통화정책");
    }

    @Test
    @DisplayName("De-duplicates queries when multiple seeds map to the same Korean phrase")
    void generateQueries_deduplicatesRepeatedPhrases() {
        List<String> seeds = List.of("inflation surges", "more inflation data");

        List<String> queries = generator.generateQueries(seeds, 10);

        assertThat(queries).containsExactly("물가 상승률");
    }
}

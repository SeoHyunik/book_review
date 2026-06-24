package com.example.macronews.service.news.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Quality guards for the curated Korea-market fallback query pack. These keep the pack focused on
 * market-context queries (not bare single nouns that hook stale lifestyle/entertainment articles),
 * within the query budget, and OR-syntax free.
 */
class NaverCuratedFallbackQueriesTest {

    private static final List<String> POLLUTED_BARE_NOUNS = List.of(
            "삼성전자", "SK하이닉스", "원달러", "반도체", "2차전지", "방산");

    @Test
    @DisplayName("Bare single-noun queries are not present in the curated pack")
    void doesNotContainBareSingleNounQueries() {
        assertThat(NaverCuratedFallbackQueries.QUERIES).doesNotContainAnyElementsOf(POLLUTED_BARE_NOUNS);
    }

    @Test
    @DisplayName("Market-context queries are included")
    void containsMarketContextQueries() {
        assertThat(NaverCuratedFallbackQueries.QUERIES).contains(
                "삼성전자 주가",
                "원달러 환율");
        // At least one SK Hynix and one US-index market-context query must be present.
        assertThat(NaverCuratedFallbackQueries.QUERIES).containsAnyOf("SK하이닉스 주가", "SK하이닉스 HBM");
        assertThat(NaverCuratedFallbackQueries.QUERIES).containsAnyOf("뉴욕증시 마감", "나스닥 마감");
        assertThat(NaverCuratedFallbackQueries.QUERIES).containsAnyOf("코스피 외국인", "코스피 기관");
    }

    @Test
    @DisplayName("Query count stays within the 18-24 budget")
    void queryCountWithinBudget() {
        assertThat(NaverCuratedFallbackQueries.QUERIES.size()).isBetween(18, 24);
    }

    @Test
    @DisplayName("No query uses boolean OR syntax and each stays short")
    void noOrSyntaxAndShortQueries() {
        assertThat(NaverCuratedFallbackQueries.QUERIES).allSatisfy(query -> {
            assertThat(query).doesNotContain(" OR ");
            assertThat(query.trim().split("\\s+")).hasSizeLessThanOrEqualTo(4);
        });
        assertThat(NaverCuratedFallbackQueries.QUERIES).doesNotHaveDuplicates();
    }
}

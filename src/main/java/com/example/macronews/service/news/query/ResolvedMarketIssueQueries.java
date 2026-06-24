package com.example.macronews.service.news.query;

import java.time.Instant;
import java.util.List;

/**
 * Final Naver query list resolved by {@link MarketIssueSeedService}, together with provenance for
 * observability. The consuming provider (Naver) only issues {@link #queries()} and logs the rest; it
 * never re-derives the priority decision.
 *
 * @param queries             the ordered, de-duplicated, capped Korean Naver search queries
 * @param source              query-source label (gdelt-dynamic / gdelt-cached-dynamic /
 *                            openai-web-search-dynamic / openai-cached-dynamic / naver-curated-fallback)
 * @param seedOrigin          underlying seed origin name (GDELT_REMOTE / OPENAI_WEB_SEARCH /
 *                            CURATED_FALLBACK / ...)
 * @param reason              short machine-readable reason carried from the winning seed source
 * @param generatedQueryCount number of dynamic (GDELT/OpenAI) queries that lead the final list
 * @param curatedQueryCount   number of curated fallback queries in the final list
 * @param evidenceCount       supporting evidence count (OpenAI source URLs); 0 for GDELT/curated
 * @param seedAgeSeconds      age of the underlying signal in seconds, or -1 when unknown
 * @param generatedAt         when the underlying signal was produced
 */
public record ResolvedMarketIssueQueries(
        List<String> queries,
        String source,
        String seedOrigin,
        String reason,
        int generatedQueryCount,
        int curatedQueryCount,
        int evidenceCount,
        long seedAgeSeconds,
        Instant generatedAt) {
}

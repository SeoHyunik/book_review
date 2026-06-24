package com.example.macronews.service.news.query;

import java.time.Instant;
import java.util.List;

/**
 * Result of resolving OpenAI web-search market issue seeds, carrying the flattened Naver query list
 * together with its provenance so callers can tell a usable dynamic signal from a non-dynamic outcome.
 *
 * <p>Only {@link #isDynamic()} results ({@link MarketIssueSeedOrigin#OPENAI_WEB_SEARCH} /
 * {@link MarketIssueSeedOrigin#OPENAI_CACHED}) should drive Naver queries; disabled/cooldown/failed
 * results carry an empty query list and signal the caller to use its own fallback.
 *
 * @param naverQueries  flattened, validated, de-duplicated, capped Korean Naver search queries
 * @param seeds         the per-issue seeds backing {@code naverQueries} (evidence for observability)
 * @param origin        where the result came from
 * @param reason        short machine-readable reason label
 * @param generatedAt   when the underlying signal was produced (original fetch time for cached results)
 * @param dynamic       whether the result is a usable dynamic signal
 * @param evidenceCount total number of supporting source URLs across the retained seeds
 */
public record MarketIssueSeedResult(
        List<String> naverQueries,
        List<MarketIssueSeed> seeds,
        MarketIssueSeedOrigin origin,
        String reason,
        Instant generatedAt,
        boolean dynamic,
        int evidenceCount) {

    public boolean isDynamic() {
        return dynamic;
    }

    public static MarketIssueSeedResult disabled(String reason, Instant generatedAt) {
        return new MarketIssueSeedResult(List.of(), List.of(), MarketIssueSeedOrigin.OPENAI_DISABLED,
                reason, generatedAt, false, 0);
    }

    public static MarketIssueSeedResult failed(String reason, Instant generatedAt) {
        return new MarketIssueSeedResult(List.of(), List.of(), MarketIssueSeedOrigin.OPENAI_FAILED,
                reason, generatedAt, false, 0);
    }

    public static MarketIssueSeedResult cooldown(String reason, Instant generatedAt) {
        return new MarketIssueSeedResult(List.of(), List.of(), MarketIssueSeedOrigin.OPENAI_COOLDOWN,
                reason, generatedAt, false, 0);
    }

    public static MarketIssueSeedResult webSearch(List<String> naverQueries, List<MarketIssueSeed> seeds,
            Instant generatedAt) {
        return new MarketIssueSeedResult(List.copyOf(naverQueries), List.copyOf(seeds),
                MarketIssueSeedOrigin.OPENAI_WEB_SEARCH, "ok", generatedAt, true, totalEvidence(seeds));
    }

    public static MarketIssueSeedResult cached(List<String> naverQueries, List<MarketIssueSeed> seeds,
            Instant generatedAt) {
        return new MarketIssueSeedResult(List.copyOf(naverQueries), List.copyOf(seeds),
                MarketIssueSeedOrigin.OPENAI_CACHED, "cached", generatedAt, true, totalEvidence(seeds));
    }

    private static int totalEvidence(List<MarketIssueSeed> seeds) {
        return seeds.stream()
                .mapToInt(seed -> seed.sourceUrls() == null ? 0 : seed.sourceUrls().size())
                .sum();
    }
}

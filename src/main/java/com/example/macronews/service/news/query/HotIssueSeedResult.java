package com.example.macronews.service.news.query;

import java.util.List;
import java.time.Instant;

/**
 * Result of resolving hot-issue seeds, carrying the seed list together with its provenance so callers
 * can tell a real GDELT-derived dynamic signal apart from a synthetic safe fallback.
 *
 * @param seeds         the resolved seed phrases, already bounded to the requested limit (never null)
 * @param origin        where the seeds came from; {@link #isDynamic()} keys off this
 * @param reason        short machine-readable reason label (for example {@code ok},
 *                      {@code cached-remote}, {@code rate-limit-cooldown})
 * @param status        last upstream HTTP status, or {@code -1} when no live call was made
 * @param usedFallback  true when {@code seeds} are synthetic deterministic fallback seeds
 * @param remoteEnabled whether the GDELT remote path is enabled by configuration
 * @param parsedSeeds   number of seeds parsed from the (live or cached) remote signal; 0 for fallback
 * @param generatedAt   when the underlying signal was produced (remote fetch time for cached results),
 *                      used to derive a seed age for observability
 */
public record HotIssueSeedResult(
        List<String> seeds,
        HotIssueSeedOrigin origin,
        String reason,
        int status,
        boolean usedFallback,
        boolean remoteEnabled,
        int parsedSeeds,
        Instant generatedAt) {

    /**
     * A seed list is dynamic only when it reflects a genuine GDELT hot-issue signal (a fresh live
     * response or a cached prior response). Synthetic fallback/cooldown/not-configured seeds are not
     * dynamic and must not be fed to the deterministic Korean query generator.
     */
    public boolean isDynamic() {
        return origin == HotIssueSeedOrigin.REMOTE || origin == HotIssueSeedOrigin.CACHED_REMOTE;
    }
}

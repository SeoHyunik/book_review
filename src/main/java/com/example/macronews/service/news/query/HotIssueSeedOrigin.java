package com.example.macronews.service.news.query;

/**
 * Provenance of a hot-issue seed list produced by {@link GdeltHotIssueSeedProvider}.
 *
 * <p>Only {@link #REMOTE} and {@link #CACHED_REMOTE} represent a genuine GDELT-derived hot-issue
 * signal and may therefore drive dynamic Naver query generation. Every other origin is a synthetic
 * safe fallback that must NOT be treated as a real dynamic signal, so the deterministic generator is
 * bypassed in favour of the curated Naver fallback query pack.
 */
public enum HotIssueSeedOrigin {

    /** Fresh seeds extracted from a successful live GDELT DOC 2.0 response. */
    REMOTE,

    /** Seeds served from a prior successful GDELT response held in the in-memory cache. */
    CACHED_REMOTE,

    /** Synthetic deterministic seeds after a malformed/empty/no-usable-title GDELT response. */
    FALLBACK,

    /** Synthetic deterministic seeds because an HTTP 429 rate-limit cooldown is active. */
    RATE_LIMIT_COOLDOWN,

    /** Synthetic deterministic seeds because a 5xx/timeout upstream-failure cooldown is active. */
    UPSTREAM_FAILURE_COOLDOWN,

    /** Synthetic deterministic seeds because GDELT is disabled or has no base URL configured. */
    NOT_CONFIGURED
}

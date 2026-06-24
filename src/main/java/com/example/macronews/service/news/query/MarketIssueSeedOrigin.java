package com.example.macronews.service.news.query;

/**
 * Provenance of an OpenAI-derived market issue seed snapshot.
 *
 * <p>Only {@link #OPENAI_WEB_SEARCH} and {@link #OPENAI_CACHED} represent a usable dynamic signal
 * (a fresh web-search result or a cached prior web-search result). The remaining values are
 * non-dynamic outcomes where the caller must degrade to its own fallback (in a later slice, the
 * Naver curated fallback query pack). This enum intentionally covers ONLY the OpenAI seed source;
 * GDELT origins and the cross-source priority are unified separately in {@code MarketIssueSeedService}
 * (introduced in G1-c), not here.
 */
public enum MarketIssueSeedOrigin {

    /** Fresh seeds extracted from a successful live OpenAI web-search response. */
    OPENAI_WEB_SEARCH,

    /** Seeds served from a prior successful OpenAI web-search response held in the in-memory cache. */
    OPENAI_CACHED,

    /** The OpenAI seed source is disabled (or not configured), so no call was made. */
    OPENAI_DISABLED,

    /** A failure cooldown or daily-call limit is active, so no call was made. */
    OPENAI_COOLDOWN,

    /** The live call or its response was unusable (non-2xx, malformed, or no usable seeds). */
    OPENAI_FAILED
}

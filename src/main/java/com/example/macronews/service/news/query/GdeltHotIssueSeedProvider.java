package com.example.macronews.service.news.query;

import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Additive provider that derives "hot issue" market-event seed phrases.
 *
 * <p>It queries GDELT's public, key-free DOC 2.0 endpoint for recent macro-relevant article
 * titles and turns them into bounded seed phrases. The shared {@link ExternalApiUtils} HTTP path
 * enforces a bounded timeout, so a slow or unreachable upstream cannot block the caller. On any
 * failure, timeout, or malformed response the provider degrades to a deterministic local seed list
 * so callers always receive a usable, stable set of seeds.
 *
 * <p>GDELT's DOC API is rate limited to protect its underlying Elasticsearch cluster and answers
 * bursts with HTTP 429. To avoid hammering a rate-limited upstream within a single ingestion flow
 * (or across closely spaced flows), this provider keeps a small in-memory cache + cooldown:
 * <ul>
 *   <li>successful remote seeds are cached for {@code cache.success-ttl} and served without a call;
 *   <li>an HTTP 429 arms a {@code cooldown.rate-limit} window classified as {@code rate-limited};
 *   <li>a 5xx/timeout/connect failure arms a shorter {@code cooldown.upstream-failure} window;
 *   <li>a malformed/empty body arms a short {@code cache.fallback-ttl} window so the same cycle does
 *       not re-enter the remote call;
 *   <li>during any active window the remote call is skipped and the safe fallback (or still-valid
 *       cached remote seeds) is returned.
 * </ul>
 *
 * <p>Logging is restricted to counts, booleans, status codes and the resolution reason to avoid
 * leaking upstream content or request details.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GdeltHotIssueSeedProvider {

    private static final int MIN_SEED_LENGTH = 3;
    private static final int MAX_SEED_LENGTH = 100;
    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_CANDIDATE_CAP = 25;
    private static final int HTTP_TOO_MANY_REQUESTS = HttpStatus.TOO_MANY_REQUESTS.value();

    // Safe defaults used whenever a configured cache/cooldown value is blank or unparseable.
    private static final Duration DEFAULT_SUCCESS_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_FALLBACK_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_RATE_LIMIT_COOLDOWN = Duration.ofMinutes(60);
    private static final Duration DEFAULT_UPSTREAM_FAILURE_COOLDOWN = Duration.ofMinutes(15);

    private static final String MODE_REMOTE = "remote";
    private static final String MODE_FALLBACK = "fallback";

    // Cooldown/cache reason labels emitted while a remote call is skipped (distinct from the live
    // classification reasons such as "rate-limited"/"upstream-unavailable" emitted on the call that
    // actually armed the window).
    private static final String REASON_RATE_LIMIT_COOLDOWN = "rate-limit-cooldown";
    private static final String REASON_UPSTREAM_COOLDOWN = "upstream-cooldown";
    private static final String REASON_FALLBACK_CACHED = "fallback-cached";

    // Deterministic local seeds used whenever the remote endpoint is disabled, fails, times out, or
    // returns a malformed/empty payload. Ordering is stable so fallback output is reproducible. Each
    // seed is event/entity-centric (named institutions, indices, tickers and releases) so the
    // downstream deterministic generator maps it to specific Korean market queries rather than broad
    // abstract phrases that surface stale-and-irrelevant Naver results.
    private static final List<String> FALLBACK_SEEDS = List.of(
            "Bank of Korea base rate decision",
            "US Federal Reserve FOMC rate decision",
            "US CPI inflation report",
            "US PCE inflation report",
            "US nonfarm payrolls jobs report",
            "WTI Brent crude oil price",
            "US dollar KRW exchange rate",
            "Samsung Electronics SK Hynix semiconductor",
            "US Treasury yields bond market",
            "KOSPI KOSDAQ stock market close"
    );

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    // Single immutable snapshot swapped atomically; reads/writes are simple, so last-write-wins under
    // concurrent ingestion is acceptable and keeps the locking footprint minimal.
    private final AtomicReference<SeedCacheState> cacheState =
            new AtomicReference<>(SeedCacheState.empty());

    // Injectable for deterministic cooldown/TTL expiry tests; defaults to the system UTC clock.
    private Clock clock = Clock.systemUTC();

    @Value("${app.news.gdelt.enabled:false}")
    private boolean enabled;

    @Value("${app.news.gdelt.base-url:https://api.gdeltproject.org/api/v2/doc/doc}")
    private String baseUrl;

    @Value("${app.news.gdelt.query:(market OR inflation OR \"interest rate\" OR \"central bank\" OR oil OR semiconductor)}")
    private String query;

    @Value("${app.news.gdelt.timespan:24h}")
    private String timespan;

    @Value("${app.news.gdelt.max-records:25}")
    private int maxRecords;

    // Cache/cooldown durations are bound as raw strings and parsed with DurationStyle (mirroring
    // ExternalApiUtils), so a hand-built ApplicationContext without Spring Boot's String->Duration
    // converter still binds them. Field initializers double as safe defaults for unit tests that skip
    // @Value injection entirely.
    @Value("${app.news.gdelt.cache.success-ttl:30m}")
    private String successTtl = "30m";

    @Value("${app.news.gdelt.cache.fallback-ttl:10m}")
    private String fallbackTtl = "10m";

    @Value("${app.news.gdelt.cooldown.rate-limit:60m}")
    private String rateLimitCooldown = "60m";

    @Value("${app.news.gdelt.cooldown.upstream-failure:15m}")
    private String upstreamFailureCooldown = "15m";

    /**
     * Resolves hot-issue seed phrases, preferring the remote GDELT signal and falling back to a
     * deterministic local list on any failure. The returned list is never empty.
     */
    public List<String> resolveHotIssueSeeds(int limit) {
        int resolvedLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        if (!enabled || !StringUtils.hasText(baseUrl)) {
            return fallback(resolvedLimit, false, false, 0, -1, "not-configured");
        }

        Instant now = clock.instant();
        SeedCacheState state = cacheState.get();

        // Fresh successful remote seeds win in every state, including while a cooldown is armed: they
        // are strictly better data than fallback, and serving them costs no remote call.
        if (state.remoteValid(now)) {
            return cachedRemote(state.remoteSeeds(), resolvedLimit);
        }
        // A rate-limit / upstream / fallback cooldown is active: skip the remote call entirely and
        // return the safe fallback. This is what prevents repeated 429s within one ingestion flow.
        if (state.skipActive(now)) {
            return fallback(resolvedLimit, false, false, 0, -1, state.skipReason());
        }

        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                new HttpHeaders(),
                buildRequestUrl(resolvedLimit),
                null
        ));
        int statusCode = result == null ? -1 : result.statusCode();
        boolean httpOk = result != null && statusCode >= 200 && statusCode < 300;
        if (!httpOk) {
            if (statusCode == HTTP_TOO_MANY_REQUESTS) {
                // Distinct rate-limit classification: GDELT is protecting its cluster, so back off for
                // a conservative default window. Retry-After is not honoured because the shared
                // ExternalApiResult abstraction exposes only status + body, never response headers.
                armCooldown(now, resolveDuration(rateLimitCooldown, DEFAULT_RATE_LIMIT_COOLDOWN),
                        REASON_RATE_LIMIT_COOLDOWN);
                return fallback(resolvedLimit, false, false, 0, statusCode, "rate-limited");
            }
            // 5xx, gateway timeout (504), connect failure (503) or any other non-2xx: arm a shorter
            // upstream-failure cooldown so transient outages do not turn into a tight retry loop.
            armCooldown(now, resolveDuration(upstreamFailureCooldown, DEFAULT_UPSTREAM_FAILURE_COOLDOWN),
                    REASON_UPSTREAM_COOLDOWN);
            return fallback(resolvedLimit, false, false, 0, statusCode, "upstream-unavailable");
        }

        SeedParseResult parsed = parseSeeds(result.body(), candidateCap(resolvedLimit));
        if (parsed.seeds().isEmpty()) {
            // Distinguish a GDELT 200 plain-text/HTML error body (malformed-body) from a valid but
            // empty result (no-articles) and from articles whose titles are unusable (no-usable-title),
            // so the post-deploy log pinpoints exactly why remote seeds were not extracted. Arm a short
            // fallback window so the same cycle does not immediately re-enter the remote call.
            boolean parsedJson = parsed.status() != SeedParseStatus.MALFORMED_BODY;
            armCooldown(now, resolveDuration(fallbackTtl, DEFAULT_FALLBACK_TTL), REASON_FALLBACK_CACHED);
            return fallback(resolvedLimit, true, parsedJson, 0, statusCode, parsed.status().reason());
        }

        // Cache the full candidate set (pre-limit) so a later, larger request can still be served from
        // cache; apply the requested limit only on the returned view.
        storeRemoteSeeds(now, parsed.seeds());
        List<String> seeds = parsed.seeds().stream().limit(resolvedLimit).toList();
        log.info("[GDELT] hot-issue seeds mode={} source=remote remoteEnabled=true httpOk=true status={} parsed=true parsedSeeds={} returnedSeeds={} usedFallback=false limit={} reason=ok",
                MODE_REMOTE, statusCode, parsed.seeds().size(), seeds.size(), resolvedLimit);
        return seeds;
    }

    private List<String> cachedRemote(List<String> cached, int limit) {
        List<String> seeds = cached.stream().limit(limit).toList();
        log.info("[GDELT] hot-issue seeds mode={} source=cache remoteEnabled=true parsed=true cachedSeeds={} returnedSeeds={} usedFallback=false limit={} reason=cached-remote",
                MODE_REMOTE, cached.size(), seeds.size(), limit);
        return seeds;
    }

    private void storeRemoteSeeds(Instant now, List<String> seeds) {
        Duration ttl = resolveDuration(successTtl, DEFAULT_SUCCESS_TTL);
        Instant expireAt = now.plus(ttl);
        // A fresh success clears any prior cooldown: the success cache now guards re-entry.
        cacheState.set(new SeedCacheState(List.copyOf(seeds), expireAt, null, null));
    }

    private void armCooldown(Instant now, Duration cooldown, String reason) {
        // A zero/negative cooldown arms no window, so the next resolve re-enters the remote call.
        Instant until = (cooldown == null || cooldown.isZero() || cooldown.isNegative())
                ? null : now.plus(cooldown);
        cacheState.updateAndGet(prev -> new SeedCacheState(
                prev.remoteSeeds(), prev.remoteSeedsExpireAt(), until, reason));
    }

    private Duration resolveDuration(String value, Duration fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            Duration parsed = DurationStyle.detectAndParse(value);
            return parsed == null || parsed.isNegative() ? fallback : parsed;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int candidateCap(int limit) {
        int cap = maxRecords > 0 ? maxRecords : DEFAULT_CANDIDATE_CAP;
        return Math.max(cap, limit);
    }

    private SeedParseResult parseSeeds(String body, int candidateCap) {
        if (!StringUtils.hasText(body) || !looksLikeJsonObject(body)) {
            // GDELT frequently answers a rejected or too-broad query with HTTP 200 and a plain-text or
            // HTML error body instead of JSON; classify a non-JSON body as malformed without logging it.
            return new SeedParseResult(List.of(), SeedParseStatus.MALFORMED_BODY);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode articles = root.path("articles");
            if (!articles.isArray() || articles.isEmpty()) {
                return new SeedParseResult(List.of(), SeedParseStatus.NO_ARTICLES);
            }
            LinkedHashSet<String> seeds = new LinkedHashSet<>();
            for (JsonNode article : articles) {
                String seed = normalizeSeed(article.path("title").asText(""));
                if (seed != null) {
                    seeds.add(seed);
                }
                if (seeds.size() >= candidateCap) {
                    break;
                }
            }
            if (seeds.isEmpty()) {
                // Valid JSON with articles, but no article carried a usable title.
                return new SeedParseResult(List.of(), SeedParseStatus.NO_USABLE_TITLE);
            }
            return new SeedParseResult(List.copyOf(seeds), SeedParseStatus.OK);
        } catch (Exception ex) {
            // JSON-looking prefix but malformed payload; degrade to fallback without dumping the body.
            log.warn("[GDELT] failed to parse hot-issue response; falling back");
            return new SeedParseResult(List.of(), SeedParseStatus.MALFORMED_BODY);
        }
    }

    private boolean looksLikeJsonObject(String body) {
        String trimmed = body.stripLeading();
        return !trimmed.isEmpty() && trimmed.charAt(0) == '{';
    }

    private String normalizeSeed(String rawTitle) {
        if (!StringUtils.hasText(rawTitle)) {
            return null;
        }
        String trimmed = rawTitle.replaceAll("\\s+", " ").trim();
        if (trimmed.length() < MIN_SEED_LENGTH) {
            return null;
        }
        if (trimmed.length() > MAX_SEED_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SEED_LENGTH).trim();
        }
        return trimmed;
    }

    private List<String> fallback(int limit, boolean httpOk, boolean parsed, int parsedSeeds, int statusCode,
            String reason) {
        List<String> seeds = FALLBACK_SEEDS.stream().limit(limit).toList();
        log.info("[GDELT] hot-issue seeds mode={} remoteEnabled={} httpOk={} status={} parsed={} parsedSeeds={} returnedSeeds={} usedFallback={} limit={} reason={}",
                MODE_FALLBACK, enabled, httpOk, statusCode, parsed, parsedSeeds, seeds.size(), true, limit, reason);
        return seeds;
    }

    private enum SeedParseStatus {
        OK("ok"),
        MALFORMED_BODY("malformed-body"),
        NO_ARTICLES("no-articles"),
        NO_USABLE_TITLE("no-usable-title");

        private final String reason;

        SeedParseStatus(String reason) {
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }

    private record SeedParseResult(List<String> seeds, SeedParseStatus status) {
    }

    /**
     * Immutable cache/cooldown snapshot. {@code remoteSeeds}/{@code remoteSeedsExpireAt} hold the last
     * successful (pre-limit) candidate list and its success-TTL expiry; {@code skipRemoteUntil}/
     * {@code skipReason} describe an armed window during which the remote call is skipped.
     */
    private record SeedCacheState(
            List<String> remoteSeeds,
            Instant remoteSeedsExpireAt,
            Instant skipRemoteUntil,
            String skipReason) {

        static SeedCacheState empty() {
            return new SeedCacheState(null, null, null, null);
        }

        boolean remoteValid(Instant now) {
            return remoteSeeds != null && !remoteSeeds.isEmpty()
                    && remoteSeedsExpireAt != null && now.isBefore(remoteSeedsExpireAt);
        }

        boolean skipActive(Instant now) {
            return skipRemoteUntil != null && now.isBefore(skipRemoteUntil);
        }
    }

    private String buildRequestUrl(int limit) {
        int resolvedMaxRecords = maxRecords > 0 ? Math.max(maxRecords, limit) : Math.max(limit, DEFAULT_CANDIDATE_CAP);
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("query", query)
                .queryParam("mode", "artlist")
                .queryParam("format", "json")
                .queryParam("sort", "datedesc")
                .queryParam("timespan", StringUtils.hasText(timespan) ? timespan : "24h")
                .queryParam("maxrecords", resolvedMaxRecords)
                .build()
                .encode()
                .toUriString();
    }
}

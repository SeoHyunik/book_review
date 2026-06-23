package com.example.macronews.service.news.query;

import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * <p>This component is intentionally NOT yet wired into the Naver query path; it only exposes the
 * seed resolution entry point. Logging is restricted to counts, booleans, and the resolution mode
 * to avoid leaking upstream content or request details.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GdeltHotIssueSeedProvider {

    private static final int MIN_SEED_LENGTH = 3;
    private static final int MAX_SEED_LENGTH = 100;
    private static final int DEFAULT_LIMIT = 10;

    private static final String MODE_REMOTE = "remote";
    private static final String MODE_FALLBACK = "fallback";

    // Deterministic local seeds used whenever the remote endpoint is disabled, fails, times out, or
    // returns a malformed/empty payload. Ordering is stable so fallback output is reproducible.
    private static final List<String> FALLBACK_SEEDS = List.of(
            "Federal Reserve interest rate decision",
            "US CPI inflation report",
            "FOMC meeting outcome",
            "US jobs nonfarm payrolls",
            "crude oil price WTI Brent",
            "US dollar index exchange rate",
            "semiconductor stocks outlook",
            "Treasury yields bond market",
            "Powell remarks monetary policy",
            "global equity market selloff"
    );

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

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

    /**
     * Resolves hot-issue seed phrases, preferring the remote GDELT signal and falling back to a
     * deterministic local list on any failure. The returned list is never empty.
     */
    public List<String> resolveHotIssueSeeds(int limit) {
        int resolvedLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        if (!enabled || !StringUtils.hasText(baseUrl)) {
            return fallback(resolvedLimit, false, false, 0, -1, "not-configured");
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
            // Keep the status code so the next log distinguishes 429 (rate-limit), 504 (timeout),
            // 503 (connect failure) and 4xx (rejected query) without dumping the response body.
            return fallback(resolvedLimit, false, false, 0, statusCode, "upstream-unavailable");
        }

        SeedParseResult parsed = parseSeeds(result.body(), resolvedLimit);
        if (parsed.seeds().isEmpty()) {
            // Distinguish a GDELT 200 plain-text/HTML error body (malformed-body) from a valid but
            // empty result (no-articles) and from articles whose titles are unusable (no-usable-title),
            // so the post-deploy log pinpoints exactly why remote seeds were not extracted.
            boolean parsedJson = parsed.status() != SeedParseStatus.MALFORMED_BODY;
            return fallback(resolvedLimit, true, parsedJson, 0, statusCode, parsed.status().reason());
        }

        log.info("[GDELT] hot-issue seeds mode={} remoteEnabled={} httpOk={} status={} parsed=true parsedSeeds={} usedFallback={} limit={}",
                MODE_REMOTE, true, true, statusCode, parsed.seeds().size(), false, resolvedLimit);
        return parsed.seeds();
    }

    private SeedParseResult parseSeeds(String body, int limit) {
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
                if (seeds.size() >= limit) {
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

    private String buildRequestUrl(int limit) {
        int resolvedMaxRecords = maxRecords > 0 ? Math.max(maxRecords, limit) : Math.max(limit, 25);
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

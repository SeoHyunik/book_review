package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.example.macronews.util.external.ExternalResponseTextNormalizer;
import com.example.macronews.util.external.ExternalResponseValueParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
@Slf4j
public class GNewsSourceProvider implements NewsSourceProvider {

    // GNews rejects (HTTP 400) overly broad, multi-term boolean expressions in its `q` parameter, so
    // the query config is treated as a list of short, simple candidate queries that are tried in turn.
    // Entries may arrive comma/semicolon/newline separated (env vs. yaml list flattening); any legacy
    // "a OR b OR c" value is additionally split on the OR token so a stale configuration degrades into
    // individual simple queries instead of being sent verbatim and rejected.
    private static final Pattern QUERY_DELIMITER = Pattern.compile("[,;\\r\\n]+");
    private static final Pattern OR_SPLITTER = Pattern.compile("(?i)\\s+OR\\s+");
    private static final int MAX_QUERIES = 6;
    private static final int MAX_QUERY_LENGTH = 60;

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.news.gnews.enabled:false}")
    private boolean enabled;

    @Value("${app.news.gnews.base-url:https://gnews.io/api/v4/search}")
    private String baseUrl;

    @Value("${app.news.gnews.api-key:}")
    private String apiKey;

    // Simple, short candidate queries tried sequentially. No boolean OR expression is sent to GNews.
    @Value("${app.news.gnews.query:stock market, federal reserve, inflation, oil prices, semiconductors}")
    private String query;

    @Value("${app.news.gnews.lang:en}")
    private String language;

    @Value("${app.news.gnews.country:us}")
    private String country;

    @Value("${app.news.gnews.max-age-hours:24}")
    private long maxAgeHours;

    @Value("${app.news.gnews.fallback-max-age-hours:36}")
    private long fallbackMaxAgeHours;

    private Clock clock = Clock.systemUTC();

    @Override
    public String sourceCode() {
        return "gnews-global";
    }

    @Override
    public boolean supports(NewsFeedPriority priority) {
        return priority == NewsFeedPriority.FOREIGN;
    }

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
        return fetchTopHeadlines(limit, NewsFreshnessBucket.FRESH);
    }

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit, NewsFreshnessBucket bucket) {
        if (!isConfigured()) {
            log.info("[GNEWS] provider empty reason=not-configured enabled={} apiKeyPresent={} configuredBaseUrl={}",
                    enabled, StringUtils.hasText(apiKey), normalizeConfiguredBaseUrl(baseUrl));
            return List.of();
        }
        List<String> queries = resolveQueries();
        if (queries.isEmpty()) {
            log.warn("[GNEWS] query is blank; skipping provider");
            return List.of();
        }

        int resolvedLimit = Math.max(limit, 1);
        String normalizedBaseUrl = normalizeConfiguredBaseUrl(baseUrl);
        String requestFamily = resolveRequestFamily(normalizedBaseUrl);
        int attempted = 0;
        int rejected = 0;
        boolean anyUpstreamOk = false;

        // Try each simple query in turn. A single broad query previously failed the whole provider with
        // HTTP 400; now a 400 only retires that one query and the next candidate is attempted. A 429 is
        // treated as terminal so we never amplify rate-limiting by fanning out across the remaining
        // queries.
        for (String candidate : queries) {
            attempted++;
            String url = buildSearchUrl(normalizedBaseUrl, candidate, resolvedLimit);
            String sanitizedUrl = sanitizeUrl(url);

            ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                    HttpMethod.GET,
                    new HttpHeaders(),
                    url,
                    null
            ));
            int status = result == null ? -1 : result.statusCode();
            boolean httpOk = result != null && status >= 200 && status < 300;
            if (!httpOk) {
                if (status == 429) {
                    log.warn("[GNEWS] provider empty reason=rate-limit bucket={} enabled={} requestFamily={} status={} attemptedQueries={} totalQueries={} configuredBaseUrl={} requestUrl={}",
                            bucket, enabled, requestFamily, status, attempted, queries.size(), normalizedBaseUrl, sanitizedUrl);
                    return List.of();
                }
                rejected++;
                log.warn("[GNEWS] query rejected reason=upstream-rejection bucket={} requestFamily={} status={} queryIndex={} totalQueries={} requestUrl={}",
                        bucket, requestFamily, status, attempted, queries.size(), sanitizedUrl);
                continue;
            }

            anyUpstreamOk = true;
            List<ExternalNewsItem> items = parseArticles(result.body(), resolvedLimit, bucket);
            if (!items.isEmpty()) {
                log.info("[GNEWS] resolved usableItems={} bucket={} requestFamily={} queryIndex={} totalQueries={}",
                        items.size(), bucket, requestFamily, attempted, queries.size());
                return items;
            }
            // HTTP 2xx but nothing usable (empty/stale): move on to the next candidate so the foreign
            // fallback still has a chance to surface at least one item.
        }

        // Every candidate was exhausted. Distinguish an all-rejected outcome (query family unusable)
        // from one where the upstream accepted a query but had no eligible articles.
        String reason = anyUpstreamOk ? "no-eligible-articles" : "upstream-rejection";
        log.warn("[GNEWS] provider empty reason={} bucket={} enabled={} requestFamily={} attemptedQueries={} rejectedQueries={} totalQueries={} configuredBaseUrl={}",
                reason, bucket, enabled, requestFamily, attempted, rejected, queries.size(), normalizedBaseUrl);
        return List.of();
    }

    // Resolves the configured query string into an ordered, de-duplicated list of short simple queries.
    // Splits on list delimiters and defensively on any boolean OR token, trims, caps length, and bounds
    // the total count so the sequential fan-out can never run away.
    private List<String> resolveQueries() {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        LinkedHashSet<String> seenLower = new LinkedHashSet<>();
        List<String> resolved = new ArrayList<>();
        for (String part : QUERY_DELIMITER.split(query)) {
            for (String term : OR_SPLITTER.split(part)) {
                String normalized = normalizeQuery(term);
                if (normalized == null) {
                    continue;
                }
                if (seenLower.add(normalized.toLowerCase(Locale.ROOT)) && resolved.size() < MAX_QUERIES) {
                    resolved.add(normalized);
                }
                if (resolved.size() >= MAX_QUERIES) {
                    return List.copyOf(resolved);
                }
            }
        }
        return List.copyOf(resolved);
    }

    private String normalizeQuery(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.replaceAll("\\s+", " ").trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH).trim();
        }
        return trimmed;
    }

    private String buildSearchUrl(String normalizedBaseUrl, String candidateQuery, int resolvedLimit) {
        return UriComponentsBuilder.fromUriString(normalizedBaseUrl)
                .queryParam("q", candidateQuery)
                .queryParam("lang", language)
                .queryParam("country", country)
                .queryParam("sortby", "publishedAt")
                .queryParam("max", resolvedLimit)
                .queryParam("apikey", apiKey)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    private List<ExternalNewsItem> parseArticles(String body, int limit, NewsFreshnessBucket bucket) {
        if (!StringUtils.hasText(body)) {
            log.info("[GNEWS] provider empty reason=upstream-empty-response bucket={} limit={}", bucket, limit);
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode articles = root.path("articles");
            if (!articles.isArray()) {
                log.info("[GNEWS] provider empty reason=upstream-empty-response bucket={} limit={} articlesArray=false",
                        bucket, limit);
                return List.of();
            }

            Instant cutoff = freshnessCutoff(bucket);
            int skippedNullPublishedAt = 0;
            int skippedStale = 0;
            List<ExternalNewsItem> mapped = new ArrayList<>();
            for (JsonNode article : articles) {
                String source = article.path("source").path("name").asText("");
                String title = article.path("title").asText("");
                String description = article.path("description").asText("");
                String url = article.path("url").asText("");
                Instant publishedAt = ExternalResponseValueParser.parseInstant(article.path("publishedAt").asText(""));
                if (publishedAt == null) {
                    skippedNullPublishedAt++;
                    continue;
                }
                if (publishedAt.isBefore(cutoff)) {
                    skippedStale++;
                    continue;
                }
                String externalId = StringUtils.hasText(url)
                        ? url
                        : (ExternalResponseTextNormalizer.defaultText(source, "GNews")
                        + "|" + ExternalResponseTextNormalizer.defaultText(title, "Untitled")
                        + "|" + publishedAt);
                mapped.add(new ExternalNewsItem(
                        externalId,
                        ExternalResponseTextNormalizer.defaultText(source, "GNews"),
                        ExternalResponseTextNormalizer.defaultText(title, "Untitled"),
                        ExternalResponseTextNormalizer.defaultText(description, ""),
                        ExternalResponseTextNormalizer.defaultText(url, ""),
                        publishedAt
                ));
            }
            List<ExternalNewsItem> limited = mapped.stream()
                    .sorted(Comparator.comparing(ExternalNewsItem::publishedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .toList();
            log.info("[GNEWS] bucket={} parsedItems={} skippedNullPublishedAt={} skippedStale={} limit={}",
                    bucket, limited.size(), skippedNullPublishedAt, skippedStale, limit);
            if (limited.isEmpty() && articles.size() > 0) {
                String reason = skippedStale == articles.size() && skippedStale > 0
                        ? "stale-only-input"
                        : "no-eligible-articles";
                log.info("[GNEWS] provider empty reason={} bucket={} parsedItems=0 skippedNullPublishedAt={} skippedStale={} limit={}",
                        reason, bucket, skippedNullPublishedAt, skippedStale, limit);
            }
            return limited;
        } catch (Exception ex) {
            log.warn("[GNEWS] failed to parse response bucket={}", bucket, ex);
            return List.of();
        }
    }

    private Instant freshnessCutoff(NewsFreshnessBucket bucket) {
        long hours = bucket == NewsFreshnessBucket.SEMI_FRESH
                ? (fallbackMaxAgeHours > 0 ? fallbackMaxAgeHours : 36L)
                : (maxAgeHours > 0 ? maxAgeHours : 24L);
        return Instant.now(clock).minus(Duration.ofHours(hours));
    }

    private String normalizeConfiguredBaseUrl(String configuredBaseUrl) {
        String trimmed = ExternalResponseTextNormalizer.defaultText(configuredBaseUrl, "https://gnews.io/api/v4/search").trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/search")) {
            return trimmed;
        }
        return trimmed + "/search";
    }

    private String sanitizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            return UriComponentsBuilder.fromUriString(url)
                    .replaceQueryParam("apikey", "***")
                    .build()
                    .toUriString();
        } catch (Exception ex) {
            return url.replace(apiKey, "***");
        }
    }

    private String resolveRequestFamily(String normalizedBaseUrl) {
        if (!StringUtils.hasText(normalizedBaseUrl)) {
            return "unknown";
        }
        int lastSlash = normalizedBaseUrl.lastIndexOf('/');
        return lastSlash >= 0 ? normalizedBaseUrl.substring(lastSlash + 1) : normalizedBaseUrl;
    }
}

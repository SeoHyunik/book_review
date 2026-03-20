package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.news.gnews.enabled:false}")
    private boolean enabled;

    @Value("${app.news.gnews.base-url:https://gnews.io/api/v4/search}")
    private String baseUrl;

    @Value("${app.news.gnews.api-key:}")
    private String apiKey;

    @Value("${app.news.gnews.query:market OR stocks OR inflation OR fed OR tariff OR oil OR semiconductor OR usd OR kospi}")
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
            log.info("[GNEWS] provider unavailable enabled={} apiKeyPresent={} configuredBaseUrl={}",
                    enabled, StringUtils.hasText(apiKey), normalizeConfiguredBaseUrl(baseUrl));
            return List.of();
        }
        if (!StringUtils.hasText(query)) {
            log.warn("[GNEWS] query is blank; skipping provider");
            return List.of();
        }

        int resolvedLimit = Math.max(limit, 1);
        String normalizedBaseUrl = normalizeConfiguredBaseUrl(baseUrl);
        String url = UriComponentsBuilder.fromUriString(normalizedBaseUrl)
                .queryParam("q", query)
                .queryParam("lang", language)
                .queryParam("country", country)
                .queryParam("sortby", "publishedAt")
                .queryParam("max", resolvedLimit)
                .queryParam("apikey", apiKey)
                .build()
                .encode()
                .toUriString();
        String sanitizedUrl = sanitizeUrl(url);

        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                new HttpHeaders(),
                url,
                null
        ));
        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            log.warn("[GNEWS] bucket={} enabled={} requestFamily={} status={} configuredBaseUrl={} requestUrl={}",
                    bucket, enabled, resolveRequestFamily(normalizedBaseUrl),
                    result == null ? -1 : result.statusCode(), normalizedBaseUrl, sanitizedUrl);
            return List.of();
        }
        return parseArticles(result.body(), resolvedLimit, bucket);
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    private List<ExternalNewsItem> parseArticles(String body, int limit, NewsFreshnessBucket bucket) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode articles = root.path("articles");
            if (!articles.isArray()) {
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
                Instant publishedAt = parseInstant(article.path("publishedAt").asText(""));
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
                        : (defaultText(source, "GNews") + "|" + defaultText(title, "Untitled") + "|" + publishedAt);
                mapped.add(new ExternalNewsItem(
                        externalId,
                        defaultText(source, "GNews"),
                        defaultText(title, "Untitled"),
                        defaultText(description, ""),
                        defaultText(url, ""),
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

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeConfiguredBaseUrl(String configuredBaseUrl) {
        String trimmed = defaultText(configuredBaseUrl, "https://gnews.io/api/v4/search").trim();
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

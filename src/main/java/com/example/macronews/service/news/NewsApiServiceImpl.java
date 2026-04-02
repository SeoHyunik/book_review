package com.example.macronews.service.news;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.service.news.source.NewsFreshnessBucket;
import com.example.macronews.service.news.source.NewsFeedPriority;
import com.example.macronews.service.news.source.NewsSourceProvider;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.example.macronews.util.external.ExternalResponseTextNormalizer;
import com.example.macronews.util.external.ExternalResponseValueParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsApiServiceImpl implements NewsApiService, NewsSourceProvider {

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.news.global.enabled:true}")
    private boolean enabled;

    @Value("${news.api.base-url:https://newsapi.org/v2/top-headlines}")
    private String baseUrl;

    @Value("${news.api.search-url:https://newsapi.org/v2/everything}")
    private String searchUrl;

    @Value("${news.api.key:}")
    private String apiKey;

    @Value("${news.api.country:us}")
    private String country;

    @Value("${news.api.category:business}")
    private String category;

    @Value("${news.api.default-limit:10}")
    private int defaultLimit;

    @Value("${news.api.recent-query:market OR stocks OR inflation OR fed OR tariff OR oil OR semiconductor OR usd OR kospi OR selloff OR rally}")
    private String recentQuery;

    @Value("${news.api.recent-query-fallback:breaking market OR intraday stocks OR fed OR inflation OR tariff OR oil OR semiconductor OR usd OR kospi}")
    private String recentQueryFallback;

    @Value("${news.api.filter-keywords:korea,kospi,kosdaq,volatility,oil,usd,interest rate,inflation,gold,semiconductor,tariff,fed}")
    private String filterKeywords;

    @Value("${news.api.recency-hours:72}")
    private long recencyHours;

    @Value("${app.news.global.max-age-hours:24}")
    private long globalMaxAgeHours;

    @Value("${app.news.global.fallback-max-age-hours:36}")
    private long globalFallbackMaxAgeHours;

    private volatile String cachedFilterKeywordSource;
    private volatile List<String> cachedFilterKeywords = List.of();
    private final AtomicBoolean emptyFilterKeywordsWarningLogged = new AtomicBoolean(false);

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
        return fetchForeignTopHeadlines(limit, NewsFreshnessBucket.FRESH);
    }

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit, NewsFreshnessBucket bucket) {
        return fetchForeignTopHeadlines(limit, bucket);
    }

    @Override
    public String sourceCode() {
        return "newsapi-global";
    }

    @Override
    public boolean supports(NewsFeedPriority priority) {
        return priority == NewsFeedPriority.FOREIGN;
    }

    @Override
    public List<ExternalNewsItem> fetchDomesticTopHeadlines(int limit) {
        return fetchDomesticTopHeadlines(limit, NewsFreshnessBucket.FRESH);
    }

    public List<ExternalNewsItem> fetchDomesticTopHeadlines(int limit, NewsFreshnessBucket bucket) {
        if (!isConfigured()) {
            log.warn("news.api.key is missing; returning empty top-headlines list");
            return List.of();
        }

        int resolvedLimit = limit > 0 ? limit : defaultLimit;
        NewsApiCycleContext cycleContext = new NewsApiCycleContext("fetchDomesticTopHeadlines");
        List<ExternalNewsItem> freshest = fetchRecentEverything(resolvedLimit, bucket, cycleContext);
        if (freshest.size() >= resolvedLimit) {
            return freshest;
        }

        List<ExternalNewsItem> headlines = fetchFromUrl(buildTopHeadlinesUrl(resolvedLimit), resolvedLimit,
                "top-headlines", bucket, cycleContext);
        return mergeAndLimit(freshest, headlines, resolvedLimit);
    }

    @Override
    public List<ExternalNewsItem> fetchForeignTopHeadlines(int limit) {
        return fetchForeignTopHeadlines(limit, NewsFreshnessBucket.FRESH);
    }

    public List<ExternalNewsItem> fetchForeignTopHeadlines(int limit, NewsFreshnessBucket bucket) {
        if (!isConfigured()) {
            log.warn("news.api.key is missing; returning empty top-headlines list");
            return List.of();
        }

        int resolvedLimit = limit > 0 ? limit : defaultLimit;
        NewsApiCycleContext cycleContext = new NewsApiCycleContext("fetchForeignTopHeadlines");
        List<ExternalNewsItem> freshest = fetchRecentEverything(resolvedLimit, bucket, cycleContext);
        if (freshest.size() >= resolvedLimit) {
            return freshest;
        }

        log.info("Recent foreign NewsAPI bucket={} results returned {} of {} items; supplementing with top-headlines",
                bucket, freshest.size(), resolvedLimit);
        List<ExternalNewsItem> headlines = fetchFromUrl(buildTopHeadlinesUrl(resolvedLimit), resolvedLimit,
                "top-headlines", bucket, cycleContext);
        return mergeAndLimit(freshest, headlines, resolvedLimit);
    }

    @Override
    public Optional<ExternalNewsItem> fetchByUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return Optional.empty();
        }
        return fetchTopHeadlines(defaultLimit * 2).stream()
                .filter(item -> StringUtils.hasText(item.url()))
                .filter(item -> item.url().equals(url))
                .findFirst();
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    private List<ExternalNewsItem> fetchRecentEverything(int limit, NewsFreshnessBucket bucket,
            NewsApiCycleContext cycleContext) {
        if (!StringUtils.hasText(recentQuery)) {
            log.warn("news.api.recent-query is blank; skipping recent NewsAPI search");
            return List.of();
        }
        List<ExternalNewsItem> primary = fetchRecentEverythingQuery(recentQuery, limit, "everything-primary", bucket,
                cycleContext);
        if (primary.size() >= limit) {
            return primary;
        }
        if (!StringUtils.hasText(recentQueryFallback)
                || recentQueryFallback.trim().equalsIgnoreCase(recentQuery.trim())) {
            return primary;
        }

        log.info("Primary recent NewsAPI bucket={} query returned {} of {} items; trying fallback recent query",
                bucket, primary.size(), limit);
        List<ExternalNewsItem> fallback = fetchRecentEverythingQuery(recentQueryFallback, limit, "everything-fallback",
                bucket, cycleContext);
        return mergeAndLimit(primary, fallback, limit);
    }

    private List<ExternalNewsItem> fetchRecentEverythingQuery(String query, int limit, String feedName,
            NewsFreshnessBucket bucket, NewsApiCycleContext cycleContext) {
        Instant cutoff = effectiveGlobalQueryCutoff();
        String url = UriComponentsBuilder.fromUriString(searchUrl)
                .queryParam("q", query)
                .queryParam("sortBy", "publishedAt")
                .queryParam("pageSize", limit)
                .queryParam("from", cutoff.toString())
                .queryParam("apiKey", apiKey)
                .build()
                .encode()
                .toUriString();
        return fetchFromUrl(url, limit, feedName, bucket, cycleContext);
    }

    private String buildTopHeadlinesUrl(int limit) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("country", country)
                .queryParam("category", category)
                .queryParam("pageSize", limit)
                .queryParam("apiKey", apiKey)
                .build()
                .encode()
                .toUriString();
    }

    private List<ExternalNewsItem> fetchFromUrl(String url, int limit, String feedName, NewsFreshnessBucket bucket,
            NewsApiCycleContext cycleContext) {
        String requestFamily = resolveRequestFamily(url);
        if (cycleContext.rateLimitEncountered()) {
            log.info("[NEWSAPI] skipping call after prior 429 cycle={} feed={} requestFamily={} bucket={}",
                    cycleContext.entryPoint(), feedName, requestFamily, bucket);
            return List.of();
        }

        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                new HttpHeaders(),
                url,
                null));

        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            if (result != null && result.statusCode() == 429) {
                cycleContext.markRateLimitEncountered();
                log.warn("[NEWSAPI] 429 received cycle={} feed={} requestFamily={} bucket={} first429InCycle=true",
                        cycleContext.entryPoint(), feedName, requestFamily, bucket);
                return List.of();
            }
            log.warn("{} call failed. status={}", feedName, result == null ? -1 : result.statusCode());
            return List.of();
        }

        return parseArticles(result.body(), limit, bucket);
    }

    private List<ExternalNewsItem> mergeAndLimit(List<ExternalNewsItem> primary, List<ExternalNewsItem> secondary,
            int limit) {
        Map<String, ExternalNewsItem> merged = new LinkedHashMap<>();
        for (ExternalNewsItem item : primary) {
            merged.putIfAbsent(dedupKey(item), item);
        }
        for (ExternalNewsItem item : secondary) {
            merged.putIfAbsent(dedupKey(item), item);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(ExternalNewsItem::publishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
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

            List<ExternalNewsItem> mapped = new ArrayList<>();
            Instant cutoff = globalFreshnessCutoff(bucket);
            List<String> resolvedFilterKeywords = resolveFilterKeywords();
            int skippedNullPublishedAt = 0;
            int skippedStale = 0;
            for (JsonNode article : articles) {
                String source = article.path("source").path("name").asText("");
                String title = article.path("title").asText("");
                String summary = article.path("description").asText("");
                String url = article.path("url").asText("");
                String publishedAt = article.path("publishedAt").asText("");
                Instant publishedInstant = ExternalResponseValueParser.parseInstant(publishedAt);

                if (publishedInstant == null) {
                    skippedNullPublishedAt++;
                    continue;
                }
                if (!isFreshEnough(publishedInstant, cutoff)) {
                    skippedStale++;
                    continue;
                }
                if (!matchesFilterKeywords(title, summary, resolvedFilterKeywords)) {
                    continue;
                }

                String externalId = StringUtils.hasText(url)
                        ? url
                        : (source + "|" + title + "|" + publishedAt).trim();

                mapped.add(new ExternalNewsItem(
                        externalId,
                        ExternalResponseTextNormalizer.defaultText(source, "External NewsAPI"),
                        ExternalResponseTextNormalizer.defaultText(title, "Untitled"),
                        ExternalResponseTextNormalizer.defaultText(summary, ""),
                        ExternalResponseTextNormalizer.defaultText(url, ""),
                        publishedInstant
                ));
            }
            List<ExternalNewsItem> limited = mapped.stream()
                    .sorted(Comparator.comparing(ExternalNewsItem::publishedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .toList();
            if (skippedNullPublishedAt > 0 || skippedStale > 0) {
                log.info("[NEWSAPI] bucket={} parsedItems={} skippedNullPublishedAt={} skippedStale={} limit={}",
                        bucket, limited.size(), skippedNullPublishedAt, skippedStale, limit);
            }
            return limited;
        } catch (Exception ex) {
            log.warn("Failed to parse external news response", ex);
            return List.of();
        }
    }

    private Instant recentCutoff() {
        long resolvedHours = recencyHours > 0 ? recencyHours : 72L;
        return Instant.now().minus(Duration.ofHours(resolvedHours));
    }

    private Instant globalFreshnessCutoff(NewsFreshnessBucket bucket) {
        long resolvedHours = bucket == NewsFreshnessBucket.SEMI_FRESH
                ? (globalFallbackMaxAgeHours > 0 ? globalFallbackMaxAgeHours : 36L)
                : (globalMaxAgeHours > 0 ? globalMaxAgeHours : 24L);
        return Instant.now().minus(Duration.ofHours(resolvedHours));
    }

    private Instant effectiveGlobalQueryCutoff() {
        Instant queryCutoff = recentCutoff();
        Instant freshnessCutoff = globalFreshnessCutoff(NewsFreshnessBucket.FRESH);
        return queryCutoff.isAfter(freshnessCutoff) ? queryCutoff : freshnessCutoff;
    }

    private List<String> resolveFilterKeywords() {
        String rawKeywords = ExternalResponseTextNormalizer.defaultText(filterKeywords, "");
        if (rawKeywords.equals(cachedFilterKeywordSource)) {
            return cachedFilterKeywords;
        }

        synchronized (this) {
            if (rawKeywords.equals(cachedFilterKeywordSource)) {
                return cachedFilterKeywords;
            }

            List<String> resolvedKeywords = Arrays.stream(rawKeywords.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();

            cachedFilterKeywordSource = rawKeywords;
            cachedFilterKeywords = resolvedKeywords;

            if (resolvedKeywords.isEmpty()) {
                if (emptyFilterKeywordsWarningLogged.compareAndSet(false, true)) {
                    log.warn("news.api.filter-keywords is empty; macro relevance filtering will fail open");
                }
            } else {
                emptyFilterKeywordsWarningLogged.set(false);
            }

            return cachedFilterKeywords;
        }
    }

    private boolean isFreshEnough(Instant publishedAt, Instant cutoff) {
        return publishedAt != null && (cutoff == null || !publishedAt.isBefore(cutoff));
    }

    private boolean matchesFilterKeywords(String title, String summary, List<String> resolvedFilterKeywords) {
        if (resolvedFilterKeywords.isEmpty()) {
            return true;
        }

        String combinedText = (ExternalResponseTextNormalizer.defaultText(title, "") + " "
                + ExternalResponseTextNormalizer.defaultText(summary, ""))
                .toLowerCase(Locale.ROOT);
        return resolvedFilterKeywords.stream().anyMatch(combinedText::contains);
    }

    private String dedupKey(ExternalNewsItem item) {
        if (item == null) {
            return "";
        }
        if (StringUtils.hasText(item.url())) {
            return item.url();
        }
        return ExternalResponseTextNormalizer.defaultText(item.externalId(), "");
    }

    private String resolveRequestFamily(String url) {
        if (!StringUtils.hasText(url)) {
            return "unknown";
        }
        try {
            String path = UriComponentsBuilder.fromUriString(url).build().getPath();
            if (!StringUtils.hasText(path)) {
                return "unknown";
            }
            int lastSlash = path.lastIndexOf('/');
            return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private static final class NewsApiCycleContext {
        private final String entryPoint;
        private boolean rateLimitEncountered;

        private NewsApiCycleContext(String entryPoint) {
            this.entryPoint = entryPoint;
        }

        private String entryPoint() {
            return entryPoint;
        }

        private boolean rateLimitEncountered() {
            return rateLimitEncountered;
        }

        private void markRateLimitEncountered() {
            this.rateLimitEncountered = true;
        }
    }
}

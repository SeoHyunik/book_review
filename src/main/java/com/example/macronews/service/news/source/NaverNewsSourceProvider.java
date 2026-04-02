package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
@Slf4j
public class NaverNewsSourceProvider implements NewsSourceProvider {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));
    private static final int NAVER_MAX_DISPLAY = 100;
    private static final int NAVER_MAX_START = 1000;
    private static final int STALE_LOG_SAMPLE_LIMIT = 3;
    private static final int STALE_LOG_TITLE_MAX_LENGTH = 80;
    private static final DateTimeFormatter NAVER_PUB_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final List<DateTimeFormatter> NAVER_PUB_DATE_FALLBACK_FORMATTERS = List.of(
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("EEE, dd MMM yyyy HH:mm:ss Z")
                    .toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("EEE, dd MMM yyyy HH:mm:ss z")
                    .toFormatter(Locale.ENGLISH)
    );
    private static final String KOREAN_BREAKING_MARKER = "\uC18D\uBCF4";
    private static final List<String> RELEVANCE_KEYWORDS = List.of(
            "fomc",
            "cpi",
            "ppi",
            "inflation",
            "rate",
            "fed",
            "powell",
            "yield",
            "oil",
            "wti",
            "brent",
            "dollar",
            "\uAE08\uB9AC",
            "\uBB3C\uAC00",
            "\uC778\uD50C\uB808\uC774\uC158",
            "\uC5F0\uC900",
            "\uD30C\uC6D4",
            "\uD658\uC728",
            "\uC720\uAC00",
            "\uB2EC\uB7EC",
            "\uACE0\uC6A9",
            "\uC99D\uC2DC",
            "\uCF54\uC2A4\uD53C",
            "\uCF54\uC2A4\uB2E5"
    );
    private static final List<String> DEFAULT_QUERIES = List.of(
            "\uCF54\uC2A4\uD53C",
            "\uCF54\uC2A4\uB2E5",
            "\uC6D0\uB2EC\uB7EC \uD658\uC728",
            "\uAE30\uC900\uAE08\uB9AC",
            "\uBBF8\uAD6D\uCC44 \uAE08\uB9AC",
            "\uCC44\uAD8C \uAE08\uB9AC",
            "\uAD6D\uC81C\uC720\uAC00",
            "\uBC18\uB3C4\uCCB4",
            "\uC5F0\uC900",
            "\uBBF8\uAD6D\uAE08\uB9AC",
            "\uBB3C\uAC00 \uBC1C\uD45C",
            "\uACE0\uC6A9 \uBC1C\uD45C",
            "\uB2EC\uB7EC\uC778\uB371\uC2A4"
    );

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.news.naver.enabled:false}")
    private boolean enabled;

    @Value("${app.news.naver.base-url:https://openapi.naver.com}")
    private String baseUrl;

    @Value("${app.news.naver.client-id:}")
    private String clientId;

    @Value("${app.news.naver.client-secret:}")
    private String clientSecret;

    @Value("${app.news.naver.queries:}")
    private String rawQueries;

    @Value("${app.news.naver.display:10}")
    private int display;

    @Value("${app.news.naver.start:1}")
    private int start;

    @Value("${app.news.naver.max-age-hours:12}")
    private long maxAgeHours;

    @Value("${app.news.naver.fallback-max-age-hours:24}")
    private long fallbackMaxAgeHours;

    @Value("${app.news.naver.max-pages:2}")
    private int maxPages;

    private Clock clock = DEFAULT_CLOCK;

    @PostConstruct
    void logConfigurationState() {
        log.info("[NAVER] configuration enabled={} clientIdPresent={} clientSecretPresent={} configured={}",
                enabled, hasClientId(), hasClientSecret(), isConfigured());
    }

    @Override
    public String sourceCode() {
        return "naver";
    }

    @Override
    public boolean supports(NewsFeedPriority priority) {
        return priority == NewsFeedPriority.DOMESTIC;
    }

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
        return fetchTopHeadlines(limit, NewsFreshnessBucket.FRESH);
    }

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit, NewsFreshnessBucket bucket) {
        if (!isConfigured()) {
            log.info("[NAVER] provider disabled or incomplete configuration");
            return List.of();
        }

        int resolvedLimit = limit > 0 ? limit : Math.max(display, 1);
        List<NaverCandidate> candidates = new ArrayList<>();
        List<String> queries = resolveQueries();
        for (String query : queries) {
            candidates.addAll(fetchQuery(query, resolvedLimit, bucket));
            if (deduplicateAndLimit(candidates, resolvedLimit).size() >= resolvedLimit) {
                break;
            }
        }
        List<ExternalNewsItem> merged = deduplicateAndLimit(candidates, resolvedLimit);
        log.info("[NAVER] bucket={} merged usableItems={} requestedLimit={} queries={}",
                bucket, merged.size(), resolvedLimit, queries.size());
        return merged;
    }

    @Override
    public boolean isConfigured() {
        return enabled && hasClientId() && hasClientSecret();
    }

    private List<String> resolveQueries() {
        List<String> configuredQueries = Arrays.stream(rawQueries.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (!configuredQueries.isEmpty()) {
            return configuredQueries;
        }

        log.warn("[NAVER] Naver news queries are empty; using safe defaults. "
                        + "Configure APP_NEWS_NAVER_QUERIES explicitly for production tuning. defaults={}",
                String.join(", ", DEFAULT_QUERIES));
        return DEFAULT_QUERIES;
    }

    private List<NaverCandidate> fetchQuery(String query, int limit, NewsFreshnessBucket bucket) {
        log.info("[NAVER] query start bucket={} query='{}' requestedLimit={}", bucket, query, limit);
        int pageSize = resolveDisplay(limit);
        List<NaverCandidate> collected = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < resolveMaxPages(); pageIndex++) {
            int pageStart = resolvePageStart(pageIndex, pageSize);
            if (pageStart < 0) {
                log.info("[NAVER] stopping paging query='{}' reason=start-out-of-range pageIndex={} pageSize={}",
                        query, pageIndex, pageSize);
                break;
            }
            ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                    HttpMethod.GET,
                    buildHeaders(),
                    buildQueryUrl(query, pageSize, pageStart),
                    null
            ));
            if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
                log.warn("[NAVER] query failed query='{}' pageStart={} status={}",
                        query, pageStart, result == null ? -1 : result.statusCode());
                break;
            }

            NaverParseResult parsed = parseItems(query, pageStart, result.body(), resolveMaxAgeHours(bucket), bucket);
            collected.addAll(parsed.items());
            if (collected.size() >= limit) {
                break;
            }
            if (parsed.rawItemCount() <= 0) {
                break;
            }
        }
        return collected;
    }

    private NaverParseResult parseItems(String query, int pageStart, String body, long maxAgeHours, NewsFreshnessBucket bucket) {
        if (!StringUtils.hasText(body)) {
            log.info("[NAVER] bucket={} query='{}' pageStart={} rawItems=0 bodyEmpty=true", bucket, query, pageStart);
            return new NaverParseResult(List.of(), 0);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                log.info("[NAVER] bucket={} query='{}' pageStart={} rawItems=0 itemsArray=false", bucket, query, pageStart);
                return new NaverParseResult(List.of(), 0);
            }

            int rawItemCount = items.size();
            int invalidPubDateCount = 0;
            int nullPublishedAtCount = 0;
            int staleItemCount = 0;
            int staleLoggedCount = 0;
            int filteredByRelevanceCount = 0;
            int missingUrlCount = 0;
            int emptyTitleCount = 0;
            Instant now = Instant.now(clock);
            Instant cutoff = now.minus(Duration.ofHours(maxAgeHours));
            List<NaverCandidate> mapped = new ArrayList<>();
            for (JsonNode item : items) {
                String cleanedTitle = cleanHtml(item.path("title").asText(""));
                String originalLink = item.path("originallink").asText("");
                String fallbackLink = item.path("link").asText("");
                String cleanedDescription = normalizeNaverDescription(
                        item.path("description").asText(""),
                        originalLink,
                        fallbackLink
                );
                String rawPubDate = item.path("pubDate").asText("");
                Instant publishedAt = parsePubDate(rawPubDate);
                String resolvedUrl = StringUtils.hasText(originalLink) ? originalLink : fallbackLink;
                String dedupTitle = normalizeTitle(cleanedTitle);
                if (StringUtils.hasText(rawPubDate) && publishedAt == null) {
                    invalidPubDateCount++;
                }
                if (publishedAt == null) {
                    nullPublishedAtCount++;
                    continue;
                }
                if (!isFreshEnough(publishedAt, cutoff)) {
                    staleItemCount++;
                    if (staleLoggedCount < STALE_LOG_SAMPLE_LIMIT) {
                        log.info("[NAVER] stale item sample bucket={} query='{}' pageStart={} publishedAt={} cutoff={} ageHours={} title='{}'",
                                bucket, query, pageStart, publishedAt, cutoff, formatAgeHours(publishedAt, now),
                                abbreviateForLog(cleanedTitle));
                        staleLoggedCount++;
                    }
                    continue;
                }
                if (!StringUtils.hasText(resolvedUrl)) {
                    missingUrlCount++;
                }
                if (!StringUtils.hasText(cleanedTitle)) {
                    emptyTitleCount++;
                }

                ExternalNewsItem mappedItem = new ExternalNewsItem(
                        resolveExternalId(resolvedUrl, dedupTitle, rawPubDate),
                        "NAVER",
                        defaultText(cleanedTitle, "Untitled"),
                        defaultText(cleanedDescription, ""),
                        defaultText(resolvedUrl, ""),
                        publishedAt
                );
                mapped.add(new NaverCandidate(mappedItem, originalLink, fallbackLink, dedupTitle));
            }
            log.info("[NAVER] bucket={} query='{}' pageStart={} rawItems={}", bucket, query, pageStart, rawItemCount);
            log.info("[NAVER] bucket={} query='{}' pageStart={} parsedItems={} nullPublishedAt={} invalidPubDate={} staleItems={} filteredByRelevance={} missingUsableLink={} emptyTitle={}",
                    bucket, query, pageStart, mapped.size(), nullPublishedAtCount, invalidPubDateCount, staleItemCount,
                    filteredByRelevanceCount, missingUrlCount, emptyTitleCount);
            return new NaverParseResult(mapped, rawItemCount);
        } catch (Exception ex) {
            log.warn("[NAVER] failed to parse response bucket={} query='{}' pageStart={}", bucket, query, pageStart, ex);
            return new NaverParseResult(List.of(), 0);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Naver-Client-Id", clientId);
        headers.add("X-Naver-Client-Secret", clientSecret);
        return headers;
    }

    private String buildQueryUrl(String query, int pageSize, int pageStart) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/v1/search/news.json")
                .queryParam("query", query)
                .queryParam("display", pageSize)
                .queryParam("start", pageStart)
                .queryParam("sort", "date")
                .build()
                .encode()
                .toUriString();
    }

    private List<ExternalNewsItem> deduplicateAndLimit(List<NaverCandidate> candidates, int limit) {
        Map<String, ExternalNewsItem> deduplicated = new LinkedHashMap<>();
        candidates.stream()
                .sorted(Comparator.comparing((NaverCandidate candidate) -> candidate.item().publishedAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(candidate -> candidate.item().url(), Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(candidate -> deduplicated.putIfAbsent(resolveDedupKey(candidate), candidate.item()));
        return deduplicated.values().stream()
                .limit(limit)
                .toList();
    }

    private String resolveDedupKey(NaverCandidate candidate) {
        String normalizedOriginalLink = normalizeUrl(candidate.originalLink());
        if (StringUtils.hasText(normalizedOriginalLink)) {
            return normalizedOriginalLink;
        }
        String normalizedTitle = candidate.normalizedTitle();
        if (StringUtils.hasText(normalizedTitle)) {
            return normalizedTitle;
        }
        String normalizedFallbackLink = normalizeUrl(candidate.fallbackLink());
        if (StringUtils.hasText(normalizedFallbackLink)) {
            return normalizedFallbackLink;
        }
        return candidate.item().externalId();
    }

    private String cleanHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String stripped = value.replaceAll("(?i)</?b>", "");
        return HtmlUtils.htmlUnescape(stripped).trim();
    }

    private Instant parsePubDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return ZonedDateTime.parse(trimmed, NAVER_PUB_DATE_FORMATTER).toInstant();
        } catch (Exception ex) {
        }
        for (DateTimeFormatter formatter : NAVER_PUB_DATE_FALLBACK_FORMATTERS) {
            try {
                return ZonedDateTime.parse(trimmed, formatter).toInstant();
            } catch (Exception ex) {
                // Continue to the next safe fallback formatter.
            }
        }
        return null;
    }

    private boolean isFreshEnough(Instant publishedAt, long allowedMaxAgeHours) {
        if (publishedAt == null) {
            return false;
        }
        return isFreshEnough(publishedAt, Instant.now(clock).minus(Duration.ofHours(allowedMaxAgeHours)));
    }

    private boolean isFreshEnough(Instant publishedAt, Instant cutoff) {
        if (publishedAt == null) {
            return false;
        }
        return !publishedAt.isBefore(cutoff);
    }

    private int resolveDisplay(int requestedLimit) {
        int configuredDisplay = display > 0 ? display : requestedLimit;
        int resolvedDisplay = Math.max(1, Math.min(Math.max(requestedLimit, 1), configuredDisplay));
        return Math.min(resolvedDisplay, NAVER_MAX_DISPLAY);
    }

    private int resolveStart() {
        int resolvedStart = start > 0 ? start : 1;
        return Math.min(resolvedStart, NAVER_MAX_START);
    }

    private int resolvePageStart(int pageIndex, int pageSize) {
        long resolvedPageSize = Math.max(pageSize, 1);
        long resolvedStart = resolveStart();
        long pageStart = resolvedStart + ((long) pageIndex * resolvedPageSize);
        if (pageStart > NAVER_MAX_START) {
            return -1;
        }
        return (int) pageStart;
    }

    private int resolveMaxPages() {
        return maxPages > 0 ? maxPages : 2;
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim()
                .replaceAll("\\[(?:\\uC18D\\uBCF4|(?i:breaking))]", " ")
                .replace(KOREAN_BREAKING_MARKER, " ")
                .replaceAll("(?i)\\bbreaking\\b", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (!StringUtils.hasText(host)) {
                return trimmed;
            }
            if (!StringUtils.hasText(scheme)) {
                return host + path;
            }
            return scheme + "://" + host + path;
        } catch (IllegalArgumentException ex) {
            return trimmed;
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean hasClientId() {
        return StringUtils.hasText(clientId);
    }

    private boolean hasClientSecret() {
        return StringUtils.hasText(clientSecret);
    }

    private boolean isRelevantForMacroNews(String title, String description) {
        return containsRelevanceKeyword(title) || containsRelevanceKeyword(description);
    }

    private boolean containsRelevanceKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : RELEVANCE_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String formatAgeHours(Instant publishedAt, Instant now) {
        double ageHours = Duration.between(publishedAt, now).toMinutes() / 60.0;
        return String.format(Locale.ROOT, "%.2f", ageHours);
    }

    private String abbreviateForLog(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        if (title.length() <= STALE_LOG_TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, STALE_LOG_TITLE_MAX_LENGTH - 3) + "...";
    }

    private long resolveMaxAgeHours(NewsFreshnessBucket bucket) {
        if (bucket == NewsFreshnessBucket.SEMI_FRESH) {
            return fallbackMaxAgeHours > 0 ? fallbackMaxAgeHours : 24L;
        }
        return maxAgeHours > 0 ? maxAgeHours : 12L;
    }

    private String resolveExternalId(String resolvedUrl, String normalizedTitle, String rawPubDate) {
        if (StringUtils.hasText(resolvedUrl)) {
            return resolvedUrl;
        }
        String titleSeed = StringUtils.hasText(normalizedTitle) ? normalizedTitle : "untitled";
        String dateSeed = StringUtils.hasText(rawPubDate) ? rawPubDate.trim() : "unknown-pubdate";
        return "naver:" + titleSeed + "|" + dateSeed;
    }

    private String normalizeNaverDescription(String rawDescription, String originalLink, String fallbackLink) {
        String cleaned = cleanHtml(rawDescription);
        if (!StringUtils.hasText(cleaned)) {
            return "";
        }
        if (sameText(cleaned, originalLink) || sameText(cleaned, fallbackLink)) {
            return "";
        }
        if (!looksPercentEncoded(cleaned)) {
            return cleaned;
        }
        try {
            String decoded = URLDecoder.decode(cleaned, StandardCharsets.UTF_8);
            if (isReadableDecodedText(cleaned, decoded)) {
                return decoded.trim();
            }
        } catch (Exception ex) {
            log.debug("[NAVER] description decode failed");
        }
        return cleaned;
    }

    private boolean looksPercentEncoded(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        int encodedTriplets = 0;
        for (int i = 0; i < value.length() - 2; i++) {
            if (value.charAt(i) == '%'
                    && isHexCharacter(value.charAt(i + 1))
                    && isHexCharacter(value.charAt(i + 2))) {
                encodedTriplets++;
            }
        }
        return encodedTriplets >= 2;
    }

    private boolean isReadableDecodedText(String original, String decoded) {
        if (!StringUtils.hasText(decoded) || decoded.equals(original) || looksPercentEncoded(decoded)) {
            return false;
        }
        for (char ch : decoded.toCharArray()) {
            if (Character.isLetter(ch) || Character.isDigit(ch) || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                return true;
            }
        }
        return false;
    }

    private boolean isHexCharacter(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equals(right.trim());
    }

    private record NaverCandidate(
            ExternalNewsItem item,
            String originalLink,
            String fallbackLink,
            String normalizedTitle
    ) {
    }

    private record NaverParseResult(
            List<NaverCandidate> items,
            int rawItemCount
    ) {
    }
}

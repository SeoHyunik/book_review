package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final List<String> DEFAULT_QUERIES = List.of(
            "\uCF54\uC2A4\uD53C",
            "\uCF54\uC2A4\uB2E5",
            "\uD658\uC728",
            "\uAE08\uB9AC",
            "\uC720\uAC00",
            "\uBC18\uB3C4\uCCB4",
            "\uC5F0\uC900"
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

    private Clock clock = DEFAULT_CLOCK;

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
        if (!isConfigured()) {
            log.info("[NAVER] provider disabled or incomplete configuration");
            return List.of();
        }

        int resolvedLimit = limit > 0 ? limit : Math.max(display, 1);
        List<NaverCandidate> candidates = new ArrayList<>();
        List<String> queries = resolveQueries();
        for (String query : queries) {
            candidates.addAll(fetchQuery(query, resolvedLimit));
        }
        List<ExternalNewsItem> merged = deduplicateAndLimit(candidates, resolvedLimit);
        log.info("[NAVER] merged usableItems={} requestedLimit={} queries={}",
                merged.size(), resolvedLimit, queries.size());
        return merged;
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
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

    private List<NaverCandidate> fetchQuery(String query, int limit) {
        log.info("[NAVER] query start query='{}' requestedLimit={}", query, limit);
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/v1/search/news.json")
                .queryParam("query", query)
                .queryParam("display", resolveDisplay(limit))
                .queryParam("start", resolveStart())
                .queryParam("sort", "date")
                .build()
                .encode()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Naver-Client-Id", clientId);
        headers.add("X-Naver-Client-Secret", clientSecret);

        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                headers,
                url,
                null
        ));
        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            log.warn("[NAVER] query failed query='{}' status={}", query, result == null ? -1 : result.statusCode());
            return List.of();
        }

        return parseItems(query, result.body());
    }

    private List<NaverCandidate> parseItems(String query, String body) {
        if (!StringUtils.hasText(body)) {
            log.info("[NAVER] query='{}' rawItems=0 bodyEmpty=true", query);
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                log.info("[NAVER] query='{}' rawItems=0 itemsArray=false", query);
                return List.of();
            }

            int rawItemCount = items.size();
            int invalidPubDateCount = 0;
            int nullPublishedAtCount = 0;
            int staleItemCount = 0;
            int missingUrlCount = 0;
            int emptyTitleCount = 0;
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
                if (!isFreshEnough(publishedAt)) {
                    staleItemCount++;
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
            log.info("[NAVER] query='{}' rawItems={}", query, rawItemCount);
            log.info("[NAVER] query='{}' parsedItems={} nullPublishedAt={} invalidPubDate={} staleItems={} missingUsableLink={} emptyTitle={}",
                    query, mapped.size(), nullPublishedAtCount, invalidPubDateCount, staleItemCount, missingUrlCount, emptyTitleCount);
            return mapped;
        } catch (Exception ex) {
            log.warn("[NAVER] failed to parse response query='{}'", query, ex);
            return List.of();
        }
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
        if (StringUtils.hasText(candidate.originalLink())) {
            return candidate.originalLink();
        }
        if (StringUtils.hasText(candidate.fallbackLink())) {
            return candidate.fallbackLink();
        }
        if (StringUtils.hasText(candidate.normalizedTitle())) {
            return candidate.normalizedTitle();
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

    private boolean isFreshEnough(Instant publishedAt) {
        if (publishedAt == null) {
            return false;
        }
        return !publishedAt.isBefore(Instant.now(clock).minus(Duration.ofHours(resolveMaxAgeHours())));
    }

    private int resolveDisplay(int requestedLimit) {
        int configuredDisplay = display > 0 ? display : requestedLimit;
        return Math.max(1, Math.min(Math.max(requestedLimit, 1), configuredDisplay));
    }

    private int resolveStart() {
        return start > 0 ? start : 1;
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

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private long resolveMaxAgeHours() {
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
}

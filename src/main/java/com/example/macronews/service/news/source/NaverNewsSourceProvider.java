package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter NAVER_PUB_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
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
        for (String query : resolveQueries()) {
            candidates.addAll(fetchQuery(query, resolvedLimit));
        }
        return deduplicateAndLimit(candidates, resolvedLimit);
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

        return parseItems(result.body());
    }

    private List<NaverCandidate> parseItems(String body) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return List.of();
            }

            List<NaverCandidate> mapped = new ArrayList<>();
            for (JsonNode item : items) {
                String cleanedTitle = cleanHtml(item.path("title").asText(""));
                String cleanedDescription = cleanHtml(item.path("description").asText(""));
                String originalLink = item.path("originallink").asText("");
                String fallbackLink = item.path("link").asText("");
                Instant publishedAt = parsePubDate(item.path("pubDate").asText(""));
                String resolvedUrl = StringUtils.hasText(originalLink) ? originalLink : fallbackLink;
                String dedupTitle = normalizeTitle(cleanedTitle);

                ExternalNewsItem mappedItem = new ExternalNewsItem(
                        StringUtils.hasText(resolvedUrl) ? resolvedUrl : dedupTitle,
                        "NAVER",
                        defaultText(cleanedTitle, "Untitled"),
                        defaultText(cleanedDescription, ""),
                        defaultText(resolvedUrl, ""),
                        publishedAt
                );
                mapped.add(new NaverCandidate(mappedItem, originalLink, fallbackLink, dedupTitle));
            }
            return mapped;
        } catch (Exception ex) {
            log.warn("[NAVER] failed to parse response", ex);
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
        return candidate.normalizedTitle();
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
        try {
            return ZonedDateTime.parse(value.trim(), NAVER_PUB_DATE_FORMATTER).toInstant();
        } catch (Exception ex) {
            return null;
        }
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

    private record NaverCandidate(
            ExternalNewsItem item,
            String originalLink,
            String fallbackLink,
            String normalizedTitle
    ) {
    }
}

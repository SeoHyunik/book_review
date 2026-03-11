package com.example.macronews.service.news;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class NewsApiServiceImpl implements NewsApiService {

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

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

    @Value("${news.api.recent-query:market OR stocks OR economy OR inflation OR fed OR tariff OR oil OR semiconductor OR korea OR kospi}")
    private String recentQuery;

    @Value("${news.api.recency-hours:72}")
    private long recencyHours;

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
        if (!isConfigured()) {
            log.warn("news.api.key is missing; returning empty top-headlines list");
            return List.of();
        }

        int resolvedLimit = limit > 0 ? limit : defaultLimit;
        List<ExternalNewsItem> freshest = fetchRecentEverything(resolvedLimit);
        if (freshest.size() >= resolvedLimit) {
            return freshest;
        }

        List<ExternalNewsItem> headlines = fetchFromUrl(buildTopHeadlinesUrl(resolvedLimit), resolvedLimit,
                "top-headlines");
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
        return StringUtils.hasText(apiKey);
    }

    private List<ExternalNewsItem> fetchRecentEverything(int limit) {
        if (!StringUtils.hasText(recentQuery)) {
            return List.of();
        }
        Instant cutoff = recentCutoff();
        String url = UriComponentsBuilder.fromUriString(searchUrl)
                .queryParam("q", recentQuery)
                .queryParam("sortBy", "publishedAt")
                .queryParam("pageSize", limit)
                .queryParam("from", cutoff.toString())
                .queryParam("apiKey", apiKey)
                .build()
                .encode()
                .toUriString();
        return fetchFromUrl(url, limit, "everything");
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

    private List<ExternalNewsItem> fetchFromUrl(String url, int limit, String feedName) {
        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                new HttpHeaders(),
                url,
                null));

        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            log.warn("{} call failed. status={}", feedName, result == null ? -1 : result.statusCode());
            return List.of();
        }

        return parseArticles(result.body(), limit);
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

    private List<ExternalNewsItem> parseArticles(String body, int limit) {
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
            Instant cutoff = recentCutoff();
            for (JsonNode article : articles) {
                String source = article.path("source").path("name").asText("");
                String title = article.path("title").asText("");
                String summary = article.path("description").asText("");
                String url = article.path("url").asText("");
                String publishedAt = article.path("publishedAt").asText("");
                Instant publishedInstant = parseInstant(publishedAt);

                if (!isFreshEnough(publishedInstant, cutoff)) {
                    continue;
                }

                String externalId = StringUtils.hasText(url)
                        ? url
                        : (source + "|" + title + "|" + publishedAt).trim();

                mapped.add(new ExternalNewsItem(
                        externalId,
                        defaultText(source, "External NewsAPI"),
                        defaultText(title, "Untitled"),
                        defaultText(summary, ""),
                        defaultText(url, ""),
                        publishedInstant
                ));
            }
            return mapped.stream()
                    .sorted(Comparator.comparing(ExternalNewsItem::publishedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to parse external news response", ex);
            return List.of();
        }
    }

    private Instant recentCutoff() {
        long resolvedHours = recencyHours > 0 ? recencyHours : 72L;
        return Instant.now().minus(Duration.ofHours(resolvedHours));
    }

    private boolean isFreshEnough(Instant publishedAt, Instant cutoff) {
        return publishedAt != null && (cutoff == null || !publishedAt.isBefore(cutoff));
    }

    private String dedupKey(ExternalNewsItem item) {
        if (item == null) {
            return "";
        }
        if (StringUtils.hasText(item.url())) {
            return item.url();
        }
        return defaultText(item.externalId(), "");
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
}

package com.example.macronews.service.news;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Value("${news.api.key:}")
    private String apiKey;

    @Value("${news.api.country:us}")
    private String country;

    @Value("${news.api.category:business}")
    private String category;

    @Value("${news.api.default-limit:10}")
    private int defaultLimit;

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("news.api.key is missing; returning empty top-headlines list");
            return List.of();
        }

        int resolvedLimit = limit > 0 ? limit : defaultLimit;
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("country", country)
                .queryParam("category", category)
                .queryParam("pageSize", resolvedLimit)
                .queryParam("apiKey", apiKey)
                .build(true)
                .toUriString();

        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                new HttpHeaders(),
                url,
                null));

        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            log.warn("Top-headlines call failed. status={}", result == null ? -1 : result.statusCode());
            return List.of();
        }

        return parseArticles(result.body(), resolvedLimit);
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
            for (JsonNode article : articles) {
                if (mapped.size() >= limit) {
                    break;
                }
                String source = article.path("source").path("name").asText("");
                String title = article.path("title").asText("");
                String summary = article.path("description").asText("");
                String url = article.path("url").asText("");
                String publishedAt = article.path("publishedAt").asText("");

                String externalId = StringUtils.hasText(url)
                        ? url
                        : (source + "|" + title + "|" + publishedAt).trim();

                mapped.add(new ExternalNewsItem(
                        externalId,
                        defaultText(source, "External NewsAPI"),
                        defaultText(title, "Untitled"),
                        defaultText(summary, ""),
                        defaultText(url, ""),
                        parseInstant(publishedAt)
                ));
            }
            return mapped;
        } catch (Exception ex) {
            log.warn("Failed to parse top-headlines response", ex);
            return List.of();
        }
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

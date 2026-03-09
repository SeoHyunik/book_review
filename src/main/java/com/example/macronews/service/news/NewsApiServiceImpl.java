package com.example.macronews.service.news;

import com.example.macronews.dto.internal.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsApiServiceImpl implements NewsApiService {

    private final ExternalApiUtils externalApiUtils;

    @Value("${news-api.rss-url:https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=en-US&gl=US&ceid=US:en}")
    private String rssUrl;

    @Value("${news-api.default-page-size:10}")
    private int defaultPageSize;

    @Override
    public List<ExternalNewsItem> fetchLatestNews(int pageSize) {
        int resolvedPageSize = pageSize > 0 ? pageSize : defaultPageSize;
        log.info("Starting external news API fetch from RSS url={}, pageSize={}", rssUrl,
                resolvedPageSize);

        ExternalApiRequest request = new ExternalApiRequest(HttpMethod.GET, new HttpHeaders(),
                rssUrl, null);
        ExternalApiResult result = externalApiUtils.callAPI(request);

        if (result == null) {
            throw new IllegalStateException("RSS fetch failed: null response");
        }
        if (result.statusCode() < 200 || result.statusCode() >= 300) {
            throw new IllegalStateException(
                    "RSS fetch failed with status=" + result.statusCode());
        }

        List<ExternalNewsItem> items = parseRss(result.body(), resolvedPageSize);
        log.info("Completed external news API fetch. fetchedCount={}", items.size());
        return items;
    }

    private List<ExternalNewsItem> parseRss(String xmlBody, int pageSize) {
        if (!StringUtils.hasText(xmlBody)) {
            return List.of();
        }

        Document document = Jsoup.parse(xmlBody, "", Parser.xmlParser());
        String sourceTitle = document.selectFirst("channel > title") != null
                ? document.selectFirst("channel > title").text()
                : "RSS";

        return document.select("item")
                .stream()
                .limit(pageSize)
                .map(item -> toExternalItem(item, sourceTitle))
                .collect(Collectors.toList());
    }

    private ExternalNewsItem toExternalItem(Element item, String defaultSource) {
        String guid = text(item, "guid");
        String title = text(item, "title");
        String description = text(item, "description");
        String contentEncoded = text(item, "content|encoded");
        String link = text(item, "link");
        String source = text(item, "source");
        String pubDate = text(item, "pubDate");

        return new ExternalNewsItem(
                StringUtils.hasText(guid) ? guid : null,
                StringUtils.hasText(source) ? source : defaultSource,
                title,
                description,
                StringUtils.hasText(contentEncoded) ? contentEncoded : description,
                link,
                parsePublishedAt(pubDate)
        );
    }

    private LocalDateTime parsePublishedAt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toLocalDateTime();
        } catch (Exception ex) {
            log.debug("Failed to parse RSS pubDate='{}', using null", value);
            return null;
        }
    }

    private String text(Element item, String cssQuery) {
        Element found = item.selectFirst(cssQuery);
        return found == null ? "" : found.text();
    }
}
package com.example.macronews.dto.request;

import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record AdminIngestionRequest(
        String source,
        String title,
        String summary,
        String content,
        String url,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime publishedAt,
        Integer limit
) {

    public AdminIngestionRequest {
        source = defaultValue(source);
        title = defaultValue(title);
        summary = defaultValue(summary);
        content = defaultValue(content);
        url = defaultValue(url);
    }

    public static AdminIngestionRequest empty() {
        return new AdminIngestionRequest("", "", "", "", "", null, null);
    }

    private static String defaultValue(String value) {
        return value == null ? "" : value;
    }
}
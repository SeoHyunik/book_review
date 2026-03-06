package com.example.bookreview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record AdminIngestionRequest(
        @NotBlank(message = "source is required")
        String source,
        @NotBlank(message = "title is required")
        String title,
        String summary,
        String content,
        String url,
        @NotNull(message = "publishedAt is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime publishedAt,
        String ingestedBy
) {

    public AdminIngestionRequest {
        source = defaultValue(source);
        title = defaultValue(title);
        summary = defaultValue(summary);
        content = defaultValue(content);
        url = defaultValue(url);
        ingestedBy = defaultValue(ingestedBy);
    }

    public static AdminIngestionRequest empty() {
        return new AdminIngestionRequest("", "", "", "", "", null, "");
    }

    private static String defaultValue(String value) {
        return value == null ? "" : value;
    }
}


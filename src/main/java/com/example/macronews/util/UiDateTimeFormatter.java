package com.example.macronews.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("uiDateTimeFormatter")
public class UiDateTimeFormatter {

    private static final ZoneId KOREA_STANDARD_TIME = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String EMPTY_VALUE = "-";

    public String formatKst(Instant value) {
        if (value == null) {
            return EMPTY_VALUE;
        }
        return DISPLAY_FORMATTER.format(value.atZone(KOREA_STANDARD_TIME));
    }

    public String formatKst(String value) {
        if (!StringUtils.hasText(value)) {
            return EMPTY_VALUE;
        }

        String trimmed = value.trim();
        try {
            return formatKst(Instant.parse(trimmed));
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(trimmed);
                return DISPLAY_FORMATTER.format(localDateTime.atZone(KOREA_STANDARD_TIME));
            } catch (DateTimeParseException ignoredAgain) {
                return trimmed;
            }
        }
    }
}

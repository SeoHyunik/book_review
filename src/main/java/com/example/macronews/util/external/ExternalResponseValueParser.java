package com.example.macronews.util.external;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.util.StringUtils;

public final class ExternalResponseValueParser {

    private ExternalResponseValueParser() {
    }

    public static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    public static Instant parseInstant(String value, DateTimeFormatter primaryFormatter,
            List<DateTimeFormatter> fallbackFormatters) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return ZonedDateTime.parse(trimmed, primaryFormatter).toInstant();
        } catch (Exception ex) {
            // Continue to the fallback formatters.
        }
        if (fallbackFormatters == null) {
            return null;
        }
        for (DateTimeFormatter formatter : fallbackFormatters) {
            try {
                return ZonedDateTime.parse(trimmed, formatter).toInstant();
            } catch (Exception ex) {
                // Continue to the next safe fallback formatter.
            }
        }
        return null;
    }

    public static Instant readInstant(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return Instant.ofEpochSecond(value.asLong());
        }
        return parseInstant(value.asText(""));
    }

    public static Instant readBasicIsoDate(JsonNode node, String field, ZoneId zoneId) {
        if (node == null || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        String text = node.path(field).asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(text.trim(), DateTimeFormatter.BASIC_ISO_DATE);
            return date.atStartOfDay(zoneId).toInstant();
        } catch (Exception ex) {
            return null;
        }
    }

    public static LocalDate parseLocalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    public static Double readDouble(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.asDouble();
        }
        String text = value.asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}

package com.example.macronews.util.external;

import java.net.URI;
import java.util.Locale;
import org.springframework.util.StringUtils;

public final class ExternalResponseTextNormalizer {

    private ExternalResponseTextNormalizer() {
    }

    public static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public static String normalizeLowerCase(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeUrl(String value) {
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
}

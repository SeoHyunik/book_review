package com.example.macronews.util;

import org.springframework.util.StringUtils;

public final class RedirectPathUtils {

    private RedirectPathUtils() {
    }

    public static boolean isSafeRelativePath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }

        String trimmed = path.trim();
        return trimmed.startsWith("/")
                && !trimmed.startsWith("//")
                && !trimmed.contains("://")
                && !trimmed.contains("\\");
    }

    public static String normalizeSafeRelativePath(String path) {
        return isSafeRelativePath(path) ? path.trim() : "";
    }
}

package com.example.macronews.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Matches keywords against text.
 *
 * <p>Korean (and other non-ASCII) keywords use plain case-insensitive substring matching, because
 * languages such as Korean do not separate words with whitespace. ASCII/English keywords are matched
 * on word boundaries so that, for example, the keyword {@code "AI"} does not match inside
 * {@code "RAID"}.
 */
public final class KeywordMatcher {

    private KeywordMatcher() {
    }

    public static boolean matches(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return false;
        }

        String haystack = text.trim();
        String needle = keyword.trim();

        if (isAsciiKeyword(needle)) {
            return matchesWordBoundary(haystack, needle);
        }
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static boolean isAsciiKeyword(String keyword) {
        for (int i = 0; i < keyword.length(); i++) {
            if (keyword.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesWordBoundary(String text, String keyword) {
        Pattern pattern = Pattern.compile(
                "\\b" + Pattern.quote(keyword) + "\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find();
    }
}

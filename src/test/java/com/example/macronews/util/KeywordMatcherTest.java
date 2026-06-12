package com.example.macronews.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeywordMatcherTest {

    @Test
    @DisplayName("Korean keyword matches as a substring")
    void matches_koreanKeywordUsesSubstring() {
        assertThat(KeywordMatcher.matches("금리 인상 소식", "금리"))
                .isTrue();
    }

    @Test
    @DisplayName("ASCII keyword matches on word boundary")
    void matches_asciiKeywordMatchesWholeWord() {
        assertThat(KeywordMatcher.matches("The AI market is growing", "AI")).isTrue();
    }

    @Test
    @DisplayName("ASCII keyword does not match inside a larger word")
    void matches_asciiKeywordIgnoresSubstring() {
        assertThat(KeywordMatcher.matches("The RAID array failed", "AI")).isFalse();
    }

    @Test
    @DisplayName("ASCII keyword matching is case insensitive")
    void matches_asciiKeywordIsCaseInsensitive() {
        assertThat(KeywordMatcher.matches("breaking news about Inflation", "inflation")).isTrue();
    }

    @Test
    @DisplayName("returns false for null or blank inputs")
    void matches_returnsFalseForBlankInputs() {
        assertThat(KeywordMatcher.matches(null, "AI")).isFalse();
        assertThat(KeywordMatcher.matches("text", " ")).isFalse();
        assertThat(KeywordMatcher.matches(" ", "AI")).isFalse();
    }
}

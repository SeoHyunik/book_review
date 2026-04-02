package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.NewsEvent;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class NewsTranslationSelector {

    String resolvePreferredInterpretationSummary(AnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "";
        }
        boolean korean = isKoreanLocale();
        String preferred = korean ? analysisResult.summaryKo() : analysisResult.summaryEn();
        String fallback = korean ? analysisResult.summaryEn() : analysisResult.summaryKo();
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return "";
    }

    String resolvePreferredHeadline(AnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "";
        }
        boolean korean = isKoreanLocale();
        String preferred = korean ? analysisResult.headlineKo() : analysisResult.headlineEn();
        String fallback = korean ? analysisResult.headlineEn() : analysisResult.headlineKo();
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return "";
    }

    String buildDisplayTitle(NewsEvent event, String preferredHeadline, String preferredSummary) {
        if (StringUtils.hasText(preferredHeadline)) {
            return preferredHeadline;
        }
        String derivedTitle = extractLeadingSentence(preferredSummary);
        if (StringUtils.hasText(derivedTitle)) {
            return derivedTitle;
        }
        return StringUtils.hasText(event.title()) ? event.title() : "";
    }

    String buildInterpretationSummary(String macroSummary, String preferredSummary, String displayTitle) {
        if (!StringUtils.hasText(preferredSummary)) {
            return macroSummary;
        }

        String remainder = removeLeadingSentence(preferredSummary, displayTitle);
        if (StringUtils.hasText(remainder)) {
            return remainder;
        }
        if (StringUtils.hasText(preferredSummary)) {
            return preferredSummary;
        }
        return macroSummary;
    }

    private boolean isKoreanLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null && "ko".equalsIgnoreCase(locale.getLanguage());
    }

    private String extractLeadingSentence(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        int sentenceBoundary = findSentenceBoundary(trimmed);
        if (sentenceBoundary < 0) {
            return trimmed;
        }
        return trimmed.substring(0, sentenceBoundary + 1).trim();
    }

    private String removeLeadingSentence(String text, String headline) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        String lead = StringUtils.hasText(headline) ? headline.trim() : extractLeadingSentence(trimmed);
        if (!StringUtils.hasText(lead) || !trimmed.startsWith(lead)) {
            return trimmed;
        }
        String remainder = trimmed.substring(lead.length()).trim();
        return remainder.replaceFirst("^[\\-:;,.\\s]+", "").trim();
    }

    private int findSentenceBoundary(String text) {
        int boundary = -1;
        for (char marker : new char[] {'.', '!', '?'}) {
            int index = text.indexOf(marker);
            if (index >= 0 && (boundary < 0 || index < boundary)) {
                boundary = index;
            }
        }
        return boundary;
    }
}

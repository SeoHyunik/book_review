package com.example.macronews.controller;

import com.example.macronews.dto.NewsListItemDto;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
final class TopicKeywordPolicy {

    private static final List<String> DOLLAR_KEYWORDS = List.of(
            "usd",
            "dollar",
            "dxy",
            "fx",
            "foreign exchange",
            "treasury",
            "yield",
            "fed",
            "fomc",
            "rate"
    );

    private static final List<String> RATES_KEYWORDS = List.of(
            "rates",
            "yield",
            "yields",
            "treasury",
            "treasuries",
            "bond",
            "bonds",
            "fed",
            "fomc",
            "powell",
            "policy",
            "interest rate",
            "rate decision",
            "rate hike",
            "rate cut"
    );

    private static final List<String> OIL_KEYWORDS = List.of(
            "oil",
            "wti",
            "brent",
            "crude",
            "energy",
            "opec",
            "production",
            "supply"
    );

    boolean matchesDollar(NewsListItemDto item) {
        return matchesKeywords(item, DOLLAR_KEYWORDS);
    }

    boolean matchesRates(NewsListItemDto item) {
        return matchesKeywords(item, RATES_KEYWORDS);
    }

    boolean matchesOil(NewsListItemDto item) {
        return matchesKeywords(item, OIL_KEYWORDS);
    }

    private boolean matchesKeywords(NewsListItemDto item, List<String> keywords) {
        if (item == null) {
            return false;
        }
        return containsKeyword(item.title(), keywords)
                || containsKeyword(item.displayTitle(), keywords)
                || containsKeyword(item.source(), keywords)
                || containsKeyword(item.macroSummary(), keywords)
                || containsKeyword(item.interpretationSummary(), keywords);
    }

    private boolean containsKeyword(String value, List<String> keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

package com.example.macronews.controller;

import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.util.KeywordMatcher;
import com.example.macronews.util.KeywordSource;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
final class TopicKeywordPolicy {

    private static final KeywordSource DOLLAR_KEYWORDS = KeywordSource.fixed(List.of(
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
    ));

    private static final KeywordSource RATES_KEYWORDS = KeywordSource.fixed(List.of(
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
    ));

    private static final KeywordSource OIL_KEYWORDS = KeywordSource.fixed(List.of(
            "oil",
            "wti",
            "brent",
            "crude",
            "energy",
            "opec",
            "production",
            "supply"
    ));

    boolean matchesDollar(NewsListItemDto item) {
        return matchesKeywords(item, DOLLAR_KEYWORDS.keywords());
    }

    boolean matchesRates(NewsListItemDto item) {
        return matchesKeywords(item, RATES_KEYWORDS.keywords());
    }

    boolean matchesOil(NewsListItemDto item) {
        return matchesKeywords(item, OIL_KEYWORDS.keywords());
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
        for (String keyword : keywords) {
            if (KeywordMatcher.matches(value, keyword)) {
                return true;
            }
        }
        return false;
    }
}

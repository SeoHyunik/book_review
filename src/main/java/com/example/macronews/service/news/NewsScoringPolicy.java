package com.example.macronews.service.news;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.MarketSignalItemDto;
import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class NewsScoringPolicy {

    private static final double DEFAULT_IMPACT_CONFIDENCE = 0.55d;
    private static final double MIN_CONFIDENCE = 0.18d;
    private static final double MAX_CONFIDENCE = 0.94d;
    private static final double NEUTRAL_WEIGHT_DAMPING = 0.65d;
    private static final double DIRECTIONAL_EDGE_THRESHOLD = 0.12d;
    private static final int CRISIS_BOOST_MIN_SAMPLE_COUNT = 3;
    private static final double CRISIS_BOOST_EDGE_THRESHOLD = 0.20d;
    private static final double CRISIS_BOOST_NEGATIVE_NEUTRAL_RATIO = 1.1d;
    private static final double CRISIS_BOOST_CONFIDENCE = 0.03d;
    private static final List<KeywordWeightRule> PRIORITY_WEIGHT_RULES = List.of(
            new KeywordWeightRule(8, 5, 3, "south korea", "korea", "kospi", "krw", "won"),
            new KeywordWeightRule(6, 4, 2, "semiconductor", "chip", "memory", "samsung", "sk hynix", "battery", "ev", "auto",
                    "shipbuilding", "ai"),
            new KeywordWeightRule(9, 6, 3, "fed", "fomc", "ecb", "boj", "bok", "central bank", "rate decision", "interest rate",
                    "cpi", "inflation", "ppi", "employment", "jobs", "payroll", "gdp", "recession", "slowdown"),
            new KeywordWeightRule(7, 5, 2, "fx", "foreign exchange", "exchange rate", "usd", "dollar", "yen", "treasury",
                    "treasury yield", "bond yield", "oil", "crude", "brent", "wti", "commodity", "commodities"),
            new KeywordWeightRule(6, 4, 2, "tariff", "trade", "export", "china", "sanctions", "geopolitics", "u.s.", "united states")
    );
    private static final List<KeywordWeightRule> NOISE_DEMOTION_RULES = List.of(
            new KeywordWeightRule(3, 2, 0, "tips", "how to", "guide", "best way", "must try", "life hack", "checklist"),
            new KeywordWeightRule(3, 2, 0, "festival", "event", "giveaway", "sale", "discount", "promotion", "opening"),
            new KeywordWeightRule(4, 2, 0, "celebrity", "star", "romance", "wedding", "fashion", "beauty", "viral", "buzz"),
            new KeywordWeightRule(3, 2, 0, "hot issue", "shocking", "surprising", "what happened", "you need to know", "attention")
    );
    private static final List<String> TRUSTED_SOURCE_MARKERS = List.of(
            "reuters", "bloomberg", "yonhap", "financial times", "wall street journal", "wsj",
            "associated press", "nikkei", "cnbc"
    );
    private static final List<String> TRUSTED_DOMAIN_MARKERS = List.of(
            "reuters.com", "bloomberg.com", "yonhapnews.co.kr", "ft.com", "wsj.com", "apnews.com",
            "nikkei.com", "cnbc.com"
    );

    Comparator<NewsEvent> buildComparator(NewsListSort sort) {
        NewsListSort resolvedSort = sort == null ? NewsListSort.PUBLISHED_DESC : sort;
        return switch (resolvedSort) {
            case PUBLISHED_ASC -> Comparator.comparing(NewsEvent::publishedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(NewsEvent::ingestedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case PRIORITY -> Comparator.comparingInt(this::calculatePriorityScore)
                    .reversed()
                    .thenComparing(NewsEvent::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case INGESTED_DESC -> Comparator.comparing(NewsEvent::ingestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(NewsEvent::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case PUBLISHED_DESC -> Comparator.comparing(NewsEvent::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(NewsEvent::ingestedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    MarketSignalItemDto aggregateSignal(MacroVariable variable, List<NewsEvent> recentAnalyzed) {
        Map<ImpactDirection, Double> weightedScores = new EnumMap<>(ImpactDirection.class);
        weightedScores.put(ImpactDirection.UP, 0d);
        weightedScores.put(ImpactDirection.DOWN, 0d);
        weightedScores.put(ImpactDirection.NEUTRAL, 0d);

        int sampleCount = 0;
        for (NewsEvent event : recentAnalyzed) {
            if (event.analysisResult() == null || event.analysisResult().macroImpacts() == null) {
                continue;
            }
            for (MacroImpact impact : event.analysisResult().macroImpacts()) {
                if (impact == null || impact.variable() != variable || impact.direction() == null) {
                    continue;
                }
                weightedScores.computeIfPresent(impact.direction(), (key, value) -> value + resolveImpactWeight(impact));
                sampleCount++;
            }
        }

        AggregatedDirection aggregatedDirection = resolveDominantDirection(variable, weightedScores, sampleCount);
        return new MarketSignalItemDto(
                variable,
                aggregatedDirection.direction(),
                variable.sentimentFor(aggregatedDirection.direction()),
                sampleCount,
                aggregatedDirection.confidence()
        );
    }

    int calculatePriorityScore(NewsEvent event) {
        String title = normalize(event.title());
        String summary = normalize(event.summary());
        String source = normalize(event.source());
        String combined = combineText(title, summary);
        String domain = normalize(extractDomain(event.url()));

        int score = 0;
        for (KeywordWeightRule rule : PRIORITY_WEIGHT_RULES) {
            score += scoreKeywords(title, rule.titleWeight(), rule.keywords());
            score += scoreKeywords(summary, rule.summaryWeight(), rule.keywords());
            score += scoreKeywords(source, rule.sourceWeight(), rule.keywords());
        }

        if (containsKeyword(title, "korea")
                && containsAnyKeyword(title, "semiconductor", "chip", "memory", "samsung", "sk hynix")) {
            score += 5;
        }
        if (containsKeyword(summary, "korea")
                && containsAnyKeyword(summary, "trade", "export", "china", "u.s.", "united states", "tariff")) {
            score += 4;
        }
        if (containsAnyKeyword(title, "kospi", "krw", "won")) {
            score += 6;
        }
        if (containsAnyKeyword(combined, "fed", "fomc", "ecb", "boj", "bok", "central bank")
                && containsAnyKeyword(combined, "interest rate", "rate decision", "cpi", "inflation", "employment",
                "jobs", "payroll", "gdp")) {
            score += 8;
        }
        if (containsAnyKeyword(combined, "treasury", "treasury yield", "bond yield", "fx", "exchange rate", "usd",
                "dollar", "yen")
                && containsAnyKeyword(combined, "fed", "fomc", "cpi", "inflation", "rate decision")) {
            score += 6;
        }
        if (containsAnyKeyword(combined, "oil", "crude", "brent", "wti", "commodity", "commodities")
                && containsAnyKeyword(combined, "inflation", "cpi", "ppi")) {
            score += 5;
        }
        if (containsAnyKeyword(combined, "tariff", "trade", "sanctions")
                && containsAnyKeyword(combined, "china", "u.s.", "united states", "korea")) {
            score += 4;
        }

        score += calculateSourceReliabilityWeight(source, domain);
        score -= calculateNoiseDemotion(title, summary, source, combined, score);
        return score;
    }

    private AggregatedDirection resolveDominantDirection(
            MacroVariable variable,
            Map<ImpactDirection, Double> weightedScores,
            int sampleCount
    ) {
        double up = weightedScores.getOrDefault(ImpactDirection.UP, 0d);
        double down = weightedScores.getOrDefault(ImpactDirection.DOWN, 0d);
        double neutral = weightedScores.getOrDefault(ImpactDirection.NEUTRAL, 0d);
        double directionalMax = Math.max(up, down);
        double directionalMin = Math.min(up, down);
        double total = up + down + neutral;

        if (total <= 0d) {
            return new AggregatedDirection(
                    ImpactDirection.NEUTRAL,
                    null,
                    new ConfidenceBreakdown(null, 0d, null, false, false)
            );
        }

        ImpactDirection directionalCandidate = up >= down ? ImpactDirection.UP : ImpactDirection.DOWN;
        double directionalEdge = directionalMax - directionalMin;
        boolean directionalWins = directionalMax > 0d
                && directionalEdge >= DIRECTIONAL_EDGE_THRESHOLD
                && directionalMax >= (neutral * 0.9d);

        if (!directionalWins) {
            Double neutralConfidence = calculateConfidence(neutral, Math.max(up, down), total);
            return new AggregatedDirection(ImpactDirection.NEUTRAL,
                    neutralConfidence,
                    new ConfidenceBreakdown(neutralConfidence, 0d, neutralConfidence, false, false));
        }

        Double baseConfidence = calculateConfidence(directionalMax, Math.max(neutral, directionalMin), total);
        Double confidence = baseConfidence;
        boolean capApplied = false;
        if (directionalCandidate != ImpactDirection.NEUTRAL
                && sampleCount >= CRISIS_BOOST_MIN_SAMPLE_COUNT
                && directionalEdge >= CRISIS_BOOST_EDGE_THRESHOLD
                && variable.sentimentFor(directionalCandidate) == SignalSentiment.NEGATIVE
                && directionalMax >= (neutral * CRISIS_BOOST_NEGATIVE_NEUTRAL_RATIO)) {
            double boostedConfidence = (confidence == null ? 0d : confidence) + CRISIS_BOOST_CONFIDENCE;
            confidence = clamp(boostedConfidence, MIN_CONFIDENCE, MAX_CONFIDENCE);
            capApplied = confidence != boostedConfidence;
        }

        return new AggregatedDirection(
                directionalCandidate,
                confidence,
                new ConfidenceBreakdown(
                        baseConfidence,
                        Math.max(0d, (confidence == null || baseConfidence == null) ? 0d : confidence - baseConfidence),
                        confidence,
                        confidence != null && baseConfidence != null && confidence > baseConfidence,
                        capApplied
                )
        );
    }

    private double resolveImpactWeight(MacroImpact impact) {
        double confidence = normalizeConfidence(impact.confidence());
        return impact.direction() == ImpactDirection.NEUTRAL ? confidence * NEUTRAL_WEIGHT_DAMPING : confidence;
    }

    private Double calculateConfidence(double winner, double runnerUp, double total) {
        if (total <= 0d) {
            return null;
        }
        double dominance = Math.max(0d, winner - runnerUp) / total;
        double participation = Math.min(1d, total / 3d);
        double confidence = 0.32d + (dominance * 0.48d) + (participation * 0.14d);
        return clamp(confidence, MIN_CONFIDENCE, MAX_CONFIDENCE);
    }

    private double normalizeConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN() || confidence <= 0d) {
            return DEFAULT_IMPACT_CONFIDENCE;
        }
        return clamp(confidence, 0.2d, 1d);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int calculateSourceReliabilityWeight(String source, String domain) {
        int weight = 0;
        if (containsAnyKeyword(source, TRUSTED_SOURCE_MARKERS.toArray(String[]::new))) {
            weight += 3;
        }
        if (containsAnyKeyword(domain, TRUSTED_DOMAIN_MARKERS.toArray(String[]::new))) {
            weight += 2;
        }
        return Math.min(weight, 4);
    }

    private int calculateNoiseDemotion(String title, String summary, String source, String combined, int currentScore) {
        int demotion = 0;
        for (KeywordWeightRule rule : NOISE_DEMOTION_RULES) {
            demotion += scoreKeywords(title, rule.titleWeight(), rule.keywords());
            demotion += scoreKeywords(summary, rule.summaryWeight(), rule.keywords());
            demotion += scoreKeywords(source, rule.sourceWeight(), rule.keywords());
        }

        if (demotion == 0) {
            return 0;
        }
        if (currentScore >= 20 || containsStrongMarketSignal(combined)) {
            return Math.max(1, demotion / 2);
        }
        return demotion;
    }

    private boolean containsStrongMarketSignal(String text) {
        return containsAnyKeyword(text,
                "fed", "fomc", "ecb", "boj", "bok", "central bank", "interest rate", "rate decision", "cpi",
                "inflation", "employment", "payroll", "gdp", "recession", "treasury", "bond yield", "fx",
                "exchange rate", "oil", "crude", "tariff", "trade", "sanctions");
    }

    private int scoreKeywords(String text, int weight, String... keywords) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int score = 0;
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                score += weight;
            }
        }
        return score;
    }

    private boolean containsAnyKeyword(String text, String... keywords) {
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKeyword(String text, String keyword) {
        return StringUtils.hasText(text) && text.contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String combineText(String... values) {
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String extractDomain(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private record KeywordWeightRule(int titleWeight, int summaryWeight, int sourceWeight, String... keywords) {
    }

    private record AggregatedDirection(ImpactDirection direction, Double confidence, ConfidenceBreakdown confidenceBreakdown) {
    }

    private record ConfidenceBreakdown(Double baseConfidence, double crisisBoost, Double finalConfidence, boolean crisisApplied, boolean capApplied) {
    }
}

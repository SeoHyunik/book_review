package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.SignalSentiment;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class MarketSentimentAggregator {

    private static final double DEFAULT_IMPACT_CONFIDENCE = 0.55d;
    private static final double MIN_CONFIDENCE = 0.18d;
    private static final double MAX_CONFIDENCE = 0.94d;
    private static final double NEUTRAL_WEIGHT_DAMPING = 0.7d;
    private static final double DOMINANCE_EDGE_THRESHOLD = 0.12d;
    private static final int CRISIS_BOOST_MIN_ITEM_COUNT = 3;
    private static final double CRISIS_BOOST_EDGE_THRESHOLD = 0.20d;
    private static final double CRISIS_BOOST_NEGATIVE_NEUTRAL_RATIO = 1.12d;
    private static final double CRISIS_BOOST_CONFIDENCE = 0.04d;

    SentimentAggregation resolveDominantSentiment(List<NewsEvent> recentItems) {
        Map<SignalSentiment, Double> weightedScores = new EnumMap<>(SignalSentiment.class);
        weightedScores.put(SignalSentiment.POSITIVE, 0d);
        weightedScores.put(SignalSentiment.NEGATIVE, 0d);
        weightedScores.put(SignalSentiment.NEUTRAL, 0d);

        for (NewsEvent event : recentItems) {
            WeightedSentiment weightedSentiment = resolvePrimarySentiment(event);
            weightedScores.computeIfPresent(weightedSentiment.sentiment(),
                    (key, value) -> value + weightedSentiment.weight());
        }

        double positive = weightedScores.getOrDefault(SignalSentiment.POSITIVE, 0d);
        double negative = weightedScores.getOrDefault(SignalSentiment.NEGATIVE, 0d);
        double neutral = weightedScores.getOrDefault(SignalSentiment.NEUTRAL, 0d);
        double total = positive + negative + neutral;
        if (total <= 0d) {
            return new SentimentAggregation(SignalSentiment.NEUTRAL, null, null);
        }

        double directionalMax = Math.max(positive, negative);
        double directionalMin = Math.min(positive, negative);
        SignalSentiment directionalCandidate = positive >= negative
                ? SignalSentiment.POSITIVE
                : SignalSentiment.NEGATIVE;

        boolean directionalWins = directionalMax > 0d
                && (directionalMax - directionalMin) >= DOMINANCE_EDGE_THRESHOLD
                && directionalMax >= (neutral * 0.92d);

        if (!directionalWins) {
            Double neutralConfidence = calculateConfidence(neutral, Math.max(positive, negative), total);
            return new SentimentAggregation(
                    SignalSentiment.NEUTRAL,
                    neutralConfidence,
                    neutralConfidence
            );
        }

        Double baseConfidence = calculateConfidence(directionalMax, Math.max(neutral, directionalMin), total);
        Double confidence = baseConfidence;
        if (directionalCandidate == SignalSentiment.NEGATIVE
                && recentItems.size() >= CRISIS_BOOST_MIN_ITEM_COUNT
                && (directionalMax - directionalMin) >= CRISIS_BOOST_EDGE_THRESHOLD
                && directionalMax >= (neutral * CRISIS_BOOST_NEGATIVE_NEUTRAL_RATIO)) {
            confidence = Math.max(0.18d, Math.min(confidence + CRISIS_BOOST_CONFIDENCE, 0.94d));
        }

        return new SentimentAggregation(
                directionalCandidate,
                confidence,
                baseConfidence
        );
    }

    private WeightedSentiment resolvePrimarySentiment(NewsEvent event) {
        AnalysisResult analysisResult = event.analysisResult();
        if (analysisResult == null) {
            return new WeightedSentiment(SignalSentiment.NEUTRAL, DEFAULT_IMPACT_CONFIDENCE * NEUTRAL_WEIGHT_DAMPING);
        }

        if (analysisResult.macroImpacts() != null) {
            for (MacroImpact impact : analysisResult.macroImpacts()) {
                if (impact == null || impact.variable() == null || impact.direction() == null) {
                    continue;
                }
                SignalSentiment sentiment = impact.variable().sentimentFor(impact.direction());
                return new WeightedSentiment(sentiment, resolveImpactWeight(sentiment, impact.confidence()));
            }
        }

        if (analysisResult.marketImpacts() != null) {
            for (MarketImpact impact : analysisResult.marketImpacts()) {
                if (impact == null || impact.market() == null || impact.direction() == null) {
                    continue;
                }
                SignalSentiment sentiment = impact.market().sentimentFor(impact.direction());
                return new WeightedSentiment(sentiment, resolveImpactWeight(sentiment, impact.confidence()));
            }
        }

        return new WeightedSentiment(SignalSentiment.NEUTRAL, DEFAULT_IMPACT_CONFIDENCE * NEUTRAL_WEIGHT_DAMPING);
    }

    private double resolveImpactWeight(SignalSentiment sentiment, Double confidence) {
        double normalized = normalizeConfidence(confidence);
        return sentiment == SignalSentiment.NEUTRAL
                ? normalized * NEUTRAL_WEIGHT_DAMPING
                : normalized;
    }

    private double normalizeConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN() || confidence <= 0d) {
            return DEFAULT_IMPACT_CONFIDENCE;
        }
        return Math.max(0.2d, Math.min(confidence, 1d));
    }

    private Double calculateConfidence(double winner, double runnerUp, double total) {
        if (total <= 0d) {
            return null;
        }
        double dominance = Math.max(0d, winner - runnerUp) / total;
        double participation = Math.min(1d, total / 3d);
        double confidence = 0.34d + (dominance * 0.46d) + (participation * 0.12d);
        return Math.max(0.18d, Math.min(confidence, 0.94d));
    }

    record SentimentAggregation(
            SignalSentiment sentiment,
            Double confidence,
            Double baseConfidence
    ) {
    }

    private record WeightedSentiment(
            SignalSentiment sentiment,
            double weight
    ) {
    }
}

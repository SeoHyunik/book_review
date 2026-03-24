package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.MarketType;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.market.MarketDataFacade;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecentMarketSummaryService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));
    private static final int MAX_KEY_DRIVERS = 3;
    private static final double DEFAULT_IMPACT_CONFIDENCE = 0.55d;
    private static final double NEUTRAL_WEIGHT_DAMPING = 0.7d;
    private static final double DOMINANCE_EDGE_THRESHOLD = 0.12d;
    private static final int CRISIS_BOOST_MIN_ITEM_COUNT = 3;
    private static final double CRISIS_BOOST_EDGE_THRESHOLD = 0.20d;
    private static final double CRISIS_BOOST_NEGATIVE_NEUTRAL_RATIO = 1.12d;
    private static final double CRISIS_BOOST_CONFIDENCE = 0.04d;
    private static final double MAX_MARKET_CONFIDENCE_BOOST = 0.12d;
    private static final double USD_KRW_NEGATIVE_LEVEL = 1380d;
    private static final double USD_KRW_POSITIVE_LEVEL = 1330d;
    private static final double GOLD_NEGATIVE_LEVEL = 3000d;
    private static final double OIL_NEGATIVE_LEVEL = 85d;
    private static final double KOSPI_NEGATIVE_LEVEL = 2500d;
    private static final double KOSPI_POSITIVE_LEVEL = 2600d;

    private final NewsEventRepository newsEventRepository;
    private final MarketDataFacade marketDataFacade;

    @Value("${app.featured.market-summary.enabled:true}")
    private boolean enabled;

    @Value("${app.featured.market-summary.window-hours:3}")
    private int windowHours;

    @Value("${app.featured.market-summary.max-items:10}")
    private int maxItems;

    @Value("${app.featured.market-summary.min-items:3}")
    private int minItems;

    private Clock clock = DEFAULT_CLOCK;

    public Optional<FeaturedMarketSummaryDto> getCurrentSummary() {
        if (!enabled) {
            return Optional.empty();
        }

        List<NewsEvent> recentItems = loadRecentAnalyzedNews();
        if (recentItems.size() < resolveMinItems()) {
            log.info("[FEATURED_SUMMARY] skipped reason=insufficient-analyzed-news queryBasis=analysisResult.createdAt|ingestedAt size={} minItems={}",
                    recentItems.size(), resolveMinItems());
            return Optional.empty();
        }

        SentimentAggregation aggregation = resolveDominantSentiment(recentItems);
        SignalSentiment dominantSentiment = aggregation.sentiment();
        List<DriverCount> topDrivers = resolveTopDrivers(recentItems);
        Instant generatedAt = Instant.now(clock);
        Instant toPublishedAt = recentItems.get(0).publishedAt();
        Instant fromPublishedAt = recentItems.get(recentItems.size() - 1).publishedAt();
        Double confidence = applyPriceAwareConfidenceModifier(aggregation.confidence(), dominantSentiment);

        return Optional.of(new FeaturedMarketSummaryDto(
                buildHeadlineKo(dominantSentiment),
                buildHeadlineEn(dominantSentiment),
                buildSummaryKo(recentItems.size(), topDrivers),
                buildSummaryEn(recentItems.size(), topDrivers),
                generatedAt,
                recentItems.size(),
                resolveWindowHours(),
                fromPublishedAt,
                toPublishedAt,
                dominantSentiment,
                topDrivers.stream().map(DriverCount::chipLabel).toList(),
                recentItems.stream()
                        .map(NewsEvent::id)
                        .filter(StringUtils::hasText)
                        .toList(),
                null,
                null,
                confidence,
                false,
                null
        ));
    }

    List<NewsEvent> loadRecentAnalyzedNews() {
        return loadRecentAnalyzedNews(resolveWindowHours(), resolveMaxItems());
    }

    List<NewsEvent> loadRecentAnalyzedNews(int requestedWindowHours, int requestedMaxItems) {
        Instant cutoff = Instant.now(clock).minus(Duration.ofHours(requestedWindowHours > 0 ? requestedWindowHours : 3));
        int effectiveMaxItems = requestedMaxItems > 0 ? requestedMaxItems : 10;
        List<NewsEvent> analyzedCandidates = newsEventRepository.findByStatus(NewsStatus.ANALYZED).stream()
                .filter(event -> event.analysisResult() != null)
                .filter(event -> resolveSummaryBasisInstant(event) != null)
                .toList();
        List<NewsEvent> recentItems = analyzedCandidates.stream()
                .filter(event -> !resolveSummaryBasisInstant(event).isBefore(cutoff))
                .sorted(Comparator.comparing(this::resolveSummaryBasisInstant, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(effectiveMaxItems)
                .toList();
        log.debug("[FEATURED_SUMMARY] queryBasis=analysisResult.createdAt|ingestedAt cutoff={} analyzedCandidates={} recentCandidates={}",
                cutoff, analyzedCandidates.size(), recentItems.size());
        return recentItems;
    }

    Instant resolveSummaryBasisInstant(NewsEvent event) {
        if (event == null || event.analysisResult() == null) {
            return null;
        }
        return event.analysisResult().createdAt() != null
                ? event.analysisResult().createdAt()
                : event.ingestedAt();
    }

    private SentimentAggregation resolveDominantSentiment(List<NewsEvent> recentItems) {
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
            return new SentimentAggregation(SignalSentiment.NEUTRAL, null);
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
            return new SentimentAggregation(
                    SignalSentiment.NEUTRAL,
                    calculateConfidence(neutral, Math.max(positive, negative), total)
            );
        }

        Double confidence = calculateConfidence(directionalMax, Math.max(neutral, directionalMin), total);
        if (directionalCandidate == SignalSentiment.NEGATIVE
                && recentItems.size() >= CRISIS_BOOST_MIN_ITEM_COUNT
                && (directionalMax - directionalMin) >= CRISIS_BOOST_EDGE_THRESHOLD
                && directionalMax >= (neutral * CRISIS_BOOST_NEGATIVE_NEUTRAL_RATIO)) {
            confidence = Math.max(0.18d, Math.min(confidence + CRISIS_BOOST_CONFIDENCE, 0.94d));
        }

        return new SentimentAggregation(
                directionalCandidate,
                confidence
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

    private Double applyPriceAwareConfidenceModifier(Double baseConfidence, SignalSentiment dominantSentiment) {
        if (baseConfidence == null || dominantSentiment == SignalSentiment.NEUTRAL) {
            return baseConfidence;
        }
        try {
            FxSnapshotDto usdKrw = marketDataFacade.getUsdKrw().orElse(null);
            GoldSnapshotDto gold = marketDataFacade.getGold().orElse(null);
            OilSnapshotDto oil = marketDataFacade.getOil().orElse(null);
            IndexSnapshotDto kospi = marketDataFacade.getKospi().orElse(null);

            int alignedSignals = 0;

            if (dominantSentiment == SignalSentiment.NEGATIVE) {
                if (usdKrw != null && usdKrw.rate() >= USD_KRW_NEGATIVE_LEVEL) {
                    alignedSignals++;
                }
                if (gold != null && gold.usdPerOunce() >= GOLD_NEGATIVE_LEVEL) {
                    alignedSignals++;
                }
                Double oilPrice = oil == null
                        ? null
                        : (oil.wtiUsd() != null ? oil.wtiUsd() : oil.brentUsd());
                if (oilPrice != null && oilPrice >= OIL_NEGATIVE_LEVEL) {
                    alignedSignals++;
                }
                if (kospi != null && kospi.price() != null && kospi.price() <= KOSPI_NEGATIVE_LEVEL) {
                    alignedSignals++;
                }
            } else if (dominantSentiment == SignalSentiment.POSITIVE) {
                if (usdKrw != null && usdKrw.rate() <= USD_KRW_POSITIVE_LEVEL) {
                    alignedSignals++;
                }
                if (kospi != null && kospi.price() != null && kospi.price() >= KOSPI_POSITIVE_LEVEL) {
                    alignedSignals++;
                }
            }

            if (alignedSignals == 0) {
                return baseConfidence;
            }

            double boost = Math.min(MAX_MARKET_CONFIDENCE_BOOST, alignedSignals * 0.03d);
            return Math.min(1.0d, baseConfidence + boost);
        } catch (RuntimeException ex) {
            log.debug("[FEATURED_SUMMARY] price-aware confidence modifier skipped", ex);
            return baseConfidence;
        }
    }

    private List<DriverCount> resolveTopDrivers(List<NewsEvent> recentItems) {
        Map<String, DriverCount> counts = new LinkedHashMap<>();
        for (NewsEvent event : recentItems) {
            AnalysisResult analysisResult = event.analysisResult();
            if (analysisResult == null) {
                continue;
            }
            collectMacroDrivers(counts, analysisResult.macroImpacts());
            collectMarketDrivers(counts, analysisResult.marketImpacts());
        }
        return counts.values().stream()
                .sorted(Comparator.comparingInt(DriverCount::count).reversed()
                        .thenComparing(DriverCount::chipLabel, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_KEY_DRIVERS)
                .toList();
    }

    private void collectMacroDrivers(Map<String, DriverCount> counts, List<MacroImpact> impacts) {
        if (impacts == null) {
            return;
        }
        for (MacroImpact impact : impacts) {
            if (impact == null || impact.variable() == null) {
                continue;
            }
            String key = "macro:" + impact.variable().name();
            counts.compute(key, (ignored, existing) -> existing == null
                    ? new DriverCount(
                    driverChipLabel(impact.variable()),
                    driverLabelKo(impact.variable()),
                    driverLabelEn(impact.variable()),
                    1)
                    : existing.increment());
        }
    }

    private void collectMarketDrivers(Map<String, DriverCount> counts, List<MarketImpact> impacts) {
        if (impacts == null) {
            return;
        }
        for (MarketImpact impact : impacts) {
            if (impact == null || impact.market() == null) {
                continue;
            }
            String key = "market:" + impact.market().name();
            counts.compute(key, (ignored, existing) -> existing == null
                    ? new DriverCount(
                    driverChipLabel(impact.market()),
                    driverLabelKo(impact.market()),
                    driverLabelEn(impact.market()),
                    1)
                    : existing.increment());
        }
    }

    private String buildHeadlineKo(SignalSentiment dominantSentiment) {
        return switch (dominantSentiment) {
            case POSITIVE -> "\uCD5C\uADFC \uAC70\uC2DC \uC2E0\uD638\uB294 \uAE0D\uC815 \uCABD\uC73C\uB85C \uAE30\uC6B8\uACE0 \uC788\uC2B5\uB2C8\uB2E4";
            case NEGATIVE -> "\uCD5C\uADFC \uAC70\uC2DC \uC2E0\uD638\uB294 \uBC29\uC5B4\uC801\uC73C\uB85C \uAE30\uC6B8\uACE0 \uC788\uC2B5\uB2C8\uB2E4";
            case NEUTRAL -> "\uCD5C\uADFC \uAC70\uC2DC \uC2E0\uD638\uB294 \uD63C\uC870 \uD750\uB984\uC785\uB2C8\uB2E4";
        };
    }

    private String buildHeadlineEn(SignalSentiment dominantSentiment) {
        return switch (dominantSentiment) {
            case POSITIVE -> "Recent macro signals lean positive";
            case NEGATIVE -> "Recent macro signals lean defensive";
            case NEUTRAL -> "Recent macro signals remain mixed";
        };
    }

    private String buildSummaryKo(int sourceCount, List<DriverCount> topDrivers) {
        String base = "\uCD5C\uADFC " + resolveWindowHours()
                + "\uC2DC\uAC04 \uB3D9\uC548 \uBD84\uC11D\uB41C \uAE30\uC0AC " + sourceCount
                + "\uAC74\uC744 \uBC14\uD0D5\uC73C\uB85C \uC815\uB9AC\uD588\uC2B5\uB2C8\uB2E4";
        if (topDrivers.isEmpty()) {
            return base + ".";
        }
        return base + ". " + joinDriverLabels(topDrivers, true)
                + " \uC774(\uAC00) \uAC00\uC7A5 \uC790\uC8FC \uB4F1\uC7A5\uD588\uC2B5\uB2C8\uB2E4.";
    }

    private String buildSummaryEn(int sourceCount, List<DriverCount> topDrivers) {
        String base = "Built from " + sourceCount + " analyzed headlines over the last "
                + resolveWindowHours() + " hours";
        if (topDrivers.isEmpty()) {
            return base + ".";
        }
        return base + ", with " + joinDriverLabels(topDrivers, false) + " appearing most often.";
    }

    private String joinDriverLabels(List<DriverCount> topDrivers, boolean korean) {
        List<String> labels = new ArrayList<>();
        for (DriverCount driver : topDrivers) {
            labels.add(korean ? driver.labelKo() : driver.labelEn());
        }
        return String.join(" / ", labels);
    }

    private String driverChipLabel(MacroVariable variable) {
        return driverLabelEn(variable);
    }

    private String driverChipLabel(MarketType marketType) {
        return driverLabelEn(marketType);
    }

    private String driverLabelKo(MacroVariable variable) {
        return switch (variable) {
            case KOSPI -> "\uCF54\uC2A4\uD53C";
            case KOSDAQ -> "\uCF54\uC2A4\uB2E5";
            case VOLATILITY -> "\uBCC0\uB3D9\uC131";
            case OIL -> "\uC720\uAC00";
            case USD -> "\uB2EC\uB7EC";
            case INTEREST_RATE -> "\uAE08\uB9AC";
            case INFLATION -> "\uBB3C\uAC00";
            case GOLD -> "\uAE08";
        };
    }

    private String driverLabelEn(MacroVariable variable) {
        return switch (variable) {
            case KOSPI -> "KOSPI";
            case KOSDAQ -> "KOSDAQ";
            case VOLATILITY -> "Volatility";
            case OIL -> "Oil";
            case USD -> "USD";
            case INTEREST_RATE -> "Interest Rate";
            case INFLATION -> "Inflation";
            case GOLD -> "Gold";
        };
    }

    private String driverLabelKo(MarketType marketType) {
        return switch (marketType) {
            case KOSPI -> "\uCF54\uC2A4\uD53C";
            case US_EQUITIES -> "\uBBF8\uAD6D \uC8FC\uC2DD";
            case ENERGY_SECTOR -> "\uC5D0\uB108\uC9C0 \uC139\uD130";
            case TECH_SECTOR -> "\uAE30\uC220 \uC139\uD130";
        };
    }

    private String driverLabelEn(MarketType marketType) {
        return switch (marketType) {
            case KOSPI -> "KOSPI";
            case US_EQUITIES -> "US Equities";
            case ENERGY_SECTOR -> "Energy Sector";
            case TECH_SECTOR -> "Tech Sector";
        };
    }

    private int resolveWindowHours() {
        return windowHours > 0 ? windowHours : 3;
    }

    private int resolveMaxItems() {
        return maxItems > 0 ? maxItems : 10;
    }

    private int resolveMinItems() {
        return Math.max(1, minItems);
    }

    private record DriverCount(String chipLabel, String labelKo, String labelEn, int count) {

        private DriverCount increment() {
            return new DriverCount(chipLabel, labelKo, labelEn, count + 1);
        }
    }

    private record WeightedSentiment(
            SignalSentiment sentiment,
            double weight
    ) {
    }

    private record SentimentAggregation(
            SignalSentiment sentiment,
            Double confidence
    ) {
    }
}

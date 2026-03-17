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
import com.example.macronews.repository.NewsEventRepository;
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

    private final NewsEventRepository newsEventRepository;

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
            log.debug("[FEATURED_SUMMARY] skipped reason=insufficient-analyzed-news size={}", recentItems.size());
            return Optional.empty();
        }

        SignalSentiment dominantSentiment = resolveDominantSentiment(recentItems);
        List<DriverCount> topDrivers = resolveTopDrivers(recentItems);
        Instant generatedAt = Instant.now(clock);
        Instant toPublishedAt = recentItems.get(0).publishedAt();
        Instant fromPublishedAt = recentItems.get(recentItems.size() - 1).publishedAt();

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
                        .toList()
        ));
    }

    List<NewsEvent> loadRecentAnalyzedNews() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofHours(resolveWindowHours()));
        return newsEventRepository.findByStatus(NewsStatus.ANALYZED).stream()
                .filter(event -> event.analysisResult() != null)
                .filter(event -> event.publishedAt() != null && !event.publishedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(NewsEvent::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(resolveMaxItems())
                .toList();
    }

    private SignalSentiment resolveDominantSentiment(List<NewsEvent> recentItems) {
        Map<SignalSentiment, Integer> counts = new EnumMap<>(SignalSentiment.class);
        counts.put(SignalSentiment.POSITIVE, 0);
        counts.put(SignalSentiment.NEGATIVE, 0);
        counts.put(SignalSentiment.NEUTRAL, 0);

        for (NewsEvent event : recentItems) {
            SignalSentiment sentiment = resolvePrimarySentiment(event);
            counts.computeIfPresent(sentiment, (key, value) -> value + 1);
        }

        int positive = counts.getOrDefault(SignalSentiment.POSITIVE, 0);
        int negative = counts.getOrDefault(SignalSentiment.NEGATIVE, 0);
        int neutral = counts.getOrDefault(SignalSentiment.NEUTRAL, 0);

        if (positive > negative && positive > neutral) {
            return SignalSentiment.POSITIVE;
        }
        if (negative > positive && negative > neutral) {
            return SignalSentiment.NEGATIVE;
        }
        return SignalSentiment.NEUTRAL;
    }

    private SignalSentiment resolvePrimarySentiment(NewsEvent event) {
        AnalysisResult analysisResult = event.analysisResult();
        if (analysisResult == null) {
            return SignalSentiment.NEUTRAL;
        }

        if (analysisResult.macroImpacts() != null) {
            for (MacroImpact impact : analysisResult.macroImpacts()) {
                if (impact == null || impact.variable() == null || impact.direction() == null) {
                    continue;
                }
                return impact.variable().sentimentFor(impact.direction());
            }
        }

        if (analysisResult.marketImpacts() != null) {
            for (MarketImpact impact : analysisResult.marketImpacts()) {
                if (impact == null || impact.market() == null || impact.direction() == null) {
                    continue;
                }
                return impact.market().sentimentFor(impact.direction());
            }
        }

        return SignalSentiment.NEUTRAL;
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
            case POSITIVE -> "최근 거시 신호는 긍정 쪽으로 기웁니다";
            case NEGATIVE -> "최근 거시 신호는 방어적으로 기웁니다";
            case NEUTRAL -> "최근 거시 신호는 혼조 흐름입니다";
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
        String base = "최근 " + resolveWindowHours() + "시간 동안 분석된 기사 " + sourceCount + "건을 바탕으로 정리했습니다";
        if (topDrivers.isEmpty()) {
            return base + ".";
        }
        return base + ". " + joinDriverLabels(topDrivers, true) + " 이(가) 가장 자주 등장했습니다.";
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
            case KOSPI -> "코스피";
            case KOSDAQ -> "코스닥";
            case VOLATILITY -> "변동성";
            case OIL -> "유가";
            case USD -> "달러";
            case INTEREST_RATE -> "금리";
            case INFLATION -> "물가";
            case GOLD -> "금";
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
            case KOSPI -> "코스피";
            case US_EQUITIES -> "미국 주식";
            case ENERGY_SECTOR -> "에너지 섹터";
            case TECH_SECTOR -> "기술 섹터";
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
}

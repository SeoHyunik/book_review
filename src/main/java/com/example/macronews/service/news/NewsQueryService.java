package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.MarketSignalItemDto;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.repository.NewsEventRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NewsQueryService {

    private static final EnumSet<MacroVariable> REVERSE_DIRECTION_VARIABLES = EnumSet.of(
            MacroVariable.OIL,
            MacroVariable.USD,
            MacroVariable.INTEREST_RATE,
            MacroVariable.INFLATION,
            MacroVariable.VOLATILITY,
            MacroVariable.GOLD
    );

    private final NewsEventRepository newsEventRepository;

    public List<NewsListItemDto> getRecentNews() {
        return getRecentNews(null, NewsListSort.PUBLISHED_DESC);
    }

    public List<NewsListItemDto> getRecentNews(NewsStatus status) {
        return getRecentNews(status, NewsListSort.PUBLISHED_DESC);
    }

    public List<NewsListItemDto> getRecentNews(NewsStatus status, NewsListSort sort) {
        return loadCandidates(status).stream()
                .sorted(buildComparator(sort))
                .limit(20)
                .map(this::toListItem)
                .toList();
    }

    public MarketSignalOverviewDto getMarketSignalOverview(NewsStatus status, NewsListSort sort) {
        List<NewsEvent> recentAnalyzed = loadCandidates(status).stream()
                .sorted(buildComparator(sort))
                .limit(20)
                .filter(event -> event.status() == NewsStatus.ANALYZED)
                .filter(event -> event.analysisResult() != null)
                .toList();

        List<MarketSignalItemDto> items = Arrays.stream(MacroVariable.values())
                .map(variable -> aggregateSignal(variable, recentAnalyzed))
                .toList();

        return new MarketSignalOverviewDto(items);
    }

    public AutoIngestionBatchStatusDto getAutoIngestionBatchStatus(int requestedCount, int returnedCount, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return new AutoIngestionBatchStatusDto(requestedCount, returnedCount, 0, 0, 0, 0, true, List.of());
        }

        Map<String, NewsEvent> eventsById = StreamSupport
                .stream(newsEventRepository.findAllById(itemIds).spliterator(), false)
                .collect(Collectors.toMap(NewsEvent::id, Function.identity()));

        List<NewsListItemDto> items = itemIds.stream()
                .map(eventsById::get)
                .filter(Objects::nonNull)
                .map(this::toListItem)
                .toList();

        int ingestedCount = countByStatus(items, NewsStatus.INGESTED);
        int analyzedCount = countByStatus(items, NewsStatus.ANALYZED);
        int failedCount = countByStatus(items, NewsStatus.FAILED);
        int pendingCount = ingestedCount;
        boolean completed = pendingCount == 0;

        return new AutoIngestionBatchStatusDto(
                requestedCount,
                returnedCount,
                ingestedCount,
                analyzedCount,
                failedCount,
                pendingCount,
                completed,
                items
        );
    }

    @Cacheable(cacheNames = "newsDetail", key = "#id")
    public Optional<NewsDetailDto> getNewsDetail(String id) {
        return newsEventRepository.findById(id).map(this::toDetail);
    }

    public List<NewsListItemDto> getNewsItemsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<String, NewsEvent> eventsById = StreamSupport
                .stream(newsEventRepository.findAllById(ids).spliterator(), false)
                .collect(Collectors.toMap(NewsEvent::id, Function.identity()));

        return ids.stream()
                .map(eventsById::get)
                .filter(Objects::nonNull)
                .map(this::toListItem)
                .toList();
    }

    private List<NewsEvent> loadCandidates(NewsStatus status) {
        return status == null
                ? newsEventRepository.findTop20ByOrderByPublishedAtDesc()
                : newsEventRepository.findByStatus(status);
    }

    private Comparator<NewsEvent> buildComparator(NewsListSort sort) {
        NewsListSort resolvedSort = sort == null ? NewsListSort.PUBLISHED_DESC : sort;
        return switch (resolvedSort) {
            case PUBLISHED_ASC -> Comparator.comparing(NewsEvent::publishedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(NewsEvent::ingestedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case PRIORITY -> Comparator.comparingInt(this::calculatePriorityScore)
                    .reversed()
                    .thenComparing(NewsEvent::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case INGESTED_DESC -> Comparator.comparing(NewsEvent::ingestedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(NewsEvent::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case PUBLISHED_DESC -> Comparator.comparing(NewsEvent::publishedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(NewsEvent::ingestedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    private NewsListItemDto toListItem(NewsEvent event) {
        boolean hasAnalysis = event.analysisResult() != null;
        String macroSummary = buildMacroSummary(event);
        return new NewsListItemDto(
                event.id(),
                event.title(),
                event.source(),
                event.publishedAt(),
                event.ingestedAt(),
                event.status(),
                hasAnalysis,
                StringUtils.hasText(event.url()),
                macroSummary,
                buildInterpretationSummary(event, macroSummary),
                calculatePriorityScore(event)
        );
    }

    private NewsDetailDto toDetail(NewsEvent event) {
        return new NewsDetailDto(
                event.id(),
                event.title(),
                event.summary(),
                event.source(),
                event.url(),
                event.publishedAt(),
                event.status(),
                event.analysisResult()
        );
    }

    private String buildInterpretationSummary(NewsEvent event, String macroSummary) {
        AnalysisResult analysisResult = event.analysisResult();
        if (analysisResult == null) {
            return macroSummary;
        }

        Locale locale = LocaleContextHolder.getLocale();
        boolean korean = locale != null && "ko".equalsIgnoreCase(locale.getLanguage());
        String preferred = korean ? analysisResult.summaryKo() : analysisResult.summaryEn();
        String fallback = korean ? analysisResult.summaryEn() : analysisResult.summaryKo();

        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        if (StringUtils.hasText(fallback)) {
            return fallback;
        }
        return macroSummary;
    }

    private String buildMacroSummary(NewsEvent event) {
        if (event.analysisResult() == null || event.analysisResult().macroImpacts() == null
                || event.analysisResult().macroImpacts().isEmpty()) {
            return "";
        }
        MacroImpact first = event.analysisResult().macroImpacts().get(0);
        if (first == null || first.variable() == null || first.direction() == null) {
            return "";
        }
        String summary = first.variable().name() + " " + first.direction().name();
        return StringUtils.hasText(summary) ? summary : "";
    }

    private MarketSignalItemDto aggregateSignal(MacroVariable variable, List<NewsEvent> recentAnalyzed) {
        Map<ImpactDirection, Integer> counts = new EnumMap<>(ImpactDirection.class);
        counts.put(ImpactDirection.UP, 0);
        counts.put(ImpactDirection.DOWN, 0);
        counts.put(ImpactDirection.NEUTRAL, 0);

        int sampleCount = 0;
        for (NewsEvent event : recentAnalyzed) {
            if (event.analysisResult() == null || event.analysisResult().macroImpacts() == null) {
                continue;
            }
            for (MacroImpact impact : event.analysisResult().macroImpacts()) {
                if (impact == null || impact.variable() != variable || impact.direction() == null) {
                    continue;
                }
                counts.computeIfPresent(impact.direction(), (key, value) -> value + 1);
                sampleCount++;
            }
        }

        ImpactDirection dominantDirection = resolveDominantDirection(counts);
        if (REVERSE_DIRECTION_VARIABLES.contains(variable)) {
            dominantDirection = invertDirection(dominantDirection);
        }
        return new MarketSignalItemDto(variable, dominantDirection, sampleCount);
    }

    private ImpactDirection resolveDominantDirection(Map<ImpactDirection, Integer> counts) {
        int up = counts.getOrDefault(ImpactDirection.UP, 0);
        int down = counts.getOrDefault(ImpactDirection.DOWN, 0);
        int neutral = counts.getOrDefault(ImpactDirection.NEUTRAL, 0);

        if (up > down && up > neutral) {
            return ImpactDirection.UP;
        }
        if (down > up && down > neutral) {
            return ImpactDirection.DOWN;
        }
        return ImpactDirection.NEUTRAL;
    }

    private ImpactDirection invertDirection(ImpactDirection direction) {
        if (direction == ImpactDirection.UP) {
            return ImpactDirection.DOWN;
        }
        if (direction == ImpactDirection.DOWN) {
            return ImpactDirection.UP;
        }
        return ImpactDirection.NEUTRAL;
    }

    private int calculatePriorityScore(NewsEvent event) {
        String title = normalize(event.title());
        String summary = normalize(event.summary());
        String source = normalize(event.source());

        int score = 0;
        score += scoreKeywords(title, 8,
                "south korea", "korea", "kospi", "krw", "won");
        score += scoreKeywords(summary, 5,
                "south korea", "korea", "kospi", "krw", "won");
        score += scoreKeywords(source, 3,
                "south korea", "korea", "kospi", "krw", "won");

        score += scoreKeywords(title, 6,
                "semiconductor", "chip", "memory", "samsung", "sk hynix", "battery", "ev", "auto",
                "shipbuilding", "ai");
        score += scoreKeywords(summary, 4,
                "semiconductor", "chip", "memory", "samsung", "sk hynix", "battery", "ev", "auto",
                "shipbuilding", "ai");
        score += scoreKeywords(source, 2,
                "semiconductor", "chip", "memory", "samsung", "sk hynix", "battery", "ev", "auto",
                "shipbuilding", "ai");

        score += scoreKeywords(title, 4,
                "oil", "energy", "inflation", "cpi", "ppi", "rate", "interest rate", "fed", "usd",
                "dollar", "fx");
        score += scoreKeywords(summary, 3,
                "oil", "energy", "inflation", "cpi", "ppi", "rate", "interest rate", "fed", "usd",
                "dollar", "fx");
        score += scoreKeywords(source, 1,
                "oil", "energy", "inflation", "cpi", "ppi", "rate", "interest rate", "fed", "usd",
                "dollar", "fx");

        score += scoreKeywords(title, 5,
                "tariff", "trade", "export", "china", "us", "sanctions", "defense", "geopolitics");
        score += scoreKeywords(summary, 3,
                "tariff", "trade", "export", "china", "us", "sanctions", "defense", "geopolitics");
        score += scoreKeywords(source, 1,
                "tariff", "trade", "export", "china", "us", "sanctions", "defense", "geopolitics");

        if (containsKeyword(title, "korea")
                && containsAnyKeyword(title, "semiconductor", "chip", "memory", "samsung", "sk hynix")) {
            score += 5;
        }
        if (containsKeyword(summary, "korea")
                && containsAnyKeyword(summary, "trade", "export", "china", "us", "tariff")) {
            score += 4;
        }
        if (containsAnyKeyword(title, "kospi", "krw", "won")) {
            score += 6;
        }

        return score;
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

    private int countByStatus(List<NewsListItemDto> items, NewsStatus status) {
        return (int) items.stream().filter(item -> item.status() == status).count();
    }
}

package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.MarketSignalItemDto;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.repository.NewsEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsQueryService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));
    private static final List<KeywordWeightRule> PRIORITY_WEIGHT_RULES = List.of(
            new KeywordWeightRule(8, 5, 3,
                    "south korea", "korea", "kospi", "krw", "won"),
            new KeywordWeightRule(6, 4, 2,
                    "semiconductor", "chip", "memory", "samsung", "sk hynix", "battery", "ev", "auto",
                    "shipbuilding", "ai"),
            new KeywordWeightRule(9, 6, 3,
                    "fed", "fomc", "ecb", "boj", "bok", "central bank", "rate decision", "interest rate",
                    "cpi", "inflation", "ppi", "employment", "jobs", "payroll", "gdp", "recession", "slowdown"),
            new KeywordWeightRule(7, 5, 2,
                    "fx", "foreign exchange", "exchange rate", "usd", "dollar", "yen", "treasury",
                    "treasury yield", "bond yield", "oil", "crude", "brent", "wti", "commodity", "commodities"),
            new KeywordWeightRule(6, 4, 2,
                    "tariff", "trade", "export", "china", "sanctions", "geopolitics", "u.s.", "united states")
    );
    private static final List<KeywordWeightRule> NOISE_DEMOTION_RULES = List.of(
            new KeywordWeightRule(3, 2, 0,
                    "tips", "how to", "guide", "best way", "must try", "life hack", "checklist"),
            new KeywordWeightRule(3, 2, 0,
                    "festival", "event", "giveaway", "sale", "discount", "promotion", "opening"),
            new KeywordWeightRule(4, 2, 0,
                    "celebrity", "star", "romance", "wedding", "fashion", "beauty", "viral", "buzz"),
            new KeywordWeightRule(3, 2, 0,
                    "hot issue", "shocking", "surprising", "what happened", "you need to know", "attention")
    );

    private final NewsEventRepository newsEventRepository;

    @Value("${app.news.naver.max-age-hours:12}")
    private long naverMaxAgeHours;

    @Value("${app.news.global.max-age-hours:24}")
    private long globalMaxAgeHours;

    @Value("${app.news.naver.fallback-max-age-hours:24}")
    private long naverFallbackMaxAgeHours;

    @Value("${app.news.global.fallback-max-age-hours:36}")
    private long globalFallbackMaxAgeHours;

    private Clock clock = DEFAULT_CLOCK;

    void setClock(Clock clock) {
        this.clock = clock == null ? DEFAULT_CLOCK : clock;
    }

    public List<NewsListItemDto> getRecentNews() {
        return getRecentNews(null, NewsListSort.PUBLISHED_DESC);
    }

    public List<NewsListItemDto> getRecentNews(NewsStatus status) {
        return getRecentNews(status, NewsListSort.PUBLISHED_DESC);
    }

    public List<NewsListItemDto> getRecentNews(NewsStatus status, NewsListSort sort) {
        List<NewsEvent> candidates = loadCandidates(status);
        return candidates.stream()
                .filter(this::isDisplayEligible)
                .sorted(buildComparator(sort))
                .limit(20)
                .map(this::toListItem)
                .toList();
    }

    public MarketSignalOverviewDto getMarketSignalOverview(NewsStatus status, NewsListSort sort) {
        List<NewsEvent> candidates = loadCandidates(status);
        List<NewsEvent> recentAnalyzed = candidates.stream()
                .filter(this::isSignalEligible)
                .sorted(buildComparator(sort))
                .limit(20)
                .toList();

        if (log.isDebugEnabled()) {
            Instant cutoff = Instant.now(clock).minus(Duration.ofHours(Math.max(globalFallbackMaxAgeHours, naverFallbackMaxAgeHours)));
            log.debug("[NEWS-QUERY] displayCandidates={} signalCandidates={} signalBasis=analysisResult.createdAt|ingestedAt|publishedAt cutoff={}",
                    candidates.size(), recentAnalyzed.size(), cutoff);
        }

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
                ? newsEventRepository.findTop20ByOrderByIngestedAtDesc()
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
        MacroImpact primaryImpact = findPrimaryMacroImpact(event.analysisResult());
        ImpactDirection primaryDirection = primaryImpact == null ? ImpactDirection.NEUTRAL : primaryImpact.direction();
        SignalSentiment primarySentiment = primaryImpact == null
                ? SignalSentiment.NEUTRAL
                : primaryImpact.variable().sentimentFor(primaryImpact.direction());
        String macroSummary = buildMacroSummary(primaryImpact);
        String preferredSummary = resolvePreferredInterpretationSummary(event.analysisResult());
        String preferredHeadline = resolvePreferredHeadline(event.analysisResult());
        String displayTitle = buildDisplayTitle(event, preferredHeadline, preferredSummary);
        return new NewsListItemDto(
                event.id(),
                event.title(),
                displayTitle,
                event.source(),
                event.publishedAt(),
                event.ingestedAt(),
                event.status(),
                hasAnalysis,
                StringUtils.hasText(event.url()),
                primaryDirection,
                primarySentiment,
                macroSummary,
                buildInterpretationSummary(event, macroSummary, preferredSummary, displayTitle),
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

    private String buildInterpretationSummary(NewsEvent event, String macroSummary, String preferredSummary, String displayTitle) {
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

    private String resolvePreferredInterpretationSummary(AnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "";
        }
        Locale locale = LocaleContextHolder.getLocale();
        boolean korean = locale != null && "ko".equalsIgnoreCase(locale.getLanguage());
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

    private String resolvePreferredHeadline(AnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "";
        }
        Locale locale = LocaleContextHolder.getLocale();
        boolean korean = locale != null && "ko".equalsIgnoreCase(locale.getLanguage());
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

    private String buildDisplayTitle(NewsEvent event, String preferredHeadline, String preferredSummary) {
        if (StringUtils.hasText(preferredHeadline)) {
            return preferredHeadline;
        }
        String derivedTitle = extractLeadingSentence(preferredSummary);
        if (StringUtils.hasText(derivedTitle)) {
            return derivedTitle;
        }
        return StringUtils.hasText(event.title()) ? event.title() : "";
    }

    private MacroImpact findPrimaryMacroImpact(AnalysisResult analysisResult) {
        if (analysisResult == null || analysisResult.macroImpacts() == null || analysisResult.macroImpacts().isEmpty()) {
            return null;
        }
        return analysisResult.macroImpacts().stream()
                .filter(impact -> impact != null && impact.variable() != null && impact.direction() != null)
                .findFirst()
                .orElse(null);
    }

    private String buildMacroSummary(MacroImpact primaryImpact) {
        if (primaryImpact == null) {
            return "";
        }
        String summary = primaryImpact.variable().name() + " " + primaryImpact.direction().name();
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
        return new MarketSignalItemDto(variable, dominantDirection, variable.sentimentFor(dominantDirection), sampleCount);
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

    private int calculatePriorityScore(NewsEvent event) {
        String title = normalize(event.title());
        String summary = normalize(event.summary());
        String source = normalize(event.source());
        String combined = combineText(title, summary);

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

        score -= calculateNoiseDemotion(title, summary, source, combined, score);
        return score;
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
                .collect(Collectors.joining(" "));
    }

    private int countByStatus(List<NewsListItemDto> items, NewsStatus status) {
        return (int) items.stream().filter(item -> item.status() == status).count();
    }

    private boolean isDisplayEligible(NewsEvent event) {
        Instant basis = resolveDisplayBasis(event);
        if (basis == null) {
            return false;
        }
        return !basis.isBefore(Instant.now(clock).minus(resolveDisplayMaxAge(event)));
    }

    private boolean isSignalEligible(NewsEvent event) {
        if (event == null || event.status() != NewsStatus.ANALYZED || event.analysisResult() == null) {
            return false;
        }
        Instant basis = resolveSignalBasis(event);
        if (basis == null) {
            return false;
        }
        return !basis.isBefore(Instant.now(clock).minus(resolveDisplayMaxAge(event)));
    }

    private Instant resolveDisplayBasis(NewsEvent event) {
        if (event == null) {
            return null;
        }
        if (event.ingestedAt() != null) {
            return event.ingestedAt();
        }
        return event.publishedAt();
    }

    private Instant resolveSignalBasis(NewsEvent event) {
        if (event == null) {
            return null;
        }
        if (event.analysisResult() != null && event.analysisResult().createdAt() != null) {
            return event.analysisResult().createdAt();
        }
        if (event.ingestedAt() != null) {
            return event.ingestedAt();
        }
        return event.publishedAt();
    }

    private Duration resolveDisplayMaxAge(NewsEvent event) {
        String source = event == null ? "" : normalize(event.source());
        boolean naver = "naver".equals(source);
        long hours = naver ? naverFallbackMaxAgeHours : globalFallbackMaxAgeHours;
        long fallbackHours = naver ? naverMaxAgeHours : globalMaxAgeHours;
        long resolvedHours = hours > 0 ? hours : fallbackHours;
        return Duration.ofHours(resolvedHours > 0 ? resolvedHours : (naver ? 24L : 36L));
    }

    private record KeywordWeightRule(
            int titleWeight,
            int summaryWeight,
            int sourceWeight,
            String... keywords
    ) {
    }
}

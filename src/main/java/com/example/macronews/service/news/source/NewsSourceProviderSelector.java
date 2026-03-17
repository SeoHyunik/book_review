package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsSourceProviderSelector {

    private static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock DEFAULT_CLOCK = Clock.system(DEFAULT_BUSINESS_ZONE);
    private static final String KOREAN_BREAKING_MARKER = "\uC18D\uBCF4";
    private static final int DEFAULT_DOMESTIC_START_HOUR = 5;
    private static final int DEFAULT_DOMESTIC_END_HOUR = 22;
    private static final int DEFAULT_BREAKING_BONUS_MINUTES = 45;
    private static final int DEFAULT_PREFERRED_SOURCE_BONUS_MINUTES = 15;

    private final List<NewsSourceProvider> providers;

    private Clock clock = DEFAULT_CLOCK;

    @Value("${app.ingestion.business-timezone:Asia/Seoul}")
    private String businessTimezone;

    @Value("${app.ingestion.domestic-start-hour:5}")
    private int domesticStartHour;

    @Value("${app.ingestion.domestic-end-hour:22}")
    private int domesticEndHour;

    @Value("${app.ingestion.breaking-bonus-minutes:45}")
    private int breakingBonusMinutes;

    @Value("${app.ingestion.preferred-source-bonus-minutes:15}")
    private int preferredSourceBonusMinutes;

    public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
        int resolvedLimit = Math.max(limit, 1);
        NewsFeedPriority preferredPriority = currentPriority();
        NewsFeedPriority fallbackPriority = preferredPriority == NewsFeedPriority.DOMESTIC
                ? NewsFeedPriority.FOREIGN
                : NewsFeedPriority.DOMESTIC;

        List<NewsSourceProvider> preferredProviders = selectConfiguredProviders(preferredPriority);
        List<NewsSourceProvider> fallbackProviders = selectConfiguredProviders(fallbackPriority);
        if (preferredProviders.isEmpty() && fallbackProviders.isEmpty()) {
            log.info("[NEWS-SOURCE] no configured provider available priority={}", preferredPriority);
            return List.of();
        }

        int fetchLimit = Math.max(resolvedLimit * 2, resolvedLimit + 2);
        Map<String, RankedNewsCandidate> ranked = new LinkedHashMap<>();
        int preferredReturned = collectCandidates(ranked, preferredProviders, preferredPriority, fetchLimit, true);
        if (preferredReturned == 0 && !preferredProviders.isEmpty() && !fallbackProviders.isEmpty()) {
            log.info("[NEWS-SOURCE] preferred provider returned 0 items; falling back to secondary providers");
        }
        collectCandidates(ranked, fallbackProviders, fallbackPriority, fetchLimit, false);

        return ranked.values().stream()
                .sorted(Comparator.comparing(RankedNewsCandidate::effectivePublishedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RankedNewsCandidate::publishedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RankedNewsCandidate::sourceCode))
                .limit(resolvedLimit)
                .map(RankedNewsCandidate::item)
                .toList();
    }

    public boolean isConfigured() {
        return providers.stream().anyMatch(NewsSourceProvider::isConfigured);
    }

    public Optional<NewsSourceProvider> selectCurrentProvider() {
        NewsFeedPriority preferredPriority = currentPriority();
        Optional<NewsSourceProvider> preferred = selectConfiguredProvider(preferredPriority);
        if (preferred.isPresent()) {
            return preferred;
        }

        NewsFeedPriority fallbackPriority = preferredPriority == NewsFeedPriority.DOMESTIC
                ? NewsFeedPriority.FOREIGN
                : NewsFeedPriority.DOMESTIC;
        Optional<NewsSourceProvider> fallback = selectConfiguredProvider(fallbackPriority);
        fallback.ifPresent(provider -> log.info(
                "[NEWS-SOURCE] fallback provider selected={} preferredPriority={} fallbackPriority={}",
                provider.sourceCode(), preferredPriority, fallbackPriority));
        return fallback;
    }

    NewsFeedPriority currentPriority() {
        return isDomesticWindow() ? NewsFeedPriority.DOMESTIC : NewsFeedPriority.FOREIGN;
    }

    boolean isDomesticWindow() {
        int startHour = resolveHour(domesticStartHour, DEFAULT_DOMESTIC_START_HOUR);
        int endHour = resolveHour(domesticEndHour, DEFAULT_DOMESTIC_END_HOUR);
        int currentHour = ZonedDateTime.now(resolveBusinessClock()).getHour();

        if (startHour <= endHour) {
            return currentHour >= startHour && currentHour <= endHour;
        }
        return currentHour >= startHour || currentHour <= endHour;
    }

    private Optional<NewsSourceProvider> selectConfiguredProvider(NewsFeedPriority priority) {
        return selectConfiguredProviders(priority).stream()
                .findFirst();
    }

    private List<NewsSourceProvider> selectConfiguredProviders(NewsFeedPriority priority) {
        return providers.stream()
                .filter(provider -> provider.supports(priority))
                .filter(NewsSourceProvider::isConfigured)
                .sorted(Comparator.comparing(NewsSourceProvider::sourceCode))
                .toList();
    }

    private int collectCandidates(Map<String, RankedNewsCandidate> ranked,
            List<NewsSourceProvider> selectedProviders,
            NewsFeedPriority priority,
            int fetchLimit,
            boolean preferredSource) {
        int returnedCount = 0;
        for (NewsSourceProvider provider : selectedProviders) {
            log.info("[NEWS-SOURCE] loading provider={} priority={} preferred={} limit={}",
                    provider.sourceCode(), priority, preferredSource, fetchLimit);
            List<ExternalNewsItem> fetched = provider.fetchTopHeadlines(fetchLimit);
            log.info("[NEWS-SOURCE] provider={} returned={}", provider.sourceCode(), fetched.size());
            returnedCount += fetched.size();
            for (ExternalNewsItem item : fetched) {
                RankedNewsCandidate candidate = rank(item, provider.sourceCode(), preferredSource);
                ranked.merge(resolveDedupKey(item), candidate, this::selectBetterCandidate);
            }
        }
        return returnedCount;
    }

    private RankedNewsCandidate rank(ExternalNewsItem item, String sourceCode, boolean preferredSource) {
        Duration preferredBonus = preferredSource
                ? Duration.ofMinutes(resolveBonus(preferredSourceBonusMinutes, DEFAULT_PREFERRED_SOURCE_BONUS_MINUTES))
                : Duration.ZERO;
        Duration breakingBonus = isBreaking(item)
                ? Duration.ofMinutes(resolveBonus(breakingBonusMinutes, DEFAULT_BREAKING_BONUS_MINUTES))
                : Duration.ZERO;
        Instant publishedAt = item == null ? null : item.publishedAt();
        Instant effectivePublishedAt = publishedAt == null
                ? null
                : publishedAt.plus(preferredBonus).plus(breakingBonus);
        return new RankedNewsCandidate(item, sourceCode, publishedAt, effectivePublishedAt);
    }

    private RankedNewsCandidate selectBetterCandidate(RankedNewsCandidate left, RankedNewsCandidate right) {
        Comparator<RankedNewsCandidate> comparator = Comparator
                .comparing(RankedNewsCandidate::effectivePublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedNewsCandidate::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedNewsCandidate::sourceCode);
        return comparator.compare(left, right) <= 0 ? left : right;
    }

    private String resolveDedupKey(ExternalNewsItem item) {
        if (item == null) {
            return "";
        }
        if (StringUtils.hasText(item.url())) {
            return item.url().trim();
        }
        if (StringUtils.hasText(item.externalId())) {
            return item.externalId().trim();
        }
        String normalizedTitle = item.title() == null ? "" : normalizeTitle(item.title());
        return (item.source() == null ? "" : item.source().trim().toLowerCase(Locale.ROOT)) + "|" + normalizedTitle;
    }

    private boolean isBreaking(ExternalNewsItem item) {
        if (item == null || item.title() == null) {
            return false;
        }
        String normalizedTitle = item.title().trim().toLowerCase(Locale.ROOT);
        return normalizedTitle.contains(KOREAN_BREAKING_MARKER.toLowerCase(Locale.ROOT))
                || normalizedTitle.matches(".*\\bbreaking\\b.*");
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim()
                .replaceAll("\\[(?:\\uC18D\\uBCF4|(?i:breaking))]", " ")
                .replace(KOREAN_BREAKING_MARKER, " ")
                .replaceAll("(?i)\\bbreaking\\b", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private int resolveBonus(int configuredValue, int fallbackValue) {
        return configuredValue >= 0 ? configuredValue : fallbackValue;
    }

    private int resolveHour(int configuredHour, int fallbackHour) {
        return configuredHour >= 0 && configuredHour <= 23 ? configuredHour : fallbackHour;
    }

    private Clock resolveBusinessClock() {
        return clock.withZone(resolveBusinessZone());
    }

    private ZoneId resolveBusinessZone() {
        try {
            return ZoneId.of(businessTimezone);
        } catch (Exception ex) {
            return DEFAULT_BUSINESS_ZONE;
        }
    }

    private record RankedNewsCandidate(
            ExternalNewsItem item,
            String sourceCode,
            Instant publishedAt,
            Instant effectivePublishedAt
    ) {
    }
}

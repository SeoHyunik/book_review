package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsSourceProviderSelector {

    private static final Clock DEFAULT_CLOCK = Clock.system(java.time.ZoneId.of("Asia/Seoul"));
    private static final int DEFAULT_DOMESTIC_START_HOUR = 5;
    private static final int DEFAULT_DOMESTIC_END_HOUR = 22;

    private final List<NewsSourceProvider> providers;

    private Clock clock = DEFAULT_CLOCK;

    @Value("${app.ingestion.domestic-start-hour:5}")
    private int domesticStartHour;

    @Value("${app.ingestion.domestic-end-hour:22}")
    private int domesticEndHour;

    public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
        return selectCurrentProvider()
                .map(provider -> {
                    log.info("[NEWS-SOURCE] selected={} priority={} limit={}", provider.sourceCode(),
                            currentPriority(), limit);
                    return provider.fetchTopHeadlines(limit);
                })
                .orElseGet(() -> {
                    log.info("[NEWS-SOURCE] no configured provider available priority={}", currentPriority());
                    return List.of();
                });
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
        int currentHour = ZonedDateTime.now(clock).getHour();

        if (startHour <= endHour) {
            return currentHour >= startHour && currentHour <= endHour;
        }
        return currentHour >= startHour || currentHour <= endHour;
    }

    private Optional<NewsSourceProvider> selectConfiguredProvider(NewsFeedPriority priority) {
        return providers.stream()
                .filter(provider -> provider.supports(priority))
                .filter(NewsSourceProvider::isConfigured)
                .sorted(Comparator.comparing(NewsSourceProvider::sourceCode))
                .findFirst();
    }

    private int resolveHour(int configuredHour, int fallbackHour) {
        return configuredHour >= 0 && configuredHour <= 23 ? configuredHour : fallbackHour;
    }
}

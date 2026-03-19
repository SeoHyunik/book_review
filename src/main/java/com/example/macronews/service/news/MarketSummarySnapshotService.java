package com.example.macronews.service.news;

import com.example.macronews.domain.MarketSummarySnapshot;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.MarketSummaryDetailDto;
import com.example.macronews.dto.MarketSummarySupportingNewsDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.repository.MarketSummarySnapshotRepository;
import com.example.macronews.repository.NewsEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSummarySnapshotService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final MarketSummarySnapshotRepository marketSummarySnapshotRepository;
    private final NewsEventRepository newsEventRepository;
    private final AiMarketSummaryService aiMarketSummaryService;
    private final NewsQueryService newsQueryService;

    @Value("${app.featured.market-summary.snapshot-enabled:true}")
    private boolean snapshotEnabled;

    @Value("${app.featured.market-summary.snapshot-read-enabled:true}")
    private boolean snapshotReadEnabled;

    @Value("${app.featured.market-summary.snapshot-max-age-minutes:180}")
    private int snapshotMaxAgeMinutes;

    private Clock clock = DEFAULT_CLOCK;

    public Optional<FeaturedMarketSummaryDto> getLatestValidSummary() {
        if (!snapshotEnabled || !snapshotReadEnabled) {
            return Optional.empty();
        }

        return marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc()
                .filter(this::isFresh)
                .map(this::toDto);
    }

    public ScheduledRefreshDecision evaluateScheduledRefresh() {
        Optional<MarketSummarySnapshot> latestValidSnapshot =
                marketSummarySnapshotRepository.findTopByValidTrueOrderByGeneratedAtDesc();
        if (latestValidSnapshot.isEmpty()) {
            return ScheduledRefreshDecision.run("no-previous-valid-snapshot", null, resolveLatestAnalyzedNewsBasis());
        }

        Instant latestSnapshotGeneratedAt = latestValidSnapshot.get().generatedAt();
        if (latestSnapshotGeneratedAt == null) {
            return ScheduledRefreshDecision.run("latest-valid-snapshot-missing-generatedAt", null, resolveLatestAnalyzedNewsBasis());
        }

        Instant latestAnalyzedNewsBasis = resolveLatestAnalyzedNewsBasis();
        if (latestAnalyzedNewsBasis == null) {
            return ScheduledRefreshDecision.skip("no-analyzed-news-since-latest-valid-snapshot",
                    latestSnapshotGeneratedAt, null);
        }
        if (!latestAnalyzedNewsBasis.isAfter(latestSnapshotGeneratedAt)) {
            return ScheduledRefreshDecision.skip("no-new-analyzed-news-since-latest-valid-snapshot",
                    latestSnapshotGeneratedAt, latestAnalyzedNewsBasis);
        }
        return ScheduledRefreshDecision.run("new-analyzed-news-detected",
                latestSnapshotGeneratedAt, latestAnalyzedNewsBasis);
    }

    public Optional<MarketSummarySnapshot> refreshSnapshot() {
        if (!snapshotEnabled) {
            log.debug("[MARKET_SUMMARY_SNAPSHOT] skipped reason=snapshot-disabled");
            return Optional.empty();
        }

        Optional<FeaturedMarketSummaryDto> generated = aiMarketSummaryService.generateCurrentSummary();
        if (generated.isEmpty()) {
            log.info("[MARKET_SUMMARY_SNAPSHOT] skipped reason=ai-summary-unavailable");
            return Optional.empty();
        }

        MarketSummarySnapshot snapshot = new MarketSummarySnapshot(
                null,
                generated.get().generatedAt(),
                generated.get().windowHours(),
                generated.get().sourceCount(),
                generated.get().fromPublishedAt(),
                generated.get().toPublishedAt(),
                generated.get().headlineKo(),
                generated.get().headlineEn(),
                generated.get().summaryKo(),
                generated.get().summaryEn(),
                generated.get().marketViewKo(),
                generated.get().marketViewEn(),
                generated.get().dominantSentiment(),
                generated.get().keyDrivers(),
                generated.get().supportingNewsIds(),
                generated.get().confidence(),
                true,
                true,
                aiMarketSummaryService.getConfiguredModel()
        );
        MarketSummarySnapshot saved = marketSummarySnapshotRepository.save(snapshot);
        log.info("[MARKET_SUMMARY_SNAPSHOT] saved id={} generatedAt={} sourceCount={}",
                saved.id(), saved.generatedAt(), saved.sourceCount());
        return Optional.of(saved);
    }

    public Optional<MarketSummaryDetailDto> getSnapshotDetail(String id) {
        return marketSummarySnapshotRepository.findById(id)
                .filter(snapshot -> snapshot.valid())
                .map(snapshot -> new MarketSummaryDetailDto(
                        snapshot.id(),
                        snapshot.generatedAt(),
                        snapshot.sourceCount(),
                        snapshot.windowHours(),
                        snapshot.headlineKo(),
                        snapshot.headlineEn(),
                        snapshot.summaryKo(),
                        snapshot.summaryEn(),
                        snapshot.marketViewKo(),
                        snapshot.marketViewEn(),
                        snapshot.dominantSentiment(),
                        snapshot.confidence(),
                        snapshot.keyDrivers(),
                        mapSupportingNews(newsQueryService.getNewsItemsByIds(snapshot.supportingNewsIds()))
                ));
    }

    boolean isFresh(MarketSummarySnapshot snapshot) {
        if (snapshot == null || snapshot.generatedAt() == null) {
            return false;
        }
        return !snapshot.generatedAt()
                .plus(resolveMaxAge())
                .isBefore(Instant.now(clock));
    }

    FeaturedMarketSummaryDto toDto(MarketSummarySnapshot snapshot) {
        return new FeaturedMarketSummaryDto(
                snapshot.headlineKo(),
                snapshot.headlineEn(),
                snapshot.summaryKo(),
                snapshot.summaryEn(),
                snapshot.generatedAt(),
                snapshot.sourceCount(),
                snapshot.windowHours(),
                snapshot.fromPublishedAt(),
                snapshot.toPublishedAt(),
                snapshot.dominantSentiment(),
                snapshot.keyDrivers(),
                snapshot.supportingNewsIds(),
                snapshot.marketViewKo(),
                snapshot.marketViewEn(),
                snapshot.confidence(),
                snapshot.aiSynthesized(),
                snapshot.id()
        );
    }

    private Duration resolveMaxAge() {
        return Duration.ofMinutes(snapshotMaxAgeMinutes > 0 ? snapshotMaxAgeMinutes : 180L);
    }

    private Instant resolveLatestAnalyzedNewsBasis() {
        return newsEventRepository.findByStatus(NewsStatus.ANALYZED).stream()
                .map(this::resolveAnalyzedNewsBasis)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant resolveAnalyzedNewsBasis(NewsEvent event) {
        if (event == null || event.status() != NewsStatus.ANALYZED) {
            return null;
        }
        AnalysisResult analysisResult = event.analysisResult();
        if (analysisResult != null && analysisResult.createdAt() != null) {
            return analysisResult.createdAt();
        }
        return event.ingestedAt();
    }

    private List<MarketSummarySupportingNewsDto> mapSupportingNews(List<NewsListItemDto> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new MarketSummarySupportingNewsDto(
                        item.id(),
                        item.displayTitle() != null && !item.displayTitle().isBlank() ? item.displayTitle() : item.title(),
                        item.source(),
                        item.publishedAt(),
                        item.primaryDirection(),
                        item.primarySentiment()
                ))
                .toList();
    }

    public record ScheduledRefreshDecision(
            boolean shouldRefresh,
            String reason,
            Instant latestSnapshotGeneratedAt,
            Instant latestAnalyzedNewsBasis
    ) {
        public static ScheduledRefreshDecision run(String reason, Instant latestSnapshotGeneratedAt, Instant latestAnalyzedNewsBasis) {
            return new ScheduledRefreshDecision(true, reason, latestSnapshotGeneratedAt, latestAnalyzedNewsBasis);
        }

        public static ScheduledRefreshDecision skip(String reason, Instant latestSnapshotGeneratedAt, Instant latestAnalyzedNewsBasis) {
            return new ScheduledRefreshDecision(false, reason, latestSnapshotGeneratedAt, latestAnalyzedNewsBasis);
        }
    }
}

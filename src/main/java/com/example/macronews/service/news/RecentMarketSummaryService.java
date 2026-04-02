package com.example.macronews.service.news;

import com.example.macronews.config.policy.FeaturedMarketSummaryPolicyProperties;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.market.MarketDataFacade;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecentMarketSummaryService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final NewsEventRepository newsEventRepository;
    private final MarketDataFacade marketDataFacade;
    private final FeaturedMarketSummaryPolicyProperties policyProperties;
    private final MarketSentimentAggregator marketSentimentAggregator;
    private final MarketPriceAdjustmentPolicy marketPriceAdjustmentPolicy;
    private final MarketDriverExtractor marketDriverExtractor;
    private final MarketSummaryComposer marketSummaryComposer;

    private Clock clock = DEFAULT_CLOCK;

    public Optional<FeaturedMarketSummaryDto> getCurrentSummary() {
        if (!policyProperties.isEnabled()) {
            return Optional.empty();
        }

        List<NewsEvent> recentItems = loadRecentAnalyzedNews();
        if (recentItems.size() < resolveMinItems()) {
            log.info("[FEATURED_SUMMARY] skipped reason=insufficient-analyzed-news queryBasis=analysisResult.createdAt|ingestedAt size={} minItems={}",
                    recentItems.size(), resolveMinItems());
            return Optional.empty();
        }

        MarketSentimentAggregator.SentimentAggregation aggregation = marketSentimentAggregator.resolveDominantSentiment(recentItems);
        SignalSentiment dominantSentiment = aggregation.sentiment();
        List<MarketDriverExtractor.DriverCount> topDrivers = marketDriverExtractor.resolveTopDrivers(recentItems);
        Instant generatedAt = Instant.now(clock);
        Instant toPublishedAt = recentItems.get(0).publishedAt();
        Instant fromPublishedAt = recentItems.get(recentItems.size() - 1).publishedAt();
        MarketPriceAdjustmentPolicy.ConfidenceBreakdown confidenceBreakdown = marketPriceAdjustmentPolicy.buildConfidenceBreakdown(
                aggregation.baseConfidence(),
                aggregation.confidence(),
                dominantSentiment,
                marketDataFacade
        );
        if (log.isDebugEnabled()) {
            log.debug("[FEATURED_SUMMARY] confidence breakdown: base={}, crisis={}, market={}, final={}",
                    confidenceBreakdown.baseConfidence(),
                    confidenceBreakdown.crisisBoost(),
                    confidenceBreakdown.marketBoost(),
                    confidenceBreakdown.finalConfidence());
            log.debug("[CONF_METRIC] type=recent_summary crisis={} market={} cap={}",
                    confidenceBreakdown.crisisApplied(),
                    confidenceBreakdown.marketApplied(),
                    confidenceBreakdown.capApplied());
        }

        return Optional.of(new FeaturedMarketSummaryDto(
                marketSummaryComposer.buildHeadlineKo(dominantSentiment),
                marketSummaryComposer.buildHeadlineEn(dominantSentiment),
                marketSummaryComposer.buildSummaryKo(resolveWindowHours(), recentItems.size(), topDrivers),
                marketSummaryComposer.buildSummaryEn(resolveWindowHours(), recentItems.size(), topDrivers),
                generatedAt,
                recentItems.size(),
                resolveWindowHours(),
                fromPublishedAt,
                toPublishedAt,
                dominantSentiment,
                topDrivers.stream().map(MarketDriverExtractor.DriverCount::chipLabel).toList(),
                recentItems.stream()
                        .map(NewsEvent::id)
                        .filter(org.springframework.util.StringUtils::hasText)
                        .toList(),
                null,
                null,
                confidenceBreakdown.finalConfidence(),
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

    private int resolveWindowHours() {
        return policyProperties.getWindowHours() > 0 ? policyProperties.getWindowHours() : 3;
    }

    private int resolveMaxItems() {
        return policyProperties.getMaxItems() > 0 ? policyProperties.getMaxItems() : 10;
    }

    private int resolveMinItems() {
        return Math.max(1, policyProperties.getMinItems());
    }
}

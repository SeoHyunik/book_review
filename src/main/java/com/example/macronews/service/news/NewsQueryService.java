package com.example.macronews.service.news;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.MarketSignalItemDto;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.repository.NewsEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsQueryService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final NewsEventRepository newsEventRepository;
    private final NewsEligibilityEvaluator eligibilityEvaluator;
    private final NewsScoringPolicy scoringPolicy;
    private final NewsDtoMapper newsDtoMapper;

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
                .filter(event -> eligibilityEvaluator.isDisplayEligible(
                        event, clock,
                        naverMaxAgeHours, globalMaxAgeHours,
                        naverFallbackMaxAgeHours, globalFallbackMaxAgeHours))
                .sorted(scoringPolicy.buildComparator(sort))
                .limit(20)
                .map(newsDtoMapper::toListItem)
                .toList();
    }

    public MarketSignalOverviewDto getMarketSignalOverview(NewsStatus status, NewsListSort sort) {
        List<NewsEvent> candidates = loadCandidates(status);
        List<NewsEvent> recentAnalyzed = candidates.stream()
                .filter(event -> eligibilityEvaluator.isSignalEligible(
                        event, clock,
                        naverMaxAgeHours, globalMaxAgeHours,
                        naverFallbackMaxAgeHours, globalFallbackMaxAgeHours))
                .sorted(scoringPolicy.buildComparator(sort))
                .limit(20)
                .toList();

        if (log.isDebugEnabled()) {
            Instant cutoff = Instant.now(clock).minus(java.time.Duration.ofHours(
                    eligibilityEvaluator.resolveMaxDisplayHours(
                            naverMaxAgeHours, globalMaxAgeHours,
                            naverFallbackMaxAgeHours, globalFallbackMaxAgeHours)));
            log.debug("[NEWS-QUERY] displayCandidates={} signalCandidates={} signalBasis=analysisResult.createdAt|ingestedAt|publishedAt cutoff={}",
                    candidates.size(), recentAnalyzed.size(), cutoff);
        }

        List<MarketSignalItemDto> items = Arrays.stream(com.example.macronews.domain.MacroVariable.values())
                .map(variable -> scoringPolicy.aggregateSignal(variable, recentAnalyzed))
                .toList();

        return new MarketSignalOverviewDto(items);
    }

    public AutoIngestionBatchStatusDto getAutoIngestionBatchStatus(int requestedCount, int returnedCount, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return new AutoIngestionBatchStatusDto(requestedCount, returnedCount, 0, 0, 0, 0, true, List.of());
        }

        Map<String, NewsEvent> eventsById = java.util.stream.StreamSupport
                .stream(newsEventRepository.findAllById(itemIds).spliterator(), false)
                .collect(java.util.stream.Collectors.toMap(NewsEvent::id, java.util.function.Function.identity()));

        List<NewsListItemDto> items = itemIds.stream()
                .map(eventsById::get)
                .filter(java.util.Objects::nonNull)
                .map(newsDtoMapper::toListItem)
                .toList();

        int ingestedCount = newsDtoMapper.countByStatus(items, NewsStatus.INGESTED);
        int analyzedCount = newsDtoMapper.countByStatus(items, NewsStatus.ANALYZED);
        int failedCount = newsDtoMapper.countByStatus(items, NewsStatus.FAILED);
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
        return newsEventRepository.findById(id).map(newsDtoMapper::toDetail);
    }

    public List<NewsListItemDto> getNewsItemsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<String, NewsEvent> eventsById = java.util.stream.StreamSupport
                .stream(newsEventRepository.findAllById(ids).spliterator(), false)
                .collect(java.util.stream.Collectors.toMap(NewsEvent::id, java.util.function.Function.identity()));

        return ids.stream()
                .map(eventsById::get)
                .filter(java.util.Objects::nonNull)
                .map(newsDtoMapper::toListItem)
                .toList();
    }

    private List<NewsEvent> loadCandidates(NewsStatus status) {
        return status == null
                ? newsEventRepository.findTop20ByOrderByIngestedAtDesc()
                : newsEventRepository.findByStatus(status);
    }
}

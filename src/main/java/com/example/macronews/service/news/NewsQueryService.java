package com.example.macronews.service.news;

import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.repository.NewsEventRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NewsQueryService {

    private final NewsEventRepository newsEventRepository;

    public List<NewsListItemDto> getRecentNews() {
        return newsEventRepository.findTop20ByOrderByPublishedAtDesc()
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Cacheable(cacheNames = "newsDetail", key = "#id")
    public Optional<NewsDetailDto> getNewsDetail(String id) {
        return newsEventRepository.findById(id).map(this::toDetail);
    }

    private NewsListItemDto toListItem(NewsEvent event) {
        boolean hasAnalysis = event.analysisResult() != null;
        return new NewsListItemDto(
                event.id(),
                event.title(),
                event.source(),
                event.publishedAt(),
                event.status(),
                hasAnalysis,
                buildMacroSummary(event)
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
}
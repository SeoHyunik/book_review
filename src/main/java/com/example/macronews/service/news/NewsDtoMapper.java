package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.NewsListItemDto;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class NewsDtoMapper {

    private final NewsScoringPolicy scoringPolicy;
    private final NewsTranslationSelector translationSelector;

    NewsDtoMapper(NewsScoringPolicy scoringPolicy, NewsTranslationSelector translationSelector) {
        this.scoringPolicy = scoringPolicy;
        this.translationSelector = translationSelector;
    }

    NewsListItemDto toListItem(NewsEvent event) {
        boolean hasAnalysis = event.analysisResult() != null;
        MacroImpact primaryImpact = findPrimaryMacroImpact(event.analysisResult());
        com.example.macronews.domain.ImpactDirection primaryDirection = primaryImpact == null
                ? com.example.macronews.domain.ImpactDirection.NEUTRAL
                : primaryImpact.direction();
        com.example.macronews.domain.SignalSentiment primarySentiment = primaryImpact == null
                ? com.example.macronews.domain.SignalSentiment.NEUTRAL
                : primaryImpact.variable().sentimentFor(primaryImpact.direction());
        String macroSummary = buildMacroSummary(primaryImpact);
        String preferredSummary = translationSelector.resolvePreferredInterpretationSummary(event.analysisResult());
        String preferredHeadline = translationSelector.resolvePreferredHeadline(event.analysisResult());
        String displayTitle = translationSelector.buildDisplayTitle(event, preferredHeadline, preferredSummary);
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
                translationSelector.buildInterpretationSummary(macroSummary, preferredSummary, displayTitle),
                scoringPolicy.calculatePriorityScore(event)
        );
    }

    NewsDetailDto toDetail(NewsEvent event) {
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

    int countByStatus(List<NewsListItemDto> items, NewsStatus status) {
        return (int) items.stream().filter(item -> item.status() == status).count();
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
}

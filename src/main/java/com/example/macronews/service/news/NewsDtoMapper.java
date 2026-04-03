package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.NewsListItemDto;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class NewsDtoMapper {

    private static final Map<String, String> NAVER_PUBLISHER_LABELS = Map.ofEntries(
            Map.entry("khan.co.kr", "경향"),
            Map.entry("hankyung.com", "한국경제"),
            Map.entry("chosun.com", "조선"),
            Map.entry("donga.com", "동아"),
            Map.entry("joongang.co.kr", "중앙"),
            Map.entry("mk.co.kr", "매일경제"),
            Map.entry("sedaily.com", "서울경제"),
            Map.entry("yna.co.kr", "연합뉴스"),
            Map.entry("mt.co.kr", "머니투데이"),
            Map.entry("edaily.co.kr", "이데일리"),
            Map.entry("asiae.co.kr", "아시아경제"),
            Map.entry("newsis.com", "뉴시스")
    );

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
        String displaySource = buildDisplaySource(event.source(), event.url());
        return new NewsListItemDto(
                event.id(),
                event.title(),
                displayTitle,
                event.source(),
                displaySource,
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

    private String buildDisplaySource(String source, String url) {
        String coarseSource = StringUtils.hasText(source) ? source.trim() : "";
        if (!StringUtils.hasText(coarseSource)) {
            return "-";
        }
        if (!"NAVER".equalsIgnoreCase(coarseSource)) {
            return coarseSource;
        }

        String publisherLabel = resolvePublisherLabel(url);
        if (!StringUtils.hasText(publisherLabel)) {
            return coarseSource;
        }
        return coarseSource + "-" + publisherLabel;
    }

    private String resolvePublisherLabel(String url) {
        String host = extractHost(url);
        if (!StringUtils.hasText(host)) {
            return "";
        }

        for (Map.Entry<String, String> entry : NAVER_PUBLISHER_LABELS.entrySet()) {
            String domain = entry.getKey();
            if (host.equalsIgnoreCase(domain) || host.endsWith("." + domain)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private String extractHost(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}

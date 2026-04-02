package com.example.macronews.controller;

import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/topic")
@RequiredArgsConstructor
@Slf4j
public class TopicController {

    private static final int MAX_TOPIC_NEWS_ITEMS = 5;
    private static final String PAGE_TITLE_KEY = "page.topic.dollar.title";
    private static final String PAGE_DESCRIPTION_KEY = "page.topic.dollar.description";
    private static final List<String> DOLLAR_KEYWORDS = List.of(
            "usd",
            "dollar",
            "dxy",
            "fx",
            "foreign exchange",
            "treasury",
            "yield",
            "fed",
            "fomc",
            "rate"
    );

    private final NewsQueryService newsQueryService;
    private final MarketDataFacade marketDataFacade;
    private final MarketForecastQueryService marketForecastQueryService;

    @GetMapping("/dollar")
    public String dollar(Model model) {
        List<NewsListItemDto> dollarNewsItems = safeGetDollarNews();
        DxySnapshotDto dxySnapshot = safeGetDxySnapshot();
        MarketForecastSnapshotDto forecastSnapshot = safeGetForecastSnapshot();

        model.addAttribute("dollarNewsItems", dollarNewsItems);
        model.addAttribute("topicNewsCount", dollarNewsItems.size());
        model.addAttribute("dxySnapshot", dxySnapshot);
        model.addAttribute("forecastSnapshot", forecastSnapshot);
        model.addAttribute("pageTitleKey", PAGE_TITLE_KEY);
        model.addAttribute("pageDescriptionKey", PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogTitleKey", PAGE_TITLE_KEY);
        model.addAttribute("ogDescriptionKey", PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogUrl", "/topic/dollar");
        return "topic/dollar";
    }

    private List<NewsListItemDto> safeGetDollarNews() {
        try {
            return newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC).stream()
                    .filter(this::isDollarRelated)
                    .limit(MAX_TOPIC_NEWS_ITEMS)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/dollar without related news due to query failure", ex);
            return List.of();
        }
    }

    private DxySnapshotDto safeGetDxySnapshot() {
        try {
            return marketDataFacade.getDxy().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/dollar without DXY snapshot due to market data failure", ex);
            return null;
        }
    }

    private MarketForecastSnapshotDto safeGetForecastSnapshot() {
        try {
            return marketForecastQueryService.getCurrentSnapshot().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/dollar without forecast snapshot due to forecast failure", ex);
            return null;
        }
    }

    private boolean isDollarRelated(NewsListItemDto item) {
        if (item == null) {
            return false;
        }
        return containsDollarKeyword(item.title())
                || containsDollarKeyword(item.displayTitle())
                || containsDollarKeyword(item.source())
                || containsDollarKeyword(item.macroSummary())
                || containsDollarKeyword(item.interpretationSummary());
    }

    private boolean containsDollarKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : DOLLAR_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

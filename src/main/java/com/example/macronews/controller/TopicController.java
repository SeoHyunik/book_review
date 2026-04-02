package com.example.macronews.controller;

import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
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
    private static final String DOLLAR_PAGE_TITLE_KEY = "page.topic.dollar.title";
    private static final String DOLLAR_PAGE_DESCRIPTION_KEY = "page.topic.dollar.description";
    private static final String RATES_PAGE_TITLE_KEY = "page.topic.rates.title";
    private static final String RATES_PAGE_DESCRIPTION_KEY = "page.topic.rates.description";
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
    private static final List<String> RATES_KEYWORDS = List.of(
            "rates",
            "yield",
            "yields",
            "treasury",
            "treasuries",
            "bond",
            "bonds",
            "fed",
            "fomc",
            "powell",
            "policy",
            "interest rate",
            "rate decision",
            "rate hike",
            "rate cut"
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
        model.addAttribute("pageTitleKey", DOLLAR_PAGE_TITLE_KEY);
        model.addAttribute("pageDescriptionKey", DOLLAR_PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogTitleKey", DOLLAR_PAGE_TITLE_KEY);
        model.addAttribute("ogDescriptionKey", DOLLAR_PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogUrl", "/topic/dollar");
        return "topic/dollar";
    }

    @GetMapping("/rates")
    public String rates(Model model) {
        List<NewsListItemDto> ratesNewsItems = safeGetRatesNews();
        Us10ySnapshotDto us10ySnapshot = safeGetUs10ySnapshot();
        MarketForecastSnapshotDto forecastSnapshot = safeGetForecastSnapshot();

        model.addAttribute("ratesNewsItems", ratesNewsItems);
        model.addAttribute("topicNewsCount", ratesNewsItems.size());
        model.addAttribute("us10ySnapshot", us10ySnapshot);
        model.addAttribute("forecastSnapshot", forecastSnapshot);
        model.addAttribute("pageTitleKey", RATES_PAGE_TITLE_KEY);
        model.addAttribute("pageDescriptionKey", RATES_PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogTitleKey", RATES_PAGE_TITLE_KEY);
        model.addAttribute("ogDescriptionKey", RATES_PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogUrl", "/topic/rates");
        return "topic/rates";
    }

    private List<NewsListItemDto> safeGetDollarNews() {
        return safeGetRelatedNews("dollar", DOLLAR_KEYWORDS);
    }

    private List<NewsListItemDto> safeGetRatesNews() {
        return safeGetRelatedNews("rates", RATES_KEYWORDS);
    }

    private List<NewsListItemDto> safeGetRelatedNews(String topicName, List<String> keywords) {
        try {
            return newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC).stream()
                    .filter(item -> matchesTopicKeywords(item, keywords))
                    .limit(MAX_TOPIC_NEWS_ITEMS)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/{} without related news due to query failure", topicName, ex);
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

    private Us10ySnapshotDto safeGetUs10ySnapshot() {
        try {
            return marketDataFacade.getUs10y().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/rates without US10Y snapshot due to market data failure", ex);
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

    private boolean matchesTopicKeywords(NewsListItemDto item, List<String> keywords) {
        if (item == null) {
            return false;
        }
        return containsTopicKeyword(item.title(), keywords)
                || containsTopicKeyword(item.displayTitle(), keywords)
                || containsTopicKeyword(item.source(), keywords)
                || containsTopicKeyword(item.macroSummary(), keywords)
                || containsTopicKeyword(item.interpretationSummary(), keywords);
    }

    private boolean containsTopicKeyword(String value, List<String> keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

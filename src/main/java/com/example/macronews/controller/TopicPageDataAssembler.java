package com.example.macronews.controller;

import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
@RequiredArgsConstructor
@Slf4j
class TopicPageDataAssembler {

    private static final int MAX_TOPIC_NEWS_ITEMS = 5;
    private static final String DOLLAR_PAGE_TITLE_KEY = "page.topic.dollar.title";
    private static final String DOLLAR_PAGE_DESCRIPTION_KEY = "page.topic.dollar.description";
    private static final String RATES_PAGE_TITLE_KEY = "page.topic.rates.title";
    private static final String RATES_PAGE_DESCRIPTION_KEY = "page.topic.rates.description";

    private final NewsQueryService newsQueryService;
    private final MarketDataFacade marketDataFacade;
    private final MarketForecastQueryService marketForecastQueryService;
    private final TopicKeywordPolicy topicKeywordPolicy;

    TopicPageData buildDollarPageData() {
        List<NewsListItemDto> dollarNewsItems = safeGetRelatedNews("dollar", topicKeywordPolicy::matchesDollar);
        DxySnapshotDto dxySnapshot = safeGetDxySnapshot("dollar");
        MarketForecastSnapshotDto forecastSnapshot = safeGetForecastSnapshot("dollar");

        return new TopicPageData("topic/dollar", attributes(
                "dollarNewsItems", dollarNewsItems,
                "topicNewsCount", dollarNewsItems.size(),
                "dxySnapshot", dxySnapshot,
                "forecastSnapshot", forecastSnapshot,
                "pageTitleKey", DOLLAR_PAGE_TITLE_KEY,
                "pageDescriptionKey", DOLLAR_PAGE_DESCRIPTION_KEY,
                "ogTitleKey", DOLLAR_PAGE_TITLE_KEY,
                "ogDescriptionKey", DOLLAR_PAGE_DESCRIPTION_KEY,
                "ogUrl", "/topic/dollar"
        ));
    }

    TopicPageData buildRatesPageData() {
        List<NewsListItemDto> ratesNewsItems = safeGetRelatedNews("rates", topicKeywordPolicy::matchesRates);
        Us10ySnapshotDto us10ySnapshot = safeGetUs10ySnapshot("rates");
        MarketForecastSnapshotDto forecastSnapshot = safeGetForecastSnapshot("rates");

        return new TopicPageData("topic/rates", attributes(
                "ratesNewsItems", ratesNewsItems,
                "topicNewsCount", ratesNewsItems.size(),
                "us10ySnapshot", us10ySnapshot,
                "forecastSnapshot", forecastSnapshot,
                "pageTitleKey", RATES_PAGE_TITLE_KEY,
                "pageDescriptionKey", RATES_PAGE_DESCRIPTION_KEY,
                "ogTitleKey", RATES_PAGE_TITLE_KEY,
                "ogDescriptionKey", RATES_PAGE_DESCRIPTION_KEY,
                "ogUrl", "/topic/rates"
        ));
    }

    private List<NewsListItemDto> safeGetRelatedNews(String topicName, Predicate<NewsListItemDto> predicate) {
        try {
            return newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC).stream()
                    .filter(predicate)
                    .limit(MAX_TOPIC_NEWS_ITEMS)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/{} without related news due to query failure", topicName, ex);
            return List.of();
        }
    }

    private DxySnapshotDto safeGetDxySnapshot(String topicName) {
        try {
            return marketDataFacade.getDxy().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/{} without DXY snapshot due to market data failure", topicName, ex);
            return null;
        }
    }

    private Us10ySnapshotDto safeGetUs10ySnapshot(String topicName) {
        try {
            return marketDataFacade.getUs10y().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/{} without US10Y snapshot due to market data failure", topicName, ex);
            return null;
        }
    }

    private MarketForecastSnapshotDto safeGetForecastSnapshot(String topicName) {
        try {
            return marketForecastQueryService.getCurrentSnapshot().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Rendering /topic/{} without forecast snapshot due to forecast failure", topicName, ex);
            return null;
        }
    }

    private Map<String, Object> attributes(Object... keyValues) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            attributes.put((String) keyValues[index], keyValues[index + 1]);
        }
        return attributes;
    }
}

record TopicPageData(String viewName, Map<String, Object> attributes) {

    void applyTo(Model model) {
        attributes.forEach(model::addAttribute);
    }
}

package com.example.macronews.controller;

import com.example.macronews.dto.forecast.MarketForecastDetailDto;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/market-forecast")
@RequiredArgsConstructor
@Slf4j
public class MarketForecastController {

    private final MarketForecastQueryService marketForecastQueryService;

    @GetMapping
    public String detail(Model model) {
        MarketForecastDetailDto forecastDetail = marketForecastQueryService.getCurrentForecastDetail().orElse(null);
        model.addAttribute("forecastDetail", forecastDetail);
        model.addAttribute("pageTitleKey", "page.market.forecast.title");
        model.addAttribute("pageDescriptionKey", "page.market.forecast.description");
        model.addAttribute("ogTitleKey", "page.market.forecast.title");
        model.addAttribute("ogDescriptionKey", "page.market.forecast.description");
        log.debug("Rendering market forecast detail page hasSnapshot={}", forecastDetail != null);
        return "forecast/detail";
    }
}

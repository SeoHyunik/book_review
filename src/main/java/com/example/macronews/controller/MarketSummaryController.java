package com.example.macronews.controller;

import com.example.macronews.dto.MarketSummaryDetailDto;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/market-summary")
@RequiredArgsConstructor
@Slf4j
public class MarketSummaryController {

    private final MarketSummarySnapshotService marketSummarySnapshotService;

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        MarketSummaryDetailDto marketSummaryDetail = marketSummarySnapshotService.getSnapshotDetail(id).orElse(null);
        if (marketSummaryDetail == null) {
            log.warn("Market summary snapshot requested with invalid id={}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Market summary snapshot not found.");
            return "redirect:/news";
        }

        model.addAttribute("marketSummaryDetail", marketSummaryDetail);
        model.addAttribute("pageTitleKey", "page.market.summary.title");
        model.addAttribute("pageDescriptionKey", "page.market.summary.description");
        model.addAttribute("ogTitleKey", "page.market.summary.title");
        model.addAttribute("ogDescriptionKey", "page.market.summary.description");
        log.debug("Rendering market summary detail page id={} supportingNews={}",
                id, marketSummaryDetail.supportingNews().size());
        return "market-summary/detail";
    }
}

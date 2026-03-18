package com.example.macronews.controller;

import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.MarketSummaryDetailDto;
import com.example.macronews.dto.MarketSummarySupportingNewsDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.service.news.AiMarketSummaryService;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.RecentMarketSummaryService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final AiMarketSummaryService aiMarketSummaryService;
    private final RecentMarketSummaryService recentMarketSummaryService;
    private final NewsQueryService newsQueryService;

    @GetMapping("/current")
    public String current(Model model, RedirectAttributes redirectAttributes) {
        MarketSummaryDetailDto marketSummaryDetail = marketSummarySnapshotService.getLatestValidSummary()
                .map(summary -> {
                    log.info("Rendering /market-summary/current using latest stored snapshot id={}", summary.snapshotId());
                    model.addAttribute("marketSummarySourceMode", "stored");
                    model.addAttribute("isStoredSnapshot", true);
                    return toDetailDto(summary);
                })
                .orElseGet(() -> aiMarketSummaryService.getCurrentSummary()
                        .map(summary -> {
                            log.info("Rendering /market-summary/current using AI summary generatedAt={}", summary.generatedAt());
                            model.addAttribute("marketSummarySourceMode", "ai");
                            model.addAttribute("isStoredSnapshot", false);
                            return toDetailDto(summary);
                        })
                        .orElseGet(() -> recentMarketSummaryService.getCurrentSummary()
                                .map(summary -> {
                                    log.info("Rendering /market-summary/current using recent summary generatedAt={}", summary.generatedAt());
                                    model.addAttribute("marketSummarySourceMode", "recent");
                                    model.addAttribute("isStoredSnapshot", false);
                                    return toDetailDto(summary);
                                })
                                .orElse(null)));

        if (marketSummaryDetail == null) {
            log.info("Redirecting /market-summary/current because no current summary is available");
            redirectAttributes.addFlashAttribute("errorMessage", currentSummaryUnavailableMessage());
            return "redirect:/news";
        }

        model.addAttribute("isCurrentSummary", true);
        populateDetailModel(model, marketSummaryDetail);
        return "market-summary/detail";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        MarketSummaryDetailDto marketSummaryDetail = marketSummarySnapshotService.getSnapshotDetail(id).orElse(null);
        if (marketSummaryDetail == null) {
            log.warn("Market summary snapshot requested with invalid id={}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Market summary snapshot not found.");
            return "redirect:/news";
        }

        model.addAttribute("marketSummarySourceMode", "stored");
        model.addAttribute("isCurrentSummary", false);
        model.addAttribute("isStoredSnapshot", true);
        populateDetailModel(model, marketSummaryDetail);
        log.debug("Rendering market summary detail page id={} supportingNews={}",
                id, marketSummaryDetail.supportingNews().size());
        return "market-summary/detail";
    }

    private void populateDetailModel(Model model, MarketSummaryDetailDto marketSummaryDetail) {
        model.addAttribute("marketSummaryDetail", marketSummaryDetail);
        model.addAttribute("pageTitleKey", "page.market.summary.title");
        model.addAttribute("pageDescriptionKey", "page.market.summary.description");
        model.addAttribute("ogTitleKey", "page.market.summary.title");
        model.addAttribute("ogDescriptionKey", "page.market.summary.description");
    }

    private MarketSummaryDetailDto toDetailDto(FeaturedMarketSummaryDto summary) {
        List<MarketSummarySupportingNewsDto> supportingNews = mapSupportingNews(
                newsQueryService.getNewsItemsByIds(summary.supportingNewsIds())
        );
        return new MarketSummaryDetailDto(
                summary.snapshotId(),
                summary.generatedAt(),
                summary.sourceCount(),
                summary.windowHours(),
                summary.headlineKo(),
                summary.headlineEn(),
                summary.summaryKo(),
                summary.summaryEn(),
                summary.marketViewKo(),
                summary.marketViewEn(),
                summary.dominantSentiment(),
                summary.confidence(),
                summary.keyDrivers(),
                supportingNews
        );
    }

    private List<MarketSummarySupportingNewsDto> mapSupportingNews(List<NewsListItemDto> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new MarketSummarySupportingNewsDto(
                        item.id(),
                        item.displayTitle() != null && !item.displayTitle().isBlank() ? item.displayTitle() : item.title(),
                        item.source(),
                        item.publishedAt(),
                        item.primaryDirection(),
                        item.primarySentiment()
                ))
                .toList();
    }

    private String currentSummaryUnavailableMessage() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null && "ko".equalsIgnoreCase(locale.getLanguage())
                ? "지금 표시할 시장 요약이 없습니다."
                : "No market summary is available right now.";
    }
}

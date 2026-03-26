package com.example.macronews.controller;

import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.service.auth.AnonymousDetailViewGateService;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.domain.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.service.news.AiMarketSummaryService;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.RecentMarketSummaryService;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private static final int NEWS_PAGE_SIZE = 5;

    private final NewsQueryService newsQueryService;
    private final MarketForecastQueryService marketForecastQueryService;
    private final MarketSummarySnapshotService marketSummarySnapshotService;
    private final AiMarketSummaryService aiMarketSummaryService;
    private final RecentMarketSummaryService recentMarketSummaryService;
    private final AnonymousDetailViewGateService anonymousDetailViewGateService;

    @GetMapping
    public String list(@RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) Integer page,
            Model model) {
        NewsStatus selectedStatus = resolveStatus(status);
        NewsListSort selectedSort = resolveSort(sort);
        List<NewsListItemDto> allNewsItems = safeGetRecentNews(selectedStatus, selectedSort);
        int totalItems = allNewsItems.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / NEWS_PAGE_SIZE));
        int currentPage = resolvePage(page, totalPages);
        int fromIndex = Math.min((currentPage - 1) * NEWS_PAGE_SIZE, totalItems);
        int toIndex = Math.min(fromIndex + NEWS_PAGE_SIZE, totalItems);
        List<NewsListItemDto> newsItems = allNewsItems.subList(fromIndex, toIndex);
        MarketSignalOverviewDto marketSignalOverview = safeGetMarketSignalOverview(selectedStatus, selectedSort);
        MarketForecastSnapshotDto marketForecastSnapshot = safeGetCurrentForecastSnapshot();
        FeaturedSummarySelection featuredSummarySelection = safeResolveFeaturedSummarySelection();
        FeaturedMarketSummaryDto featuredStoredMarketSummary = featuredSummarySelection.storedSummary();
        FeaturedMarketSummaryDto featuredAiMarketSummary = featuredSummarySelection.aiSummary();
        FeaturedMarketSummaryDto featuredMarketSummary = featuredSummarySelection.recentSummary();
        NewsListItemDto featuredNews = allNewsItems.isEmpty() ? null : allNewsItems.get(0);
        String featuredPrimaryMode = featuredSummarySelection.primaryMode();
        boolean featuredSummaryMode = featuredSummarySelection.summaryMode();
        model.addAttribute("newsItems", newsItems);
        model.addAttribute("featuredNews", featuredNews);
        model.addAttribute("featuredStoredMarketSummary", featuredStoredMarketSummary);
        model.addAttribute("featuredAiMarketSummary", featuredAiMarketSummary);
        model.addAttribute("featuredMarketSummary", featuredMarketSummary);
        model.addAttribute("featuredPrimaryMode", featuredPrimaryMode);
        model.addAttribute("featuredSummaryMode", featuredSummaryMode);
        model.addAttribute("marketSignalOverview", marketSignalOverview);
        model.addAttribute("marketForecastSnapshot", marketForecastSnapshot);
        model.addAttribute("selectedStatus", selectedStatus == null ? "" : selectedStatus.name());
        model.addAttribute("selectedSort", selectedSort.name().toLowerCase());
        model.addAttribute("currentPageNumber", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPreviousPage", currentPage > 1);
        model.addAttribute("hasNextPage", currentPage < totalPages);
        model.addAttribute("pageNumbers", totalPages <= 1 ? List.<Integer>of() : java.util.stream.IntStream.rangeClosed(1, totalPages).boxed().toList());
        model.addAttribute("pageTitleKey", "page.news.list.title");
        model.addAttribute("pageDescriptionKey", "page.news.list.description");
        model.addAttribute("ogTitleKey", "page.news.list.title");
        model.addAttribute("ogDescriptionKey", "page.news.list.description");
        log.info("Rendering news list page entries={} statusFilter={} sort={} featuredMode={}",
                newsItems.size(), selectedStatus, selectedSort, featuredPrimaryMode);
        return "news/list";
    }

    private List<NewsListItemDto> safeGetRecentNews(NewsStatus selectedStatus, NewsListSort selectedSort) {
        try {
            return newsQueryService.getRecentNews(selectedStatus, selectedSort);
        } catch (RuntimeException ex) {
            log.warn("Rendering /news with empty news list due to query failure status={} sort={}",
                    selectedStatus, selectedSort, ex);
            return List.of();
        }
    }

    private MarketSignalOverviewDto safeGetMarketSignalOverview(NewsStatus selectedStatus, NewsListSort selectedSort) {
        try {
            return newsQueryService.getMarketSignalOverview(selectedStatus, selectedSort);
        } catch (RuntimeException ex) {
            log.warn("Rendering /news without market signal overview due to query failure status={} sort={}",
                    selectedStatus, selectedSort, ex);
            return new MarketSignalOverviewDto(List.of());
        }
    }

    private MarketForecastSnapshotDto safeGetCurrentForecastSnapshot() {
        try {
            return marketForecastQueryService.getCurrentSnapshot().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Rendering /news without market forecast snapshot due to forecast query failure", ex);
            return null;
        }
    }

    private FeaturedSummarySelection safeResolveFeaturedSummarySelection() {
        try {
            return resolveFeaturedSummarySelection();
        } catch (RuntimeException ex) {
            log.warn("Rendering /news without featured market summary due to summary resolution failure", ex);
            return new FeaturedSummarySelection(null, null, null, "article", false);
        }
    }

    private FeaturedSummarySelection resolveFeaturedSummarySelection() {
        FeaturedMarketSummaryDto storedSummary = marketSummarySnapshotService.getLatestValidSummary().orElse(null);
        if (storedSummary != null) {
            log.debug("Featured summary mode resolved to stored snapshot id={}", storedSummary.snapshotId());
            return new FeaturedSummarySelection(storedSummary, null, null, "stored-summary", true);
        }

        FeaturedMarketSummaryDto aiSummary = aiMarketSummaryService.getCurrentSummary().orElse(null);
        if (aiSummary != null) {
            log.debug("Featured summary mode resolved to current AI summary generatedAt={}", aiSummary.generatedAt());
            return new FeaturedSummarySelection(null, aiSummary, null, "ai-summary", true);
        }

        FeaturedMarketSummaryDto recentSummary = recentMarketSummaryService.getCurrentSummary().orElse(null);
        if (recentSummary != null) {
            log.debug("Featured summary mode resolved to recent summary generatedAt={}", recentSummary.generatedAt());
            return new FeaturedSummarySelection(null, null, recentSummary, "recent-summary", true);
        }

        log.debug("Featured summary mode resolved to article fallback");
        return new FeaturedSummarySelection(null, null, null, "article", false);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id,
            Authentication authentication,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        NewsDetailDto newsDetail = newsQueryService.getNewsDetail(id).orElse(null);
        if (newsDetail == null) {
            log.warn("News detail requested with invalid id={}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "News event not found.");
            return "redirect:/news";
        }

        if (isAnonymous(authentication) && !anonymousDetailViewGateService.canAccess(id, session)) {
            redirectAttributes.addAttribute("continue", "/news/" + id);
            redirectAttributes.addAttribute("gated", "1");
            return "redirect:/login";
        }
        if (isAnonymous(authentication)) {
            anonymousDetailViewGateService.recordAccess(id, session);
        }

        model.addAttribute("newsDetail", newsDetail);
        String localizedTitle = resolveLocalizedDetailTitle(newsDetail);
        String localizedSummary = resolveLocalizedInterpretationSummary(newsDetail, localizedTitle);
        String originalArticleSummary = resolveOriginalArticleSummary(newsDetail, localizedTitle);
        model.addAttribute("localizedDetailTitle", localizedTitle);
        model.addAttribute("localizedInterpretationSummary", localizedSummary);
        model.addAttribute("originalArticleSummary", originalArticleSummary);
        model.addAttribute("pageTitle", localizedTitle == null ? "News Detail" : localizedTitle);
        String description = localizedSummary == null || localizedSummary.isBlank()
                ? "Macro news detail and interpretation result."
                : localizedSummary;
        model.addAttribute("pageDescription", description);
        model.addAttribute("ogTitle", localizedTitle == null ? "News Detail" : localizedTitle);
        model.addAttribute("ogDescription", description);
        model.addAttribute("ogUrl", "/news/" + newsDetail.id());
        log.debug("Rendering news detail page for id={}", id);
        return "news/detail";
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
    }

    private NewsStatus resolveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return NewsStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring unsupported news list status filter={}", status);
            return null;
        }
    }

    private NewsListSort resolveSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return NewsListSort.PUBLISHED_DESC;
        }
        try {
            return NewsListSort.valueOf(sort.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring unsupported news list sort={}", sort);
            return NewsListSort.PUBLISHED_DESC;
        }
    }

    private int resolvePage(Integer page, int totalPages) {
        if (page == null || page < 1) {
            return 1;
        }
        return Math.min(page, Math.max(totalPages, 1));
    }

    private String resolveLocalizedDetailTitle(NewsDetailDto newsDetail) {
        String preferredHeadline = resolvePreferredAnalysisHeadline(newsDetail.analysisResult());
        if (StringUtils.hasText(preferredHeadline)) {
            return preferredHeadline;
        }
        String preferredSummary = resolvePreferredAnalysisSummary(newsDetail.analysisResult());
        String summaryHeadline = extractLeadingSentence(preferredSummary);
        if (StringUtils.hasText(summaryHeadline)) {
            return summaryHeadline;
        }
        return StringUtils.hasText(newsDetail.title()) ? newsDetail.title() : "News Detail";
    }

    private String resolveLocalizedInterpretationSummary(NewsDetailDto newsDetail, String localizedTitle) {
        String preferredSummary = resolvePreferredAnalysisSummary(newsDetail.analysisResult());
        String condensedSummary = removeLeadingSentence(preferredSummary, localizedTitle);
        if (StringUtils.hasText(condensedSummary)) {
            return condensedSummary;
        }
        if (StringUtils.hasText(preferredSummary)
                && !preferredSummary.equals(localizedTitle)) {
            return preferredSummary;
        }
        return StringUtils.hasText(newsDetail.summary()) ? newsDetail.summary() : "";
    }

    private String resolveOriginalArticleSummary(NewsDetailDto newsDetail, String localizedTitle) {
        String originalSummary = StringUtils.hasText(newsDetail.summary()) ? newsDetail.summary().trim() : "";
        if (StringUtils.hasText(originalSummary)
                && !originalSummary.equals(newsDetail.title())
                && !originalSummary.equals(localizedTitle)) {
            return originalSummary;
        }
        return "";
    }

    private String resolvePreferredAnalysisSummary(AnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "";
        }
        var locale = LocaleContextHolder.getLocale();
        boolean korean = "ko".equalsIgnoreCase(locale.getLanguage());
        String preferred = korean ? analysisResult.summaryKo() : analysisResult.summaryEn();
        String fallback = korean ? analysisResult.summaryEn() : analysisResult.summaryKo();
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : "";
    }

    private String resolvePreferredAnalysisHeadline(AnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "";
        }
        var locale = LocaleContextHolder.getLocale();
        boolean korean = "ko".equalsIgnoreCase(locale.getLanguage());
        String preferred = korean ? analysisResult.headlineKo() : analysisResult.headlineEn();
        String fallback = korean ? analysisResult.headlineEn() : analysisResult.headlineKo();
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : "";
    }

    private String extractLeadingSentence(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        int sentenceBoundary = findSentenceBoundary(trimmed);
        if (sentenceBoundary < 0) {
            return trimmed;
        }
        return trimmed.substring(0, sentenceBoundary + 1).trim();
    }

    private String removeLeadingSentence(String text, String headline) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        String lead = StringUtils.hasText(headline) ? headline.trim() : extractLeadingSentence(trimmed);
        if (!StringUtils.hasText(lead) || !trimmed.startsWith(lead)) {
            return trimmed;
        }
        String remainder = trimmed.substring(lead.length()).trim();
        return remainder.replaceFirst("^[\\-:;,.\\s]+", "").trim();
    }

    private int findSentenceBoundary(String text) {
        int boundary = -1;
        for (char marker : new char[] {'.', '!', '?'}) {
            int index = text.indexOf(marker);
            if (index >= 0 && (boundary < 0 || index < boundary)) {
                boundary = index;
            }
        }
        return boundary;
    }

    private record FeaturedSummarySelection(
            FeaturedMarketSummaryDto storedSummary,
            FeaturedMarketSummaryDto aiSummary,
            FeaturedMarketSummaryDto recentSummary,
            String primaryMode,
            boolean summaryMode
    ) {
    }
}

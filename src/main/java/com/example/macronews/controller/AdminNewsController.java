package com.example.macronews.controller;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.request.AdminIngestionRequest;
import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.NewsApiService;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.NewsQueryService;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/news")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminNewsController {

    private static final int DEFAULT_LIMIT = 10;
    private static final String MANUAL_PAGE = "/admin/news/manual";
    private static final String AUTO_PAGE = "/admin/news/auto";
    private static final String NEWS_PAGE = "/news";

    private final NewsIngestionService newsIngestionService;
    private final NewsApiService newsApiService;
    private final MacroAiService macroAiService;
    private final NewsQueryService newsQueryService;

    @GetMapping
    public String ingestForm(Model model) {
        return "redirect:" + MANUAL_PAGE;
    }

    @GetMapping("/manual")
    public String manualIngestForm(@RequestParam(name = "status", required = false) String status, Model model) {
        if (!model.containsAttribute("adminIngestionRequest")) {
            model.addAttribute("adminIngestionRequest", AdminIngestionRequest.empty());
        }
        NewsStatus selectedStatus = resolveStatus(status);
        List<NewsListItemDto> recentNewsItems = newsQueryService.getRecentNews(selectedStatus);
        populateAdminListModel(model, recentNewsItems, selectedStatus);
        model.addAttribute("pageTitle", "Admin News Manual Ingestion");
        log.debug("Rendering admin manual news ingestion form with {} recent items", recentNewsItems.size());
        return "admin/news/ingest-manual";
    }

    @GetMapping("/auto")
    public String autoIngestForm(@RequestParam(name = "status", required = false) String status, Model model) {
        NewsStatus selectedStatus = resolveStatus(status);
        List<NewsListItemDto> recentNewsItems = newsQueryService.getRecentNews(selectedStatus);
        populateAdminListModel(model, recentNewsItems, selectedStatus);
        model.addAttribute("pageTitle", "Admin News Automatic Ingestion");
        model.addAttribute("newsApiConfigured", newsApiService.isConfigured());
        log.debug("Rendering admin automatic news ingestion form with {} recent items", recentNewsItems.size());
        return "admin/news/ingest-api";
    }

    @PostMapping("/ingest")
    public String ingest(@ModelAttribute("adminIngestionRequest") AdminIngestionRequest request,
            RedirectAttributes redirectAttributes) {

        try {
            if (hasManualPayload(request)) {
                NewsEvent ingested = newsIngestionService.ingestManual(request);
                NewsEvent interpreted = macroAiService.interpretAndSave(ingested.id());
                redirectAttributes.addFlashAttribute("successMessage",
                        "News ingested and interpreted. id=" + interpreted.id());
                return "redirect:/news/" + interpreted.id();
            }

            int limit = request.limit() != null && request.limit() > 0
                    ? request.limit()
                    : DEFAULT_LIMIT;
            List<NewsEvent> ingested = newsIngestionService.ingestTopHeadlines(limit);
            redirectAttributes.addFlashAttribute("successMessage",
                    "External ingestion completed. total=" + ingested.size());
            return "redirect:" + MANUAL_PAGE;
        } catch (RuntimeException ex) {
            log.error("Admin news ingestion failed", ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "News ingestion failed. Please try again.");
            redirectAttributes.addFlashAttribute("adminIngestionRequest", request);
            return "redirect:" + MANUAL_PAGE;
        }
    }

    @PostMapping("/{id}/reinterpret")
    public String reinterpret(@PathVariable String id,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        log.info("[ADMIN] reinterpret requested id={}", id);
        try {
            NewsEvent interpreted = macroAiService.interpretAndSave(id);
            log.info("[ADMIN] reinterpret completed id={} status={}", interpreted.id(), interpreted.status());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Re-interpretation completed. id=" + interpreted.id() + " status=" + interpreted.status());
        } catch (RuntimeException ex) {
            log.error("Admin reinterpretation failed for id={}", id, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Re-interpretation failed. id=" + id);
        }
        return "redirect:" + resolveAdminRedirect(returnTo, status);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes redirectAttributes) {
        log.info("[ADMIN] delete requested id={}", id);
        boolean deleted = newsIngestionService.deleteById(id);
        if (deleted) {
            redirectAttributes.addFlashAttribute("successMessage", "News item deleted. id=" + id);
        } else {
            redirectAttributes.addFlashAttribute("warningMessage", "News item not found. id=" + id);
        }
        return "redirect:" + resolveAdminRedirect(returnTo, status);
    }

    @PostMapping("/ingest-api")
    public String ingestFromApi(@RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            RedirectAttributes redirectAttributes) {
        try {
            if (!newsApiService.isConfigured()) {
                log.info("Admin external ingestion skipped because news.api.key is not configured");
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Automatic ingestion requires external news API configuration (news.api.key). Manual ingestion is still available.");
                return "redirect:" + AUTO_PAGE;
            }

            List<NewsEvent> ingested = newsIngestionService.ingestTopHeadlines(pageSize);
            redirectAttributes.addFlashAttribute("successMessage",
                    "External ingestion completed. total=" + ingested.size());
        } catch (RuntimeException ex) {
            log.error("Admin external ingestion failed", ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "External ingestion failed. Please check logs/config.");
        }
        return "redirect:" + AUTO_PAGE;
    }

    private boolean hasManualPayload(AdminIngestionRequest request) {
        return StringUtils.hasText(request.source())
                && StringUtils.hasText(request.title());
    }

    private void populateAdminListModel(Model model, List<NewsListItemDto> recentNewsItems, NewsStatus selectedStatus) {
        model.addAttribute("recentNewsItems", recentNewsItems);
        model.addAttribute("selectedStatus", selectedStatus == null ? "" : selectedStatus.name());
        model.addAttribute("statusOptions", Arrays.stream(NewsStatus.values()).map(Enum::name).toList());
    }

    private NewsStatus resolveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return NewsStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring unsupported admin status filter={}", status);
            return null;
        }
    }

    private String resolveAdminRedirect(String returnTo, String status) {
        String basePath;
        if (NEWS_PAGE.equals(returnTo)) {
            basePath = NEWS_PAGE;
        } else if (AUTO_PAGE.equals(returnTo)) {
            basePath = AUTO_PAGE;
        } else {
            basePath = MANUAL_PAGE;
        }
        return MANUAL_PAGE.equals(basePath) || AUTO_PAGE.equals(basePath)
                ? (StringUtils.hasText(status) ? basePath + "?status=" + status : basePath)
                : basePath;
    }
}

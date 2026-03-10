package com.example.macronews.controller;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.request.AdminIngestionRequest;
import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.NewsApiService;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.NewsQueryService;
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

    private final NewsIngestionService newsIngestionService;
    private final NewsApiService newsApiService;
    private final MacroAiService macroAiService;
    private final NewsQueryService newsQueryService;

    @GetMapping
    public String ingestForm(Model model) {
        return "redirect:/admin/news/manual";
    }

    @GetMapping("/manual")
    public String manualIngestForm(Model model) {
        if (!model.containsAttribute("adminIngestionRequest")) {
            model.addAttribute("adminIngestionRequest", AdminIngestionRequest.empty());
        }
        List<NewsListItemDto> recentNewsItems = newsQueryService.getRecentNews();
        model.addAttribute("recentNewsItems", recentNewsItems);
        model.addAttribute("pageTitle", "Admin News Manual Ingestion");
        log.debug("Rendering admin manual news ingestion form with {} recent items", recentNewsItems.size());
        return "admin/news/ingest-manual";
    }

    @GetMapping("/auto")
    public String autoIngestForm(Model model) {
        List<NewsListItemDto> recentNewsItems = newsQueryService.getRecentNews();
        model.addAttribute("recentNewsItems", recentNewsItems);
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
            return "redirect:/admin/news/manual";
        } catch (RuntimeException ex) {
            log.error("Admin news ingestion failed", ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "News ingestion failed. Please try again.");
            redirectAttributes.addFlashAttribute("adminIngestionRequest", request);
            return "redirect:/admin/news/manual";
        }
    }

    @PostMapping("/{id}/reinterpret")
    public String reinterpret(@PathVariable String id, RedirectAttributes redirectAttributes) {
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
        return "redirect:/admin/news/manual";
    }

    @PostMapping("/ingest-api")
    public String ingestFromApi(@RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            RedirectAttributes redirectAttributes) {
        try {
            if (!newsApiService.isConfigured()) {
                log.info("Admin external ingestion skipped because news.api.key is not configured");
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Automatic ingestion requires external news API configuration (news.api.key). Manual ingestion is still available.");
                return "redirect:/admin/news/auto";
            }

            List<NewsEvent> ingested = newsIngestionService.ingestTopHeadlines(pageSize);
            redirectAttributes.addFlashAttribute("successMessage",
                    "External ingestion completed. total=" + ingested.size());
        } catch (RuntimeException ex) {
            log.error("Admin external ingestion failed", ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "External ingestion failed. Please check logs/config.");
        }
        return "redirect:/admin/news/auto";
    }

    private boolean hasManualPayload(AdminIngestionRequest request) {
        return StringUtils.hasText(request.source())
                && StringUtils.hasText(request.title());
    }
}

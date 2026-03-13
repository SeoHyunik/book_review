package com.example.macronews.controller;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.request.AdminIngestionRequest;
import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.NewsApiService;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.example.macronews.util.RedirectPathUtils;
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
import org.springframework.web.util.UriComponentsBuilder;

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
        model.addAttribute("pageTitleKey", "page.admin.manual.title");
        model.addAttribute("pageDescriptionKey", "page.admin.manual.description");
        log.debug("Rendering admin manual news ingestion form with {} recent items", recentNewsItems.size());
        return "admin/news/ingest-manual";
    }

    @GetMapping("/auto")
    public String autoIngestForm(@RequestParam(name = "status", required = false) String status, Model model) {
        NewsStatus selectedStatus = resolveStatus(status);
        List<NewsListItemDto> recentNewsItems = newsQueryService.getRecentNews(selectedStatus);
        populateAdminListModel(model, recentNewsItems, selectedStatus);
        populateAutoBatchStatusFromFlash(model);
        model.addAttribute("pageTitleKey", "page.admin.auto.title");
        model.addAttribute("pageDescriptionKey", "page.admin.auto.description");
        model.addAttribute("newsApiConfigured", newsApiService.isConfigured());
        log.debug("Rendering admin automatic news ingestion form with {} recent items", recentNewsItems.size());
        return "admin/news/ingest-api";
    }

    @GetMapping("/auto/batch-status")
    public String autoBatchStatus(
            @RequestParam(name = "requestedCount") int requestedCount,
            @RequestParam(name = "returnedCount") int returnedCount,
            @RequestParam(name = "itemIds") String itemIds,
            Model model) {
        populateAutoBatchStatus(model, requestedCount, returnedCount, parseItemIds(itemIds));
        model.addAttribute("autoBatchStatusUrl", "/admin/news/auto/batch-status");
        return "admin/news/fragments/auto-batch-status :: autoBatchStatusPanel";
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
            @RequestParam(name = "sort", required = false) String sort,
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
        return "redirect:" + resolveAdminRedirect(returnTo, status, sort);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirectAttributes) {
        log.info("[ADMIN] delete requested id={}", id);
        boolean deleted = newsIngestionService.deleteById(id);
        if (deleted) {
            redirectAttributes.addFlashAttribute("successMessage", "News item deleted. id=" + id);
        } else {
            redirectAttributes.addFlashAttribute("warningMessage", "News item not found. id=" + id);
        }
        return "redirect:" + resolveAdminRedirect(returnTo, status, sort);
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

            int resolvedPageSize = resolveAutoPageSize(pageSize);
            List<NewsEvent> ingested = newsIngestionService.ingestTopHeadlines(resolvedPageSize);
            AutoIngestionBatchStatusDto autoBatchStatus =
                    newsQueryService.getAutoIngestionBatchStatus(resolvedPageSize, ingested.size(),
                            ingested.stream().map(NewsEvent::id).toList());
            redirectAttributes.addFlashAttribute("successMessage", buildAutoIngestionFlashMessage(autoBatchStatus));
            if (pageSize <= 0) {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Requested page size was invalid, so automatic ingestion used the default size of "
                                + DEFAULT_LIMIT + ".");
            }
            redirectAttributes.addFlashAttribute("autoBatchStatus", autoBatchStatus);
            redirectAttributes.addFlashAttribute("autoBatchRequestedCount", resolvedPageSize);
            redirectAttributes.addFlashAttribute("autoBatchReturnedCount", ingested.size());
            redirectAttributes.addFlashAttribute("autoBatchItemIds", ingested.stream().map(NewsEvent::id).toList());
            log.info("[ADMIN-AUTO] automatic ingestion completed requested={} returned={} analyzed={} pending={} failed={}",
                    resolvedPageSize, autoBatchStatus.returnedCount(), autoBatchStatus.analyzedCount(),
                    autoBatchStatus.pendingCount(), autoBatchStatus.failedCount());
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

    private void populateAutoBatchStatusFromFlash(Model model) {
        Map<String, Object> attributes = model.asMap();
        if (attributes.get("autoBatchStatus") instanceof AutoIngestionBatchStatusDto autoBatchStatus) {
            model.addAttribute("autoBatchStatus", autoBatchStatus);
            model.addAttribute("autoBatchStatusUrl", "/admin/news/auto/batch-status");
            if (attributes.get("autoBatchItemIds") instanceof List<?> itemIds) {
                model.addAttribute("autoBatchItemIdsCsv", itemIds.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .collect(java.util.stream.Collectors.joining(",")));
            }
            return;
        }
        Integer requestedCount = asInteger(attributes.get("autoBatchRequestedCount"));
        Integer returnedCount = asInteger(attributes.get("autoBatchReturnedCount"));
        List<String> itemIds = asStringList(attributes.get("autoBatchItemIds"));
        if (requestedCount == null || returnedCount == null || itemIds.isEmpty()) {
            return;
        }
        populateAutoBatchStatus(model, requestedCount, returnedCount, itemIds);
    }

    private void populateAutoBatchStatus(Model model, int requestedCount, int returnedCount, List<String> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }
        AutoIngestionBatchStatusDto autoBatchStatus =
                newsQueryService.getAutoIngestionBatchStatus(requestedCount, returnedCount, itemIds);
        model.addAttribute("autoBatchStatus", autoBatchStatus);
        model.addAttribute("autoBatchItemIdsCsv", String.join(",", itemIds));
        model.addAttribute("autoBatchStatusUrl", "/admin/news/auto/batch-status");
    }

    private Integer asInteger(Object value) {
        return value instanceof Integer integer ? integer : null;
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private List<String> parseItemIds(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
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

    private String resolveAdminRedirect(String returnTo, String status, String sort) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedSort = normalizeSort(sort);
        String safeReturnTo = RedirectPathUtils.normalizeSafeRelativePath(returnTo);
        String basePath = NEWS_PAGE;
        if (NEWS_PAGE.equals(safeReturnTo)) {
            basePath = NEWS_PAGE;
        } else if (AUTO_PAGE.equals(safeReturnTo)) {
            basePath = AUTO_PAGE;
        } else if (MANUAL_PAGE.equals(safeReturnTo)) {
            basePath = MANUAL_PAGE;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(basePath);
        if (StringUtils.hasText(normalizedStatus)) {
            builder.queryParam("status", normalizedStatus);
        }
        if (NEWS_PAGE.equals(basePath) && StringUtils.hasText(normalizedSort)) {
            builder.queryParam("sort", normalizedSort);
        }
        return builder.build(true).toUriString();
    }

    private String normalizeStatus(String status) {
        NewsStatus newsStatus = resolveStatus(status);
        return newsStatus == null ? "" : newsStatus.name();
    }

    private String normalizeSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return "";
        }
        try {
            return NewsListSort.valueOf(sort.trim().toUpperCase()).name().toLowerCase();
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring unsupported admin sort={}", sort);
            return "";
        }
    }

    private int resolveAutoPageSize(int pageSize) {
        return pageSize > 0 ? pageSize : DEFAULT_LIMIT;
    }

    private String buildAutoIngestionFlashMessage(AutoIngestionBatchStatusDto autoBatchStatus) {
        if (autoBatchStatus.returnedCount() == 0) {
            return "Automatic ingestion completed, but the external feed returned no items.";
        }
        if (autoBatchStatus.failedCount() > 0) {
            return "Automatic ingestion completed. returned=" + autoBatchStatus.returnedCount()
                    + ", analyzed=" + autoBatchStatus.analyzedCount()
                    + ", pending=" + autoBatchStatus.pendingCount()
                    + ", failed=" + autoBatchStatus.failedCount();
        }
        if (!autoBatchStatus.completed()) {
            return "Automatic ingestion completed. returned=" + autoBatchStatus.returnedCount()
                    + ", analyzed=" + autoBatchStatus.analyzedCount()
                    + ", pending analysis=" + autoBatchStatus.pendingCount();
        }
        return "Automatic ingestion completed. returned=" + autoBatchStatus.returnedCount()
                + ", analyzed=" + autoBatchStatus.analyzedCount();
    }
}

package com.example.macronews.controller;

import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
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

    private final NewsQueryService newsQueryService;

    @GetMapping
    public String list(@RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sort", required = false) String sort,
            Model model) {
        NewsStatus selectedStatus = resolveStatus(status);
        NewsListSort selectedSort = resolveSort(sort);
        List<NewsListItemDto> newsItems = newsQueryService.getRecentNews(selectedStatus, selectedSort);
        model.addAttribute("newsItems", newsItems);
        model.addAttribute("selectedStatus", selectedStatus == null ? "" : selectedStatus.name());
        model.addAttribute("selectedSort", selectedSort.name().toLowerCase());
        model.addAttribute("pageTitleKey", "page.news.list.title");
        model.addAttribute("pageDescriptionKey", "page.news.list.description");
        model.addAttribute("ogTitleKey", "page.news.list.title");
        model.addAttribute("ogDescriptionKey", "page.news.list.description");
        log.debug("Rendering news list page with {} entries statusFilter={} sort={}",
                newsItems.size(), selectedStatus, selectedSort);
        return "news/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        NewsDetailDto newsDetail = newsQueryService.getNewsDetail(id).orElse(null);
        if (newsDetail == null) {
            log.warn("News detail requested with invalid id={}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "News event not found.");
            return "redirect:/news";
        }

        model.addAttribute("newsDetail", newsDetail);
        model.addAttribute("pageTitle", newsDetail.title() == null ? "News Detail" : newsDetail.title());
        String description = newsDetail.summary() == null || newsDetail.summary().isBlank()
                ? "Macro news detail and interpretation result."
                : newsDetail.summary();
        model.addAttribute("pageDescription", description);
        model.addAttribute("ogTitle", newsDetail.title() == null ? "News Detail" : newsDetail.title());
        model.addAttribute("ogDescription", description);
        model.addAttribute("ogUrl", "/news/" + newsDetail.id());
        log.debug("Rendering news detail page for id={}", id);
        return "news/detail";
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
}

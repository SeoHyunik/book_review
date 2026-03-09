package com.example.macronews.controller;

import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.service.news.NewsQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsQueryService newsQueryService;

    @GetMapping
    public String list(Model model) {
        List<NewsListItemDto> newsItems = newsQueryService.getRecentNews();
        model.addAttribute("newsItems", newsItems);
        model.addAttribute("pageTitle", "Macro News");
        model.addAttribute("pageDescription", "Recent macro news with AI interpretation status.");
        model.addAttribute("ogTitle", "Macro News");
        model.addAttribute("ogDescription", "Recent macro news with AI interpretation status.");
        log.debug("Rendering news list page with {} entries", newsItems.size());
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
}
package com.example.macronews.controller;

import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/archive")
@RequiredArgsConstructor
@Slf4j
public class ArchiveController {

    private static final int MAX_ARCHIVE_ITEMS = 20;
    private static final String PAGE_TITLE_KEY = "page.archive.title";
    private static final String PAGE_DESCRIPTION_KEY = "page.archive.description";

    private final NewsQueryService newsQueryService;

    @GetMapping
    public String list(Model model) {
        List<NewsListItemDto> archiveItems = safeGetArchiveItems();

        model.addAttribute("archiveItems", archiveItems);
        model.addAttribute("archiveCount", archiveItems.size());
        model.addAttribute("pageTitleKey", PAGE_TITLE_KEY);
        model.addAttribute("pageDescriptionKey", PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogTitleKey", PAGE_TITLE_KEY);
        model.addAttribute("ogDescriptionKey", PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogUrl", "/archive");
        return "archive/list";
    }

    private List<NewsListItemDto> safeGetArchiveItems() {
        try {
            return newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC).stream()
                    .limit(MAX_ARCHIVE_ITEMS)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Rendering /archive without archive items due to query failure", ex);
            return List.of();
        }
    }
}

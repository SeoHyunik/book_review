package com.example.macronews.controller;

import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.service.news.NewsQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;

@Controller
@RequestMapping("/archive")
@RequiredArgsConstructor
@Slf4j
public class ArchiveController {

    private static final int ARCHIVE_PAGE_SIZE = 20;
    private static final String PAGE_TITLE_KEY = "page.archive.title";
    private static final String PAGE_DESCRIPTION_KEY = "page.archive.description";

    private final NewsQueryService newsQueryService;

    @GetMapping
    public String list(@RequestParam(value = "page", required = false) String pageParam, Model model) {
        Page<NewsListItemDto> archivePage = safeGetArchivePage(pageParam);

        model.addAttribute("archiveItems", archivePage.getContent());
        model.addAttribute("archiveCount", archivePage.getTotalElements());
        model.addAttribute("archiveCurrentPage", archivePage.getNumber() + 1);
        model.addAttribute("archiveTotalPages", archivePage.getTotalPages());
        model.addAttribute("archiveHasPreviousPage", archivePage.hasPrevious());
        model.addAttribute("archiveHasNextPage", archivePage.hasNext());
        model.addAttribute("pageTitleKey", PAGE_TITLE_KEY);
        model.addAttribute("pageDescriptionKey", PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogTitleKey", PAGE_TITLE_KEY);
        model.addAttribute("ogDescriptionKey", PAGE_DESCRIPTION_KEY);
        model.addAttribute("ogUrl", "/archive");
        return "archive/list";
    }

    private Page<NewsListItemDto> safeGetArchivePage(String pageParam) {
        try {
            return newsQueryService.getArchiveNews(resolvePageNumber(pageParam), ARCHIVE_PAGE_SIZE);
        } catch (RuntimeException ex) {
            log.warn("Rendering /archive without archive items due to query failure", ex);
            return Page.empty(PageRequest.of(0, ARCHIVE_PAGE_SIZE));
        }
    }

    private int resolvePageNumber(String pageParam) {
        if (!StringUtils.hasText(pageParam)) {
            return 1;
        }
        try {
            return Math.max(Integer.parseInt(pageParam.trim()), 1);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}

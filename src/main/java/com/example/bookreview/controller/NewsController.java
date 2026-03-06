package com.example.bookreview.controller;

import com.example.bookreview.dto.domain.NewsEvent;
import com.example.bookreview.service.news.NewsQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsQueryService newsQueryService;

    @GetMapping
    public String list(Model model) {
        List<NewsEvent> newsEvents = newsQueryService.getRecentNews();
        model.addAttribute("newsEvents", newsEvents);
        model.addAttribute("pageTitle", "Macro News");
        log.debug("Rendering news list page with {} entries", newsEvents.size());
        return "news/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        NewsEvent newsEvent = newsQueryService.getNewsDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "News event not found"));
        model.addAttribute("newsEvent", newsEvent);
        model.addAttribute("pageTitle", "News Detail");
        log.debug("Rendering news detail page for id={}", id);
        return "news/detail";
    }
}

package com.example.macronews.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/topic")
@RequiredArgsConstructor
public class TopicController {

    private final TopicPageDataAssembler topicPageDataAssembler;

    @GetMapping("/dollar")
    public String dollar(Model model) {
        TopicPageData pageData = topicPageDataAssembler.buildDollarPageData();
        pageData.applyTo(model);
        return pageData.viewName();
    }

    @GetMapping("/rates")
    public String rates(Model model) {
        TopicPageData pageData = topicPageDataAssembler.buildRatesPageData();
        pageData.applyTo(model);
        return pageData.viewName();
    }
}

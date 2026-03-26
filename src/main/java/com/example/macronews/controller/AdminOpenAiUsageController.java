package com.example.macronews.controller;

import com.example.macronews.service.openai.OpenAiUsageReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/openai-usage")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOpenAiUsageController {

    private final OpenAiUsageReportService openAiUsageReportService;

    @GetMapping
    public String usage(@RequestParam(name = "page", required = false) Integer page, Model model) {
        model.addAttribute("pageTitleKey", "page.admin.openai.title");
        model.addAttribute("pageDescriptionKey", "page.admin.openai.description");
        model.addAttribute("usageDashboard", openAiUsageReportService.getDashboard(resolvePage(page)));
        return "admin/openai/usage";
    }

    private int resolvePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }
}

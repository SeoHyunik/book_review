package com.example.bookreview.controller;

import com.example.bookreview.dto.request.AdminIngestionRequest;
import com.example.bookreview.service.news.NewsIngestionService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/news")
@RequiredArgsConstructor
@Slf4j
public class AdminNewsController {

    private final NewsIngestionService newsIngestionService;

    @GetMapping
    public String ingestForm(Model model) {
        if (!model.containsAttribute("adminIngestionRequest")) {
            model.addAttribute("adminIngestionRequest", AdminIngestionRequest.empty());
        }
        model.addAttribute("pageTitle", "Admin News Ingestion");
        log.debug("Rendering admin news ingestion form");
        return "admin/news/ingest";
    }

    @PostMapping("/ingest")
    public String ingest(@Valid @ModelAttribute("adminIngestionRequest") AdminIngestionRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Principal principal) {
        if (bindingResult.hasErrors()) {
            log.warn("Admin news ingestion validation failed: {}", bindingResult.getAllErrors());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "News ingestion failed: invalid input.");
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.adminIngestionRequest",
                    bindingResult);
            redirectAttributes.addFlashAttribute("adminIngestionRequest", request);
            log.info("Redirecting to /admin/news due to validation errors");
            return "redirect:/admin/news";
        }

        try {
            String ingestedBy = principal == null ? "" : principal.getName();
            AdminIngestionRequest requestWithUser = new AdminIngestionRequest(
                    request.source(),
                    request.title(),
                    request.summary(),
                    request.content(),
                    request.url(),
                    request.publishedAt(),
                    ingestedBy
            );
            String savedId = newsIngestionService.ingestOne(requestWithUser);
            redirectAttributes.addFlashAttribute("successMessage",
                    "News ingested successfully. id=" + savedId);
            log.info("Admin news ingestion succeeded, redirecting to detail id={}", savedId);
            return "redirect:/news/" + savedId;
        } catch (RuntimeException ex) {
            log.error("Admin news ingestion failed; redirecting back to form", ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "News ingestion failed. Please try again.");
            redirectAttributes.addFlashAttribute("adminIngestionRequest", request);
            return "redirect:/admin/news";
        }
    }
}


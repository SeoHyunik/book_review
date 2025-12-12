package com.example.bookreview.controller;

import com.example.bookreview.domain.Review;
import com.example.bookreview.dto.ReviewRequest;
import com.example.bookreview.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("reviews", reviewService.getReviews());
        model.addAttribute("pageTitle", "리뷰 목록");
        return "reviews/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        Review review = reviewService.getReview(id).orElse(null);
        model.addAttribute("review", review);
        model.addAttribute("pageTitle", "리뷰 상세");
        return "reviews/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("reviewRequest", new ReviewRequest());
        model.addAttribute("pageTitle", "새 리뷰 작성");
        return "reviews/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("reviewRequest") ReviewRequest reviewRequest, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "reviews/form";
        }

        Review review = reviewService.createReview(reviewRequest);
        return "redirect:/reviews/" + review.getId();
    }
}

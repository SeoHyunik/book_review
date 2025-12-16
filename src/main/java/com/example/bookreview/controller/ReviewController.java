package com.example.bookreview.controller;

import com.example.bookreview.domain.Review;
import com.example.bookreview.dto.ReviewRequest;
import com.example.bookreview.service.ReviewService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String list(Model model) {
        model.addAttribute("reviews", reviewService.getReviews());
        model.addAttribute("pageTitle", "리뷰 목록");
        return "reviews/list";
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Review> listJson() {
        return reviewService.getReviews();
    }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String detail(@PathVariable String id, Model model) {
        Review review = reviewService.getReview(id).orElse(null);
        model.addAttribute("review", review);
        model.addAttribute("pageTitle", "리뷰 상세");
        return "reviews/detail";
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Review> detailJson(@PathVariable String id) {
        return reviewService.getReview(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/new", produces = MediaType.TEXT_HTML_VALUE)
    public String createForm(Model model) {
        model.addAttribute("reviewRequest", ReviewRequest.empty());
        model.addAttribute("pageTitle", "새 리뷰 작성");
        return "reviews/form";
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String create(@Valid @ModelAttribute("reviewRequest") ReviewRequest reviewRequest, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "reviews/form";
        }

        Review review = reviewService.createReview(reviewRequest);
        return "redirect:/reviews/" + review.id();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Review> createJson(@Valid @RequestBody ReviewRequest reviewRequest) {
        Review review = reviewService.createReview(reviewRequest);
        return ResponseEntity.created(URI.create("/reviews/" + review.id())).body(review);
    }
}

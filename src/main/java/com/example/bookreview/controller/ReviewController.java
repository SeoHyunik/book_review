package com.example.bookreview.controller;

import com.example.bookreview.dto.internal.Review;
import com.example.bookreview.dto.request.ReviewRequest;
import com.example.bookreview.service.ReviewService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String list(Model model) {
        log.info("[MVC] Rendering review list page");
        model.addAttribute("pageTitle", "리뷰 목록");
        return "reviews/list";
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Review> listJson() {
        log.info("[MVC] Fetching review list as JSON for API consumer");
        return reviewService.getReviews();
    }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String detail(@PathVariable String id, Model model) {
        log.info("[MVC] Displaying review detail page for id={}", id);
        Review review = reviewService.getReview(id).orElse(null);
        model.addAttribute("review", review);
        model.addAttribute("pageTitle", "리뷰 상세");
        return "reviews/detail";
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Review> detailJson(@PathVariable String id) {
        log.info("[MVC] Fetching review detail as JSON for id={}", id);
        return reviewService.getReview(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/new", produces = MediaType.TEXT_HTML_VALUE)
    public String createForm(Model model) {
        log.info("[MVC] Rendering review creation form");
        model.addAttribute("reviewRequest", ReviewRequest.empty());
        model.addAttribute("pageTitle", "새 리뷰 작성");
        return "reviews/form";
    }

    @PostMapping(consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public String create(@Valid @ModelAttribute("reviewRequest") ReviewRequest reviewRequest, BindingResult bindingResult,
        Model model) {
        log.info("[MVC] Received HTML form submission for new review: title='{}'", reviewRequest.getTitle());
        if (bindingResult.hasErrors()) {
            log.warn("[MVC] Validation errors while creating review via form: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "새 리뷰 작성");
            return "reviews/form";
        }

        Review review = reviewService.createReview(reviewRequest);
        log.info("[MVC] Review created successfully via form with id={}", review.id());
        return "redirect:/reviews/" + review.id();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Review> createJson(@Valid @RequestBody ReviewRequest reviewRequest) {
        log.info("[MVC] Received JSON request to create review: title='{}'", reviewRequest.getTitle());
        Review review = reviewService.createReview(reviewRequest);
        log.info("[MVC] Review created successfully via API with id={}", review.id());
        return ResponseEntity.created(URI.create("/reviews/" + review.id())).body(review);
    }
}

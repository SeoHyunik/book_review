package com.example.bookreview.controller;

import com.example.bookreview.dto.response.DeleteReviewResponse;
import com.example.bookreview.dto.internal.DeleteReviewResult;
import com.example.bookreview.service.review.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewApiController {

    private final ReviewService reviewService;

    @DeleteMapping("/{id}")
    public ResponseEntity<DeleteReviewResponse> deleteReview(@PathVariable String id) {
        log.info("[API] Delete request for review id={}", id);
        DeleteReviewResult result = reviewService.deleteReview(id);
        return ResponseEntity.ok(DeleteReviewResponse.from(result));
    }
}

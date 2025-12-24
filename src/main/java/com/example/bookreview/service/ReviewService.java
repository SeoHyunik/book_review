package com.example.bookreview.service;

import com.example.bookreview.dto.internal.Review;
import com.example.bookreview.dto.request.ReviewRequest;
import java.util.List;
import java.util.Optional;

public interface ReviewService {

    List<Review> getReviews();

    Optional<Review> getReview(String id);

    Review createReview(ReviewRequest request);

    DeleteReviewResult deleteReview(String id);
}

package com.example.bookreview.dto.response;

import com.example.bookreview.dto.internal.IntegrationStatus;
import com.example.bookreview.dto.domain.Review;

public record ReviewCreationResponse(
        String savedReviewId,
        IntegrationStatus integrationStatus,
        String message,
        Review review) {
}

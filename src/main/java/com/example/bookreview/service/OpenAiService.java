package com.example.bookreview.service;

import com.example.bookreview.service.model.AiReviewResult;

public interface OpenAiService {

    AiReviewResult generateImprovedReview(String title, String originalContent);
}

package com.example.bookreview.service.openai;

import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.response.OpenAiResponse;
import reactor.core.publisher.Mono;

public interface OpenAiService {

    Mono<OpenAiResponse> improveReview(String originalContent);

    AiReviewResult generateImprovedReview(String title, String originalContent);
}

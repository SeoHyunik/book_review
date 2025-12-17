package com.example.bookreview.service;

import com.example.bookreview.dto.OpenAiResponse;
import reactor.core.publisher.Mono;

public interface OpenAiService {

    Mono<OpenAiResponse> improveReview(String originalContent);
}

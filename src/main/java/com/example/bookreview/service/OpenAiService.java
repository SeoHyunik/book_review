package com.example.bookreview.service;

import com.example.bookreview.dto.OpenAiResult;

public interface OpenAiService {

    OpenAiResult improveReview(String originalContent);
}

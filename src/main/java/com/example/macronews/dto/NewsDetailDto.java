package com.example.macronews.dto;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.NewsStatus;
import java.time.Instant;

public record NewsDetailDto(
        String id,
        String title,
        String summary,
        String source,
        String url,
        Instant publishedAt,
        NewsStatus status,
        AnalysisResult analysisResult
) {
}
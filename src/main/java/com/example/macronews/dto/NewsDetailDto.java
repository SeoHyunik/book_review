package com.example.macronews.dto;

import com.example.macronews.domain.AnalysisResult;
import java.time.Instant;

public record NewsDetailDto(
        String id,
        String title,
        String summary,
        String source,
        Instant publishedAt,
        AnalysisResult analysisResult
) {
}
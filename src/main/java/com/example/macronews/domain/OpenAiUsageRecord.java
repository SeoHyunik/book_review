package com.example.macronews.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "openai_usage_records")
public record OpenAiUsageRecord(
        @Id String id,
        Instant timestamp,
        String model,
        OpenAiUsageFeatureType featureType,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}

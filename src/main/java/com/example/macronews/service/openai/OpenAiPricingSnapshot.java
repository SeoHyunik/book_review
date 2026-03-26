package com.example.macronews.service.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Map;

public record OpenAiPricingSnapshot(
        String version,
        SourceMetadata source,
        String currency,
        String unit,
        Map<String, ModelPricing> models,
        @JsonProperty("feature_profiles") Map<String, FeaturePricingProfile> featureProfiles
) {

    public record SourceMetadata(
            String type,
            @JsonProperty("verified_at") String verifiedAt,
            String notes
    ) {
    }

    public record ModelPricing(
            String status,
            @JsonProperty("source_status") String sourceStatus,
            BigDecimal input,
            @JsonProperty("cached_input") BigDecimal cachedInput,
            BigDecimal output
    ) {
    }

    public record FeaturePricingProfile(
            String model,
            @JsonProperty("source_status") String sourceStatus,
            BigDecimal input,
            @JsonProperty("cached_input") BigDecimal cachedInput,
            BigDecimal output
    ) {
    }
}

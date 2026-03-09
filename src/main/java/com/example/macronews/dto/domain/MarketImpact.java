package com.example.macronews.dto.domain;

import lombok.Builder;
import org.springframework.data.mongodb.core.mapping.Field;

@Builder
public record MarketImpact(
        @Field("asset") String asset,
        @Field("direction") ImpactDirection direction,
        @Field("confidence") ImpactConfidence confidence,
        @Field("rationale") String rationale
) {
}


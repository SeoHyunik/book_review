package com.example.bookreview.dto.domain;

import lombok.Builder;
import org.springframework.data.mongodb.core.mapping.Field;

@Builder
public record MacroImpact(
        @Field("indicator") String indicator,
        @Field("direction") ImpactDirection direction,
        @Field("confidence") ImpactConfidence confidence,
        @Field("rationale") String rationale
) {
}

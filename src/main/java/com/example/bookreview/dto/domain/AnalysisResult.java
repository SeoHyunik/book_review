package com.example.bookreview.dto.domain;

import java.util.List;
import lombok.Builder;
import org.springframework.data.mongodb.core.mapping.Field;

@Builder
public record AnalysisResult(
        @Field("summary") String summary,
        @Field("macroImpacts") List<MacroImpact> macroImpacts,
        @Field("marketImpacts") List<MarketImpact> marketImpacts
) {
}


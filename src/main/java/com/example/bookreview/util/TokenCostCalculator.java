package com.example.bookreview.util;

import com.example.bookreview.dto.internal.CostResult;
import com.example.bookreview.dto.internal.TokenPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCostCalculator {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private static final Map<String, TokenPrice> MODEL_PRICES = Map.of(
            "gpt-4o", new TokenPrice(new BigDecimal("0.01"), new BigDecimal("0.01")),
            "gpt-4o-mini", new TokenPrice(new BigDecimal("0.005"), new BigDecimal("0.005"))
    );

    public CostResult calculate(String model, int promptTokens, int completionTokens) {
        String effectiveModel = StringUtils.hasText(model) ? model : "gpt-4o";
        TokenPrice price = MODEL_PRICES.getOrDefault(effectiveModel, MODEL_PRICES.get("gpt-4o"));

        BigDecimal promptCost = price.promptPricePerThousand()
                .multiply(BigDecimal.valueOf(promptTokens))
                .divide(THOUSAND, 6, RoundingMode.HALF_UP);
        BigDecimal completionCost = price.completionPricePerThousand()
                .multiply(BigDecimal.valueOf(completionTokens))
                .divide(THOUSAND, 6, RoundingMode.HALF_UP);

        BigDecimal totalCost = promptCost.add(completionCost).setScale(6, RoundingMode.HALF_UP);
        long totalTokens = (long) promptTokens + completionTokens;
        log.debug(
                "[OPENAI] Calculated token cost: model={}, promptTokens={}, completionTokens={}, totalCost={}",
                effectiveModel, promptTokens, completionTokens, totalCost);
        return new CostResult(totalTokens, totalCost);
    }

}

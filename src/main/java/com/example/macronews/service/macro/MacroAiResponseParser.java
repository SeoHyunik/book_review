package com.example.macronews.service.macro;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.MarketType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
final class MacroAiResponseParser {

    private final ObjectMapper objectMapper;

    AnalysisResult parseAnalysisResult(String responseBody, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("OpenAI response message content was empty");
            }

            JsonNode node = extractJsonNode(content);
            JsonNode macroImpactsNode = node.path("macroImpacts");
            JsonNode marketImpactsNode = node.path("marketImpacts");
            if (!macroImpactsNode.isArray() || !marketImpactsNode.isArray()) {
                throw new IllegalStateException("Required impact arrays are missing");
            }

            List<MacroImpact> macroImpacts = parseMacroImpacts(macroImpactsNode);
            List<MarketImpact> marketImpacts = parseMarketImpacts(marketImpactsNode);

            return new AnalysisResult(
                    model,
                    Instant.now(),
                    readOptionalText(node, "headlineKo"),
                    readOptionalText(node, "headlineEn"),
                    readOptionalText(node, "summaryKo"),
                    readOptionalText(node, "summaryEn"),
                    macroImpacts,
                    marketImpacts
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse macro interpretation response", ex);
        }
    }

    private JsonNode extractJsonNode(String content) throws IOException {
        try {
            return objectMapper.readTree(content);
        } catch (Exception ignored) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return objectMapper.readTree(content.substring(start, end + 1));
            }
            throw new IOException("No JSON object found in model response");
        }
    }

    private List<MacroImpact> parseMacroImpacts(JsonNode arrayNode) {
        List<MacroImpact> impacts = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            MacroVariable variable = parseMacroVariable(node.path("variable").asText(null));
            ImpactDirection direction = parseDirection(node.path("direction").asText(null));
            double confidence = parseConfidence(node.path("confidence"));
            if (variable == null) {
                continue;
            }
            impacts.add(new MacroImpact(variable, direction, confidence));
        }
        return impacts;
    }

    private List<MarketImpact> parseMarketImpacts(JsonNode arrayNode) {
        List<MarketImpact> impacts = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            MarketType market = parseMarketType(node.path("market").asText(null));
            ImpactDirection direction = parseDirection(node.path("direction").asText(null));
            double confidence = parseConfidence(node.path("confidence"));
            if (market == null) {
                continue;
            }
            impacts.add(new MarketImpact(market, direction, confidence));
        }
        return impacts;
    }

    private MacroVariable parseMacroVariable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MacroVariable.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private MarketType parseMarketType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MarketType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ImpactDirection parseDirection(String value) {
        if (!StringUtils.hasText(value)) {
            return ImpactDirection.NEUTRAL;
        }
        try {
            return ImpactDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ImpactDirection.NEUTRAL;
        }
    }

    private String readOptionalText(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        String value = node.path(fieldName).asText("").trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private double parseConfidence(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0.5d;
        }
        try {
            double raw = node.isNumber() ? node.asDouble() : Double.parseDouble(node.asText("0.5"));
            if (raw < 0d) {
                return 0d;
            }
            if (raw > 1d) {
                return 1d;
            }
            return raw;
        } catch (Exception ex) {
            return 0.5d;
        }
    }
}

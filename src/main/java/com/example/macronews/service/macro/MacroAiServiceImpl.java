package com.example.macronews.service.macro;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.MarketType;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class MacroAiServiceImpl implements MacroAiService {

    private static final String SOURCE_PLACEHOLDER = "{{source}}";
    private static final String TITLE_PLACEHOLDER = "{{title}}";
    private static final String SUMMARY_PLACEHOLDER = "{{summary}}";
    private static final String URL_PLACEHOLDER = "{{url}}";
    private static final String PUBLISHED_AT_PLACEHOLDER = "{{publishedAt}}";

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;
    private final NewsEventRepository newsEventRepository;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.api-url:}")
    private String openAiUrl;

    @Value("${openai.model:gpt-4o}")
    private String openAiModel;

    @Value("${openai.max-tokens:800}")
    private int openAiMaxTokens;

    @Value("${openai.temperature:0.2}")
    private double openAiTemperature;

    @Value("${openai.macro-prompt-file:classpath:ai/prompts/macro_interpretation_prompt.json}")
    private Resource macroPromptFile;

    @Override
    public AnalysisResult interpret(NewsEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        log.info("[INTERPRET] start id={} status={}", event.id(), event.status());

        validateConfig();

        String payload = buildPayload(event);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        ExternalApiResult apiResult = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.POST,
                headers,
                openAiUrl,
                payload
        ));

        if (apiResult == null) {
            throw new IllegalStateException("OpenAI interpretation response was null");
        }
        if (apiResult.statusCode() < 200 || apiResult.statusCode() >= 300) {
            throw new IllegalStateException(
                    "OpenAI interpretation failed with status=" + apiResult.statusCode());
        }

        AnalysisResult result = parseAnalysisResult(apiResult.body());
        log.info("[INTERPRET] success id={} macroImpacts={} marketImpacts={}", event.id(),
                result.macroImpacts() == null ? 0 : result.macroImpacts().size(),
                result.marketImpacts() == null ? 0 : result.marketImpacts().size());
        return result;
    }

    @Override
    @CacheEvict(cacheNames = "newsDetail", key = "#newsEventId", beforeInvocation = true)
    public NewsEvent interpretAndSave(String newsEventId) {
        log.info("[INTERPRET] persist-start id={}", newsEventId);
        NewsEvent event = newsEventRepository.findById(newsEventId)
                .orElseThrow(() -> new IllegalArgumentException("NewsEvent not found: " + newsEventId));

        try {
            AnalysisResult analysisResult = interpret(event);
            NewsEvent analyzed = copyWithStatusAndResult(event, NewsStatus.ANALYZED, analysisResult);
            NewsEvent saved = newsEventRepository.save(analyzed);
            log.info("[INTERPRET] persist-success id={} status={}", saved.id(), saved.status());
            return saved;
        } catch (Exception ex) {
            log.error("[INTERPRET] persist-failure id={}", newsEventId, ex);
            NewsEvent failed = copyWithStatusAndResult(event, NewsStatus.FAILED, null);
            NewsEvent saved = newsEventRepository.save(failed);
            log.info("[INTERPRET] persisted-failed id={} status={}", saved.id(), saved.status());
            return saved;
        }
    }

    private NewsEvent copyWithStatusAndResult(NewsEvent base, NewsStatus status,
            AnalysisResult result) {
        return new NewsEvent(
                base.id(),
                base.externalId(),
                base.title(),
                base.summary(),
                base.source(),
                base.url(),
                base.publishedAt(),
                base.ingestedAt(),
                status,
                result
        );
    }

    private void validateConfig() {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalStateException("openai.api-key is not configured");
        }
        if (!StringUtils.hasText(openAiUrl)) {
            throw new IllegalStateException("openai.api-url is not configured");
        }
        if (!StringUtils.hasText(openAiModel)) {
            throw new IllegalStateException("openai.model is not configured");
        }
    }

    private String buildPayload(NewsEvent event) {
        try {
            JsonNode promptRoot = loadPromptTemplate();
            JsonNode promptMessages = promptRoot.get("messages");
            if (promptMessages == null || !promptMessages.isArray()) {
                throw new IllegalStateException("Macro prompt file must contain messages array");
            }

            List<Object> messages = new ArrayList<>();
            for (JsonNode node : promptMessages) {
                String role = node.path("role").asText("");
                if (!StringUtils.hasText(role)) {
                    continue;
                }

                String content;
                if (node.has("content")) {
                    content = node.get("content").asText("");
                } else {
                    content = fillTemplate(node.path("template").asText(""), event);
                }
                if (!StringUtils.hasText(content)) {
                    continue;
                }

                messages.add(java.util.Map.of("role", role, "content", content));
            }

            java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("model", openAiModel);
            root.put("messages", messages);
            root.put("max_tokens", openAiMaxTokens);
            root.put("temperature", openAiTemperature);
            root.put("response_format", java.util.Map.of("type", "json_object"));

            return objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build macro interpretation payload", ex);
        }
    }

    private JsonNode loadPromptTemplate() throws IOException {
        try (Reader reader = new InputStreamReader(macroPromptFile.getInputStream(),
                StandardCharsets.UTF_8)) {
            return objectMapper.readTree(reader);
        }
    }

    private String fillTemplate(String template, NewsEvent event) {
        return template
                .replace(SOURCE_PLACEHOLDER, safe(event.source()))
                .replace(TITLE_PLACEHOLDER, safe(event.title()))
                .replace(SUMMARY_PLACEHOLDER, safe(event.summary()))
                .replace(URL_PLACEHOLDER, safe(event.url()))
                .replace(PUBLISHED_AT_PLACEHOLDER, String.valueOf(event.publishedAt()));
    }

    private AnalysisResult parseAnalysisResult(String responseBody) {
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
                    openAiModel,
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
            return MacroVariable.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private MarketType parseMarketType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MarketType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ImpactDirection parseDirection(String value) {
        if (!StringUtils.hasText(value)) {
            return ImpactDirection.NEUTRAL;
        }
        try {
            return ImpactDirection.valueOf(value.trim().toUpperCase());
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

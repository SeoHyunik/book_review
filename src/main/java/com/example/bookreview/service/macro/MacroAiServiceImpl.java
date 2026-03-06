package com.example.bookreview.service.macro;

import com.example.bookreview.dto.domain.AnalysisResult;
import com.example.bookreview.dto.domain.ImpactConfidence;
import com.example.bookreview.dto.domain.ImpactDirection;
import com.example.bookreview.dto.domain.MacroImpact;
import com.example.bookreview.dto.domain.MarketImpact;
import com.example.bookreview.dto.domain.NewsEvent;
import com.example.bookreview.dto.request.ExternalApiRequest;
import com.example.bookreview.util.ExternalApiResult;
import com.example.bookreview.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String CONTENT_PLACEHOLDER = "{{content}}";
    private static final String URL_PLACEHOLDER = "{{url}}";
    private static final String PUBLISHED_AT_PLACEHOLDER = "{{publishedAt}}";

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.api-url:}")
    private String openAiUrl;

    @Value("${openai.model:}")
    private String openAiModel;

    @Value("${openai.max-tokens:800}")
    private int openAiMaxTokens;

    @Value("${openai.temperature:0.2}")
    private double openAiTemperature;

    @Value("${openai.macro-prompt-file:classpath:ai/prompts/macro_interpretation_prompt.json}")
    private Resource macroPromptFile;

    @Override
    public AnalysisResult interpretNewsEvent(NewsEvent newsEvent) {
        if (newsEvent == null) {
            throw new IllegalArgumentException("newsEvent must not be null");
        }
        log.info("Starting AI interpretation for newsEventId={}, externalId={}", newsEvent.id(),
                newsEvent.externalId());

        validateConfig();

        String payload = buildPayload(newsEvent);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        ExternalApiRequest request = new ExternalApiRequest(
                HttpMethod.POST,
                headers,
                openAiUrl,
                payload
        );

        ExternalApiResult apiResult = externalApiUtils.callAPI(request);
        if (apiResult == null) {
            throw new IllegalStateException("OpenAI interpretation response was null");
        }

        if (apiResult.statusCode() < 200 || apiResult.statusCode() >= 300) {
            throw new IllegalStateException(
                    "OpenAI interpretation failed with status=" + apiResult.statusCode());
        }

        AnalysisResult result = parseAnalysisResult(apiResult.body());
        log.info("AI interpretation succeeded for newsEventId={}", newsEvent.id());
        return result;
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

    private String buildPayload(NewsEvent newsEvent) {
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
                    String template = node.path("template").asText("");
                    content = fillTemplate(template, newsEvent);
                }
                if (!StringUtils.hasText(content)) {
                    continue;
                }

                messages.add(java.util.Map.of(
                        "role", role,
                        "content", content
                ));
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

    private String fillTemplate(String template, NewsEvent newsEvent) {
        return template
                .replace(SOURCE_PLACEHOLDER, safe(newsEvent.source()))
                .replace(TITLE_PLACEHOLDER, safe(newsEvent.title()))
                .replace(SUMMARY_PLACEHOLDER, safe(newsEvent.summary()))
                .replace(CONTENT_PLACEHOLDER, safe(newsEvent.content()))
                .replace(URL_PLACEHOLDER, safe(newsEvent.url()))
                .replace(PUBLISHED_AT_PLACEHOLDER, String.valueOf(newsEvent.publishedAt()));
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

            JsonNode analysisNode = extractJsonNode(content);
            return AnalysisResult.builder()
                    .summary(analysisNode.path("summary").asText(""))
                    .macroImpacts(parseMacroImpacts(analysisNode.path("macroImpacts")))
                    .marketImpacts(parseMarketImpacts(analysisNode.path("marketImpacts")))
                    .build();
        } catch (Exception ex) {
            log.error("Failed to parse macro interpretation response. rawBody={}", responseBody, ex);
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
                String jsonBlock = content.substring(start, end + 1);
                return objectMapper.readTree(jsonBlock);
            }
            throw new IOException("No JSON object found in model response");
        }
    }

    private List<MacroImpact> parseMacroImpacts(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<MacroImpact> impacts = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            impacts.add(MacroImpact.builder()
                    .indicator(node.path("indicator").asText(""))
                    .direction(parseDirection(node.path("direction").asText(null)))
                    .confidence(parseConfidence(node.path("confidence").asText(null)))
                    .rationale(node.path("rationale").asText(""))
                    .build());
        }
        return impacts;
    }

    private List<MarketImpact> parseMarketImpacts(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<MarketImpact> impacts = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            impacts.add(MarketImpact.builder()
                    .asset(node.path("asset").asText(""))
                    .direction(parseDirection(node.path("direction").asText(null)))
                    .confidence(parseConfidence(node.path("confidence").asText(null)))
                    .rationale(node.path("rationale").asText(""))
                    .build());
        }
        return impacts;
    }

    private ImpactDirection parseDirection(String direction) {
        if (!StringUtils.hasText(direction)) {
            return ImpactDirection.NEUTRAL;
        }
        try {
            return ImpactDirection.valueOf(direction.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ImpactDirection.NEUTRAL;
        }
    }

    private ImpactConfidence parseConfidence(String confidence) {
        if (!StringUtils.hasText(confidence)) {
            return ImpactConfidence.MEDIUM;
        }
        try {
            return ImpactConfidence.valueOf(confidence.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ImpactConfidence.MEDIUM;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
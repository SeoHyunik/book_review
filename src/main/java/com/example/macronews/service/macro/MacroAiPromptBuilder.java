package com.example.macronews.service.macro;

import com.example.macronews.domain.NewsEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
final class MacroAiPromptBuilder {

    private static final String SOURCE_PLACEHOLDER = "{{source}}";
    private static final String TITLE_PLACEHOLDER = "{{title}}";
    private static final String SUMMARY_PLACEHOLDER = "{{summary}}";
    private static final String URL_PLACEHOLDER = "{{url}}";
    private static final String PUBLISHED_AT_PLACEHOLDER = "{{publishedAt}}";

    private final ObjectMapper objectMapper;

    String buildPayload(NewsEvent event, String model, int maxTokens, double temperature, Resource promptFile) {
        try {
            JsonNode promptRoot = loadPromptTemplate(promptFile);
            JsonNode promptMessages = promptRoot.get("messages");
            if (promptMessages == null || !promptMessages.isArray()) {
                throw new IllegalStateException("Macro prompt file must contain messages array");
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            for (JsonNode node : promptMessages) {
                String role = node.path("role").asText("");
                if (!StringUtils.hasText(role)) {
                    continue;
                }

                String content = node.has("content")
                        ? node.get("content").asText("")
                        : fillTemplate(node.path("template").asText(""), event);
                if (!StringUtils.hasText(content)) {
                    continue;
                }

                messages.add(Map.of("role", role, "content", content));
            }

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("model", model);
            root.put("messages", messages);
            root.put("max_tokens", maxTokens);
            root.put("temperature", temperature);
            root.put("response_format", Map.of("type", "json_object"));
            return objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build macro interpretation payload", ex);
        }
    }

    private JsonNode loadPromptTemplate(Resource promptFile) throws IOException {
        try (Reader reader = new InputStreamReader(promptFile.getInputStream(), StandardCharsets.UTF_8)) {
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

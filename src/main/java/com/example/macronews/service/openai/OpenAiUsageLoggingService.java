package com.example.macronews.service.openai;

import com.example.macronews.domain.OpenAiUsageFeatureType;
import com.example.macronews.domain.OpenAiUsageRecord;
import com.example.macronews.repository.OpenAiUsageRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiUsageLoggingService {

    private final OpenAiUsageRecordRepository openAiUsageRecordRepository;
    private final ObjectMapper objectMapper;

    public void recordUsage(OpenAiUsageFeatureType featureType, String model, String responseBody) {
        if (featureType == null || !StringUtils.hasText(responseBody)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode usage = root.path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                return;
            }

            int promptTokens = Math.max(usage.path("prompt_tokens").asInt(0), 0);
            int completionTokens = Math.max(usage.path("completion_tokens").asInt(0), 0);
            int totalTokens = usage.has("total_tokens")
                    ? Math.max(usage.path("total_tokens").asInt(promptTokens + completionTokens), 0)
                    : promptTokens + completionTokens;

            if (promptTokens == 0 && completionTokens == 0 && totalTokens == 0) {
                return;
            }

            openAiUsageRecordRepository.save(new OpenAiUsageRecord(
                    null,
                    Instant.now(),
                    resolveRecordedModel(root, model),
                    featureType,
                    promptTokens,
                    completionTokens,
                    totalTokens
            ));
        } catch (Exception ex) {
            log.debug("[OPENAI-USAGE] usage capture skipped", ex);
        }
    }

    private String resolveRecordedModel(JsonNode root, String configuredModel) {
        String responseModel = root.path("model").asText("").trim();
        if (StringUtils.hasText(responseModel)) {
            return responseModel;
        }
        if (StringUtils.hasText(configuredModel)) {
            return configuredModel.trim();
        }
        return "unknown";
    }
}

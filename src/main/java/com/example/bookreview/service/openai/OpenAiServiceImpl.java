package com.example.bookreview.service.openai;

import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.internal.CostResult;
import com.example.bookreview.dto.internal.ParsedOpenAiResult;
import com.example.bookreview.dto.request.ExternalApiRequest;
import com.example.bookreview.dto.response.OpenAiResponse;
import com.example.bookreview.exception.MissingApiKeyException;
import com.example.bookreview.util.ExternalApiUtils;
import com.example.bookreview.util.TokenCostCalculator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiServiceImpl implements OpenAiService {

    private static final String DEFAULT_OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String TITLE_PLACEHOLDER = "{{title}}";
    private static final String CONTENT_PLACEHOLDER = "{{originalContent}}";
    private final ExternalApiUtils apiUtils;
    private final Gson gson;
    private final TokenCostCalculator tokenCostCalculator;
    @Value("${openai.api-key:}")
    private String openAiApiKey;
    @Value("${openai.api-url:" + DEFAULT_OPENAI_URL + "}")
    private String openAiUrl;
    @Value("${openai.model:" + DEFAULT_MODEL + "}")
    private String openAiModel;
    @Value("${openai.prompt-file}")
    private Resource promptFile;

    @Override
    public AiReviewResult generateImprovedReview(String title, String originalContent) {
        log.info("[OPENAI] Received request to improve review content for title='{}'", title);
        try {
            ParsedOpenAiResult parsedResult = executeImproveReview(title, originalContent);
            CostResult costResult = tokenCostCalculator.calculate(
                    parsedResult.model(), parsedResult.inputTokens(), parsedResult.outputTokens());
            return new AiReviewResult(
                    parsedResult.improvedContent(),
                    parsedResult.model(),
                    parsedResult.inputTokens(),
                    parsedResult.outputTokens(),
                    costResult.totalTokens(),
                    costResult.usdCost());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OPENAI] Failed to generate improved review", e);
            throw new RuntimeException("OpenAI API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public Mono<OpenAiResponse> improveReview(String originalContent) {
        return Mono.fromCallable(() -> {
            AiReviewResult result = generateImprovedReview("Untitled", originalContent);
            return new OpenAiResponse(result.improvedContent(), result.model(),
                    result.promptTokens(), result.completionTokens());
        });
    }

    private ParsedOpenAiResult executeImproveReview(String title, String originalContent) {
        validateRequest(title, originalContent);

        String payload = buildChatCompletionPayload(title, originalContent);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        ExternalApiRequest apiRequest = new ExternalApiRequest(
                HttpMethod.POST,
                headers,
                openAiUrl,
                payload);

        ParsedOpenAiResult parsedResult = callAndParse(apiRequest);
        if (!"stop".equalsIgnoreCase(parsedResult.finishReason())) {
            log.warn("[OPENAI] OpenAI response finish_reason was '{}', retrying once",
                    parsedResult.finishReason());
            parsedResult = callAndParse(apiRequest);
        }

        return parsedResult;
    }

    private ParsedOpenAiResult callAndParse(ExternalApiRequest apiRequest) {
        log.debug("[OPENAI] Sending request to OpenAI endpoint: {}", openAiUrl);
        ResponseEntity<String> responseEntity = apiUtils.callAPI(apiRequest);
        if (responseEntity == null || !responseEntity.hasBody()) {
            throw new IllegalStateException("OpenAI API response body was empty");
        }
        log.info("[OPENAI] Received response with status {}", responseEntity.getStatusCode());
        return parseResponse(responseEntity.getBody());
    }

    private void validateRequest(String title, String originalContent) {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new MissingApiKeyException("OpenAI API key is not configured");
        }
        Assert.isTrue(StringUtils.hasText(title), "Review title must not be blank");
        Assert.isTrue(StringUtils.hasText(originalContent),
                "Original review content must not be blank");
    }

    private String buildChatCompletionPayload(String title, String originalContent) {
        JsonObject root = new JsonObject();
        root.addProperty("model", openAiModel);
        root.add("messages", buildMessages(title, originalContent));
        root.addProperty("temperature", 0.7);

        return gson.toJson(root);
    }

    private JsonArray buildMessages(String title, String originalContent) {
        JsonObject promptRoot = loadPromptTemplate();
        JsonArray promptMessages = promptRoot.getAsJsonArray("messages");
        Assert.notNull(promptMessages, "Prompt file must include a messages array");

        JsonArray messages = new JsonArray();
        for (JsonElement element : promptMessages) {
            JsonObject promptMessage = element.getAsJsonObject();
            String role = promptMessage.has("role") ? promptMessage.get("role").getAsString() : "";
            Assert.hasText(role, "Prompt message role must not be blank");

            String content = null;
            if (promptMessage.has("content")) {
                content = promptMessage.get("content").getAsString();
            } else if (promptMessage.has("template")) {
                String template = promptMessage.get("template").getAsString();
                content = template.replace(TITLE_PLACEHOLDER, title)
                        .replace(CONTENT_PLACEHOLDER, originalContent);
            }
            Assert.hasText(content, "Prompt message content must not be blank");

            JsonObject message = new JsonObject();
            message.addProperty("role", role);
            message.addProperty("content", content);
            messages.add(message);
        }

        return messages;
    }

    private JsonObject loadPromptTemplate() {
        try (Reader reader = new InputStreamReader(promptFile.getInputStream(),
                StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root == null) {
                throw new IllegalStateException("Prompt file was empty");
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt file: " + promptFile, e);
        }
    }

    private ParsedOpenAiResult parseResponse(String responseBody) {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        if (root == null) {
            throw new IllegalStateException("Failed to parse OpenAI response body");
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI returned no choices");
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new IllegalStateException("OpenAI response did not include message content");
        }

        String improvedContent = message.get("content").getAsString();
        String finishReason =
                firstChoice.has("finish_reason") ? firstChoice.get("finish_reason").getAsString()
                        : "";

        String model = root.has("model") ? root.get("model").getAsString() : "";
        JsonObject usage = root.getAsJsonObject("usage");
        int promptTokens = extractTokenCount(usage, "prompt_tokens");
        int completionTokens = extractTokenCount(usage, "completion_tokens");

        return new ParsedOpenAiResult(improvedContent.trim(), model, finishReason, promptTokens,
                completionTokens);
    }

    private int extractTokenCount(JsonObject usage, String field) {
        if (usage == null) {
            return 0;
        }
        JsonElement element = usage.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

}

package com.example.bookreview.service.openai;

import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.request.ExternalApiRequest;
import com.example.bookreview.dto.response.OpenAiResponse;
import com.example.bookreview.exception.MissingApiKeyException;
import com.example.bookreview.exception.OpenAiApiException;
import com.example.bookreview.util.ExternalApiUtils;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiServiceImpl implements OpenAiService {

    private static final String DEFAULT_OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String TITLE_PLACEHOLDER = "{{title}}";
    private static final String CONTENT_PLACEHOLDER = "{{originalContent}}";
    private static final String FALLBACK_PREFIX = "[IMPROVEMENT_SKIPPED]\n";
    private static final String RATE_LIMIT_REASON = "RATE_LIMITED";
    private final ExternalApiUtils apiUtils;
    private final Gson gson;
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
        return executeImproveReview(title, originalContent);
    }

    @Override
    public OpenAiResponse improveReview(String originalContent) {
        AiReviewResult result = generateImprovedReview("Untitled", originalContent);
        return new OpenAiResponse(result.improvedContent(), result.model(), 0, 0);
    }

    private AiReviewResult executeImproveReview(String title, String originalContent) {
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

        AiReviewResult result = callAndParse(apiRequest, originalContent);
        if (result.fromAi() && !"stop".equalsIgnoreCase(result.reason())) {
            log.warn("[OPENAI] OpenAI response finish_reason was '{}', retrying once",
                    result.reason());
            result = callAndParse(apiRequest, originalContent);
        }

        return result;
    }

    private AiReviewResult callAndParse(ExternalApiRequest apiRequest, String originalContent) {
        log.debug("[OPENAI] Sending request to OpenAI endpoint: {}", openAiUrl);
        ResponseEntity<String> responseEntity = apiUtils.callAPI(apiRequest);
        if (responseEntity == null) {
            throw new OpenAiApiException("OpenAI API 응답을 받을 수 없습니다.");
        }

        int statusCode = responseEntity.getStatusCode().value();
        log.info("[OPENAI] Received response with status {}", statusCode);

        return switch (statusCode) {
            case int code when code >= 200 && code < 300 -> parseResponse(responseEntity.getBody());
            case 429 -> {
                log.warn("[OPENAI] Rate limited by OpenAI (429). Returning fallback response.");
                yield fallbackResult(originalContent, RATE_LIMIT_REASON);
            }
            default -> throw new OpenAiApiException(
                    "OpenAI API 호출 실패: status=" + statusCode);
        };
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

    private AiReviewResult parseResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new OpenAiApiException("OpenAI API response body was empty");
        }
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        if (root == null) {
            throw new OpenAiApiException("Failed to parse OpenAI response body");
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new OpenAiApiException("OpenAI returned no choices");
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new OpenAiApiException("OpenAI response did not include message content");
        }

        String improvedContent = message.get("content").getAsString();
        String finishReason =
                firstChoice.has("finish_reason") ? firstChoice.get("finish_reason").getAsString()
                        : "";

        String model = root.has("model") ? root.get("model").getAsString() : "";

        return new AiReviewResult(improvedContent.trim(), true, model, finishReason);
    }

    private AiReviewResult fallbackResult(String originalContent, String reason) {
        String content = FALLBACK_PREFIX + originalContent;
        return new AiReviewResult(content, false, "fallback", reason);
    }

}

package com.example.bookreview.service.openai;

import com.example.bookreview.dto.internal.AiReviewResult;
import com.example.bookreview.dto.request.ExternalApiRequest;
import com.example.bookreview.dto.response.OpenAiResponse;
import com.example.bookreview.exception.MissingApiKeyException;
import com.example.bookreview.exception.OpenAiApiException;
import com.example.bookreview.util.ExternalApiError;
import com.example.bookreview.util.ExternalApiResult;
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
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiServiceImpl implements OpenAiService {

    private static final String DEFAULT_OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_OPENAI_MODELS_URL = "https://api.openai.com/v1/models";
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String TITLE_PLACEHOLDER = "{{title}}";
    private static final String CONTENT_PLACEHOLDER = "{{originalContent}}";
    private static final String FALLBACK_PREFIX = "[IMPROVEMENT_SKIPPED]\n";
    private static final String RATE_LIMIT_REASON = "RATE_LIMIT";
    private static final String INSUFFICIENT_QUOTA_REASON = "INSUFFICIENT_QUOTA";
    private static final String INVALID_KEY_REASON = "INVALID_KEY";
    private static final String INVALID_MODEL_REASON = "INVALID_MODEL";
    private static final String UNSUPPORTED_PARAM_REASON = "UNSUPPORTED_PARAM";
    private static final String UNKNOWN_REASON = "UNKNOWN";
    private final ExternalApiUtils apiUtils;
    private final Gson gson;
    @Value("${openai.api-key:}")
    private String openAiApiKey;
    @Value("${openai.api-url:" + DEFAULT_OPENAI_URL + "}")
    private String openAiUrl;
    @Value("${openai.models-url:" + DEFAULT_OPENAI_MODELS_URL + "}")
    private String openAiModelsUrl;
    @Value("${openai.model:" + DEFAULT_MODEL + "}")
    private String openAiModel;
    @Value("${openai.max-tokens:0}")
    private int openAiMaxTokens;
    @Value("${openai.temperature:#{null}}")
    private Double openAiTemperature;
    @Value("${openai.prompt-file}")
    private Resource promptFile;

    @Override
    public AiReviewResult generateImprovedReview(String title, String originalContent) {
        log.info("[OPENAI] Received request to improve review content for title='{}'", title);
        validateRequest(title, originalContent);
        OpenAiStatusCheck statusCheck = checkOpenAiStatus();
        if (!statusCheck.available()) {
            return new AiReviewResult(FALLBACK_PREFIX + originalContent, false, "unavailable",
                    statusCheck.reason(), 0, 0, 0);
        }
        return executeImproveReview(title, originalContent);
    }

    @Override
    public OpenAiResponse improveReview(String originalContent) {
        AiReviewResult result = generateImprovedReview("Untitled", originalContent);
        return new OpenAiResponse(result.improvedContent(), result.model(), result.promptTokens(),
                result.completionTokens());
    }

    private AiReviewResult executeImproveReview(String title, String originalContent) {
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
        ExternalApiResult apiResult = apiUtils.callAPI(apiRequest);
        if (apiResult == null) {
            throw new OpenAiApiException("OpenAI API 응답을 받을 수 없습니다.");
        }

        int statusCode = apiResult.statusCode();
        log.info("[OPENAI] Received response with status {}", statusCode);

        return switch (statusCode) {
            case int code when code >= 200 && code < 300 -> parseResponse(apiResult.body());
            case 429 -> {
                ExternalApiError error = apiUtils.parseErrorResponse(apiResult.body());
                String reason = resolveReason(statusCode, error);
                log.warn(
                        "[OPENAI] OpenAI rate limit status={} type={} code={} message={} param={}",
                        statusCode,
                        error.type(),
                        error.code(),
                        error.message(),
                        error.param());
                yield fallbackResult(originalContent, reason);
            }
            default -> {
                ExternalApiError error = apiUtils.parseErrorResponse(apiResult.body());
                String reason = resolveReason(statusCode, error);
                log.warn(
                        "[OPENAI] OpenAI error response status={} type={} code={} message={} param={}",
                        statusCode,
                        error.type(),
                        error.code(),
                        error.message(),
                        error.param());
                yield fallbackResult(originalContent, reason);
            }
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
        if (openAiMaxTokens > 0) {
            root.addProperty("max_tokens", openAiMaxTokens);
        }
        if (openAiTemperature != null) {
            root.addProperty("temperature", openAiTemperature);
        }

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

        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        if (root.has("usage") && root.get("usage").isJsonObject()) {
            JsonObject usage = root.getAsJsonObject("usage");
            promptTokens = readInt(usage, "prompt_tokens");
            completionTokens = readInt(usage, "completion_tokens");
            totalTokens = readInt(usage, "total_tokens");
        }

        return new AiReviewResult(improvedContent.trim(), true, model, finishReason, promptTokens,
                completionTokens, totalTokens);
    }

    private AiReviewResult fallbackResult(String originalContent, String reason) {
        String content = FALLBACK_PREFIX + originalContent;
        return new AiReviewResult(content, false, "fallback", reason, 0, 0, 0);
    }

    private int readInt(JsonObject root, String field) {
        if (root == null || field == null || !root.has(field)) {
            return 0;
        }
        JsonElement element = root.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (Exception ex) {
            return 0;
        }
    }

    private OpenAiStatusCheck checkOpenAiStatus() {
        log.info("[OPENAI] Checking OpenAI API status before requesting completion");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openAiApiKey);
        ExternalApiRequest apiRequest = new ExternalApiRequest(
                HttpMethod.GET,
                headers,
                openAiModelsUrl,
                null);
        ExternalApiResult result = apiUtils.callAPI(apiRequest);
        if (result == null) {
            return new OpenAiStatusCheck(false, UNKNOWN_REASON);
        }
        if (result.statusCode() >= 200 && result.statusCode() < 300) {
            if (isModelAvailable(result.body())) {
                log.info("[OPENAI] Status check OK — valid OpenAI API key");
                return new OpenAiStatusCheck(true, "OK");
            }
            log.warn("[OPENAI] OpenAI model '{}' not found in /v1/models response",
                    openAiModel);
            return new OpenAiStatusCheck(false, INVALID_MODEL_REASON);
        }
        ExternalApiError error = apiUtils.parseErrorResponse(result.body());
        String reason = resolveReason(result.statusCode(), error);
        log.warn("[OPENAI] OpenAI status check failed status={} type={} code={} message={} param={}",
                result.statusCode(), error.type(), error.code(), error.message(), error.param());
        return new OpenAiStatusCheck(false, reason);
    }

    private String resolveReason(int statusCode, ExternalApiError error) {
        String type = error != null ? error.type() : null;
        String code = error != null ? error.code() : null;
        String param = error != null ? error.param() : null;
        if (statusCode == 401) {
            return INVALID_KEY_REASON;
        }
        if (statusCode == 429 || containsIgnoreCase(type, "rate_limit")) {
            return RATE_LIMIT_REASON;
        }
        if (containsIgnoreCase(type, "insufficient_quota")
                || containsIgnoreCase(code, "insufficient_quota")) {
            return INSUFFICIENT_QUOTA_REASON;
        }
        if (containsIgnoreCase(type, "invalid_api_key")
                || containsIgnoreCase(code, "invalid_api_key")) {
            return INVALID_KEY_REASON;
        }
        if (containsIgnoreCase(code, "unsupported_parameter")) {
            if (containsIgnoreCase(param, "model")) {
                return INVALID_MODEL_REASON;
            }
            return UNSUPPORTED_PARAM_REASON;
        }
        return UNKNOWN_REASON;
    }

    private boolean containsIgnoreCase(String value, String token) {
        if (value == null || token == null) {
            return false;
        }
        return value.toLowerCase().contains(token.toLowerCase());
    }

    private boolean isModelAvailable(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        try {
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);
            if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
                return false;
            }
            JsonArray data = root.getAsJsonArray("data");
            for (JsonElement element : data) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject model = element.getAsJsonObject();
                if (model.has("id") && openAiModel.equals(model.get("id").getAsString())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.debug("[OPENAI] Failed to parse models response", ex);
            return false;
        }
    }

    private record OpenAiStatusCheck(boolean available, String reason) {
    }

}

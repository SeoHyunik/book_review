package com.example.bookreview.util;

import com.example.bookreview.dto.request.ExternalApiRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalApiUtils {

    private final WebClient.Builder webClientBuilder;

    public ExternalApiResult callAPI(ExternalApiRequest request) {
        Assert.notNull(request, "External API request must not be null");
        Assert.notNull(request.method(), "HTTP method must not be null");
        Assert.hasText(request.url(), "Request URL must not be blank");

        HttpHeaders headers = request.headers() != null ? request.headers() : new HttpHeaders();
        HttpHeaders sanitizedHeaders = sanitizeHeaders(headers);
        log.info("[HTTP] Calling external API: method={}, url={}, headers={}", request.method(),
                request.url(), sanitizedHeaders);

        try {
            return webClientBuilder
                    .build()
                    .method(request.method())
                    .uri(request.url())
                    .headers(httpHeaders -> httpHeaders.addAll(headers))
                    .bodyValue(request.body() != null ? request.body() : "")
                    .exchangeToMono(response -> {
                        int statusCode = response.statusCode().value();
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new ExternalApiResult(statusCode, body));
                    })
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("[HTTP] External API responded with status={} bodyLength={}",
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString() != null ? ex.getResponseBodyAsString().length()
                            : 0);
            return new ExternalApiResult(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (WebClientRequestException ex) {
            log.warn("[HTTP] External API request failed: {}", ex.getMessage());
            return new ExternalApiResult(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage());
        }
    }

    public ExternalApiError parseErrorResponse(String body) {
        if (body == null || body.isBlank()) {
            return new ExternalApiError(null, null, null);
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                return new ExternalApiError(null, null, null);
            }
            JsonObject root = element.getAsJsonObject();
            if (!root.has("error") || !root.get("error").isJsonObject()) {
                return new ExternalApiError(null, null, null);
            }
            JsonObject error = root.getAsJsonObject("error");
            String message = error.has("message") ? error.get("message").getAsString() : null;
            String type = error.has("type") ? error.get("type").getAsString() : null;
            String code = error.has("code") ? error.get("code").getAsString() : null;
            return new ExternalApiError(message, type, code);
        } catch (Exception ex) {
            log.debug("[HTTP] Failed to parse external API error body", ex);
            return new ExternalApiError(null, null, null);
        }
    }

    private HttpHeaders sanitizeHeaders(HttpHeaders headers) {
        HttpHeaders sanitized = new HttpHeaders();
        headers.forEach((key, values) -> {
            if ("authorization".equalsIgnoreCase(key)) {
                values.forEach(value -> sanitized.add(key, maskAuthorization(value)));
            } else {
                sanitized.addAll(key, values);
            }
        });
        return sanitized;
    }

    private String maskAuthorization(String value) {
        if (value == null || value.isBlank()) {
            return "****(masked)";
        }
        if (!value.startsWith("Bearer ")) {
            return "****(masked)";
        }
        String token = value.substring("Bearer ".length());
        String prefix = token;
        int secondDash = nthIndex(token, '-', 2);
        if (secondDash > 0) {
            prefix = token.substring(0, secondDash + 1);
        } else if (token.length() > 6) {
            prefix = token.substring(0, 6);
        }
        return "Bearer " + prefix + "****(masked)";
    }

    private int nthIndex(String value, char token, int n) {
        int index = -1;
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == token) {
                count++;
                if (count == n) {
                    index = i;
                    break;
                }
            }
        }
        return index;
    }
}

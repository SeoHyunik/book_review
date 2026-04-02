package com.example.macronews.util;

import com.example.macronews.dto.request.ExternalApiRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalApiUtils {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient.Builder webClientBuilder;
    @Value("${app.external-api.timeout:30s}")
    private Duration timeout = DEFAULT_TIMEOUT;

    public ExternalApiResult callAPI(ExternalApiRequest request) {
        Assert.notNull(request, "External API request must not be null");
        Assert.notNull(request.method(), "HTTP method must not be null");
        Assert.hasText(request.url(), "Request URL must not be blank");

        HttpHeaders headers = request.headers() != null ? request.headers() : new HttpHeaders();
        HttpHeaders sanitizedHeaders = sanitizeHeaders(headers);
        log.info("[HTTP] Calling external API: method={}, url={}, headers={}", request.method(),
                sanitizeUrl(request.url()), sanitizedHeaders);

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
                    .timeout(resolveTimeout())
                    .onErrorResume(TimeoutException.class, ex -> {
                        Duration resolvedTimeout = resolveTimeout();
                        log.warn("[HTTP] External API request timed out after {}: method={}, url={}",
                                resolvedTimeout, request.method(), sanitizeUrl(request.url()));
                        return Mono.just(new ExternalApiResult(
                                HttpStatus.GATEWAY_TIMEOUT.value(),
                                "External API request timed out after " + resolvedTimeout));
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

    private Duration resolveTimeout() {
        return timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_TIMEOUT
                : timeout;
    }

    public ExternalApiError parseErrorResponse(String body) {
        ExternalApiError empty = new ExternalApiError(null, null, null, null);
        try {
            return Optional.ofNullable(body)
                    .filter(StringUtils::hasText)
                    .map(JsonParser::parseString)
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .filter(root -> root.has("error") && root.get("error").isJsonObject())
                    .map(root -> root.getAsJsonObject("error"))
                    .map(error -> new ExternalApiError(
                            readString(error, "message"),
                            readString(error, "type"),
                            readString(error, "code"),
                            readString(error, "param")))
                    .orElse(empty);
        } catch (Exception ex) {
            log.debug("[HTTP] Failed to parse external API error body", ex);
            return empty;
        }
    }

    private String readString(JsonObject root, String field) {
        return Optional.ofNullable(root)
                .filter(obj -> obj.has(field))
                .map(obj -> obj.get(field))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .orElse(null);
    }

    private HttpHeaders sanitizeHeaders(HttpHeaders headers) {
        HttpHeaders sanitized = new HttpHeaders();
        headers.forEach((key, values) -> {
            if ("authorization".equalsIgnoreCase(key)
                    || "x-naver-client-id".equalsIgnoreCase(key)
                    || "x-naver-client-secret".equalsIgnoreCase(key)) {
                values.forEach(value -> sanitized.add(key, maskAuthorization(value)));
            } else {
                sanitized.addAll(key, values);
            }
        });
        return sanitized;
    }

    private String sanitizeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sanitized = value.replaceAll("(?i)([?&](?:apiKey|api_key|access_key|serviceKey)=)[^&]+", "$1****(masked)");
        sanitized = sanitized.replaceAll("(?i)(/v6/)[^/]+(/latest/[^?]+)", "$1****(masked)$2");
        return sanitized;
    }

    private String maskAuthorization(String value) {
        if (value == null || value.isBlank()) {
            return "****(masked)";
        }
        if (value.startsWith("Token ")) {
            return "Token ****(masked)";
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

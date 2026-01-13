package com.example.bookreview.util;

import com.example.bookreview.dto.request.ExternalApiRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public ResponseEntity<String> callAPI(ExternalApiRequest request) {
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
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.toEntity(String.class);
                        }
                        if (response.statusCode().value() == 429) {
                            return response.toEntity(String.class);
                        }
                        return response.createException().flatMap(Mono::error);
                    })
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("[HTTP] External API responded with status={} bodyLength={}",
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString() != null ? ex.getResponseBodyAsString().length()
                            : 0);
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(ex.getHeaders())
                    .body(ex.getResponseBodyAsString());
        } catch (WebClientRequestException ex) {
            log.warn("[HTTP] External API request failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ex.getMessage());
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

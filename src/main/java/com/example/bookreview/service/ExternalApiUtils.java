package com.example.bookreview.service;

import com.example.bookreview.dto.internal.ExternalApiRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

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
        log.info("[HTTP] Calling external API: method={}, url={}, headers={}", request.method(), request.url(), headers);

        return webClientBuilder
                .build()
                .method(request.method())
                .uri(request.url())
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(request.body() != null ? request.body() : "")
                .retrieve()
                .toEntity(String.class)
                .block();
    }
}

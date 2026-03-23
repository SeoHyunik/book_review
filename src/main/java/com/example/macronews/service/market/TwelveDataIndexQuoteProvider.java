package com.example.macronews.service.market;

import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwelveDataIndexQuoteProvider implements IndexQuoteProvider {

    private static final String QUOTE_URL = "https://api.twelvedata.com/quote";

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.market.index.enabled:false}")
    private boolean enabled;

    @Value("${app.market.index.api-key:}")
    private String apiKey;

    @Override
    public Optional<IndexSnapshotDto> getQuote(String symbol) {
        if (!isConfigured() || !StringUtils.hasText(symbol)) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromUriString(QUOTE_URL)
                .queryParam("symbol", symbol)
                .queryParam("apikey", apiKey)
                .build()
                .encode()
                .toUriString();
        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                new HttpHeaders(),
                url,
                null
        ));
        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            log.warn("[INDEX] provider call failed status={} symbol={}", result == null ? -1 : result.statusCode(), symbol);
            return Optional.empty();
        }
        return parseSnapshot(symbol, result.body());
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    Optional<IndexSnapshotDto> parseSnapshot(String requestedSymbol, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if ("error".equalsIgnoreCase(root.path("status").asText(""))) {
                return Optional.empty();
            }

            Double price = readDouble(root, "price");
            if (price == null) {
                price = readDouble(root, "close");
            }
            if (price == null) {
                return Optional.empty();
            }

            String responseSymbol = root.path("symbol").asText("");
            String symbol = StringUtils.hasText(responseSymbol) ? responseSymbol : requestedSymbol;
            Instant capturedAt = readInstant(root, "timestamp");
            if (capturedAt == null) {
                capturedAt = readInstant(root, "datetime");
            }
            if (capturedAt == null) {
                capturedAt = Instant.now();
            }
            return Optional.of(new IndexSnapshotDto(symbol, price, capturedAt));
        } catch (Exception ex) {
            log.warn("[INDEX] failed to parse response symbol={}", requestedSymbol, ex);
            return Optional.empty();
        }
    }

    private Double readDouble(JsonNode node, String field) {
        if (!node.has(field) || node.path(field).isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.asDouble();
        }
        String text = value.asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private Instant readInstant(JsonNode node, String field) {
        if (!node.has(field) || node.path(field).isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return Instant.ofEpochSecond(value.asLong());
        }
        String text = value.asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }
}

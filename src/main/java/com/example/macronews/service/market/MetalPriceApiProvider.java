package com.example.macronews.service.market;

import com.example.macronews.dto.market.GoldSnapshotDto;
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
public class MetalPriceApiProvider implements GoldPriceProvider {

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.market.gold.enabled:false}")
    private boolean enabled;

    @Value("${app.market.gold.base-url:https://api.metalpriceapi.com/v1/latest}")
    private String baseUrl;

    @Value("${app.market.gold.api-key:}")
    private String apiKey;

    @Override
    public Optional<GoldSnapshotDto> getGold() {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("api_key", apiKey)
                .queryParam("base", "USD")
                .queryParam("currencies", "XAU")
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
            log.warn("[GOLD] provider call failed status={}", result == null ? -1 : result.statusCode());
            return Optional.empty();
        }
        return parseSnapshot(result.body());
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    Optional<GoldSnapshotDto> parseSnapshot(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode rates = root.path("rates");
            if (!rates.has("XAU")) {
                return Optional.empty();
            }
            double xauRate = rates.path("XAU").asDouble(Double.NaN);
            if (Double.isNaN(xauRate) || xauRate <= 0d) {
                return Optional.empty();
            }
            double usdPerOunce = 1d / xauRate;
            Instant capturedAt = root.has("timestamp")
                    ? Instant.ofEpochSecond(root.path("timestamp").asLong())
                    : Instant.now();
            String baseCurrency = root.path("base").asText("USD");
            return Optional.of(new GoldSnapshotDto(baseCurrency, usdPerOunce, capturedAt));
        } catch (Exception ex) {
            log.warn("[GOLD] failed to parse response", ex);
            return Optional.empty();
        }
    }
}

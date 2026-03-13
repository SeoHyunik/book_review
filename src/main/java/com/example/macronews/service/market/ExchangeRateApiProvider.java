package com.example.macronews.service.market;

import com.example.macronews.dto.market.FxSnapshotDto;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateApiProvider implements ExchangeRateProvider {

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.market.fx.enabled:false}")
    private boolean enabled;

    @Value("${app.market.fx.base-url:https://v6.exchangerate-api.com}")
    private String baseUrl;

    @Value("${app.market.fx.api-key:}")
    private String apiKey;

    @Override
    public Optional<FxSnapshotDto> getUsdKrw() {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String url = baseUrl + "/v6/" + apiKey + "/latest/USD";
        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                new HttpHeaders(),
                url,
                null
        ));
        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            log.warn("[FX] provider call failed status={}", result == null ? -1 : result.statusCode());
            return Optional.empty();
        }
        return parseSnapshot(result.body());
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    Optional<FxSnapshotDto> parseSnapshot(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode rates = root.path("conversion_rates");
            if (!rates.has("KRW")) {
                return Optional.empty();
            }
            double rate = rates.path("KRW").asDouble(Double.NaN);
            if (Double.isNaN(rate)) {
                return Optional.empty();
            }
            Instant capturedAt = root.has("time_last_update_unix")
                    ? Instant.ofEpochSecond(root.path("time_last_update_unix").asLong())
                    : Instant.now();
            return Optional.of(new FxSnapshotDto("USD", "KRW", rate, capturedAt));
        } catch (Exception ex) {
            log.warn("[FX] failed to parse response", ex);
            return Optional.empty();
        }
    }
}

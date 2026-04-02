package com.example.macronews.service.market;

import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.example.macronews.util.external.ExternalResponseValueParser;
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
public class OilPriceApiProvider implements OilPriceProvider {

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.market.oil.enabled:false}")
    private boolean enabled;

    @Value("${app.market.oil.url:https://api.oilpriceapi.com/v1/prices/latest}")
    private String url;

    @Value("${app.market.oil.api-key:}")
    private String apiKey;

    @Override
    public Optional<OilSnapshotDto> getOil() {
        if (!isConfigured()) {
            return Optional.empty();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Token " + apiKey);
        ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                HttpMethod.GET,
                headers,
                url,
                null
        ));
        if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            log.warn("[OIL] provider call failed status={}", result == null ? -1 : result.statusCode());
            return Optional.empty();
        }
        return parseSnapshot(result.body());
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    Optional<OilSnapshotDto> parseSnapshot(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                data = root;
            }

            Double wti = ExternalResponseValueParser.readDouble(data, "wti");
            Double brent = ExternalResponseValueParser.readDouble(data, "brent");
            Instant capturedAt = ExternalResponseValueParser.readInstant(data, "timestamp");
            if (capturedAt == null) {
                capturedAt = ExternalResponseValueParser.readInstant(data, "created_at");
            }
            if (capturedAt == null) {
                capturedAt = Instant.now();
            }
            if (wti == null && brent == null) {
                return Optional.empty();
            }
            return Optional.of(new OilSnapshotDto(wti, brent, capturedAt));
        } catch (Exception ex) {
            log.warn("[OIL] failed to parse response", ex);
            return Optional.empty();
        }
    }

}

package com.example.macronews.service.market;

import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.example.macronews.util.external.ExternalResponseValueParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
public class FredUs10yProvider implements Us10yProvider {

    private static final String SERIES_ID = "DGS10";
    private static final String SOURCE = "FRED";

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.market.us10y.enabled:false}")
    private boolean enabled;

    @Value("${app.market.us10y.base-url:https://api.stlouisfed.org/fred}")
    private String baseUrl;

    @Value("${app.market.us10y.api-key:}")
    private String apiKey;

    @Override
    public Optional<Us10ySnapshotDto> getUs10y() {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/series/observations")
                .queryParam("series_id", SERIES_ID)
                .queryParam("api_key", apiKey)
                .queryParam("file_type", "json")
                .queryParam("sort_order", "desc")
                .queryParam("limit", 20)
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
            log.warn("[US10Y] provider call failed status={}", result == null ? -1 : result.statusCode());
            return Optional.empty();
        }
        return parseSnapshot(result.body());
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    Optional<Us10ySnapshotDto> parseSnapshot(String body) {
        try {
            JsonNode observations = objectMapper.readTree(body).path("observations");
            if (!observations.isArray()) {
                return Optional.empty();
            }

            for (JsonNode observation : observations) {
                String valueText = observation.path("value").asText("");
                if (!StringUtils.hasText(valueText) || ".".equals(valueText.trim())) {
                    continue;
                }

                Double yield = ExternalResponseValueParser.readDouble(observation, "value");
                LocalDate asOfDate = ExternalResponseValueParser.parseLocalDate(observation.path("date").asText(""));
                if (yield == null || asOfDate == null) {
                    continue;
                }

                return Optional.of(new Us10ySnapshotDto(yield, asOfDate, SOURCE, SERIES_ID));
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("[US10Y] failed to parse response", ex);
            return Optional.empty();
        }
    }

}

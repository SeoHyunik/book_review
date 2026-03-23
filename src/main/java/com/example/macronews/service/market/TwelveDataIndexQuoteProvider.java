package com.example.macronews.service.market;

import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private static final String LEGACY_QUOTE_URL = "https://api.twelvedata.com/quote";
    private static final String PUBLIC_DATA_URL = "https://apis.data.go.kr/1160100/service/GetMarketIndexInfoService/getStockMarketIndex";
    private static final String KOSPI_SYMBOL = "KOSPI";
    private static final String KOSPI_INDEX_NAME = "코스피";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

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

        if (isKospiSymbol(symbol)) {
            return fetchKospiFromPublicData();
        }

        return fetchLegacyQuote(symbol);
    }

    private Optional<IndexSnapshotDto> fetchKospiFromPublicData() {
        String url = UriComponentsBuilder.fromUriString(PUBLIC_DATA_URL)
                .queryParam("serviceKey", apiKey)
                .queryParam("resultType", "json")
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .queryParam("idxNm", KOSPI_INDEX_NAME)
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
            log.warn("[INDEX] public data call failed status={} idxNm={}", result == null ? -1 : result.statusCode(), KOSPI_INDEX_NAME);
            return Optional.empty();
        }
        return parsePublicDataSnapshot(result.body());
    }

    private Optional<IndexSnapshotDto> fetchLegacyQuote(String symbol) {
        String url = UriComponentsBuilder.fromUriString(LEGACY_QUOTE_URL)
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
            log.warn("[INDEX] legacy provider call failed status={} symbol={}", result == null ? -1 : result.statusCode(), symbol);
            return Optional.empty();
        }
        return parseLegacySnapshot(symbol, result.body());
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    Optional<IndexSnapshotDto> parsePublicDataSnapshot(String body) {
        try {
            JsonNode item = readPublicDataItem(body);
            if (item == null || item.isMissingNode() || item.isNull()) {
                return Optional.empty();
            }

            Double price = readDouble(item, "clpr");
            if (price == null) {
                return Optional.empty();
            }

            String label = item.path("idxNm").asText("");
            String symbol = StringUtils.hasText(label) ? label : KOSPI_SYMBOL;
            Instant capturedAt = readBasicDate(item, "basDt");
            if (capturedAt == null) {
                capturedAt = Instant.now();
            }
            return Optional.of(new IndexSnapshotDto(symbol, price, capturedAt));
        } catch (Exception ex) {
            log.warn("[INDEX] failed to parse public data response", ex);
            return Optional.empty();
        }
    }

    Optional<IndexSnapshotDto> parseLegacySnapshot(String requestedSymbol, String body) {
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

    private JsonNode readPublicDataItem(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode itemNode = root.path("response").path("body").path("items").path("item");
        if (itemNode.isArray()) {
            return itemNode.size() > 0 ? itemNode.get(0) : null;
        }
        return itemNode;
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

    private Instant readBasicDate(JsonNode node, String field) {
        if (!node.has(field) || node.path(field).isNull()) {
            return null;
        }
        String text = node.path(field).asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
            return date.atStartOfDay(KOREA_ZONE).toInstant();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isKospiSymbol(String symbol) {
        return KOSPI_SYMBOL.equalsIgnoreCase(symbol) || KOSPI_INDEX_NAME.equals(symbol);
    }
}

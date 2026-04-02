package com.example.macronews.service.market;

import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.example.macronews.util.external.ExternalResponseTextNormalizer;
import com.example.macronews.util.external.ExternalResponseValueParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
public class TwelveDataDxyProvider implements DxyProvider {

    private static final String SYMBOL_SEARCH_URL = "https://api.twelvedata.com/symbol_search";
    private static final String QUOTE_URL = "https://api.twelvedata.com/quote";
    private static final String DIRECT_SOURCE = "TWELVE_DATA_DIRECT";
    private static final String SYNTHETIC_SOURCE = "TWELVE_DATA_SYNTHETIC";
    private static final String SYNTHETIC_SOURCE_SERIES = "FX_BASKET_6";
    private static final double SYNTHETIC_MULTIPLIER = 50.14348112d;
    private static final double EUR_USD_EXPONENT = -0.576d;
    private static final double USD_JPY_EXPONENT = 0.136d;
    private static final double GBP_USD_EXPONENT = -0.119d;
    private static final double USD_CAD_EXPONENT = 0.091d;
    private static final double USD_SEK_EXPONENT = 0.042d;
    private static final double USD_CHF_EXPONENT = 0.036d;
    private static final List<String> SYMBOL_SEARCH_TERMS = List.of("DXY", "Dollar Index", "U.S. Dollar Index");
    private static final List<String> FX_PAIR_SYMBOLS = List.of(
            "EUR/USD",
            "USD/JPY",
            "GBP/USD",
            "USD/CAD",
            "USD/SEK",
            "USD/CHF"
    );

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    @Value("${app.market.dxy.enabled:false}")
    private boolean enabled;

    @Value("${app.market.dxy.api-key:}")
    private String apiKey;

    @Override
    public Optional<DxySnapshotDto> getDxy() {
        if (!isConfigured()) {
            return Optional.empty();
        }

        Optional<String> directSymbol = discoverDirectSymbol();
        if (directSymbol.isPresent()) {
            Optional<DxySnapshotDto> directSnapshot = fetchQuoteSnapshot(directSymbol.get(), false, directSymbol.get());
            if (directSnapshot.isPresent()) {
                return directSnapshot;
            }
            log.warn("[DXY] direct symbol quote unavailable symbol={}, falling back to synthetic basket", directSymbol.get());
        }

        return computeSyntheticDxy();
    }

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    Optional<DxySnapshotDto> parseQuoteSnapshot(String body, boolean synthetic, String sourceSeries) {
        try {
            JsonNode root = objectMapper.readTree(body);
            Double value = ExternalResponseValueParser.readDouble(root, "price");
            if (value == null) {
                value = ExternalResponseValueParser.readDouble(root, "close");
            }
            if (value == null || value <= 0d) {
                return Optional.empty();
            }

            Instant capturedAt = ExternalResponseValueParser.readInstant(root, "timestamp");
            if (capturedAt == null) {
                capturedAt = ExternalResponseValueParser.readInstant(root, "datetime");
            }
            if (capturedAt == null) {
                return Optional.empty();
            }

            String responseSeries = root.path("symbol").asText("");
            String resolvedSeries = StringUtils.hasText(responseSeries) ? responseSeries : sourceSeries;
            return Optional.of(new DxySnapshotDto(
                    value,
                    capturedAt,
                    synthetic ? SYNTHETIC_SOURCE : DIRECT_SOURCE,
                    resolvedSeries,
                    synthetic
            ));
        } catch (Exception ex) {
            log.warn("[DXY] failed to parse quote response sourceSeries={}", sourceSeries, ex);
            return Optional.empty();
        }
    }

    Optional<String> discoverDirectSymbol() {
        Set<String> candidates = new LinkedHashSet<>();
        for (String term : SYMBOL_SEARCH_TERMS) {
            Optional<String> symbol = searchSymbol(term);
            if (symbol.isPresent()) {
                candidates.add(symbol.get());
            }
        }
        return candidates.stream().findFirst();
    }

    Optional<DxySnapshotDto> computeSyntheticDxy() {
        List<FxPairQuote> quotes = FX_PAIR_SYMBOLS.stream()
                .map(this::fetchFxPairQuote)
                .flatMap(Optional::stream)
                .toList();
        if (quotes.size() != FX_PAIR_SYMBOLS.size()) {
            return Optional.empty();
        }

        double dxy = SYNTHETIC_MULTIPLIER;
        for (FxPairQuote quote : quotes) {
            dxy *= Math.pow(quote.value(), quote.exponent());
        }
        Instant asOfDateTime = quotes.stream()
                .map(FxPairQuote::capturedAt)
                .min(Instant::compareTo)
                .orElse(null);
        if (asOfDateTime == null) {
            return Optional.empty();
        }
        return Optional.of(new DxySnapshotDto(
                dxy,
                asOfDateTime,
                SYNTHETIC_SOURCE,
                SYNTHETIC_SOURCE_SERIES,
                true
        ));
    }

    Optional<FxPairQuote> fetchFxPairQuote(String symbol) {
        Optional<DxySnapshotDto> snapshot = fetchQuoteSnapshot(symbol, true, symbol);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        DxySnapshotDto quote = snapshot.get();
        double exponent = resolveExponent(symbol);
        if (Double.isNaN(exponent)) {
            return Optional.empty();
        }
        return Optional.of(new FxPairQuote(symbol, quote.asOfDateTime(), quote.value(), exponent));
    }

    Optional<DxySnapshotDto> fetchQuoteSnapshot(String symbol, boolean synthetic, String sourceSeries) {
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
            log.warn("[DXY] quote call failed status={} symbol={}", result == null ? -1 : result.statusCode(), symbol);
            return Optional.empty();
        }
        return parseQuoteSnapshot(result.body(), synthetic, sourceSeries);
    }

    Optional<String> searchSymbol(String term) {
        String url = UriComponentsBuilder.fromUriString(SYMBOL_SEARCH_URL)
                .queryParam("symbol", term)
                .queryParam("apikey", apiKey)
                .queryParam("outputsize", 10)
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
            log.warn("[DXY] symbol search failed status={} term={}", result == null ? -1 : result.statusCode(), term);
            return Optional.empty();
        }

        try {
            JsonNode data = objectMapper.readTree(result.body()).path("data");
            if (!data.isArray()) {
                return Optional.empty();
            }
            for (JsonNode candidate : data) {
                if (!isVerifiedDxyCandidate(candidate)) {
                    continue;
                }
                String symbol = candidate.path("symbol").asText("");
                if (StringUtils.hasText(symbol)) {
                    return Optional.of(symbol);
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("[DXY] failed to parse symbol search response term={}", term, ex);
            return Optional.empty();
        }
    }

    private boolean isVerifiedDxyCandidate(JsonNode candidate) {
        if (candidate == null || !candidate.isObject()) {
            return false;
        }
        String symbol = ExternalResponseTextNormalizer.normalizeLowerCase(candidate.path("symbol").asText(""));
        String name = ExternalResponseTextNormalizer.normalizeLowerCase(candidate.path("instrument_name").asText(""));
        String type = ExternalResponseTextNormalizer.normalizeLowerCase(candidate.path("instrument_type").asText(""));
        return "dxy".equals(symbol)
                || name.contains("dollar index")
                || name.contains("u.s. dollar index")
                || name.contains("us dollar index")
                || (type.contains("index") && name.contains("dollar"));
    }

    private double resolveExponent(String symbol) {
        if (symbol == null) {
            return Double.NaN;
        }
        return switch (symbol.replace(" ", "").toUpperCase()) {
            case "EUR/USD" -> EUR_USD_EXPONENT;
            case "USD/JPY" -> USD_JPY_EXPONENT;
            case "GBP/USD" -> GBP_USD_EXPONENT;
            case "USD/CAD" -> USD_CAD_EXPONENT;
            case "USD/SEK" -> USD_SEK_EXPONENT;
            case "USD/CHF" -> USD_CHF_EXPONENT;
            default -> Double.NaN;
        };
    }

    private record FxPairQuote(String symbol, Instant capturedAt, double value, double exponent) {
    }
}

package com.example.macronews.service.forecast;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.OpenAiUsageFeatureType;
import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.openai.OpenAiUsageLoggingService;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsAggregationService {

    private static final int MIN_REQUIRED_NEWS_ITEMS = 2;
    private static final MarketMood DEFAULT_MOOD = MarketMood.CLOUD;

    private final NewsEventRepository newsEventRepository;
    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;
    private final OpenAiUsageLoggingService openAiUsageLoggingService;
    private final MarketDataFacade marketDataFacade;

    private final AtomicReference<CachedSnapshot> cachedSnapshot = new AtomicReference<>();

    @Value("${app.forecast.enabled:true}")
    private boolean forecastEnabled;

    @Value("${app.forecast.window-hours:3}")
    private int windowHours;

    @Value("${app.forecast.max-news-items:20}")
    private int maxNewsItems;

    @Value("${app.forecast.cache-minutes:15}")
    private int cacheMinutes;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.api-url:}")
    private String openAiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${openai.max-tokens:800}")
    private int openAiMaxTokens;

    @Value("${openai.temperature:0.2}")
    private double openAiTemperature;

    @Value("${openai.forecast-prompt-file:classpath:ai/prompts/market_forecast_aggregation_prompt.json}")
    private Resource forecastPromptFile;

    public Optional<MarketForecastSnapshotDto> getCurrentSnapshot() {
        if (!forecastEnabled) {
            return Optional.empty();
        }

        CachedSnapshot current = cachedSnapshot.get();
        if (current != null && !current.isExpired(resolveCacheDuration())) {
            return current.snapshot();
        }

        Optional<MarketForecastSnapshotDto> regenerated = generateCurrentSnapshot();
        cachedSnapshot.set(new CachedSnapshot(regenerated, Instant.now()));
        return regenerated;
    }

    Optional<MarketForecastSnapshotDto> generateCurrentSnapshot() {
        ForecastPreparation preparation = loadForecastPreparation();
        List<NewsEvent> candidates = preparation.recentNews();
        if (candidates.size() < MIN_REQUIRED_NEWS_ITEMS) {
            log.info("[FORECAST] skipped reason=insufficient-news size={}", candidates.size());
            return Optional.empty();
        }
        if (!isConfigured()) {
            log.info("[FORECAST] skipped reason=openai-not-configured");
            return Optional.empty();
        }

        try {
            String marketContext = preparation.marketContext();
            String payload = buildPayload(candidates, marketContext);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            ExternalApiResult apiResult = externalApiUtils.callAPI(new ExternalApiRequest(
                    HttpMethod.POST,
                    headers,
                    openAiUrl,
                    payload
            ));
            if (apiResult == null || apiResult.statusCode() < 200 || apiResult.statusCode() >= 300) {
                log.warn("[FORECAST] aggregation failed status={}", apiResult == null ? -1 : apiResult.statusCode());
                return Optional.empty();
            }
            openAiUsageLoggingService.recordUsage(
                    OpenAiUsageFeatureType.MARKET_FORECAST,
                    openAiModel,
                    apiResult.body());
            return Optional.of(parseSnapshot(apiResult.body(), candidates));
        } catch (Exception ex) {
            log.warn("[FORECAST] aggregation failed", ex);
            return Optional.empty();
        }
    }

    private ForecastPreparation loadForecastPreparation() {
        try {
            ForecastPreparation preparation = Mono.zip(
                            Mono.fromCallable(this::loadRecentAnalyzedNews)
                                    .subscribeOn(Schedulers.boundedElastic()),
                            Mono.fromCallable(this::resolveMarketContext)
                                    .subscribeOn(Schedulers.boundedElastic()))
                    .map(tuple -> new ForecastPreparation(tuple.getT1(), tuple.getT2()))
                    .block();
            return preparation == null ? ForecastPreparation.empty() : preparation;
        } catch (RuntimeException ex) {
            log.warn("[FORECAST] preparation failed", ex);
            return ForecastPreparation.empty();
        }
    }

    List<NewsEvent> loadRecentAnalyzedNews() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(resolveWindowHours()));
        return newsEventRepository.findByStatus(NewsStatus.ANALYZED).stream()
                .filter(event -> event.analysisResult() != null)
                .filter(event -> event.publishedAt() != null && !event.publishedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(NewsEvent::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(resolveMaxNewsItems())
                .toList();
    }

    String buildPayload(List<NewsEvent> recentNews, String marketContext) {
        try {
            JsonNode promptRoot = loadPromptTemplate();
            JsonNode promptMessages = promptRoot.path("messages");
            if (!promptMessages.isArray()) {
                throw new IllegalStateException("Forecast prompt file must contain messages array");
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            for (JsonNode node : promptMessages) {
                String role = node.path("role").asText("");
                if (!StringUtils.hasText(role)) {
                    continue;
                }
                String content = node.has("content")
                        ? node.path("content").asText("")
                        : appendMarketContext(
                                node.path("template").asText("").replace("{{newsItemsJson}}", buildNewsItemsJson(recentNews)),
                                marketContext
                        );
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                messages.add(Map.of("role", role, "content", content));
            }

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("model", openAiModel);
            root.put("messages", messages);
            root.put("max_tokens", openAiMaxTokens);
            root.put("temperature", openAiTemperature);
            root.put("response_format", Map.of("type", "json_object"));
            return objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build market forecast payload", ex);
        }
    }

    MarketForecastSnapshotDto parseSnapshot(String responseBody, List<NewsEvent> sourceNews) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("Market forecast response content was empty");
            }

            JsonNode node = extractJsonNode(content);
            MarketMood mood = parseMood(node.path("mood").asText(null));
            List<String> keyDrivers = readStringArray(node.path("keyDrivers"), 5);
            List<String> relatedNewsIds = filterKnownNewsIds(readStringArray(node.path("relatedNewsIds"), resolveMaxNewsItems()), sourceNews);
            List<String> relatedNewsTitles = readStringArray(node.path("relatedNewsTitles"), resolveMaxNewsItems());
            if (relatedNewsIds.isEmpty()) {
                relatedNewsIds = sourceNews.stream().map(NewsEvent::id).filter(StringUtils::hasText).limit(5).toList();
            }
            if (relatedNewsTitles.isEmpty()) {
                relatedNewsTitles = sourceNews.stream().map(NewsEvent::title).filter(StringUtils::hasText).limit(5).toList();
            }

            return new MarketForecastSnapshotDto(
                    mood,
                    readOptionalText(node, "headlineKo"),
                    readOptionalText(node, "headlineEn"),
                    readOptionalText(node, "summaryKo"),
                    readOptionalText(node, "summaryEn"),
                    keyDrivers,
                    relatedNewsIds,
                    relatedNewsTitles,
                    parseMacroDirections(node.path("macroDirections")),
                    Instant.now().toString(),
                    sourceNews.size()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse market forecast aggregation response", ex);
        }
    }

    private JsonNode loadPromptTemplate() throws IOException {
        try (Reader reader = new InputStreamReader(forecastPromptFile.getInputStream(), StandardCharsets.UTF_8)) {
            return objectMapper.readTree(reader);
        }
    }

    private String buildNewsItemsJson(List<NewsEvent> recentNews) throws JsonProcessingException {
        List<Map<String, Object>> items = new ArrayList<>();
        for (NewsEvent event : recentNews) {
            AnalysisResult analysis = event.analysisResult();
            items.add(Map.of(
                    "id", defaultText(event.id(), ""),
                    "title", defaultText(event.title(), ""),
                    "source", defaultText(event.source(), ""),
                    "publishedAt", event.publishedAt() == null ? "" : event.publishedAt().toString(),
                    "headlineKo", analysis == null ? "" : defaultText(analysis.headlineKo(), ""),
                    "headlineEn", analysis == null ? "" : defaultText(analysis.headlineEn(), ""),
                    "summaryKo", analysis == null ? "" : defaultText(analysis.summaryKo(), ""),
                    "summaryEn", analysis == null ? "" : defaultText(analysis.summaryEn(), ""),
                    "macroImpacts", summarizeMacroImpacts(analysis == null ? List.of() : analysis.macroImpacts()),
                    "marketImpacts", summarizeMarketImpacts(analysis == null ? List.of() : analysis.marketImpacts())
            ));
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(items);
    }

    private String resolveMarketContext() {
        try {
            List<String> lines = new ArrayList<>();
            MarketDataFacade.MarketDataSnapshot marketDataSnapshot = marketDataFacade.getCurrentMarketSnapshot();
            FxSnapshotDto fxSnapshot = marketDataSnapshot.usdKrw().orElse(null);
            GoldSnapshotDto goldSnapshot = marketDataSnapshot.gold().orElse(null);
            OilSnapshotDto oilSnapshot = marketDataSnapshot.oil().orElse(null);
            IndexSnapshotDto kospiSnapshot = marketDataSnapshot.kospi().orElse(null);
            Us10ySnapshotDto us10ySnapshot = marketDataSnapshot.us10y().orElse(null);
            DxySnapshotDto dxySnapshot = marketDataSnapshot.dxy().orElse(null);

            if (fxSnapshot != null) {
                lines.add("- USD/KRW: " + formatDecimal(fxSnapshot.rate()));
            }
            if (goldSnapshot != null) {
                lines.add("- Gold: " + formatDecimal(goldSnapshot.usdPerOunce()));
            }
            if (oilSnapshot != null) {
                if (oilSnapshot.wtiUsd() != null) {
                    lines.add("- WTI: " + formatDecimal(oilSnapshot.wtiUsd()));
                }
                if (oilSnapshot.brentUsd() != null) {
                    lines.add("- Brent: " + formatDecimal(oilSnapshot.brentUsd()));
                }
            }
            if (kospiSnapshot != null && kospiSnapshot.price() != null) {
                lines.add("- KOSPI: " + formatDecimal(kospiSnapshot.price()));
            }
            if (us10ySnapshot != null) {
                lines.add("- US 10Y: " + formatDecimal(us10ySnapshot.yield()) + "% ("
                        + us10ySnapshot.source() + " " + us10ySnapshot.sourceSeries() + ")");
            }
            if (dxySnapshot != null) {
                String sourceLabel = dxySnapshot.synthetic()
                        ? dxySnapshot.source() + " synthetic"
                        : dxySnapshot.source();
                lines.add("- DXY: " + formatDecimal(dxySnapshot.value()) + " (" + sourceLabel
                        + ", " + dxySnapshot.sourceSeries() + ")");
            }

            if (lines.isEmpty()) {
                log.debug("[FORECAST] market context skipped");
                return "";
            }

            StringBuilder builder = new StringBuilder("Current market context:");
            for (String line : lines) {
                builder.append('\n').append(line);
            }
            log.debug("[FORECAST] market context injected lines={}", lines.size());
            return builder.toString();
        } catch (RuntimeException ex) {
            log.debug("[FORECAST] market context skipped", ex);
            return "";
        }
    }

    private String appendMarketContext(String content, String marketContext) {
        if (!StringUtils.hasText(marketContext)) {
            return content;
        }
        return content + "\n\n" + marketContext;
    }

    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String formatDecimal(java.math.BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private List<Map<String, Object>> summarizeMacroImpacts(List<MacroImpact> impacts) {
        if (impacts == null) {
            return List.of();
        }
        return impacts.stream()
                .filter(impact -> impact != null && impact.variable() != null && impact.direction() != null)
                .map(impact -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("variable", impact.variable().name());
                    item.put("direction", impact.direction().name());
                    item.put("confidence", impact.confidence());
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> summarizeMarketImpacts(List<MarketImpact> impacts) {
        if (impacts == null) {
            return List.of();
        }
        return impacts.stream()
                .filter(impact -> impact != null && impact.market() != null && impact.direction() != null)
                .map(impact -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("market", impact.market().name());
                    item.put("direction", impact.direction().name());
                    item.put("confidence", impact.confidence());
                    return item;
                })
                .toList();
    }

    private JsonNode extractJsonNode(String content) throws IOException {
        try {
            return objectMapper.readTree(content);
        } catch (Exception ignored) {
            String sanitized = content.replace("```json", "").replace("```", "").trim();
            try {
                return objectMapper.readTree(sanitized);
            } catch (Exception alsoIgnored) {
                int start = sanitized.indexOf('{');
                int end = sanitized.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    return objectMapper.readTree(sanitized.substring(start, end + 1));
                }
                throw new IOException("No JSON object found in aggregation response");
            }
        }
    }

    private MarketMood parseMood(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_MOOD;
        }
        try {
            return MarketMood.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DEFAULT_MOOD;
        }
    }

    private List<String> readStringArray(JsonNode node, int maxItems) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            String value = child.asText("").trim();
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
            if (values.size() >= maxItems) {
                break;
            }
        }
        return values;
    }

    private List<String> filterKnownNewsIds(List<String> requestedIds, List<NewsEvent> sourceNews) {
        if (requestedIds.isEmpty()) {
            return List.of();
        }
        java.util.Set<String> knownIds = sourceNews.stream()
                .map(NewsEvent::id)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        return requestedIds.stream()
                .filter(knownIds::contains)
                .distinct()
                .toList();
    }

    private Map<MacroVariable, ImpactDirection> parseMacroDirections(JsonNode node) {
        Map<MacroVariable, ImpactDirection> directions = new EnumMap<>(MacroVariable.class);
        if (node == null || !node.isObject()) {
            return directions;
        }
        for (MacroVariable variable : MacroVariable.values()) {
            if (!node.has(variable.name())) {
                continue;
            }
            directions.put(variable, parseDirection(node.path(variable.name()).asText(null)));
        }
        return directions;
    }

    private ImpactDirection parseDirection(String value) {
        if (!StringUtils.hasText(value)) {
            return ImpactDirection.NEUTRAL;
        }
        try {
            return ImpactDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ImpactDirection.NEUTRAL;
        }
    }

    private String readOptionalText(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        String value = node.path(fieldName).asText("").trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private int resolveWindowHours() {
        return windowHours > 0 ? windowHours : 3;
    }

    private int resolveMaxNewsItems() {
        return maxNewsItems > 0 ? maxNewsItems : 20;
    }

    private Duration resolveCacheDuration() {
        return Duration.ofMinutes(cacheMinutes > 0 ? cacheMinutes : 15L);
    }

    private boolean isConfigured() {
        return StringUtils.hasText(openAiApiKey)
                && StringUtils.hasText(openAiUrl)
                && StringUtils.hasText(openAiModel);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record ForecastPreparation(List<NewsEvent> recentNews, String marketContext) {

        private static ForecastPreparation empty() {
            return new ForecastPreparation(List.of(), "");
        }
    }

    private record CachedSnapshot(Optional<MarketForecastSnapshotDto> snapshot, Instant createdAt) {

        boolean isExpired(Duration ttl) {
            return ttl == null || ttl.isZero() || ttl.isNegative()
                    || createdAt.plus(ttl).isBefore(Instant.now());
        }
    }
}

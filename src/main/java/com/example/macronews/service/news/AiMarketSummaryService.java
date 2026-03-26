package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.OpenAiUsageFeatureType;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.forecast.MarketForecastSummaryHandoffDto;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.service.forecast.MarketForecastQueryService;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AiMarketSummaryService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));
    private static final int MAX_KEY_DRIVERS = 5;

    private final RecentMarketSummaryService recentMarketSummaryService;
    private final MarketForecastQueryService marketForecastQueryService;
    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;
    private final OpenAiUsageLoggingService openAiUsageLoggingService;

    private final AtomicReference<CachedSummary> cachedSummary = new AtomicReference<>();

    @Value("${app.featured.market-summary.ai-enabled:true}")
    private boolean aiEnabled;

    @Value("${app.featured.market-summary.ai-model:gpt-4o-mini}")
    private String aiModel;

    @Value("${app.featured.market-summary.ai-window-hours:3}")
    private int aiWindowHours;

    @Value("${app.featured.market-summary.ai-max-items:10}")
    private int aiMaxItems;

    @Value("${app.featured.market-summary.ai-min-items:3}")
    private int aiMinItems;

    @Value("${app.featured.market-summary.ai-max-input-chars:12000}")
    private int aiMaxInputChars;

    @Value("${app.featured.market-summary.ai-cache-minutes:15}")
    private int aiCacheMinutes;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.api-url:}")
    private String openAiUrl;

    @Value("${openai.max-tokens:800}")
    private int openAiMaxTokens;

    @Value("${openai.temperature:0.2}")
    private double openAiTemperature;

    @Value("${openai.featured-market-summary-prompt-file:classpath:ai/prompts/featured_market_summary_prompt.json}")
    private Resource promptFile;

    private Clock clock = DEFAULT_CLOCK;

    public Optional<FeaturedMarketSummaryDto> getCurrentSummary() {
        if (!aiEnabled) {
            return Optional.empty();
        }
        if (!isConfigured()) {
            log.debug("[MARKET_SUMMARY] skipped reason=openai-not-configured");
            return Optional.empty();
        }

        CachedSummary current = cachedSummary.get();
        if (current != null && !current.isExpired(resolveCacheDuration(), clock)) {
            return current.summary();
        }

        Optional<FeaturedMarketSummaryDto> generated = generateCurrentSummary();
        cachedSummary.set(new CachedSummary(generated, Instant.now(clock)));
        return generated;
    }

    public Optional<FeaturedMarketSummaryDto> generateCurrentSummary() {
        List<NewsEvent> recentItems = recentMarketSummaryService.loadRecentAnalyzedNews(
                resolveWindowHours(), resolveMaxItems());
        if (recentItems.size() < resolveMinItems()) {
            log.info("[MARKET_SUMMARY] skipped reason=insufficient-analyzed-news queryBasis=analysisResult.createdAt|ingestedAt size={} minItems={}",
                    recentItems.size(), resolveMinItems());
            return Optional.empty();
        }

        try {
            Optional<MarketForecastSummaryHandoffDto> forecastHandoff = marketForecastQueryService.getCurrentSummaryHandoff();
            log.debug("[MARKET_SUMMARY] secondary-forecast-handoff available={}", forecastHandoff.isPresent());
            String payload = buildPayload(recentItems, forecastHandoff.orElse(null));
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
                log.warn("[MARKET_SUMMARY] synthesis failed status={}", apiResult == null ? -1 : apiResult.statusCode());
                return Optional.empty();
            }

            openAiUsageLoggingService.recordUsage(
                    OpenAiUsageFeatureType.MARKET_SUMMARY,
                    resolveModel(),
                    apiResult.body());

            return Optional.of(parseSummary(apiResult.body(), recentItems));
        } catch (Exception ex) {
            log.warn("[MARKET_SUMMARY] synthesis failed", ex);
            return Optional.empty();
        }
    }

    String buildPayload(List<NewsEvent> recentItems, MarketForecastSummaryHandoffDto forecastHandoff) {
        try {
            JsonNode promptRoot = loadPromptTemplate();
            JsonNode promptMessages = promptRoot.path("messages");
            if (!promptMessages.isArray()) {
                throw new IllegalStateException("Featured market summary prompt file must contain messages array");
            }

            String newsItemsJson = buildNewsItemsJson(recentItems);
            String forecastSummaryHandoffJson = buildForecastSummaryHandoffJson(forecastHandoff);
            List<Map<String, Object>> messages = new ArrayList<>();
            for (JsonNode node : promptMessages) {
                String role = node.path("role").asText("");
                if (!StringUtils.hasText(role)) {
                    continue;
                }
                String content = node.has("content")
                        ? node.path("content").asText("")
                        : node.path("template").asText("")
                                .replace("{{newsItemsJson}}", newsItemsJson)
                                .replace("{{forecastSummaryHandoffJson}}", forecastSummaryHandoffJson);
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                messages.add(Map.of("role", role, "content", content));
            }

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("model", resolveModel());
            root.put("messages", messages);
            root.put("max_tokens", openAiMaxTokens);
            root.put("temperature", openAiTemperature);
            root.put("response_format", Map.of("type", "json_object"));
            return objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build featured market summary payload", ex);
        }
    }

    private String buildForecastSummaryHandoffJson(MarketForecastSummaryHandoffDto forecastHandoff) throws JsonProcessingException {
        if (forecastHandoff == null) {
            return "{}";
        }

        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("mood", forecastHandoff.mood() == null ? "" : forecastHandoff.mood().name());
        handoff.put("macroDirections", forecastHandoff.macroDirections() == null ? Map.of() : forecastHandoff.macroDirections());
        handoff.put("keyDrivers", forecastHandoff.keyDrivers() == null ? List.of() : forecastHandoff.keyDrivers());
        handoff.put("relatedNewsIds", forecastHandoff.relatedNewsIds() == null ? List.of() : forecastHandoff.relatedNewsIds());
        handoff.put("analyzedNewsCount", forecastHandoff.analyzedNewsCount());
        handoff.put("generatedAt", defaultText(forecastHandoff.generatedAt()));
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(handoff);
    }

    FeaturedMarketSummaryDto parseSummary(String responseBody, List<NewsEvent> sourceNews) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("Market summary response content was empty");
            }

            JsonNode node = extractJsonNode(content);
            String headlineEn = readRequiredText(node, "headlineEn");
            String summaryEn = readRequiredText(node, "summaryEn");
            SignalSentiment dominantSentiment = parseSentiment(node.path("dominantSentiment").asText(null));

            List<String> keyDrivers = readStringArray(node.path("keyDrivers"), MAX_KEY_DRIVERS);
            List<String> supportingNewsIds = filterKnownNewsIds(
                    readStringArray(node.path("supportingNewsIds"), resolveMaxItems()),
                    sourceNews
            );
            if (supportingNewsIds.isEmpty()) {
                supportingNewsIds = sourceNews.stream()
                        .map(NewsEvent::id)
                        .filter(StringUtils::hasText)
                        .limit(resolveMaxItems())
                        .toList();
            }

            Instant generatedAt = Instant.now(clock);
            Instant toPublishedAt = sourceNews.get(0).publishedAt();
            Instant fromPublishedAt = sourceNews.get(sourceNews.size() - 1).publishedAt();

            return new FeaturedMarketSummaryDto(
                    readOptionalText(node, "headlineKo"),
                    headlineEn,
                    readOptionalText(node, "summaryKo"),
                    summaryEn,
                    generatedAt,
                    sourceNews.size(),
                    resolveWindowHours(),
                    fromPublishedAt,
                    toPublishedAt,
                    dominantSentiment,
                    keyDrivers,
                    supportingNewsIds,
                    readOptionalText(node, "marketViewKo"),
                    readOptionalText(node, "marketViewEn"),
                    parseConfidence(node.path("confidence")),
                    true,
                    null
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse featured market summary response", ex);
        }
    }

    private JsonNode loadPromptTemplate() throws IOException {
        try (Reader reader = new InputStreamReader(promptFile.getInputStream(), StandardCharsets.UTF_8)) {
            return objectMapper.readTree(reader);
        }
    }

    private String buildNewsItemsJson(List<NewsEvent> recentItems) throws JsonProcessingException {
        List<Map<String, Object>> items = new ArrayList<>();
        int maxInputChars = resolveMaxInputChars();
        int currentLength = 2;

        for (NewsEvent event : recentItems) {
            Map<String, Object> item = summarizeNewsItem(event);
            String itemJson = objectMapper.writeValueAsString(item);
            int additionalLength = items.isEmpty() ? itemJson.length() : itemJson.length() + 1;
            if (!items.isEmpty() && currentLength + additionalLength > maxInputChars) {
                break;
            }
            if (items.isEmpty() && currentLength + additionalLength > maxInputChars) {
                item = truncateItem(item, Math.max(1200, maxInputChars - currentLength - 16));
                itemJson = objectMapper.writeValueAsString(item);
                if (currentLength + itemJson.length() > maxInputChars) {
                    break;
                }
            }
            items.add(item);
            currentLength += additionalLength;
        }

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(items);
    }

    private Map<String, Object> summarizeNewsItem(NewsEvent event) {
        AnalysisResult analysis = event.analysisResult();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", defaultText(event.id()));
        item.put("publishedAt", event.publishedAt() == null ? "" : event.publishedAt().toString());
        item.put("source", defaultText(event.source()));
        item.put("title", truncate(defaultText(event.title()), 180));
        item.put("articleSummary", truncate(defaultText(event.summary()), 220));
        item.put("headlineKo", analysis == null ? "" : truncate(defaultText(analysis.headlineKo()), 160));
        item.put("headlineEn", analysis == null ? "" : truncate(defaultText(analysis.headlineEn()), 160));
        item.put("summaryKo", analysis == null ? "" : truncate(defaultText(analysis.summaryKo()), 260));
        item.put("summaryEn", analysis == null ? "" : truncate(defaultText(analysis.summaryEn()), 260));
        item.put("primaryDirection", resolvePrimaryDirection(analysis).name());
        item.put("primarySentiment", resolvePrimarySentiment(analysis).name());
        item.put("macroImpacts", summarizeMacroImpacts(analysis == null ? List.of() : analysis.macroImpacts()));
        item.put("marketImpacts", summarizeMarketImpacts(analysis == null ? List.of() : analysis.marketImpacts()));
        return item;
    }

    private Map<String, Object> truncateItem(Map<String, Object> item, int itemCharBudget) {
        Map<String, Object> truncated = new LinkedHashMap<>(item);
        int halfBudget = Math.max(120, itemCharBudget / 4);
        truncated.put("articleSummary", truncate((String) item.getOrDefault("articleSummary", ""), halfBudget));
        truncated.put("summaryKo", truncate((String) item.getOrDefault("summaryKo", ""), halfBudget));
        truncated.put("summaryEn", truncate((String) item.getOrDefault("summaryEn", ""), halfBudget));
        return truncated;
    }

    private List<Map<String, Object>> summarizeMacroImpacts(List<MacroImpact> impacts) {
        if (impacts == null) {
            return List.of();
        }
        return impacts.stream()
                .filter(impact -> impact != null && impact.variable() != null && impact.direction() != null)
                .limit(4)
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
                .limit(4)
                .map(impact -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("market", impact.market().name());
                    item.put("direction", impact.direction().name());
                    item.put("confidence", impact.confidence());
                    return item;
                })
                .toList();
    }

    private ImpactDirection resolvePrimaryDirection(AnalysisResult analysis) {
        if (analysis == null) {
            return ImpactDirection.NEUTRAL;
        }
        if (analysis.macroImpacts() != null) {
            for (MacroImpact impact : analysis.macroImpacts()) {
                if (impact != null && impact.direction() != null) {
                    return impact.direction();
                }
            }
        }
        if (analysis.marketImpacts() != null) {
            for (MarketImpact impact : analysis.marketImpacts()) {
                if (impact != null && impact.direction() != null) {
                    return impact.direction();
                }
            }
        }
        return ImpactDirection.NEUTRAL;
    }

    private SignalSentiment resolvePrimarySentiment(AnalysisResult analysis) {
        if (analysis == null) {
            return SignalSentiment.NEUTRAL;
        }
        if (analysis.macroImpacts() != null) {
            for (MacroImpact impact : analysis.macroImpacts()) {
                if (impact != null && impact.variable() != null && impact.direction() != null) {
                    return impact.variable().sentimentFor(impact.direction());
                }
            }
        }
        if (analysis.marketImpacts() != null) {
            for (MarketImpact impact : analysis.marketImpacts()) {
                if (impact != null && impact.market() != null && impact.direction() != null) {
                    return impact.market().sentimentFor(impact.direction());
                }
            }
        }
        return SignalSentiment.NEUTRAL;
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
                throw new IOException("No JSON object found in market summary response");
            }
        }
    }

    private SignalSentiment parseSentiment(String value) {
        if (!StringUtils.hasText(value)) {
            return SignalSentiment.NEUTRAL;
        }
        try {
            return SignalSentiment.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SignalSentiment.NEUTRAL;
        }
    }

    private String readRequiredText(JsonNode node, String fieldName) {
        String value = readOptionalText(node, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Required field was missing: " + fieldName);
        }
        return value;
    }

    private String readOptionalText(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        String value = node.path(fieldName).asText("").trim();
        return StringUtils.hasText(value) ? value : null;
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

    private Double parseConfidence(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            double value = node.isNumber() ? node.asDouble() : Double.parseDouble(node.asText(""));
            if (value < 0d) {
                return 0d;
            }
            if (value > 1d) {
                return 1d;
            }
            return value;
        } catch (Exception ex) {
            return null;
        }
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return defaultText(value);
        }
        return value.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private boolean isConfigured() {
        return StringUtils.hasText(openAiApiKey)
                && StringUtils.hasText(openAiUrl)
                && StringUtils.hasText(resolveModel());
    }

    public String getConfiguredModel() {
        return resolveModel();
    }

    private String resolveModel() {
        return StringUtils.hasText(aiModel) ? aiModel : "";
    }

    private int resolveWindowHours() {
        return aiWindowHours > 0 ? aiWindowHours : 3;
    }

    private int resolveMaxItems() {
        return aiMaxItems > 0 ? aiMaxItems : 10;
    }

    private int resolveMinItems() {
        return Math.max(1, aiMinItems);
    }

    private int resolveMaxInputChars() {
        return aiMaxInputChars > 0 ? aiMaxInputChars : 12000;
    }

    private Duration resolveCacheDuration() {
        return Duration.ofMinutes(aiCacheMinutes > 0 ? aiCacheMinutes : 15L);
    }

    private record CachedSummary(Optional<FeaturedMarketSummaryDto> summary, Instant createdAt) {

        private boolean isExpired(Duration ttl, Clock clock) {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                return true;
            }
            return createdAt.plus(ttl).isBefore(Instant.now(clock));
        }
    }
}

package com.example.macronews.service.news.query;

import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Disabled-by-default provider that derives Korea-market issue seeds from an OpenAI web-search call.
 *
 * <p>It is intended as the SECOND-priority dynamic seed source, used only when GDELT cannot supply a
 * REMOTE/CACHED_REMOTE hot-issue signal. In this slice it is NOT wired into the Naver ingestion path;
 * it only exposes {@link #resolveMarketIssueSeeds()} and is exercised purely through mocked HTTP.
 *
 * <p>Design guarantees:
 * <ul>
 *   <li>{@code enabled=false} (the default) or a missing API key returns immediately with no call;
 *   <li>a successful result is cached for {@code success-ttl} and served without another call;
 *   <li>a failure arms a {@code failure-cooldown} window during which no call is made;
 *   <li>a per-day call budget caps the number of live calls;
 *   <li>seeds without evidence, below the confidence threshold, or carrying OR-syntax / advice-like
 *       queries are dropped; queries are normalized, de-duplicated and capped;
 *   <li>any failure degrades quietly so a caller can fall back without an exception.
 * </ul>
 *
 * <p>The payload targets the OpenAI Responses API with the {@code web_search} tool. The exact request
 * and response schema must be re-verified against current OpenAI docs before enabling in production;
 * here it is only validated against fixtures. No raw response body, source URL or evidence text is
 * logged — only counts and short reason labels.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiMarketIssueSeedProvider {

    private static final int MIN_QUERY_TOKENS = 1;
    private static final int MAX_QUERY_TOKENS = 3;
    private static final int DEFAULT_MAX_QUERIES = 12;
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.5;
    private static final Duration DEFAULT_SUCCESS_TTL = Duration.ofMinutes(45);
    private static final Duration DEFAULT_FAILURE_COOLDOWN = Duration.ofMinutes(45);

    // Standalone boolean OR token (any case); such queries must never be sent to Naver.
    private static final Pattern OR_TOKEN = Pattern.compile("(?i)\\bor\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // Advice / price-target / return-prediction markers. A query containing any of these is a trading
    // signal, not a news search term, and is dropped — seeds are search terms, not investment advice.
    private static final List<String> ADVICE_MARKERS = List.of(
            "매수", "매도", "목표가", "수익률", "사라", "팔아", "추천주", "급등주", "상한가", "익절", "손절");

    // Single-line Korean instruction sent as the Responses API `input`. Intentionally inlined (no
    // prompt resource file in this slice). Purpose: generate short Korean Naver SEARCH QUERIES for the
    // latest Korea-market issues, each backed by web-search evidence; never investment advice.
    private static final String SEED_INSTRUCTION = String.join(" ",
            "너는 한국 증시 뉴스 검색어(seed) 생성기다.",
            "웹 검색으로 한국 증시/환율/금리/반도체/2차전지/방산/조선/전력기기 관련 최신 이슈를 조사하라.",
            "각 이슈는 반드시 evidenceTitles와 sourceUrls(출처 URL)를 포함해야 하며, 출처 없는 이슈는 제외한다.",
            "매수/매도/목표가/수익률 예측 등 투자 조언은 절대 생성하지 마라.",
            "naverQueries는 1~3어절의 짧은 한국어 검색어이며 OR 문법을 쓰지 않는다.",
            "결과는 다음 JSON만 출력한다:",
            "{\"seeds\":[{\"topicFamily\":\"\",\"issue\":\"\",\"naverQueries\":[\"\"],",
            "\"confidence\":0.0,\"evidenceTitles\":[\"\"],\"sourceUrls\":[\"\"]}]}");

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;

    private final AtomicReference<SeedSnapshotState> state =
            new AtomicReference<>(SeedSnapshotState.empty());

    // Injectable for deterministic TTL/cooldown/daily-budget tests; defaults to the system UTC clock.
    private Clock clock = Clock.systemUTC();

    @Value("${app.news.openai-seed.enabled:false}")
    private boolean enabled;

    // Reuses the existing OpenAI secret; never logged.
    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${app.news.openai-seed.responses-url:https://api.openai.com/v1/responses}")
    private String responsesUrl;

    @Value("${app.news.openai-seed.model:gpt-5.5}")
    private String model;

    @Value("${app.news.openai-seed.max-queries:12}")
    private int maxQueries;

    @Value("${app.news.openai-seed.confidence-threshold:0.5}")
    private double confidenceThreshold;

    // Durations bound as raw strings and parsed with DurationStyle (mirroring the GDELT provider) so a
    // hand-built ApplicationContext without Spring Boot's String->Duration converter still binds them.
    @Value("${app.news.openai-seed.success-ttl:45m}")
    private String successTtl = "45m";

    @Value("${app.news.openai-seed.failure-cooldown:45m}")
    private String failureCooldown = "45m";

    @Value("${app.news.openai-seed.daily-call-limit:24}")
    private int dailyCallLimit;

    /**
     * Resolves Korea-market issue seeds, preferring a cached snapshot and never throwing. The returned
     * result is dynamic only for {@link MarketIssueSeedOrigin#OPENAI_WEB_SEARCH}/
     * {@link MarketIssueSeedOrigin#OPENAI_CACHED}.
     */
    public MarketIssueSeedResult resolveMarketIssueSeeds() {
        Instant now = clock.instant();

        if (!enabled) {
            return logged(MarketIssueSeedResult.disabled("disabled", now), now);
        }
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(responsesUrl)) {
            return logged(MarketIssueSeedResult.disabled("not-configured", now), now);
        }

        SeedSnapshotState current = state.get();
        if (current.successValid(now)) {
            return logged(MarketIssueSeedResult.cached(current.cachedQueries(), current.cachedSeeds(),
                    current.cachedAt()), now);
        }
        if (current.cooldownActive(now)) {
            return logged(current.hasCachedSuccess()
                    ? MarketIssueSeedResult.cached(current.cachedQueries(), current.cachedSeeds(), current.cachedAt())
                    : MarketIssueSeedResult.cooldown("failure-cooldown", now), now);
        }

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (resolvedDailyLimit() > 0 && current.usedToday(today) >= resolvedDailyLimit()) {
            return logged(current.hasCachedSuccess()
                    ? MarketIssueSeedResult.cached(current.cachedQueries(), current.cachedSeeds(), current.cachedAt())
                    : MarketIssueSeedResult.cooldown("daily-limit", now), now);
        }

        // Count the call before issuing it so a thrown/failed call still consumes the daily budget.
        state.updateAndGet(prev -> prev.withCallOn(today));

        ExternalApiResult apiResult;
        try {
            apiResult = externalApiUtils.callAPI(new ExternalApiRequest(
                    HttpMethod.POST, buildHeaders(), responsesUrl, buildPayload()));
        } catch (Exception ex) {
            log.warn("[OPENAI-SEED] web-search call failed exceptionally");
            state.updateAndGet(prev -> prev.withCooldown(now.plus(resolvedFailureCooldown())));
            return logged(MarketIssueSeedResult.failed("call-exception", now), now);
        }

        int status = apiResult == null ? -1 : apiResult.statusCode();
        if (apiResult == null || status < 200 || status >= 300) {
            state.updateAndGet(prev -> prev.withCooldown(now.plus(resolvedFailureCooldown())));
            return logged(MarketIssueSeedResult.failed("upstream-status-" + status, now), now);
        }

        List<MarketIssueSeed> seeds = parseSeeds(apiResult.body());
        List<String> naverQueries = flattenQueries(seeds);
        if (seeds.isEmpty() || naverQueries.isEmpty()) {
            state.updateAndGet(prev -> prev.withCooldown(now.plus(resolvedFailureCooldown())));
            return logged(MarketIssueSeedResult.failed("no-usable-seeds", now), now);
        }

        MarketIssueSeedResult success = MarketIssueSeedResult.webSearch(naverQueries, seeds, now);
        state.updateAndGet(prev -> prev.withSuccess(naverQueries, seeds, now, now.plus(resolvedSuccessTtl())));
        return logged(success, now);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private String buildPayload() {
        try {
            Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("model", model);
            root.put("tools", List.of(Map.of("type", "web_search")));
            root.put("input", SEED_INSTRUCTION);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            // Should not happen for a plain map; fall back to a minimal valid body.
            return "{\"model\":\"" + model + "\",\"tools\":[{\"type\":\"web_search\"}],\"input\":\"\"}";
        }
    }

    private List<MarketIssueSeed> parseSeeds(String body) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = extractContentText(root);
            if (!StringUtils.hasText(content)) {
                return List.of();
            }
            JsonNode payload = extractJsonObject(content);
            JsonNode seedsNode = payload.path("seeds");
            if (!seedsNode.isArray()) {
                return List.of();
            }
            List<MarketIssueSeed> seeds = new ArrayList<>();
            for (JsonNode seedNode : seedsNode) {
                MarketIssueSeed seed = toValidSeed(seedNode);
                if (seed != null) {
                    seeds.add(seed);
                }
            }
            return seeds;
        } catch (Exception ex) {
            log.warn("[OPENAI-SEED] failed to parse web-search response; degrading");
            return List.of();
        }
    }

    // Supports both Responses API shapes (top-level `output_text`, and `output[].content[].text`) plus
    // a Chat-Completions-like `choices[0].message.content` as a defensive fallback.
    private String extractContentText(JsonNode root) {
        String outputText = root.path("output_text").asText("");
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }
        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode block : output) {
                JsonNode contentArray = block.path("content");
                if (contentArray.isArray()) {
                    for (JsonNode part : contentArray) {
                        String text = part.path("text").asText("");
                        if (StringUtils.hasText(text)) {
                            builder.append(text);
                        }
                    }
                }
            }
            if (builder.length() > 0) {
                return builder.toString();
            }
        }
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private JsonNode extractJsonObject(String content) throws Exception {
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
                throw new IllegalStateException("No JSON object found in seed response");
            }
        }
    }

    private MarketIssueSeed toValidSeed(JsonNode seedNode) {
        String topicFamily = seedNode.path("topicFamily").asText("").trim();
        String issue = seedNode.path("issue").asText("").trim();
        if (!StringUtils.hasText(topicFamily) || !StringUtils.hasText(issue)) {
            return null;
        }
        double confidence = seedNode.path("confidence").asDouble(0.0);
        if (confidence < resolvedConfidenceThreshold()) {
            return null;
        }
        List<String> evidenceTitles = readStringList(seedNode.path("evidenceTitles"));
        List<String> sourceUrls = readStringList(seedNode.path("sourceUrls"));
        if (evidenceTitles.isEmpty() || sourceUrls.isEmpty()) {
            return null;
        }
        List<String> queries = readAcceptableQueries(seedNode.path("naverQueries"));
        if (queries.isEmpty()) {
            return null;
        }
        return new MarketIssueSeed(topicFamily, issue, queries, confidence, evidenceTitles, sourceUrls);
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            String value = child.asText("").trim();
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> readAcceptableQueries(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        for (JsonNode child : node) {
            String normalized = normalizeQuery(child.asText(""));
            if (normalized != null) {
                queries.add(normalized);
            }
        }
        return List.copyOf(queries);
    }

    private String normalizeQuery(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = WHITESPACE.matcher(raw).replaceAll(" ").trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (OR_TOKEN.matcher(trimmed).find()) {
            return null;
        }
        int tokenCount = trimmed.split(" ").length;
        if (tokenCount < MIN_QUERY_TOKENS || tokenCount > MAX_QUERY_TOKENS) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String marker : ADVICE_MARKERS) {
            if (lower.contains(marker)) {
                return null;
            }
        }
        return trimmed;
    }

    private List<String> flattenQueries(List<MarketIssueSeed> seeds) {
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (MarketIssueSeed seed : seeds) {
            for (String query : seed.naverQueries()) {
                deduped.add(query);
                if (deduped.size() >= resolvedMaxQueries()) {
                    return List.copyOf(deduped);
                }
            }
        }
        return List.copyOf(deduped);
    }

    private MarketIssueSeedResult logged(MarketIssueSeedResult result, Instant now) {
        long ageSeconds = Math.max(0, Duration.between(result.generatedAt(), now).getSeconds());
        log.info("[OPENAI-SEED] market issue seeds origin={} dynamic={} enabled={} reason={} seeds={} naverQueries={} evidenceCount={} seedAgeSeconds={}",
                result.origin(), result.isDynamic(), enabled, result.reason(), result.seeds().size(),
                result.naverQueries().size(), result.evidenceCount(), ageSeconds);
        return result;
    }

    private int resolvedMaxQueries() {
        return maxQueries > 0 ? maxQueries : DEFAULT_MAX_QUERIES;
    }

    private int resolvedDailyLimit() {
        return Math.max(0, dailyCallLimit);
    }

    private double resolvedConfidenceThreshold() {
        return confidenceThreshold >= 0 ? confidenceThreshold : DEFAULT_CONFIDENCE_THRESHOLD;
    }

    private Duration resolvedSuccessTtl() {
        return resolveDuration(successTtl, DEFAULT_SUCCESS_TTL);
    }

    private Duration resolvedFailureCooldown() {
        return resolveDuration(failureCooldown, DEFAULT_FAILURE_COOLDOWN);
    }

    private Duration resolveDuration(String value, Duration fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            Duration parsed = DurationStyle.detectAndParse(value);
            return parsed == null || parsed.isNegative() || parsed.isZero() ? fallback : parsed;
        } catch (Exception ex) {
            return fallback;
        }
    }

    /**
     * Immutable cache/cooldown/daily-budget snapshot swapped atomically. {@code cachedQueries}/
     * {@code cachedSeeds}/{@code cachedAt}/{@code successExpiresAt} hold the last successful snapshot;
     * {@code cooldownUntil} an armed failure window; {@code callDay}/{@code callsToday} the per-day
     * call budget.
     */
    private record SeedSnapshotState(
            List<String> cachedQueries,
            List<MarketIssueSeed> cachedSeeds,
            Instant cachedAt,
            Instant successExpiresAt,
            Instant cooldownUntil,
            LocalDate callDay,
            int callsToday) {

        static SeedSnapshotState empty() {
            return new SeedSnapshotState(null, null, null, null, null, null, 0);
        }

        boolean hasCachedSuccess() {
            return cachedQueries != null && !cachedQueries.isEmpty();
        }

        boolean successValid(Instant now) {
            return hasCachedSuccess() && successExpiresAt != null && now.isBefore(successExpiresAt);
        }

        boolean cooldownActive(Instant now) {
            return cooldownUntil != null && now.isBefore(cooldownUntil);
        }

        int usedToday(LocalDate today) {
            return today.equals(callDay) ? callsToday : 0;
        }

        SeedSnapshotState withCallOn(LocalDate today) {
            int used = usedToday(today);
            return new SeedSnapshotState(cachedQueries, cachedSeeds, cachedAt, successExpiresAt,
                    cooldownUntil, today, used + 1);
        }

        SeedSnapshotState withCooldown(Instant cooldownUntil) {
            return new SeedSnapshotState(cachedQueries, cachedSeeds, cachedAt, successExpiresAt,
                    cooldownUntil, callDay, callsToday);
        }

        SeedSnapshotState withSuccess(List<String> queries, List<MarketIssueSeed> seeds, Instant fetchedAt,
                Instant successExpiresAt) {
            // A fresh success clears any prior cooldown; the success TTL now guards re-entry.
            return new SeedSnapshotState(List.copyOf(queries), List.copyOf(seeds), fetchedAt,
                    successExpiresAt, null, callDay, callsToday);
        }
    }
}

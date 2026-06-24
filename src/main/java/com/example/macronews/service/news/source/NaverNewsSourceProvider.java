package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.ExternalApiRequest;
import com.example.macronews.service.news.query.DeterministicQueryGenerator;
import com.example.macronews.service.news.query.GdeltHotIssueSeedProvider;
import com.example.macronews.service.news.query.HotIssueSeedOrigin;
import com.example.macronews.service.news.query.HotIssueSeedResult;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.example.macronews.util.external.ExternalResponseTextNormalizer;
import com.example.macronews.util.external.ExternalResponseValueParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
@Slf4j
public class NaverNewsSourceProvider implements NewsSourceProvider {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));
    private static final int NAVER_MAX_DISPLAY = 100;
    private static final int NAVER_MAX_START = 1000;
    private static final int STALE_LOG_SAMPLE_LIMIT = 3;
    private static final int STALE_LOG_TITLE_MAX_LENGTH = 80;
    // When the FRESH pass returns nothing because every candidate was stale, run a bounded recovery
    // pass that re-queries the leading (highest-intent) queries within the SAME freshness bucket as
    // the caller. It refines query intent without widening the freshness window, so the FRESH path
    // never returns SEMI_FRESH-only or stale items, and it stays cheap by never fanning out across the
    // full configured query list.
    private static final int SECOND_PASS_MAX_QUERIES = 3;
    private static final DateTimeFormatter NAVER_PUB_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final List<DateTimeFormatter> NAVER_PUB_DATE_FALLBACK_FORMATTERS = List.of(
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("EEE, dd MMM yyyy HH:mm:ss Z")
                    .toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("EEE, dd MMM yyyy HH:mm:ss z")
                    .toFormatter(Locale.ENGLISH)
    );
    private static final String KOREAN_BREAKING_MARKER = "\uC18D\uBCF4";
    private static final List<String> RELEVANCE_KEYWORDS = List.of(
            "fomc",
            "cpi",
            "ppi",
            "inflation",
            "rate",
            "fed",
            "powell",
            "yield",
            "oil",
            "wti",
            "brent",
            "dollar",
            "\uAE08\uB9AC",
            "\uBB3C\uAC00",
            "\uC778\uD50C\uB808\uC774\uC158",
            "\uC5F0\uC900",
            "\uD30C\uC6D4",
            "\uD658\uC728",
            "\uC720\uAC00",
            "\uB2EC\uB7EC",
            "\uACE0\uC6A9",
            "kospi",
            "kosdaq",
            "\uC99D\uC2DC",
            "\uCF54\uC2A4\uD53C",
            "\uCF54\uC2A4\uB2E5"
    );
    // Short ASCII tokens (rate, oil, fed, ppi, ...) cause false positives when matched as raw
    // substrings inside unrelated words, so they require an ASCII word-boundary match. Korean
    // keywords keep substring matching because Java \b boundaries do not behave for Hangul.
    private static final List<Pattern> ASCII_RELEVANCE_PATTERNS;
    private static final List<String> NON_ASCII_RELEVANCE_KEYWORDS;

    static {
        List<Pattern> asciiPatterns = new ArrayList<>();
        List<String> nonAsciiKeywords = new ArrayList<>();
        for (String keyword : RELEVANCE_KEYWORDS) {
            if (isAsciiKeyword(keyword)) {
                asciiPatterns.add(Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE));
            } else {
                nonAsciiKeywords.add(keyword.toLowerCase(Locale.ROOT));
            }
        }
        ASCII_RELEVANCE_PATTERNS = List.copyOf(asciiPatterns);
        NON_ASCII_RELEVANCE_KEYWORDS = List.copyOf(nonAsciiKeywords);
    }

    // Default queries are intentionally aligned with RELEVANCE_KEYWORDS so the safe-default path
    // still produces macro-relevant items when app.news.naver.queries is unset or blank.
    private static final List<String> DEFAULT_QUERIES = List.of(
            "\uCF54\uC2A4\uD53C \uC9C0\uC218",
            "\uCF54\uC2A4\uD53C \uB9C8\uAC10",
            "\uCF54\uC2A4\uB2E5 \uC9C0\uC218",
            "\uCF54\uC2A4\uB2E5 \uB9C8\uAC10",
            "\uC6D0\uB2EC\uB7EC \uD658\uC728",
            "\uD55C\uAD6D\uC740\uD589 \uAE30\uC900\uAE08\uB9AC",
            "\uBBF8\uAD6D \uC5F0\uC900 \uAE08\uB9AC",
            "FOMC \uD68C\uC758 \uACB0\uACFC",
            "\uD30C\uC6D4 \uC758\uC7A5 \uBC1C\uC5B8",
            "\uBBF8\uAD6D CPI \uBB3C\uAC00",
            "\uBBF8\uAD6D PPI \uBB3C\uAC00",
            "\uBBF8\uAD6D \uACE0\uC6A9\uC9C0\uD45C \uBC1C\uD45C",
            "\uAD6D\uC81C\uC720\uAC00 WTI",
            "\uBE0C\uB80C\uD2B8\uC720 \uAC00\uACA9",
            "\uBC18\uB3C4\uCCB4 \uC8FC\uAC00",
            "\uB274\uC695\uC99D\uC2DC \uB9C8\uAC10",
            "\uB2EC\uB7EC\uC778\uB371\uC2A4 \uD658\uC728",
            "\uC778\uD50C\uB808\uC774\uC158 \uAE08\uB9AC"
    );

    // Curated fallback query pack used ONLY when the GDELT seed signal is NOT a genuine dynamic
    // hot-issue feed (origin RATE_LIMIT_COOLDOWN / UPSTREAM_FAILURE_COOLDOWN / FALLBACK /
    // NOT_CONFIGURED). The built-in DEFAULT_QUERIES above repeatedly produced stale/lifestyle-noise
    // (\uCF54\uC2A4\uD53C \uC9C0\uC218/\uCF54\uC2A4\uB2E5 \uC9C0\uC218/\uCF54\uC2A4\uD53C \uB9C8\uAC10/...) or rawItems=0, so this pack is intentionally weighted
    // toward Korea-market reaction terms and major domestic tickers/sectors. Market-reaction queries
    // (\uCF54\uC2A4\uD53C \uC0C1\uC2B9/\uD558\uB77D, \uC6D0\uB2EC\uB7EC, \uD658\uC728 \uC0C1\uC2B9/\uD558\uB77D, \uB274\uC695\uC99D\uC2DC, \uB098\uC2A4\uB2E5) lead because they most reliably map to
    // fresh, macro-relevant Naver articles; bare tickers follow. Each entry is 1-3 tokens, no OR
    // syntax, and the count stays inside the 18-24 budget.
    private static final List<String> CURATED_FALLBACK_QUERIES = List.of(
            "\uC0BC\uC131\uC804\uC790",
            "SK\uD558\uC774\uB2C9\uC2A4",
            "\uCF54\uC2A4\uD53C \uC0C1\uC2B9",
            "\uCF54\uC2A4\uD53C \uD558\uB77D",
            "\uC6D0\uB2EC\uB7EC",
            "\uD658\uC728 \uC0C1\uC2B9",
            "\uD658\uC728 \uD558\uB77D",
            "\uB274\uC695\uC99D\uC2DC",
            "\uB098\uC2A4\uB2E5",
            "\uBC18\uB3C4\uCCB4",
            "2\uCC28\uC804\uC9C0",
            "\uB450\uC0B0\uC5D0\uB108\uBE4C\uB9AC\uD2F0",
            "\uD55C\uBBF8\uBC18\uB3C4\uCCB4",
            "\uD604\uB300\uCC28",
            "\uAE30\uC544",
            "LG\uC5D0\uB108\uC9C0\uC194\uB8E8\uC158",
            "NAVER",
            "\uCE74\uCE74\uC624",
            "\uCF54\uC2A4\uB2E5 \uC0C1\uC2B9",
            "\uCF54\uC2A4\uB2E5 \uD558\uB77D",
            "\uAD6D\uC81C\uC720\uAC00",
            "\uBC29\uC0B0"
    );

    // app.news.naver.queries may arrive comma, semicolon, or newline separated (env vs. yaml list
    // flattening), so accept all three delimiters. Caps guard against runaway/abusive bindings.
    private static final Pattern QUERY_DELIMITER = Pattern.compile("[,;\\r\\n]+");
    private static final int MAX_CONFIGURED_QUERIES = 50;
    private static final int MAX_QUERY_LENGTH = 100;
    // Bounds for the optional dynamic (GDELT-seed -> deterministic-generator) query source. They cap
    // how many hot-issue seeds are requested and how many generated queries are merged ahead of the
    // static safe defaults, so the dynamic path can never fan out unbounded upstream calls.
    private static final int DYNAMIC_SEED_LIMIT = 10;
    private static final int DYNAMIC_QUERY_LIMIT = 10;
    // Caps the merged (generated-ahead-of-default) query list so the dynamic path stays within a sane
    // per-cycle Naver query budget. Generated queries lead and the static defaults fill the remainder,
    // so the highest-intent hot-issue queries are always retained while the long default tail is
    // trimmed. Kept below the generated+default sum (10 + 18) to avoid an oversized fan-out.
    private static final int MAX_MERGED_DYNAMIC_QUERIES = 24;

    private final ExternalApiUtils externalApiUtils;
    private final ObjectMapper objectMapper;
    private final GdeltHotIssueSeedProvider gdeltHotIssueSeedProvider;
    private final DeterministicQueryGenerator deterministicQueryGenerator;

    @Value("${app.news.naver.enabled:false}")
    private boolean enabled;

    @Value("${app.news.naver.base-url:https://openapi.naver.com}")
    private String baseUrl;

    @Value("${app.news.naver.client-id:}")
    private String clientId;

    @Value("${app.news.naver.client-secret:}")
    private String clientSecret;

    @Value("${app.news.naver.queries:}")
    private String rawQueries;

    // Off by default so the safe-default behavior is preserved unless operators explicitly opt in.
    // When enabled, and only when no explicit queries are configured, the GDELT-seed + deterministic
    // generator source is merged ahead of the built-in defaults.
    @Value("${app.news.naver.dynamic-queries-enabled:false}")
    private boolean dynamicQueriesEnabled;

    @Value("${app.news.naver.display:10}")
    private int display;

    @Value("${app.news.naver.start:1}")
    private int start;

    @Value("${app.news.naver.max-age-hours:168}")
    private long maxAgeHours;

    @Value("${app.news.naver.fallback-max-age-hours:336}")
    private long fallbackMaxAgeHours;

    @Value("${app.news.naver.max-pages:3}")
    private int maxPages;

    private Clock clock = DEFAULT_CLOCK;

    @PostConstruct
    void logConfigurationState() {
        List<String> configuredQueries = parseConfiguredQueries();
        String queryMode = configuredQueries.isEmpty() ? "default" : "configured";
        int queryCount = configuredQueries.isEmpty() ? DEFAULT_QUERIES.size() : configuredQueries.size();
        log.info("[NAVER] configuration enabled={} clientIdPresent={} clientSecretPresent={} configured={} "
                        + "dynamicQueriesEnabled={} queryMode={} queryCount={} display={} start={} maxPages={} maxAgeHours={} "
                        + "fallbackMaxAgeHours={} baseUrl={}",
                enabled, hasClientId(), hasClientSecret(), isConfigured(), dynamicQueriesEnabled, queryMode, queryCount,
                resolveDisplay(display), resolveStart(), resolveMaxPages(), resolveMaxAgeHours(NewsFreshnessBucket.FRESH),
                resolveMaxAgeHours(NewsFreshnessBucket.SEMI_FRESH), baseUrl);
    }

    @Override
    public String sourceCode() {
        return "naver";
    }

    @Override
    public boolean supports(NewsFeedPriority priority) {
        return priority == NewsFeedPriority.DOMESTIC;
    }

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
        return fetchTopHeadlines(limit, NewsFreshnessBucket.FRESH);
    }

    @Override
    public List<ExternalNewsItem> fetchTopHeadlines(int limit, NewsFreshnessBucket bucket) {
        if (!isConfigured()) {
            log.info("[NAVER] provider empty reason=not-configured enabled={} clientIdPresent={} clientSecretPresent={}",
                    enabled, hasClientId(), hasClientSecret());
            return List.of();
        }

        int resolvedLimit = limit > 0 ? limit : Math.max(display, 1);
        List<String> queries = resolveQueries();
        NaverPassResult firstPass = runQueryPass(queries, resolvedLimit, bucket);
        List<ExternalNewsItem> merged = deduplicateAndLimit(firstPass.candidates(), resolvedLimit);
        // The pass that produced the final (possibly empty) merged result; used to build the
        // provider-wide empty summary so the recovery pass, when it runs, drives the reported cause.
        NaverPassResult effectivePass = firstPass;
        log.info("[NAVER] bucket={} merged usableItems={} requestedLimit={} queries={}",
                bucket, merged.size(), resolvedLimit, queries.size());

        if (merged.isEmpty() && shouldRunStaleRecoveryPass(bucket, firstPass)) {
            // The recovery pass refines query intent within the caller's freshness bucket; it must
            // never widen the window (FRESH stays FRESH), so a stale-only first pass is never
            // "recovered" by retaining stale items. SEMI_FRESH already uses its wider window in its
            // own first pass and so does not need a separate recovery pass.
            NewsFreshnessBucket recoveryBucket = bucket;
            List<String> recoveryQueries = resolveSecondPassQueries(queries);
            log.info("[NAVER] second-pass start reason=stale-only-first-pass primaryBucket={} recoveryBucket={} "
                            + "firstPassStaleItems={} firstPassRawItems={} recoveryQueries={}",
                    bucket, recoveryBucket, firstPass.staleItemCount(), firstPass.rawItemCount(),
                    recoveryQueries.size());
            NaverPassResult secondPass = runQueryPass(recoveryQueries, resolvedLimit, recoveryBucket);
            merged = deduplicateAndLimit(secondPass.candidates(), resolvedLimit);
            effectivePass = secondPass;
            log.info("[NAVER] second-pass complete recoveryBucket={} recoveredItems={} recoveryStaleItems={} recoveryRawItems={}",
                    recoveryBucket, merged.size(), secondPass.staleItemCount(), secondPass.rawItemCount());
        }

        if (merged.isEmpty()) {
            // Provider-wide empty summary: aggregate the per-query dispositions of the pass that
            // produced the empty result into a single cause, instead of a fixed no-usable-items
            // label. Since merged is empty no candidate was accepted, so stale + relevance-filtered +
            // unusable partition the raw items, letting the dominant cause be reported honestly.
            String reason = resolveProviderEmptyReason(effectivePass);
            log.info("[NAVER] provider empty reason={} bucket={} requestedLimit={} queries={} configured={} rawItems={} staleItems={} filteredByRelevance={} unusableItems={}",
                    reason, bucket, resolvedLimit, queries.size(), isConfigured(),
                    effectivePass.rawItemCount(), effectivePass.staleItemCount(),
                    effectivePass.filteredByRelevanceCount(), effectivePass.unusableItemCount());
        }
        return merged;
    }

    private NaverPassResult runQueryPass(List<String> queries, int resolvedLimit, NewsFreshnessBucket bucket) {
        List<NaverCandidate> candidates = new ArrayList<>();
        int staleItems = 0;
        int rawItems = 0;
        int filteredByRelevance = 0;
        int unusableItems = 0;
        for (String query : queries) {
            NaverQueryOutcome outcome = fetchQuery(query, resolvedLimit, bucket);
            candidates.addAll(outcome.candidates());
            staleItems += outcome.staleItemCount();
            rawItems += outcome.rawItemCount();
            filteredByRelevance += outcome.filteredByRelevanceCount();
            unusableItems += outcome.unusableItemCount();
            if (deduplicateAndLimit(candidates, resolvedLimit).size() >= resolvedLimit) {
                break;
            }
        }
        return new NaverPassResult(candidates, rawItems, staleItems, filteredByRelevance, unusableItems);
    }

    // The recovery pass only runs for the FRESH primary path, and only when the first pass actually
    // dropped items for being stale. It re-queries the leading queries within the FRESH window (it
    // never widens freshness), so any other empty result (no raw items, relevance filtering, unusable
    // pubDate) is not helped and we skip the extra upstream calls. SEMI_FRESH callers already use the
    // wider window in their first pass, so they get no separate recovery pass.
    private boolean shouldRunStaleRecoveryPass(NewsFreshnessBucket bucket, NaverPassResult firstPass) {
        if (bucket != NewsFreshnessBucket.FRESH) {
            return false;
        }
        return firstPass.staleItemCount() > 0;
    }

    private List<String> resolveSecondPassQueries(List<String> queries) {
        return queries.stream()
                .limit(SECOND_PASS_MAX_QUERIES)
                .toList();
    }

    // Classifies a provider-wide empty result by its dominant cause. merged being empty means no
    // candidate survived, so stale + relevance-filtered + unusable partition the raw items exactly;
    // when one cause accounts for every raw item it is reported specifically, otherwise the result is
    // a genuine mix and reported as such so callers can tell a recoverable cause from a structural one.
    private String resolveProviderEmptyReason(NaverPassResult pass) {
        int rawItems = pass.rawItemCount();
        if (rawItems <= 0) {
            return "no-raw-items";
        }
        if (pass.staleItemCount() == rawItems) {
            return "stale-only";
        }
        if (pass.filteredByRelevanceCount() == rawItems) {
            return "relevance-only";
        }
        if (pass.unusableItemCount() == rawItems) {
            return "unusable-input";
        }
        return "mixed-no-usable-items";
    }

    @Override
    public boolean isConfigured() {
        return enabled && hasClientId() && hasClientSecret();
    }

    private List<String> resolveQueries() {
        List<String> configuredQueries = parseConfiguredQueries();
        // rawQueriesPresent distinguishes a truly unset APP_NEWS_NAVER_QUERIES from one that was
        // bound but normalized away to nothing (blank/quotes-only), since both fall through to the
        // default path. defaultOnly tells whether the request is running purely on safe defaults.
        boolean rawQueriesPresent = StringUtils.hasText(rawQueries);
        if (!configuredQueries.isEmpty()) {
            // Explicit operator-configured queries are honored as-is; the dynamic GDELT-seed +
            // generator source never dilutes an explicit configuration.
            log.info("[NAVER] query-source resolved source=configured rawQueriesPresent={} defaultOnly=false resolvedQueryCount={}",
                    rawQueriesPresent, configuredQueries.size());
            return configuredQueries;
        }

        // No explicit configuration. When the dynamic source is enabled, merge GDELT-seeded generated
        // queries ahead of the static defaults so hot-issue intent leads while the safe defaults stay
        // as the fallback tail. When disabled, behavior is unchanged (safe-default fallback preserved).
        if (dynamicQueriesEnabled) {
            List<String> mergedQueries = resolveMergedDynamicQueries(rawQueriesPresent);
            if (!mergedQueries.isEmpty()) {
                return mergedQueries;
            }
        }

        log.warn("[NAVER] Naver news queries are empty; using safe defaults. "
                        + "Configure APP_NEWS_NAVER_QUERIES explicitly for production tuning. defaults={}",
                String.join(", ", DEFAULT_QUERIES));
        log.info("[NAVER] query-source resolved source=default rawQueriesPresent={} defaultOnly=true resolvedQueryCount={}",
                rawQueriesPresent, DEFAULT_QUERIES.size());
        return DEFAULT_QUERIES;
    }

    // Resolves the dynamic query source (GDELT hot-issue seeds -> deterministic Korean generator). The
    // deterministic generator is used ONLY when the seed result is a genuine dynamic GDELT signal
    // (origin REMOTE/CACHED_REMOTE); synthetic fallback/cooldown/not-configured seeds are never treated
    // as dynamic and instead fall through to the curated Naver fallback query pack (the built-in
    // defaults) with zero generated queries, so a rate-limit cooldown can no longer masquerade as a
    // real dynamic query source. Any dynamic-source failure also degrades to the safe defaults.
    private List<String> resolveMergedDynamicQueries(boolean rawQueriesPresent) {
        try {
            HotIssueSeedResult seedResult = gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(DYNAMIC_SEED_LIMIT);
            long seedAgeSeconds = resolveSeedAgeSeconds(seedResult.generatedAt());

            if (!seedResult.isDynamic()) {
                // Synthetic fallback signal: do not feed it to the generator. Use the curated Korea-market
                // fallback query pack instead (NOT the polluted built-in defaults), and make the
                // provenance explicit (generatedQueryCount=0).
                log.info("[NAVER] query-source resolved source=naver-curated-fallback rawQueriesPresent={} defaultOnly=true "
                                + "seedOrigin={} gdeltReason={} gdeltStatus={} seedAgeSeconds={} generatedQueryCount=0 curatedQueryCount={} resolvedQueryCount={}",
                        rawQueriesPresent, seedResult.origin(), seedResult.reason(), seedResult.status(), seedAgeSeconds,
                        CURATED_FALLBACK_QUERIES.size(), CURATED_FALLBACK_QUERIES.size());
                return CURATED_FALLBACK_QUERIES;
            }

            List<String> generatedQueries = deterministicQueryGenerator.generateQueries(seedResult.seeds(), DYNAMIC_QUERY_LIMIT).stream()
                    .map(this::normalizeConfiguredQuery)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(DYNAMIC_QUERY_LIMIT)
                    .toList();

            // LinkedHashSet keeps generated queries first (insertion order) and drops any default that a
            // generated query already covers, so the merge stays deduplicated without reordering intent.
            LinkedHashSet<String> merged = new LinkedHashSet<>(generatedQueries);
            merged.addAll(DEFAULT_QUERIES);
            List<String> mergedQueries = merged.stream()
                    .limit(MAX_MERGED_DYNAMIC_QUERIES)
                    .toList();

            String source = seedResult.origin() == HotIssueSeedOrigin.REMOTE ? "gdelt-dynamic" : "gdelt-cached-dynamic";
            log.info("[NAVER] query-source resolved source={} rawQueriesPresent={} defaultOnly=false seedOrigin={} gdeltReason={} "
                            + "gdeltStatus={} seedAgeSeconds={} seedCount={} generatedQueryCount={} defaultQueryCount={} resolvedQueryCount={}",
                    source, rawQueriesPresent, seedResult.origin(), seedResult.reason(), seedResult.status(), seedAgeSeconds,
                    seedResult.seeds().size(), generatedQueries.size(), DEFAULT_QUERIES.size(), mergedQueries.size());
            return mergedQueries;
        } catch (Exception ex) {
            log.warn("[NAVER] dynamic query source failed; using safe defaults. rawQueriesPresent={} defaultOnly=true resolvedQueryCount={}",
                    rawQueriesPresent, DEFAULT_QUERIES.size(), ex);
            return DEFAULT_QUERIES;
        }
    }

    // Seed age in seconds from the GDELT signal's generation time to now, or -1 when unknown. Negative
    // clock skew is clamped to 0 so the diagnostic stays sane.
    private long resolveSeedAgeSeconds(Instant generatedAt) {
        if (generatedAt == null) {
            return -1;
        }
        return Math.max(0, Duration.between(generatedAt, Instant.now(clock)).getSeconds());
    }

    private List<String> parseConfiguredQueries() {
        if (!StringUtils.hasText(rawQueries)) {
            return List.of();
        }
        return Arrays.stream(QUERY_DELIMITER.split(rawQueries))
                .map(this::normalizeConfiguredQuery)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(MAX_CONFIGURED_QUERIES)
                .toList();
    }

    private String normalizeConfiguredQuery(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        // Env/YAML binding frequently leaves a single layer of wrapping quotes on each entry.
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH).trim();
        }
        return trimmed;
    }

    private NaverQueryOutcome fetchQuery(String query, int limit, NewsFreshnessBucket bucket) {
        log.info("[NAVER] query start bucket={} query='{}' requestedLimit={}", bucket, query, limit);
        int pageSize = resolveDisplay(limit);
        List<NaverCandidate> collected = new ArrayList<>();
        int staleItems = 0;
        int rawItems = 0;
        int filteredByRelevance = 0;
        int unusableItems = 0;
        for (int pageIndex = 0; pageIndex < resolveMaxPages(); pageIndex++) {
            int pageStart = resolvePageStart(pageIndex, pageSize);
            if (pageStart < 0) {
                log.info("[NAVER] stopping paging query='{}' reason=start-out-of-range pageIndex={} pageSize={}",
                        query, pageIndex, pageSize);
                break;
            }
            ExternalApiResult result = externalApiUtils.callAPI(new ExternalApiRequest(
                    HttpMethod.GET,
                    buildHeaders(),
                    buildQueryUrl(query, pageSize, pageStart),
                    null
            ));
            if (result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
                int statusCode = result == null ? -1 : result.statusCode();
                String reason = statusCode == 429 ? "rate-limit" : "upstream-rejection";
                log.warn("[NAVER] provider empty reason={} bucket={} query='{}' pageStart={} status={}",
                        reason, bucket, query, pageStart, statusCode);
                break;
            }

            NaverParseResult parsed = parseItems(query, pageStart, result.body(), resolveMaxAgeHours(bucket), bucket);
            collected.addAll(parsed.items());
            staleItems += parsed.staleItemCount();
            rawItems += parsed.rawItemCount();
            filteredByRelevance += parsed.filteredByRelevanceCount();
            unusableItems += parsed.unusableItemCount();
            if (collected.size() >= limit) {
                break;
            }
            if (parsed.rawItemCount() <= 0) {
                break;
            }
        }
        return new NaverQueryOutcome(collected, rawItems, staleItems, filteredByRelevance, unusableItems);
    }

    // Package-private for direct diagnostic assertions in unit tests (see NaverParseResult).
    NaverParseResult parseItems(String query, int pageStart, String body, long maxAgeHours, NewsFreshnessBucket bucket) {
        if (!StringUtils.hasText(body)) {
            log.info("[NAVER] provider empty reason=upstream-empty-response bucket={} query='{}' pageStart={} rawItems=0 bodyEmpty=true",
                    bucket, query, pageStart);
            return new NaverParseResult(List.of(), 0, 0, 0, 0, 0, 0, 0, 0);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                log.info("[NAVER] provider empty reason=upstream-empty-response bucket={} query='{}' pageStart={} rawItems=0 itemsArray=false",
                        bucket, query, pageStart);
                return new NaverParseResult(List.of(), 0, 0, 0, 0, 0, 0, 0, 0);
            }

            int rawItemCount = items.size();
            int invalidPubDateCount = 0;
            int nullPublishedAtCount = 0;
            int staleItemCount = 0;
            int staleLoggedCount = 0;
            int filteredByRelevanceCount = 0;
            int missingUrlCount = 0;
            int emptyTitleCount = 0;
            int fallbackRetainedCount = 0;
            // Diagnostic-only freshness x relevance breakdown for every dated item. These counters
            // never affect the drop decisions below; they exist so a stale-only empty result can be
            // told apart from a stale-AND-irrelevant one (the stale-first drop order otherwise hides
            // irrelevant-but-old items inside staleItems with filteredByRelevance=0).
            int staleAndIrrelevantCount = 0;
            int staleButRelevantCount = 0;
            int freshButIrrelevantCount = 0;
            int freshAndRelevantCount = 0;
            Instant now = Instant.now(clock);
            Instant cutoff = now.minus(Duration.ofHours(maxAgeHours));
            // FRESH-window cutoff used only to observe (never to filter) how many retained items
            // survive purely because the SEMI_FRESH fallback window is wider than FRESH. This makes
            // it visible that useful semi-fresh items within fallbackMaxAgeHours are kept, not hidden.
            Instant freshCutoff = now.minus(Duration.ofHours(resolveMaxAgeHours(NewsFreshnessBucket.FRESH)));
            List<NaverCandidate> mapped = new ArrayList<>();
            for (JsonNode item : items) {
                String cleanedTitle = cleanHtml(item.path("title").asText(""));
                String originalLink = item.path("originallink").asText("");
                String fallbackLink = item.path("link").asText("");
                String cleanedDescription = normalizeNaverDescription(
                        item.path("description").asText(""),
                        originalLink,
                        fallbackLink
                );
                String rawPubDate = item.path("pubDate").asText("");
                Instant publishedAt = ExternalResponseValueParser.parseInstant(
                        rawPubDate, NAVER_PUB_DATE_FORMATTER, NAVER_PUB_DATE_FALLBACK_FORMATTERS);
                String resolvedUrl = StringUtils.hasText(originalLink) ? originalLink : fallbackLink;
                String dedupTitle = normalizeTitle(cleanedTitle);
                if (StringUtils.hasText(rawPubDate) && publishedAt == null) {
                    invalidPubDateCount++;
                }
                if (publishedAt == null) {
                    nullPublishedAtCount++;
                    continue;
                }
                // Evaluate freshness and relevance once, up front, for both the diagnostic breakdown
                // and the drop decisions. The drop order (stale first, then relevance) is unchanged;
                // we only also record relevance for stale items, which the old order never measured.
                boolean fresh = isFreshEnough(publishedAt, cutoff);
                boolean relevant = isRelevantForMacroNews(cleanedTitle, cleanedDescription);
                if (fresh) {
                    if (relevant) {
                        freshAndRelevantCount++;
                    } else {
                        freshButIrrelevantCount++;
                    }
                } else {
                    if (relevant) {
                        staleButRelevantCount++;
                    } else {
                        staleAndIrrelevantCount++;
                    }
                }
                if (!fresh) {
                    staleItemCount++;
                    if (staleLoggedCount < STALE_LOG_SAMPLE_LIMIT) {
                        log.info("[NAVER] stale item sample bucket={} query='{}' pageStart={} publishedAt={} cutoff={} ageHours={} title='{}'",
                                bucket, query, pageStart, publishedAt, cutoff, formatAgeHours(publishedAt, now),
                                abbreviateForLog(cleanedTitle));
                        staleLoggedCount++;
                    }
                    continue;
                }
                if (!relevant) {
                    filteredByRelevanceCount++;
                    continue;
                }
                if (!StringUtils.hasText(resolvedUrl)) {
                    missingUrlCount++;
                }
                if (!StringUtils.hasText(cleanedTitle)) {
                    emptyTitleCount++;
                }
                if (!isFreshEnough(publishedAt, freshCutoff)) {
                    fallbackRetainedCount++;
                }

                ExternalNewsItem mappedItem = new ExternalNewsItem(
                        resolveExternalId(resolvedUrl, dedupTitle, rawPubDate),
                        "NAVER",
                        ExternalResponseTextNormalizer.defaultText(cleanedTitle, "Untitled"),
                        ExternalResponseTextNormalizer.defaultText(cleanedDescription, ""),
                        ExternalResponseTextNormalizer.defaultText(resolvedUrl, ""),
                        publishedAt
                );
                mapped.add(new NaverCandidate(mappedItem, originalLink, fallbackLink, dedupTitle));
            }
            log.info("[NAVER] bucket={} query='{}' pageStart={} rawItems={}", bucket, query, pageStart, rawItemCount);
            log.info("[NAVER] bucket={} query='{}' pageStart={} parsedItems={} nullPublishedAt={} invalidPubDate={} staleItems={} filteredByRelevance={} missingUsableLink={} emptyTitle={} fallbackRetained={} staleAndIrrelevant={} staleButRelevant={} freshButIrrelevant={} freshAndRelevant={}",
                    bucket, query, pageStart, mapped.size(), nullPublishedAtCount, invalidPubDateCount, staleItemCount,
                    filteredByRelevanceCount, missingUrlCount, emptyTitleCount, fallbackRetainedCount,
                    staleAndIrrelevantCount, staleButRelevantCount, freshButIrrelevantCount, freshAndRelevantCount);
            if (mapped.isEmpty() && rawItemCount > 0) {
                String reason = staleItemCount == rawItemCount
                        ? "stale-only-input"
                        : (nullPublishedAtCount == rawItemCount || invalidPubDateCount == rawItemCount
                                ? "unusable-input"
                                : filteredByRelevanceCount == rawItemCount
                                        ? "relevance-only-input"
                                : "no-usable-items");
                log.info("[NAVER] provider empty reason={} bucket={} query='{}' pageStart={} rawItems={} staleItems={} nullPublishedAt={} invalidPubDate={} filteredByRelevance={} missingUsableLink={} emptyTitle={}",
                        reason, bucket, query, pageStart, rawItemCount, staleItemCount, nullPublishedAtCount,
                        invalidPubDateCount, filteredByRelevanceCount, missingUrlCount, emptyTitleCount);
            }
            return new NaverParseResult(mapped, rawItemCount, staleItemCount, filteredByRelevanceCount, nullPublishedAtCount,
                    staleAndIrrelevantCount, staleButRelevantCount, freshButIrrelevantCount, freshAndRelevantCount);
        } catch (Exception ex) {
            log.warn("[NAVER] failed to parse response bucket={} query='{}' pageStart={}", bucket, query, pageStart, ex);
            return new NaverParseResult(List.of(), 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Naver-Client-Id", clientId);
        headers.add("X-Naver-Client-Secret", clientSecret);
        return headers;
    }

    private String buildQueryUrl(String query, int pageSize, int pageStart) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/v1/search/news.json")
                .queryParam("query", query)
                .queryParam("display", pageSize)
                .queryParam("start", pageStart)
                .queryParam("sort", "date")
                .build()
                .encode()
                .toUriString();
    }

    private List<ExternalNewsItem> deduplicateAndLimit(List<NaverCandidate> candidates, int limit) {
        Map<String, ExternalNewsItem> deduplicated = new LinkedHashMap<>();
        candidates.stream()
                .sorted(Comparator.comparing((NaverCandidate candidate) -> candidate.item().publishedAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(candidate -> candidate.item().url(), Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(candidate -> deduplicated.putIfAbsent(resolveDedupKey(candidate), candidate.item()));
        return deduplicated.values().stream()
                .limit(limit)
                .toList();
    }

    private String resolveDedupKey(NaverCandidate candidate) {
        String normalizedOriginalLink = ExternalResponseTextNormalizer.normalizeUrl(candidate.originalLink());
        if (StringUtils.hasText(normalizedOriginalLink)) {
            return normalizedOriginalLink;
        }
        String normalizedTitle = candidate.normalizedTitle();
        if (StringUtils.hasText(normalizedTitle)) {
            return normalizedTitle;
        }
        String normalizedFallbackLink = ExternalResponseTextNormalizer.normalizeUrl(candidate.fallbackLink());
        if (StringUtils.hasText(normalizedFallbackLink)) {
            return normalizedFallbackLink;
        }
        return candidate.item().externalId();
    }

    private String cleanHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String stripped = value.replaceAll("(?i)</?b>", "");
        return HtmlUtils.htmlUnescape(stripped).trim();
    }

    private boolean isFreshEnough(Instant publishedAt, long allowedMaxAgeHours) {
        if (publishedAt == null) {
            return false;
        }
        return isFreshEnough(publishedAt, Instant.now(clock).minus(Duration.ofHours(allowedMaxAgeHours)));
    }

    private boolean isFreshEnough(Instant publishedAt, Instant cutoff) {
        if (publishedAt == null) {
            return false;
        }
        return !publishedAt.isBefore(cutoff);
    }

    private int resolveDisplay(int requestedLimit) {
        int configuredDisplay = display > 0 ? display : requestedLimit;
        int resolvedDisplay = Math.max(1, Math.min(Math.max(requestedLimit, 1), configuredDisplay));
        return Math.min(resolvedDisplay, NAVER_MAX_DISPLAY);
    }

    private int resolveStart() {
        int resolvedStart = start > 0 ? start : 1;
        return Math.min(resolvedStart, NAVER_MAX_START);
    }

    private int resolvePageStart(int pageIndex, int pageSize) {
        long resolvedPageSize = Math.max(pageSize, 1);
        long resolvedStart = resolveStart();
        long pageStart = resolvedStart + ((long) pageIndex * resolvedPageSize);
        if (pageStart > NAVER_MAX_START) {
            return -1;
        }
        return (int) pageStart;
    }

    private int resolveMaxPages() {
        return maxPages > 0 ? maxPages : 3;
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return ExternalResponseTextNormalizer.normalizeLowerCase(title.trim())
                .replaceAll("\\[(?:\\uC18D\\uBCF4|(?i:breaking))]", " ")
                .replace(KOREAN_BREAKING_MARKER, " ")
                .replaceAll("(?i)\\bbreaking\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasClientId() {
        return StringUtils.hasText(clientId);
    }

    private boolean hasClientSecret() {
        return StringUtils.hasText(clientSecret);
    }

    private boolean isRelevantForMacroNews(String title, String description) {
        return containsRelevanceKeyword(title) || containsRelevanceKeyword(description);
    }

    private boolean containsRelevanceKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : NON_ASCII_RELEVANCE_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        for (Pattern pattern : ASCII_RELEVANCE_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsciiKeyword(String keyword) {
        for (int i = 0; i < keyword.length(); i++) {
            if (keyword.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private String formatAgeHours(Instant publishedAt, Instant now) {
        double ageHours = Duration.between(publishedAt, now).toMinutes() / 60.0;
        return String.format(Locale.ROOT, "%.2f", ageHours);
    }

    private String abbreviateForLog(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        if (title.length() <= STALE_LOG_TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, STALE_LOG_TITLE_MAX_LENGTH - 3) + "...";
    }

    private long resolveMaxAgeHours(NewsFreshnessBucket bucket) {
        long freshMaxAge = maxAgeHours > 0 ? maxAgeHours : 12L;
        if (bucket == NewsFreshnessBucket.SEMI_FRESH) {
            long fallbackMaxAge = fallbackMaxAgeHours > 0 ? fallbackMaxAgeHours : 24L;
            // SEMI_FRESH is the lenient fallback bucket, so its window must never be tighter than
            // FRESH. A misconfigured fallback-max-age-hours < max-age-hours would otherwise make the
            // fallback stricter than the primary pass and silently drop items FRESH would accept.
            return Math.max(fallbackMaxAge, freshMaxAge);
        }
        return freshMaxAge;
    }

    private String resolveExternalId(String resolvedUrl, String normalizedTitle, String rawPubDate) {
        if (StringUtils.hasText(resolvedUrl)) {
            return resolvedUrl;
        }
        String titleSeed = StringUtils.hasText(normalizedTitle) ? normalizedTitle : "untitled";
        String dateSeed = StringUtils.hasText(rawPubDate) ? rawPubDate.trim() : "unknown-pubdate";
        return "naver:" + titleSeed + "|" + dateSeed;
    }

    private String normalizeNaverDescription(String rawDescription, String originalLink, String fallbackLink) {
        String cleaned = cleanHtml(rawDescription);
        if (!StringUtils.hasText(cleaned)) {
            return "";
        }
        if (sameText(cleaned, originalLink) || sameText(cleaned, fallbackLink)) {
            return "";
        }
        if (!looksPercentEncoded(cleaned)) {
            return cleaned;
        }
        try {
            String decoded = URLDecoder.decode(cleaned, StandardCharsets.UTF_8);
            if (isReadableDecodedText(cleaned, decoded)) {
                return decoded.trim();
            }
        } catch (Exception ex) {
            log.debug("[NAVER] description decode failed");
        }
        return cleaned;
    }

    private boolean looksPercentEncoded(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        int encodedTriplets = 0;
        for (int i = 0; i < value.length() - 2; i++) {
            if (value.charAt(i) == '%'
                    && isHexCharacter(value.charAt(i + 1))
                    && isHexCharacter(value.charAt(i + 2))) {
                encodedTriplets++;
            }
        }
        return encodedTriplets >= 2;
    }

    private boolean isReadableDecodedText(String original, String decoded) {
        if (!StringUtils.hasText(decoded) || decoded.equals(original) || looksPercentEncoded(decoded)) {
            return false;
        }
        for (char ch : decoded.toCharArray()) {
            if (Character.isLetter(ch) || Character.isDigit(ch) || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                return true;
            }
        }
        return false;
    }

    private boolean isHexCharacter(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equals(right.trim());
    }

    private record NaverCandidate(
            ExternalNewsItem item,
            String originalLink,
            String fallbackLink,
            String normalizedTitle
    ) {
    }

    // Package-private so diagnostics (the freshness x relevance breakdown) can be asserted directly
    // in unit tests without parsing log output, which would be brittle.
    record NaverParseResult(
            List<NaverCandidate> items,
            int rawItemCount,
            int staleItemCount,
            int filteredByRelevanceCount,
            int unusableItemCount,
            int staleAndIrrelevantCount,
            int staleButRelevantCount,
            int freshButIrrelevantCount,
            int freshAndRelevantCount
    ) {
    }

    private record NaverQueryOutcome(
            List<NaverCandidate> candidates,
            int rawItemCount,
            int staleItemCount,
            int filteredByRelevanceCount,
            int unusableItemCount
    ) {
    }

    private record NaverPassResult(
            List<NaverCandidate> candidates,
            int rawItemCount,
            int staleItemCount,
            int filteredByRelevanceCount,
            int unusableItemCount
    ) {
    }
}

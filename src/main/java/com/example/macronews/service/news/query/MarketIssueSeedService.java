package com.example.macronews.service.news.query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves the dynamic Naver query source by applying a strict priority chain:
 *
 * <ol>
 *   <li>GDELT {@code REMOTE} / {@code CACHED_REMOTE} hot-issue seeds -> deterministic Korean queries;
 *   <li>otherwise, OpenAI web-search market issue seeds (when enabled and dynamic);
 *   <li>otherwise, the curated Korea-market fallback query pack.
 * </ol>
 *
 * <p>The OpenAI source is only consulted when GDELT is NOT dynamic, so a healthy GDELT signal never
 * triggers an OpenAI call. Any failure degrades quietly to the curated fallback. This service owns the
 * cross-source decision so {@code NaverNewsSourceProvider} stays thin (it only issues the returned
 * queries). It does not change provider priority, freshness, or relevance behavior.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketIssueSeedService {

    private static final int DYNAMIC_SEED_LIMIT = 10;
    private static final int DYNAMIC_QUERY_LIMIT = 10;
    // Caps the merged (dynamic-lead + curated-tail) list to a sane per-cycle Naver query budget.
    private static final int MAX_MERGED_DYNAMIC_QUERIES = 24;
    private static final int MAX_QUERY_LENGTH = 100;

    private static final String SOURCE_GDELT_DYNAMIC = "gdelt-dynamic";
    private static final String SOURCE_GDELT_CACHED_DYNAMIC = "gdelt-cached-dynamic";
    private static final String SOURCE_OPENAI_WEB_SEARCH = "openai-web-search-dynamic";
    private static final String SOURCE_OPENAI_CACHED = "openai-cached-dynamic";
    private static final String SOURCE_CURATED_FALLBACK = "naver-curated-fallback";
    private static final String CURATED_ORIGIN = "CURATED_FALLBACK";

    private final GdeltHotIssueSeedProvider gdeltHotIssueSeedProvider;
    private final DeterministicQueryGenerator deterministicQueryGenerator;
    private final OpenAiMarketIssueSeedProvider openAiMarketIssueSeedProvider;

    // Injectable for deterministic seed-age tests; defaults to the system UTC clock.
    private Clock clock = Clock.systemUTC();

    /**
     * Resolves the final Naver query list according to the GDELT -> OpenAI -> curated priority chain.
     * Never returns {@code null} and never throws; the curated fallback is the always-available floor.
     */
    public ResolvedMarketIssueQueries resolve() {
        Instant now = clock.instant();
        try {
            HotIssueSeedResult gdelt = gdeltHotIssueSeedProvider.resolveHotIssueSeedResult(DYNAMIC_SEED_LIMIT);
            if (gdelt.isDynamic()) {
                return fromGdelt(gdelt, now);
            }

            // GDELT is a synthetic fallback/cooldown signal: only now may the OpenAI source be tried.
            MarketIssueSeedResult openAi = openAiMarketIssueSeedProvider.resolveMarketIssueSeeds();
            if (openAi.isDynamic()) {
                return fromOpenAi(openAi, now);
            }
            return curated(openAi.reason(), openAi.generatedAt(), now);
        } catch (Exception ex) {
            log.warn("[MARKET-SEED] resolution failed; using curated fallback", ex);
            return curated("resolve-error", now, now);
        }
    }

    private ResolvedMarketIssueQueries fromGdelt(HotIssueSeedResult gdelt, Instant now) {
        List<String> generated = normalizeQueries(
                deterministicQueryGenerator.generateQueries(gdelt.seeds(), DYNAMIC_QUERY_LIMIT));
        List<String> merged = mergeWithCuratedTail(generated);
        String source = gdelt.origin() == HotIssueSeedOrigin.REMOTE
                ? SOURCE_GDELT_DYNAMIC : SOURCE_GDELT_CACHED_DYNAMIC;
        int generatedInMerged = countLead(generated, merged);
        return logged(new ResolvedMarketIssueQueries(
                merged, source, gdelt.origin().name(), gdelt.reason(),
                generatedInMerged, merged.size() - generatedInMerged, 0,
                ageSeconds(gdelt.generatedAt(), now), gdelt.generatedAt()));
    }

    private ResolvedMarketIssueQueries fromOpenAi(MarketIssueSeedResult openAi, Instant now) {
        List<String> lead = normalizeQueries(openAi.naverQueries());
        List<String> merged = mergeWithCuratedTail(lead);
        String source = openAi.origin() == MarketIssueSeedOrigin.OPENAI_WEB_SEARCH
                ? SOURCE_OPENAI_WEB_SEARCH : SOURCE_OPENAI_CACHED;
        int generatedInMerged = countLead(lead, merged);
        return logged(new ResolvedMarketIssueQueries(
                merged, source, openAi.origin().name(), openAi.reason(),
                generatedInMerged, merged.size() - generatedInMerged, openAi.evidenceCount(),
                ageSeconds(openAi.generatedAt(), now), openAi.generatedAt()));
    }

    private ResolvedMarketIssueQueries curated(String reason, Instant seedGeneratedAt, Instant now) {
        List<String> curated = NaverCuratedFallbackQueries.QUERIES;
        return logged(new ResolvedMarketIssueQueries(
                curated, SOURCE_CURATED_FALLBACK, CURATED_ORIGIN, reason,
                0, curated.size(), 0, ageSeconds(seedGeneratedAt, now), seedGeneratedAt));
    }

    // LinkedHashSet keeps the dynamic lead first (insertion order) and drops any curated entry already
    // covered by a lead query, so the merge stays de-duplicated without reordering intent.
    private List<String> mergeWithCuratedTail(List<String> lead) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(lead);
        merged.addAll(NaverCuratedFallbackQueries.QUERIES);
        return merged.stream().limit(MAX_MERGED_DYNAMIC_QUERIES).toList();
    }

    private List<String> normalizeQueries(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String query : raw) {
            String normalized = normalizeQuery(query);
            if (normalized != null) {
                out.add(normalized);
            }
            if (out.size() >= DYNAMIC_QUERY_LIMIT) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private String normalizeQuery(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        // Env/YAML binding occasionally leaves a single layer of wrapping quotes on each entry.
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
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private int countLead(List<String> lead, List<String> merged) {
        Set<String> leadSet = new LinkedHashSet<>(lead);
        return (int) merged.stream().filter(leadSet::contains).count();
    }

    private long ageSeconds(Instant generatedAt, Instant now) {
        if (generatedAt == null) {
            return -1;
        }
        return Math.max(0, Duration.between(generatedAt, now).getSeconds());
    }

    private ResolvedMarketIssueQueries logged(ResolvedMarketIssueQueries resolved) {
        log.info("[MARKET-SEED] query-source resolved source={} seedOrigin={} reason={} generatedQueryCount={} curatedQueryCount={} evidenceCount={} seedAgeSeconds={} resolvedQueryCount={}",
                resolved.source(), resolved.seedOrigin(), resolved.reason(), resolved.generatedQueryCount(),
                resolved.curatedQueryCount(), resolved.evidenceCount(), resolved.seedAgeSeconds(),
                resolved.queries().size());
        return resolved;
    }
}

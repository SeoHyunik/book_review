package com.example.macronews.service.news.query;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Additive generator that maps hot-issue seed phrases into short Korean Naver event-phrase queries.
 *
 * <p>Given seed phrases (for example the English market-event seeds produced by
 * {@link GdeltHotIssueSeedProvider}), this component derives compact Korean search phrases that are
 * well suited to Naver news search. Mapping is purely deterministic: the same seed input always
 * yields the same ordered query list, with no network calls, randomness, or LLM usage. The output
 * is never empty; when no seed keyword matches, a stable Korean default query list is returned so
 * callers always have something usable.
 *
 * <p>This component is intentionally NOT yet wired into the Naver query path; it only exposes the
 * query generation entry point. Logging is restricted to counts and the resolution mode to avoid
 * leaking seed content.
 */
@Component
@Slf4j
public class DeterministicQueryGenerator {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 40;

    private static final String MODE_MAPPED = "mapped";
    private static final String MODE_DEFAULT = "default";

    // Deterministic keyword -> Korean event-phrase mapping. Keys are matched on whole-word/token
    // boundaries against each seed (so "oil" does not match "roils"), and iteration order is stable
    // so generated queries are reproducible. Each keyword maps to one or more event/entity-specific
    // Korean queries (for example "한국은행 기준금리 동결" rather than the broad "기준금리 발표"); broad
    // abstract phrases such as "금융 시장 동향"/"물가 상승률" are deliberately avoided because they
    // surface mostly stale-and-irrelevant Naver results.
    private static final Map<Pattern, List<String>> KEYWORD_QUERIES = buildKeywordQueries();

    // Stable Korean default queries used whenever no seed keyword matches, so output is never empty.
    // Kept event/entity-specific for the same reason as the keyword mapping above.
    private static final List<String> DEFAULT_QUERIES = List.of(
            "한국은행 기준금리 동결",
            "미국 CPI 물가 발표",
            "FOMC 금리 동결",
            "WTI 유가 상승",
            "원달러 환율 마감",
            "삼성전자 주가 전망",
            "미국 국채금리 상승",
            "코스피 마감 시황"
    );

    /**
     * Generates short Korean Naver event-phrase queries from the given seed phrases.
     *
     * <p>The result is deterministic, reproducible, de-duplicated, bounded by {@code limit}, and
     * never empty.
     */
    public List<String> generateQueries(List<String> seeds, int limit) {
        int resolvedLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (seeds != null) {
            collect:
            for (String seed : seeds) {
                if (!StringUtils.hasText(seed)) {
                    continue;
                }
                String normalizedSeed = seed.toLowerCase(Locale.ROOT);
                for (Map.Entry<Pattern, List<String>> entry : KEYWORD_QUERIES.entrySet()) {
                    if (!entry.getKey().matcher(normalizedSeed).find()) {
                        continue;
                    }
                    for (String phrase : entry.getValue()) {
                        String query = normalizeQuery(phrase);
                        if (query != null) {
                            queries.add(query);
                        }
                        if (queries.size() >= resolvedLimit) {
                            break collect;
                        }
                    }
                }
            }
        }

        if (queries.isEmpty()) {
            List<String> defaults = DEFAULT_QUERIES.stream().limit(resolvedLimit).toList();
            log.info("[QUERYGEN] korean queries mode={} seedCount={} generated={} limit={}",
                    MODE_DEFAULT, seeds == null ? 0 : seeds.size(), defaults.size(), resolvedLimit);
            return defaults;
        }

        List<String> mapped = queries.stream().limit(resolvedLimit).toList();
        log.info("[QUERYGEN] korean queries mode={} seedCount={} generated={} limit={}",
                MODE_MAPPED, seeds.size(), mapped.size(), resolvedLimit);
        return mapped;
    }

    private String normalizeQuery(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.replaceAll("\\s+", " ").trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return null;
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH).trim();
        }
        return trimmed;
    }

    private static Map<Pattern, List<String>> buildKeywordQueries() {
        // LinkedHashMap preserves insertion order so query generation stays reproducible. Event/entity
        // specific keywords are listed first so they take priority over the broader index/market terms
        // when a single seed matches several keys. Every value is a concrete Korean search phrase that
        // a real macro/market article headline is likely to contain.
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("interest rate", List.of("한국은행 기준금리 동결", "금통위 기준금리 결정"));
        map.put("base rate", List.of("한국은행 기준금리 동결"));
        map.put("federal reserve", List.of("미국 연준 금리 결정", "FOMC 금리 동결"));
        map.put("fomc", List.of("FOMC 금리 동결"));
        map.put("powell", List.of("파월 금리 인하 발언"));
        map.put("monetary policy", List.of("미국 연준 통화정책"));
        map.put("central bank", List.of("한국은행 기준금리 동결"));
        map.put("inflation", List.of("미국 CPI 물가 발표", "미국 PCE 물가 발표"));
        map.put("cpi", List.of("미국 CPI 물가 발표"));
        map.put("pce", List.of("미국 PCE 물가 발표"));
        map.put("jobs", List.of("미국 고용지표 발표"));
        map.put("payrolls", List.of("비농업 고용 발표"));
        map.put("oil", List.of("WTI 유가 상승", "브렌트유 유가 상승"));
        map.put("crude", List.of("WTI 유가 상승"));
        map.put("wti", List.of("WTI 유가 상승"));
        map.put("brent", List.of("브렌트유 유가 상승"));
        map.put("dollar", List.of("원달러 환율 마감", "달러인덱스 환율"));
        map.put("exchange rate", List.of("원달러 환율 마감"));
        map.put("semiconductor", List.of("삼성전자 주가 전망", "SK하이닉스 주가 전망", "반도체 수출 증가"));
        map.put("samsung", List.of("삼성전자 주가 전망"));
        map.put("hynix", List.of("SK하이닉스 주가 전망"));
        map.put("treasury", List.of("미국 국채금리 상승"));
        map.put("yield", List.of("국채금리 상승"));
        map.put("bond", List.of("국채금리 상승"));
        map.put("kospi", List.of("코스피 마감 시황"));
        map.put("kosdaq", List.of("코스닥 마감 시황"));
        map.put("equity", List.of("코스피 마감 시황"));
        map.put("stock", List.of("코스피 마감 시황"));
        map.put("market", List.of("코스피 마감 시황"));

        // Compile each keyword to a whole-word/token-boundary pattern so substrings inside larger
        // words (for example "oil" within "roils") no longer produce false matches.
        Map<Pattern, List<String>> patterns = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b");
            patterns.put(pattern, entry.getValue());
        }
        return patterns;
    }
}

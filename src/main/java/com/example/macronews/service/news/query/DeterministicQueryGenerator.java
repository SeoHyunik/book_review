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
    // so generated queries are reproducible.
    private static final Map<Pattern, String> KEYWORD_QUERIES = buildKeywordQueries();

    // Stable Korean default queries used whenever no seed keyword matches, so output is never empty.
    private static final List<String> DEFAULT_QUERIES = List.of(
            "미국 기준금리 발표",
            "소비자물가 상승률",
            "연준 통화정책 회의",
            "국제 유가 동향",
            "환율 원달러 환율",
            "반도체 업황 전망",
            "국채 금리 시장",
            "글로벌 증시 급락"
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
            for (String seed : seeds) {
                if (!StringUtils.hasText(seed)) {
                    continue;
                }
                String normalizedSeed = seed.toLowerCase(Locale.ROOT);
                for (Map.Entry<Pattern, String> entry : KEYWORD_QUERIES.entrySet()) {
                    if (entry.getKey().matcher(normalizedSeed).find()) {
                        String query = normalizeQuery(entry.getValue());
                        if (query != null) {
                            queries.add(query);
                        }
                    }
                    if (queries.size() >= resolvedLimit) {
                        break;
                    }
                }
                if (queries.size() >= resolvedLimit) {
                    break;
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

    private static Map<Pattern, String> buildKeywordQueries() {
        // LinkedHashMap preserves insertion order so query generation stays reproducible.
        Map<String, String> map = new LinkedHashMap<>();
        map.put("interest rate", "기준금리 발표");
        map.put("federal reserve", "연준 통화정책");
        map.put("fomc", "FOMC 회의 결과");
        map.put("powell", "파월 발언 통화정책");
        map.put("monetary policy", "통화정책 방향");
        map.put("inflation", "물가 상승률");
        map.put("cpi", "소비자물가 지수");
        map.put("jobs", "미국 고용지표");
        map.put("payrolls", "비농업 고용지표");
        map.put("oil", "국제 유가 동향");
        map.put("crude", "원유 가격 변동");
        map.put("dollar", "원달러 환율");
        map.put("exchange rate", "환율 동향");
        map.put("semiconductor", "반도체 업황 전망");
        map.put("treasury", "미국 국채 금리");
        map.put("bond", "채권 시장 동향");
        map.put("yield", "국채 수익률");
        map.put("equity", "글로벌 증시 동향");
        map.put("stock", "주식 시장 전망");
        map.put("market", "금융 시장 동향");
        map.put("central bank", "중앙은행 정책");

        // Compile each keyword to a whole-word/token-boundary pattern so substrings inside larger
        // words (for example "oil" within "roils") no longer produce false matches.
        Map<Pattern, String> patterns = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b");
            patterns.put(pattern, entry.getValue());
        }
        return patterns;
    }
}

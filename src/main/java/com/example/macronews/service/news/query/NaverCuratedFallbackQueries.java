package com.example.macronews.service.news.query;

import java.util.List;

/**
 * Curated Korea-market fallback query pack, shared by the market issue seed resolution chain.
 *
 * <p>Used when no genuine dynamic signal is available (GDELT not REMOTE/CACHED_REMOTE and the OpenAI
 * seed source disabled/failed/cooldown) and as the static tail behind a dynamic lead. The pack is
 * intentionally weighted toward Korea-market reaction terms and major domestic tickers/sectors, with
 * market-reaction queries (코스피 상승/하락, 원달러, 환율 상승/하락, 뉴욕증시, 나스닥) leading because they map
 * most reliably to fresh, macro-relevant Naver articles. Each entry is 1-3 tokens, OR-syntax free, and
 * the count stays inside the 18-24 query budget. Extracted from {@code NaverNewsSourceProvider} so both
 * the provider and {@code MarketIssueSeedService} can reuse it without widening any public surface.
 */
public final class NaverCuratedFallbackQueries {

    public static final List<String> QUERIES = List.of(
            "삼성전자",
            "SK하이닉스",
            "코스피 상승",
            "코스피 하락",
            "원달러",
            "환율 상승",
            "환율 하락",
            "뉴욕증시",
            "나스닥",
            "반도체",
            "2차전지",
            "두산에너빌리티",
            "한미반도체",
            "현대차",
            "기아",
            "LG에너지솔루션",
            "NAVER",
            "카카오",
            "코스닥 상승",
            "코스닥 하락",
            "국제유가",
            "방산"
    );

    private NaverCuratedFallbackQueries() {
    }
}

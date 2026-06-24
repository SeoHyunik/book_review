package com.example.macronews.service.news.query;

import java.util.List;

/**
 * Curated Korea-market fallback query pack, shared by the market issue seed resolution chain.
 *
 * <p>Used when no genuine dynamic signal is available (GDELT not REMOTE/CACHED_REMOTE and the OpenAI
 * seed source disabled/failed/cooldown) and as the static tail behind a dynamic lead. Every entry pairs
 * a ticker/sector with an explicit market-context token (주가/실적/환율/마감/외국인/기관/상승/하락) instead of a
 * bare single noun, because bare nouns such as "삼성전자"/"원달러"/"반도체" repeatedly hooked stale
 * lifestyle/entertainment articles in production. Market-reaction queries lead because they map most
 * reliably to fresh, macro-relevant Naver articles. Each entry is 1-4 tokens, OR-syntax free, and the
 * count stays inside the 18-24 query budget. Extracted from {@code NaverNewsSourceProvider} so both the
 * provider and {@code MarketIssueSeedService} can reuse it without widening any public surface.
 */
public final class NaverCuratedFallbackQueries {

    public static final List<String> QUERIES = List.of(
            "삼성전자 주가",
            "SK하이닉스 주가",
            "코스피 외국인",
            "코스피 하락",
            "코스피 상승",
            "원달러 환율",
            "환율 상승",
            "환율 하락",
            "뉴욕증시 마감",
            "나스닥 마감",
            "반도체 주가",
            "삼성전자 실적",
            "SK하이닉스 HBM",
            "코스닥 하락",
            "코스닥 상승",
            "코스피 기관",
            "국제유가 상승",
            "국제유가 하락",
            "2차전지 주가",
            "방산주",
            "조선주",
            "전력기기"
    );

    private NaverCuratedFallbackQueries() {
    }
}

package com.example.macronews.service.news.query;

import java.util.List;

/**
 * A single web-search-derived market issue and the short Korean Naver search queries it maps to.
 *
 * <p>Every retained seed must carry at least one source URL and one evidence title; seeds without
 * supporting evidence are dropped during validation so the pipeline never acts on an unsourced,
 * model-invented "issue". The {@code naverQueries} are plain news search terms, NOT investment advice.
 *
 * @param topicFamily   coarse macro/sector family (for example "반도체", "환율", "금리"); must be non-blank
 * @param issue         one-line Korean issue description; must be non-blank
 * @param naverQueries  short (1-3 token) Korean Naver search queries, OR-syntax free, de-duplicated
 * @param confidence    model confidence in [0,1]; seeds below the configured threshold are dropped
 * @param evidenceTitles supporting article/source titles; a seed with none is dropped
 * @param sourceUrls    supporting source URLs; a seed with none is dropped
 */
public record MarketIssueSeed(
        String topicFamily,
        String issue,
        List<String> naverQueries,
        double confidence,
        List<String> evidenceTitles,
        List<String> sourceUrls) {
}

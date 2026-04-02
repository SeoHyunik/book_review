package com.example.macronews.service.news.source;

import java.util.Comparator;

final class ProviderRankingPolicy {

    Comparator<NewsSourceProvider> providerComparator(NewsFeedPriority priority) {
        return Comparator.comparingInt((NewsSourceProvider provider) -> providerOrder(priority, provider))
                .thenComparing(NewsSourceProvider::sourceCode);
    }

    private int providerOrder(NewsFeedPriority priority, NewsSourceProvider provider) {
        String code = provider.sourceCode();
        if (priority == NewsFeedPriority.DOMESTIC) {
            return "naver".equals(code) ? 0 : 10;
        }
        if ("newsapi-global".equals(code)) {
            return 0;
        }
        if ("gnews-global".equals(code)) {
            return 1;
        }
        return 10;
    }
}

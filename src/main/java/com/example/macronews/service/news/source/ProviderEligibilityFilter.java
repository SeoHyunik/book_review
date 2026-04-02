package com.example.macronews.service.news.source;

import java.util.List;
import java.util.Optional;

final class ProviderEligibilityFilter {

    List<NewsSourceProvider> selectConfiguredProviders(List<NewsSourceProvider> providers,
            NewsFeedPriority priority,
            ProviderRankingPolicy rankingPolicy) {
        return providers.stream()
                .filter(provider -> provider.supports(priority))
                .filter(NewsSourceProvider::isConfigured)
                .sorted(rankingPolicy.providerComparator(priority))
                .toList();
    }

    Optional<NewsSourceProvider> selectConfiguredProvider(List<NewsSourceProvider> providers,
            NewsFeedPriority priority,
            ProviderRankingPolicy rankingPolicy) {
        return selectConfiguredProviders(providers, priority, rankingPolicy).stream()
                .findFirst();
    }
}

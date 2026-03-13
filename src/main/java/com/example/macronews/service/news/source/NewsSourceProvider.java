package com.example.macronews.service.news.source;

import com.example.macronews.dto.external.ExternalNewsItem;
import java.util.List;

public interface NewsSourceProvider {

    String sourceCode();

    boolean supports(NewsFeedPriority priority);

    List<ExternalNewsItem> fetchTopHeadlines(int limit);

    boolean isConfigured();
}

package com.example.macronews.service.news;

import com.example.macronews.dto.external.ExternalNewsItem;
import java.util.List;
import java.util.Optional;

public interface NewsApiService {

    List<ExternalNewsItem> fetchTopHeadlines(int limit);

    Optional<ExternalNewsItem> fetchByUrl(String url);
}
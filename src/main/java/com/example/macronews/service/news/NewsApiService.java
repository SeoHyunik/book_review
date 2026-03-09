package com.example.macronews.service.news;

import com.example.macronews.dto.internal.ExternalNewsItem;
import java.util.List;

public interface NewsApiService {

    List<ExternalNewsItem> fetchLatestNews(int pageSize);
}
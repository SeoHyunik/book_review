package com.example.bookreview.service.news;

import com.example.bookreview.dto.internal.ExternalNewsItem;
import java.util.List;

public interface NewsApiService {

    List<ExternalNewsItem> fetchLatestNews(int pageSize);
}
package com.example.macronews.service.news;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.AdminIngestionRequest;
import java.util.List;

public interface NewsIngestionService {

    NewsEvent ingestExternalItem(ExternalNewsItem item);

    List<NewsEvent> ingestTopHeadlines(int limit);

    NewsEvent ingestManual(AdminIngestionRequest request);

    boolean deleteById(String id);
}

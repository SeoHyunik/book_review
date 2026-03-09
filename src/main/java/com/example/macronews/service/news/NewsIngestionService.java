package com.example.macronews.service.news;

import com.example.macronews.dto.request.AdminIngestionRequest;

public interface NewsIngestionService {

    String ingestOne(AdminIngestionRequest request);

    int ingestLatestFromApi(int pageSize);
}
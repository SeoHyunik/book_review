package com.example.bookreview.service.news;

import com.example.bookreview.dto.request.AdminIngestionRequest;

public interface NewsIngestionService {

    String ingestOne(AdminIngestionRequest request);
}


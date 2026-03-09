package com.example.macronews.service.news;

import com.example.macronews.dto.domain.NewsEvent;
import com.example.macronews.repository.NewsEventRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewsQueryService {

    private final NewsEventRepository newsEventRepository;

    public List<NewsEvent> getRecentNews() {
        return newsEventRepository.findTop20ByOrderByIngestedAtDesc();
    }

    public Optional<NewsEvent> getNewsDetail(String id) {
        return newsEventRepository.findById(id);
    }
}


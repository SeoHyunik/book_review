package com.example.macronews.repository;

import com.example.macronews.dto.domain.NewsEvent;
import com.example.macronews.dto.domain.NewsStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsEventRepository extends MongoRepository<NewsEvent, String> {

    List<NewsEvent> findTop20ByOrderByPublishedAtDesc();

    List<NewsEvent> findByStatus(NewsStatus status);

    Optional<NewsEvent> findByUrl(String url);

    // Existing scaffold compatibility methods
    Optional<NewsEvent> findByExternalId(String externalId);

    List<NewsEvent> findTop20ByStatusOrderByPublishedAtDesc(NewsStatus status);

    List<NewsEvent> findTop20ByOrderByIngestedAtDesc();
}
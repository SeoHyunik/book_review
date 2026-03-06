package com.example.bookreview.repository;

import com.example.bookreview.dto.domain.NewsEvent;
import com.example.bookreview.dto.domain.NewsStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsEventRepository extends MongoRepository<NewsEvent, String> {

    Optional<NewsEvent> findByExternalId(String externalId);

    List<NewsEvent> findTop20ByStatusOrderByPublishedAtDesc(NewsStatus status);

    List<NewsEvent> findTop20ByOrderByIngestedAtDesc();
}


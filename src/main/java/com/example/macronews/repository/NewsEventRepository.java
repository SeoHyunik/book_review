package com.example.macronews.repository;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsEventRepository extends MongoRepository<NewsEvent, String> {

    List<NewsEvent> findTop20ByOrderByPublishedAtDesc();

    List<NewsEvent> findTop20ByOrderByIngestedAtDesc();

    List<NewsEvent> findByStatus(NewsStatus status);

    List<NewsEvent> findByIngestedAtBefore(Instant cutoff);

    Optional<NewsEvent> findByUrl(String url);

    Optional<NewsEvent> findByExternalId(String externalId);
}

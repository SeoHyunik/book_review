package com.example.macronews.repository;

import com.example.macronews.domain.MarketSummarySnapshot;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketSummarySnapshotRepository extends MongoRepository<MarketSummarySnapshot, String> {

    Optional<MarketSummarySnapshot> findTopByValidTrueOrderByGeneratedAtDesc();
}

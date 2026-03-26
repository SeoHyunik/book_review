package com.example.macronews.repository;

import com.example.macronews.domain.OpenAiUsageRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpenAiUsageRecordRepository extends MongoRepository<OpenAiUsageRecord, String> {

    Page<OpenAiUsageRecord> findAllByOrderByTimestampDesc(Pageable pageable);

    List<OpenAiUsageRecord> findByTimestampGreaterThanEqualOrderByTimestampDesc(Instant timestamp);
}

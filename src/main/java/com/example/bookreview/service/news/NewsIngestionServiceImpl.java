package com.example.bookreview.service.news;

import com.example.bookreview.dto.domain.NewsEvent;
import com.example.bookreview.dto.domain.NewsStatus;
import com.example.bookreview.dto.request.AdminIngestionRequest;
import com.example.bookreview.repository.NewsEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsIngestionServiceImpl implements NewsIngestionService {

    private static final String DEFAULT_INGESTED_BY = "admin-manual";

    private final NewsEventRepository newsEventRepository;

    @Override
    @Transactional
    public String ingestOne(AdminIngestionRequest request) {
        String externalId = buildExternalId(request);
        log.info("Starting news ingestion: source='{}', title='{}', externalId='{}'", request.source(),
                request.title(), externalId);

        return newsEventRepository.findByExternalId(externalId)
                .map(original -> {
                    log.info("Duplicate detected for externalId='{}', originalId={}", externalId,
                            original.id());
                    NewsEvent duplicate = NewsEvent.builder()
                            .externalId(externalId)
                            .source(request.source())
                            .title(request.title())
                            .summary(request.summary())
                            .content(request.content())
                            .url(request.url())
                            .publishedAt(request.publishedAt())
                            .ingestedAt(LocalDateTime.now())
                            .status(NewsStatus.DUPLICATE)
                            .duplicateOfId(original.id())
                            .ingestedBy(resolveIngestedBy(request.ingestedBy()))
                            .build();
                    NewsEvent saved = newsEventRepository.save(duplicate);
                    log.info("NewsEvent saved as DUPLICATE with id={}", saved.id());
                    return saved.id();
                })
                .orElseGet(() -> {
                    NewsEvent ingested = NewsEvent.builder()
                            .externalId(externalId)
                            .source(request.source())
                            .title(request.title())
                            .summary(request.summary())
                            .content(request.content())
                            .url(request.url())
                            .publishedAt(request.publishedAt())
                            .ingestedAt(LocalDateTime.now())
                            .status(NewsStatus.INGESTED)
                            .ingestedBy(resolveIngestedBy(request.ingestedBy()))
                            .build();
                    NewsEvent saved = newsEventRepository.save(ingested);
                    log.info("NewsEvent saved as INGESTED with id={}", saved.id());
                    return saved.id();
                });
    }

    private String resolveIngestedBy(String ingestedBy) {
        if (ingestedBy == null || ingestedBy.isBlank()) {
            return DEFAULT_INGESTED_BY;
        }
        return ingestedBy;
    }

    private String buildExternalId(AdminIngestionRequest request) {
        String seed = (request.source() + "|" + request.title() + "|" + request.publishedAt()).trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}


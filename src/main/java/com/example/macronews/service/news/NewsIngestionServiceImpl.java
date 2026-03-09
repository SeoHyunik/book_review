package com.example.macronews.service.news;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.dto.external.ExternalNewsItem;
import com.example.macronews.dto.request.AdminIngestionRequest;
import com.example.macronews.repository.NewsEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsIngestionServiceImpl implements NewsIngestionService {

    private final NewsEventRepository newsEventRepository;
    private final NewsApiService newsApiService;

    @Override
    @Transactional
    public NewsEvent ingestExternalItem(ExternalNewsItem item) {
        if (item == null) {
            throw new IllegalArgumentException("ExternalNewsItem must not be null");
        }

        String resolvedExternalId = resolveExternalId(item);
        Optional<NewsEvent> existing = findDuplicate(item, resolvedExternalId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        NewsEvent event = new NewsEvent(
                null,
                resolvedExternalId,
                defaultText(item.title(), "Untitled"),
                defaultText(item.summary(), ""),
                defaultText(item.source(), "External"),
                defaultText(item.url(), ""),
                item.publishedAt() == null ? now : item.publishedAt(),
                now,
                NewsStatus.INGESTED,
                null
        );

        return newsEventRepository.save(event);
    }

    @Override
    @Transactional
    public List<NewsEvent> ingestTopHeadlines(int limit) {
        List<ExternalNewsItem> externalItems = newsApiService.fetchTopHeadlines(limit);
        List<NewsEvent> results = new ArrayList<>();
        for (ExternalNewsItem item : externalItems) {
            results.add(ingestExternalItem(item));
        }
        return results;
    }

    @Override
    @Transactional
    public NewsEvent ingestManual(AdminIngestionRequest request) {
        ExternalNewsItem item = new ExternalNewsItem(
                null,
                request.source(),
                request.title(),
                request.summary(),
                request.url(),
                toInstant(request.publishedAt())
        );
        return ingestExternalItem(item);
    }

    private Optional<NewsEvent> findDuplicate(ExternalNewsItem item, String resolvedExternalId) {
        if (StringUtils.hasText(resolvedExternalId)) {
            Optional<NewsEvent> byExternalId = newsEventRepository.findByExternalId(resolvedExternalId);
            if (byExternalId.isPresent()) {
                return byExternalId;
            }
        }

        if (StringUtils.hasText(item.url())) {
            Optional<NewsEvent> byUrl = newsEventRepository.findByUrl(item.url());
            if (byUrl.isPresent()) {
                return byUrl;
            }
        }

        return Optional.empty();
    }

    private String resolveExternalId(ExternalNewsItem item) {
        if (StringUtils.hasText(item.externalId())) {
            return item.externalId().trim();
        }
        String seed = defaultText(item.source(), "") + "|"
                + defaultText(item.title(), "") + "|"
                + (item.publishedAt() == null ? "" : item.publishedAt().toString());
        return sha256(seed);
    }

    private String sha256(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
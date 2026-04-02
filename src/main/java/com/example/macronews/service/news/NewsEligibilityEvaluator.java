package com.example.macronews.service.news;

import com.example.macronews.domain.NewsEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class NewsEligibilityEvaluator {

    boolean isDisplayEligible(
            NewsEvent event,
            Clock clock,
            long naverMaxAgeHours,
            long globalMaxAgeHours,
            long naverFallbackMaxAgeHours,
            long globalFallbackMaxAgeHours
    ) {
        Instant basis = resolveDisplayBasis(event);
        if (basis == null) {
            return false;
        }
        return !basis.isBefore(Instant.now(clock).minus(resolveDisplayMaxAge(event,
                naverMaxAgeHours, globalMaxAgeHours, naverFallbackMaxAgeHours, globalFallbackMaxAgeHours)));
    }

    boolean isSignalEligible(
            NewsEvent event,
            Clock clock,
            long naverMaxAgeHours,
            long globalMaxAgeHours,
            long naverFallbackMaxAgeHours,
            long globalFallbackMaxAgeHours
    ) {
        if (event == null || event.status() != com.example.macronews.domain.NewsStatus.ANALYZED || event.analysisResult() == null) {
            return false;
        }
        Instant basis = resolveSignalBasis(event);
        if (basis == null) {
            return false;
        }
        return !basis.isBefore(Instant.now(clock).minus(resolveDisplayMaxAge(event,
                naverMaxAgeHours, globalMaxAgeHours, naverFallbackMaxAgeHours, globalFallbackMaxAgeHours)));
    }

    long resolveMaxDisplayHours(long naverMaxAgeHours, long globalMaxAgeHours, long naverFallbackMaxAgeHours, long globalFallbackMaxAgeHours) {
        return Math.max(resolveDisplayHours(true, naverMaxAgeHours, globalMaxAgeHours, naverFallbackMaxAgeHours, globalFallbackMaxAgeHours),
                resolveDisplayHours(false, naverMaxAgeHours, globalMaxAgeHours, naverFallbackMaxAgeHours, globalFallbackMaxAgeHours));
    }

    private Instant resolveDisplayBasis(NewsEvent event) {
        if (event == null) {
            return null;
        }
        if (event.ingestedAt() != null) {
            return event.ingestedAt();
        }
        return event.publishedAt();
    }

    private Instant resolveSignalBasis(NewsEvent event) {
        if (event == null) {
            return null;
        }
        if (event.analysisResult() != null && event.analysisResult().createdAt() != null) {
            return event.analysisResult().createdAt();
        }
        if (event.ingestedAt() != null) {
            return event.ingestedAt();
        }
        return event.publishedAt();
    }

    private Duration resolveDisplayMaxAge(
            NewsEvent event,
            long naverMaxAgeHours,
            long globalMaxAgeHours,
            long naverFallbackMaxAgeHours,
            long globalFallbackMaxAgeHours
    ) {
        String source = event == null ? "" : normalize(event.source());
        boolean naver = "naver".equals(source);
        return Duration.ofHours(resolveDisplayHours(naver,
                naverMaxAgeHours, globalMaxAgeHours, naverFallbackMaxAgeHours, globalFallbackMaxAgeHours));
    }

    private long resolveDisplayHours(
            boolean naver,
            long naverMaxAgeHours,
            long globalMaxAgeHours,
            long naverFallbackMaxAgeHours,
            long globalFallbackMaxAgeHours
    ) {
        long primaryHours = naver ? naverMaxAgeHours : globalMaxAgeHours;
        long fallbackHours = naver ? naverFallbackMaxAgeHours : globalFallbackMaxAgeHours;
        long defaultHours = naver ? 12L : 24L;
        if (primaryHours > 0) {
            return primaryHours;
        }
        if (fallbackHours > 0) {
            return fallbackHours;
        }
        return defaultHours;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}

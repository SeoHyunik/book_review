package com.example.macronews.service.news;

import com.example.macronews.domain.NewsEvent;
import java.util.List;

/**
 * Duplicate-aware result of a batch ingestion run.
 *
 * <p>Distinguishes articles that were newly persisted this run from ones that were already stored
 * (duplicates), so callers can tell a productive run from one that only re-observed existing items.
 * {@link #events()} preserves the previous {@code List<NewsEvent>} return contract for callers that
 * only need the resulting events.
 */
public record NewsIngestionSummary(
        int requested,
        int selected,
        int newlyPersisted,
        int duplicates,
        int submittedForAnalysis,
        List<NewsEvent> events
) {
    public NewsIngestionSummary {
        events = events == null ? List.of() : List.copyOf(events);
    }

    /** Number of events returned to the caller (newly persisted + duplicates). */
    public int returned() {
        return events.size();
    }
}

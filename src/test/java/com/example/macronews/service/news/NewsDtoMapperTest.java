package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsDtoMapperTest {

    private final NewsDtoMapper newsDtoMapper = new NewsDtoMapper(
            new NewsScoringPolicy(),
            new NewsTranslationSelector());

    @Test
    @DisplayName("NAVER item with recognized publisher URL should render combined source label")
    void toListItem_combinesNaverSourceWithPublisherLabelWhenAvailable() {
        NewsEvent event = new NewsEvent(
                "news-1",
                null,
                "KOSPI rises",
                "Market breadth improves.",
                "NAVER",
                "https://www.khan.co.kr/economy/article",
                Instant.parse("2026-03-17T02:10:00Z"),
                Instant.parse("2026-03-17T02:15:00Z"),
                NewsStatus.ANALYZED,
                null,
                null,
                null
        );

        var item = newsDtoMapper.toListItem(event);

        assertThat(item.displaySource()).isEqualTo("NAVER-경향");
    }

    @Test
    @DisplayName("NAVER item without publisher detail should fall back to coarse source label")
    void toListItem_fallsBackToCoarseSourceWhenPublisherLabelMissing() {
        NewsEvent event = new NewsEvent(
                "news-2",
                null,
                "KOSPI rises",
                "Market breadth improves.",
                "NAVER",
                "https://example.com/article",
                Instant.parse("2026-03-17T02:10:00Z"),
                Instant.parse("2026-03-17T02:15:00Z"),
                NewsStatus.ANALYZED,
                null,
                null,
                null
        );

        var item = newsDtoMapper.toListItem(event);

        assertThat(item.displaySource()).isEqualTo("NAVER");
    }
}

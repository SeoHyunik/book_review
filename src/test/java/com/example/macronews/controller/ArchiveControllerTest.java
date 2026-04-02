package com.example.macronews.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;

@ExtendWith(MockitoExtension.class)
class ArchiveControllerTest {

    @Mock
    private NewsQueryService newsQueryService;

    @InjectMocks
    private ArchiveController archiveController;

    @Test
    @DisplayName("list should render recent analyzed news in reverse chronological order")
    void givenAnalyzedNews_whenList_thenRenderArchivePageWithSeoMetadataAndItems() {
        NewsListItemDto first = archiveItem("archive-1", "Latest headline", Instant.parse("2026-03-17T03:00:00Z"));
        NewsListItemDto second = archiveItem("archive-2", "Older headline", Instant.parse("2026-03-17T02:00:00Z"));
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(first, second));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = archiveController.list(model);

        assertThat(viewName).isEqualTo("archive/list");
        assertThat(model.getAttribute("archiveItems")).isEqualTo(List.of(first, second));
        assertThat(model.getAttribute("archiveCount")).isEqualTo(2);
        assertThat(model.getAttribute("pageTitleKey")).isEqualTo("page.archive.title");
        assertThat(model.getAttribute("pageDescriptionKey")).isEqualTo("page.archive.description");
        assertThat(model.getAttribute("ogTitleKey")).isEqualTo("page.archive.title");
        assertThat(model.getAttribute("ogDescriptionKey")).isEqualTo("page.archive.description");
        assertThat(model.getAttribute("ogUrl")).isEqualTo("/archive");
    }

    @Test
    @DisplayName("list should fail open when archive lookup fails")
    void givenArchiveQueryFailure_whenList_thenRenderEmptyArchivePage() {
        willThrow(new RuntimeException("archive unavailable"))
                .given(newsQueryService)
                .getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC);

        ConcurrentModel model = new ConcurrentModel();
        String viewName = archiveController.list(model);

        assertThat(viewName).isEqualTo("archive/list");
        assertThat(model.getAttribute("archiveItems")).isEqualTo(List.of());
        assertThat(model.getAttribute("archiveCount")).isEqualTo(0);
    }

    private NewsListItemDto archiveItem(String id, String title, Instant publishedAt) {
        return new NewsListItemDto(
                id,
                title,
                title,
                "Reuters",
                publishedAt,
                publishedAt.plusSeconds(300),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.NEUTRAL,
                SignalSentiment.NEUTRAL,
                title,
                "Policy remains steady.",
                8
        );
    }
}

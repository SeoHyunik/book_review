package com.example.macronews.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.MarketSummaryDetailDto;
import com.example.macronews.dto.MarketSummarySupportingNewsDto;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class MarketSummaryControllerTest {

    @Mock
    private MarketSummarySnapshotService marketSummarySnapshotService;

    @InjectMocks
    private MarketSummaryController marketSummaryController;

    @Test
    @DisplayName("snapshot exists should load detail page")
    void detail_returnsDetailPageWhenSnapshotExists() {
        MarketSummaryDetailDto detail = new MarketSummaryDetailDto(
                "snapshot-1",
                Instant.parse("2026-03-17T03:00:00Z"),
                3,
                3,
                "headline ko",
                "headline en",
                "summary ko",
                "summary en",
                "view ko",
                "view en",
                SignalSentiment.POSITIVE,
                0.8d,
                List.of("USD", "Oil"),
                List.of(new MarketSummarySupportingNewsDto(
                        "news-1",
                        "KOSPI rises",
                        "Yonhap",
                        Instant.parse("2026-03-17T02:10:00Z"),
                        com.example.macronews.domain.ImpactDirection.UP,
                        SignalSentiment.POSITIVE
                ))
        );
        given(marketSummarySnapshotService.getSnapshotDetail("snapshot-1")).willReturn(Optional.of(detail));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = marketSummaryController.detail("snapshot-1", model, new RedirectAttributesModelMap());

        assertThat(viewName).isEqualTo("market-summary/detail");
        assertThat(model.getAttribute("marketSummaryDetail")).isEqualTo(detail);
    }

    @Test
    @DisplayName("missing snapshot should redirect safely")
    void detail_redirectsWhenSnapshotMissing() {
        given(marketSummarySnapshotService.getSnapshotDetail("missing")).willReturn(Optional.empty());

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String viewName = marketSummaryController.detail("missing", new ConcurrentModel(), redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/news");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
    }
}

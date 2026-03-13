package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NewsIngestionServiceImplTest {

    @Mock
    private NewsEventRepository newsEventRepository;

    @Mock
    private NewsSourceProviderSelector newsSourceProviderSelector;

    @Mock
    private MacroAiService macroAiService;

    @Mock
    private Executor ingestionExecutor;

    @InjectMocks
    private NewsIngestionServiceImpl newsIngestionService;

    @Test
    @DisplayName("deleteById should delete existing news item")
    void deleteById_deletesExistingItem() {
        given(newsEventRepository.existsById("news-1")).willReturn(true);

        boolean deleted = newsIngestionService.deleteById("news-1");

        assertThat(deleted).isTrue();
        verify(newsEventRepository).deleteById("news-1");
    }

    @Test
    @DisplayName("deleteById should return false when item is missing")
    void deleteById_returnsFalseWhenMissing() {
        given(newsEventRepository.existsById("missing-news")).willReturn(false);

        boolean deleted = newsIngestionService.deleteById("missing-news");

        assertThat(deleted).isFalse();
        verify(newsEventRepository, never()).deleteById("missing-news");
    }

    @Test
    @DisplayName("ingestTopHeadlines should delegate headline loading to the selector")
    void ingestTopHeadlines_delegatesHeadlineLoadingToSelector() {
        given(newsSourceProviderSelector.fetchTopHeadlines(3)).willReturn(java.util.List.of());

        newsIngestionService.ingestTopHeadlines(3);

        verify(newsSourceProviderSelector).fetchTopHeadlines(3);
        verify(newsEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

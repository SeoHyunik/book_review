package com.example.macronews.service.macro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.util.ExternalApiResult;
import com.example.macronews.util.ExternalApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MacroAiServiceImplTest {

    @Mock
    private ExternalApiUtils externalApiUtils;

    @Mock
    private NewsEventRepository newsEventRepository;

    private MacroAiServiceImpl macroAiService;

    @BeforeEach
    void setUp() {
        macroAiService = new MacroAiServiceImpl(externalApiUtils, new ObjectMapper(), newsEventRepository);
        ReflectionTestUtils.setField(macroAiService, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(macroAiService, "openAiUrl", "https://example.com/openai");
        ReflectionTestUtils.setField(macroAiService, "openAiModel", "gpt-test");
        ReflectionTestUtils.setField(macroAiService, "macroPromptFile", new ByteArrayResource(("{\"messages\":[{\"role\":\"system\",\"content\":\"Return JSON\"},{\"role\":\"user\",\"template\":\"Title: {{title}}\"}]}" ).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("interpret should parse localized summaries when both are present")
    void interpret_parsesLocalizedSummaries() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summaryKo\\\":\\\"한국어 요약\\\",\\\"summaryEn\\\":\\\"English summary\\\",\\\"macroImpacts\\\":[],\\\"marketImpacts\\\":[]}\"}}]}"));

        var result = macroAiService.interpret(sampleEvent());

        assertThat(result.summaryKo()).isEqualTo("한국어 요약");
        assertThat(result.summaryEn()).isEqualTo("English summary");
        assertThat(result.macroImpacts()).isEmpty();
        assertThat(result.marketImpacts()).isEmpty();
    }

    @Test
    @DisplayName("interpret should allow one localized summary to be missing")
    void interpret_allowsMissingLocalizedSummary() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summaryEn\\\":\\\"English only summary\\\",\\\"macroImpacts\\\":[],\\\"marketImpacts\\\":[]}\"}}]}"));

        var result = macroAiService.interpret(sampleEvent());

        assertThat(result.summaryKo()).isNull();
        assertThat(result.summaryEn()).isEqualTo("English only summary");
    }

    private NewsEvent sampleEvent() {
        return new NewsEvent(
                "news-1",
                null,
                "KOSPI rises on chip demand",
                "Samsung shares gained on export optimism.",
                "Yonhap",
                "https://example.com/news-1",
                Instant.parse("2026-03-10T09:00:00Z"),
                Instant.parse("2026-03-10T09:05:00Z"),
                NewsStatus.INGESTED,
                null
        );
    }
}

package com.example.macronews.service.macro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.openai.OpenAiUsageLoggingService;
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

    @Mock
    private OpenAiUsageLoggingService openAiUsageLoggingService;

    private MacroAiServiceImpl macroAiService;

    @BeforeEach
    void setUp() {
        macroAiService = new MacroAiServiceImpl(externalApiUtils, new ObjectMapper(), newsEventRepository, openAiUsageLoggingService);
        ReflectionTestUtils.setField(macroAiService, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(macroAiService, "openAiUrl", "https://example.com/openai");
        ReflectionTestUtils.setField(macroAiService, "openAiModel", "gpt-test");
        ReflectionTestUtils.setField(macroAiService, "macroPromptFile", new ByteArrayResource((
                "{\"messages\":[{\"role\":\"system\",\"content\":\"Return JSON\"},{\"role\":\"user\",\"template\":\"Title: {{title}}\"}]}")
                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("interpret should parse localized headlines and summaries when both are present")
    void interpret_parsesLocalizedSummaries() {
        String response = "{\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200},\"choices\":[{\"message\":{\"content\":\"{\\\"headlineKo\\\":\\\"Korean headline\\\",\\\"headlineEn\\\":\\\"English headline\\\",\\\"summaryKo\\\":\\\"Korean summary\\\",\\\"summaryEn\\\":\\\"English summary\\\",\\\"macroImpacts\\\":[],\\\"marketImpacts\\\":[]}\"}}]}";
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200, response));

        var result = macroAiService.interpret(sampleEvent());

        assertThat(result.headlineKo()).isEqualTo("Korean headline");
        assertThat(result.headlineEn()).isEqualTo("English headline");
        assertThat(result.summaryKo()).isEqualTo("Korean summary");
        assertThat(result.summaryEn()).isEqualTo("English summary");
        assertThat(result.macroImpacts()).isEmpty();
        assertThat(result.marketImpacts()).isEmpty();
        verify(openAiUsageLoggingService).recordUsage(any(), any(), any());
    }

    @Test
    @DisplayName("interpret should allow one localized headline or summary to be missing")
    void interpret_allowsMissingLocalizedSummary() {
        given(externalApiUtils.callAPI(any())).willReturn(new ExternalApiResult(200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"headlineEn\\\":\\\"English only headline\\\",\\\"summaryEn\\\":\\\"English only summary\\\",\\\"macroImpacts\\\":[],\\\"marketImpacts\\\":[]}\"}}]}"));

        var result = macroAiService.interpret(sampleEvent());

        assertThat(result.headlineKo()).isNull();
        assertThat(result.headlineEn()).isEqualTo("English only headline");
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

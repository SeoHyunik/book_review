package com.example.macronews.service.macro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.openai.OpenAiUsageLoggingService;
import com.example.macronews.util.ExternalApiResult;
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
    private MacroAiPromptBuilder macroAiPromptBuilder;

    @Mock
    private MacroAiClient macroAiClient;

    @Mock
    private MacroAiResponseParser macroAiResponseParser;

    @Mock
    private NewsEventRepository newsEventRepository;

    @Mock
    private OpenAiUsageLoggingService openAiUsageLoggingService;

    private MacroAiServiceImpl macroAiService;

    @BeforeEach
    void setUp() {
        macroAiService = new MacroAiServiceImpl(
                macroAiPromptBuilder,
                macroAiClient,
                macroAiResponseParser,
                newsEventRepository,
                openAiUsageLoggingService
        );
        ReflectionTestUtils.setField(macroAiService, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(macroAiService, "openAiUrl", "https://example.com/openai");
        ReflectionTestUtils.setField(macroAiService, "interpretationModel", "gpt-test");
        ReflectionTestUtils.setField(macroAiService, "openAiMaxTokens", 800);
        ReflectionTestUtils.setField(macroAiService, "openAiTemperature", 0.2d);
        ReflectionTestUtils.setField(macroAiService, "macroPromptFile", new ByteArrayResource((
                "{\"messages\":[{\"role\":\"system\",\"content\":\"Return JSON\"},{\"role\":\"user\",\"template\":\"Title: {{title}}\"}]}")
                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("interpret should parse localized headlines and summaries when both are present")
    void givenLocalizedSummaries_whenInterpret_thenParsesLocalizedSummaries() {
        String response = "{\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200},\"choices\":[{\"message\":{\"content\":\"{\\\"headlineKo\\\":\\\"Korean headline\\\",\\\"headlineEn\\\":\\\"English headline\\\",\\\"summaryKo\\\":\\\"Korean summary\\\",\\\"summaryEn\\\":\\\"English summary\\\",\\\"macroImpacts\\\":[],\\\"marketImpacts\\\":[]}\"}}]}";
        given(macroAiPromptBuilder.buildPayload(any(), any(), anyInt(), anyDouble(), any())).willReturn("payload");
        given(macroAiClient.call(any(), any(), any())).willReturn(new ExternalApiResult(200, response));
        given(macroAiResponseParser.parseAnalysisResult(any(), any())).willReturn(new com.example.macronews.domain.AnalysisResult(
                "gpt-test",
                Instant.parse("2026-03-10T10:00:00Z"),
                "Korean headline",
                "English headline",
                "Korean summary",
                "English summary",
                java.util.List.of(),
                java.util.List.of()
        ));

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
    void givenMissingLocalizedFields_whenInterpret_thenAllowsMissingLocalizedSummary() {
        given(macroAiPromptBuilder.buildPayload(any(), any(), anyInt(), anyDouble(), any())).willReturn("payload");
        given(macroAiClient.call(any(), any(), any())).willReturn(new ExternalApiResult(200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"headlineEn\\\":\\\"English only headline\\\",\\\"summaryEn\\\":\\\"English only summary\\\",\\\"macroImpacts\\\":[],\\\"marketImpacts\\\":[]}\"}}]}"));
        given(macroAiResponseParser.parseAnalysisResult(any(), any())).willReturn(new com.example.macronews.domain.AnalysisResult(
                "gpt-test",
                Instant.parse("2026-03-10T10:00:00Z"),
                null,
                "English only headline",
                null,
                "English only summary",
                java.util.List.of(),
                java.util.List.of()
        ));

        var result = macroAiService.interpret(sampleEvent());

        assertThat(result.headlineKo()).isNull();
        assertThat(result.headlineEn()).isEqualTo("English only headline");
        assertThat(result.summaryKo()).isNull();
        assertThat(result.summaryEn()).isEqualTo("English only summary");
    }

    @Test
    @DisplayName("interpretAndSave should initialize retry metadata on initial failure")
    void givenInterpretationFailure_whenInterpretAndSave_thenInitializesRetryMetadata() {
        NewsEvent event = sampleEvent();
        given(newsEventRepository.findById("news-1")).willReturn(java.util.Optional.of(event));
        given(macroAiPromptBuilder.buildPayload(any(), any(), anyInt(), anyDouble(), any())).willReturn("payload");
        given(macroAiClient.call(any(), any(), any())).willThrow(new IllegalStateException("boom"));
        given(newsEventRepository.save(any(NewsEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

        NewsEvent saved = macroAiService.interpretAndSave("news-1");

        assertThat(saved.status()).isEqualTo(NewsStatus.FAILED);
        assertThat(saved.analysisRetryCount()).isZero();
        assertThat(saved.analysisLastAttemptAt()).isNotNull();
        verify(newsEventRepository).save(argThat(news ->
                news.status() == NewsStatus.FAILED
                        && news.analysisRetryCount() != null
                        && news.analysisRetryCount() == 0
                        && news.analysisLastAttemptAt() != null));
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
                null,
                null,
                null
        );
    }
}

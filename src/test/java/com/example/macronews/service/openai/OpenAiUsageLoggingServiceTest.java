package com.example.macronews.service.openai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.macronews.domain.OpenAiUsageRecord;
import com.example.macronews.repository.OpenAiUsageRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAiUsageLoggingServiceTest {

    @Mock
    private OpenAiUsageRecordRepository openAiUsageRecordRepository;

    private OpenAiUsageLoggingService openAiUsageLoggingService;

    @BeforeEach
    void setUp() {
        openAiUsageLoggingService = new OpenAiUsageLoggingService(openAiUsageRecordRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("recordUsage should persist usage fields when response includes usage")
    void recordUsage_savesUsageRecord() {
        openAiUsageLoggingService.recordUsage(
                com.example.macronews.domain.OpenAiUsageFeatureType.MACRO_INTERPRETATION,
                "gpt-4o",
                "{\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":25,\"total_tokens\":125}}"
        );

        ArgumentCaptor<OpenAiUsageRecord> captor = ArgumentCaptor.forClass(OpenAiUsageRecord.class);
        verify(openAiUsageRecordRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().promptTokens()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().completionTokens()).isEqualTo(25);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().totalTokens()).isEqualTo(125);
    }

    @Test
    @DisplayName("recordUsage should skip persistence when usage fields are missing")
    void recordUsage_skipsWhenUsageMissing() {
        openAiUsageLoggingService.recordUsage(
                com.example.macronews.domain.OpenAiUsageFeatureType.MARKET_FORECAST,
                "gpt-4o",
                "{\"choices\":[]}"
        );

        verify(openAiUsageRecordRepository, never()).save(any());
    }
}

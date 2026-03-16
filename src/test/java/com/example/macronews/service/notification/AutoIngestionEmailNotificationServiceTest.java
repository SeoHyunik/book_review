package com.example.macronews.service.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.AutoIngestionControlStatusDto;
import com.example.macronews.dto.AutoIngestionRunOutcome;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutoIngestionEmailNotificationServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private ObjectProvider<JavaMailSender> javaMailSenderProvider;

    private AutoIngestionEmailNotificationService autoIngestionEmailNotificationService;

    @BeforeEach
    void setUp() {
        lenient().when(javaMailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        autoIngestionEmailNotificationService =
                new AutoIngestionEmailNotificationService(javaMailSenderProvider);
    }

    @Test
    @DisplayName("sendRunResult should skip when email notification is disabled")
    void sendRunResult_skipsWhenDisabled() {
        ReflectionTestUtils.setField(autoIngestionEmailNotificationService, "enabled", false);
        ReflectionTestUtils.setField(autoIngestionEmailNotificationService, "recipient", "ops@example.com");

        autoIngestionEmailNotificationService.sendRunResult(sampleControlStatus(AutoIngestionRunOutcome.COMPLETED),
                sampleBatchStatus());

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendRunResult should send concise batch summary email when enabled")
    void sendRunResult_sendsEmailWhenEnabled() {
        ReflectionTestUtils.setField(autoIngestionEmailNotificationService, "enabled", true);
        ReflectionTestUtils.setField(autoIngestionEmailNotificationService, "recipient", "ops@example.com");
        ReflectionTestUtils.setField(autoIngestionEmailNotificationService, "from", "mni@example.com");

        autoIngestionEmailNotificationService.sendRunResult(sampleControlStatus(AutoIngestionRunOutcome.COMPLETED),
                sampleBatchStatus());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(message.getTo()).containsExactly("ops@example.com");
        org.assertj.core.api.Assertions.assertThat(message.getFrom()).isEqualTo("mni@example.com");
        org.assertj.core.api.Assertions.assertThat(message.getSubject()).contains("completed");
        org.assertj.core.api.Assertions.assertThat(message.getText()).contains("Outcome: COMPLETED");
        org.assertj.core.api.Assertions.assertThat(message.getText()).contains("Requested: 3");
        org.assertj.core.api.Assertions.assertThat(message.getText()).contains("Failed: 1");
    }

    @Test
    @DisplayName("sendRunResult should not break ingestion flow when mail send fails")
    void sendRunResult_doesNotThrowWhenMailSendFails() {
        ReflectionTestUtils.setField(autoIngestionEmailNotificationService, "enabled", true);
        ReflectionTestUtils.setField(autoIngestionEmailNotificationService, "recipient", "ops@example.com");
        doThrow(new IllegalStateException("mail down")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> autoIngestionEmailNotificationService.sendRunResult(
                sampleControlStatus(AutoIngestionRunOutcome.FAILED), null)).doesNotThrowAnyException();
    }

    private AutoIngestionControlStatusDto sampleControlStatus(AutoIngestionRunOutcome outcome) {
        return new AutoIngestionControlStatusDto(
                true,
                false,
                outcome,
                Instant.parse("2026-03-16T01:00:00Z"),
                Instant.parse("2026-03-16T01:03:00Z"),
                3,
                2,
                1,
                0,
                1
        );
    }

    private AutoIngestionBatchStatusDto sampleBatchStatus() {
        return new AutoIngestionBatchStatusDto(3, 2, 2, 1, 1, 0, false, List.of());
    }
}
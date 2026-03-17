package com.example.macronews.service.notification;

import com.example.macronews.dto.AutoIngestionBatchStatusDto;
import com.example.macronews.dto.AutoIngestionControlStatusDto;
import com.example.macronews.dto.AutoIngestionRunOutcome;
import com.example.macronews.service.ops.OpsFeatureToggleService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class AutoIngestionEmailNotificationService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final OpsFeatureToggleService opsFeatureToggleService;

    @Value("${app.notification.email.enabled:false}")
    private boolean enabled;

    @Value("${app.notification.email.recipient:}")
    private String recipient;

    @Value("${app.notification.email.from:}")
    private String from;

    public AutoIngestionEmailNotificationService(
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            OpsFeatureToggleService opsFeatureToggleService) {
        this.javaMailSenderProvider = javaMailSenderProvider;
        this.opsFeatureToggleService = opsFeatureToggleService;
    }

    public boolean isConfigured() {
        return enabled;
    }

    public boolean isRuntimeEnabled() {
        return opsFeatureToggleService.isEmailNotificationRuntimeEnabled();
    }

    public boolean hasRecipient() {
        return StringUtils.hasText(recipient);
    }

    public boolean hasMailSender() {
        return javaMailSenderProvider.getIfAvailable() != null;
    }

    public boolean isEffectivelyEnabled() {
        return isConfigured() && isRuntimeEnabled() && hasRecipient() && hasMailSender();
    }

    public boolean isEnabled() {
        return isEffectivelyEnabled();
    }

    public void sendRunResult(AutoIngestionControlStatusDto controlStatus, AutoIngestionBatchStatusDto batchStatus) {
        if (!isConfigured()) {
            return;
        }
        if (!isRuntimeEnabled()) {
            log.debug("[MAIL] notification skipped reason=runtime-disabled");
            return;
        }
        if (!hasRecipient()) {
            return;
        }
        JavaMailSender mailSender = javaMailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.debug("[MAIL] notification skipped reason=mail-sender-unavailable");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient.trim());
            if (StringUtils.hasText(from)) {
                message.setFrom(from.trim());
            }
            message.setSubject(buildSubject(controlStatus));
            message.setText(buildBody(controlStatus, batchStatus));
            mailSender.send(message);
            log.info("[MAIL] automatic ingestion result notification sent outcome={} recipient={}",
                    controlStatus == null ? "UNKNOWN" : controlStatus.latestOutcome(), recipient);
        } catch (Exception ex) {
            log.warn("[MAIL] automatic ingestion notification failed recipient={}", recipient, ex);
        }
    }

    private String buildSubject(AutoIngestionControlStatusDto controlStatus) {
        AutoIngestionRunOutcome outcome = controlStatus == null || controlStatus.latestOutcome() == null
                ? AutoIngestionRunOutcome.IDLE
                : controlStatus.latestOutcome();
        return switch (outcome) {
            case COMPLETED -> "[MNI] Auto ingestion completed";
            case COMPLETED_WITH_FAILURES -> "[MNI] Auto ingestion completed with failures";
            case NO_RESULTS -> "[MNI] Auto ingestion returned no items";
            case FAILED -> "[MNI] Auto ingestion failed";
            case IN_PROGRESS -> "[MNI] Auto ingestion in progress";
            case IDLE -> "[MNI] Auto ingestion status";
        };
    }

    private String buildBody(AutoIngestionControlStatusDto controlStatus, AutoIngestionBatchStatusDto batchStatus) {
        StringBuilder body = new StringBuilder();
        body.append("Automatic ingestion run summary").append(System.lineSeparator()).append(System.lineSeparator());
        if (controlStatus != null) {
            body.append("Outcome: ").append(controlStatus.latestOutcome()).append(System.lineSeparator());
            if (controlStatus.latestStartedAt() != null) {
                body.append("Started At (KST): ")
                        .append(TIME_FORMATTER.format(controlStatus.latestStartedAt().atZone(BUSINESS_ZONE)))
                        .append(System.lineSeparator());
            }
            if (controlStatus.latestCompletedAt() != null) {
                body.append("Completed At (KST): ")
                        .append(TIME_FORMATTER.format(controlStatus.latestCompletedAt().atZone(BUSINESS_ZONE)))
                        .append(System.lineSeparator());
            }
        }
        if (batchStatus != null) {
            body.append("Requested: ").append(batchStatus.requestedCount()).append(System.lineSeparator());
            body.append("Returned: ").append(batchStatus.returnedCount()).append(System.lineSeparator());
            body.append("Ingested: ").append(batchStatus.ingestedCount()).append(System.lineSeparator());
            body.append("Analyzed: ").append(batchStatus.analyzedCount()).append(System.lineSeparator());
            body.append("Pending: ").append(batchStatus.pendingCount()).append(System.lineSeparator());
            body.append("Failed: ").append(batchStatus.failedCount()).append(System.lineSeparator());
        } else {
            body.append("Requested: ").append(controlStatus == null ? 0 : safe(controlStatus.latestRequestedCount()))
                    .append(System.lineSeparator());
            body.append("Returned: ").append(controlStatus == null ? 0 : safe(controlStatus.latestReturnedCount()))
                    .append(System.lineSeparator());
            body.append("Analyzed: ").append(controlStatus == null ? 0 : safe(controlStatus.latestAnalyzedCount()))
                    .append(System.lineSeparator());
            body.append("Pending: ").append(controlStatus == null ? 0 : safe(controlStatus.latestPendingCount()))
                    .append(System.lineSeparator());
            body.append("Failed: ").append(controlStatus == null ? 0 : safe(controlStatus.latestFailedCount()))
                    .append(System.lineSeparator());
        }
        return body.toString();
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}

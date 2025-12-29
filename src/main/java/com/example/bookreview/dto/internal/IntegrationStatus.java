package com.example.bookreview.dto.internal;

import org.springframework.util.StringUtils;

public record IntegrationStatus(
        Status openAiStatus,
        Status currencyStatus,
        Status driveStatus,
        String warningMessage) {

    public IntegrationStatus {
        openAiStatus = defaultStatus(openAiStatus);
        currencyStatus = defaultStatus(currencyStatus);
        driveStatus = defaultStatus(driveStatus);
        warningMessage = truncate(warningMessage);
    }

    private Status defaultStatus(Status status) {
        return status == null ? Status.SUCCESS : status;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    public enum Status {
        SUCCESS,
        FAILED,
        SKIPPED
    }
}

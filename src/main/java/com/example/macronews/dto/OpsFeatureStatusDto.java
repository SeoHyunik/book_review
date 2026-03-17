package com.example.macronews.dto;

public record OpsFeatureStatusDto(
        boolean configEnabled,
        boolean runtimeEnabled,
        boolean effectivelyEnabled,
        boolean detailConfigured,
        boolean mailSenderAvailable
) {
}

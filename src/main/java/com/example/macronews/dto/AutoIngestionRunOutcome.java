package com.example.macronews.dto;

public enum AutoIngestionRunOutcome {
    IDLE,
    IN_PROGRESS,
    COMPLETED,
    COMPLETED_WITH_FAILURES,
    NO_RESULTS,
    FAILED
}

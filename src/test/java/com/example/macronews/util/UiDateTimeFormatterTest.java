package com.example.macronews.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UiDateTimeFormatterTest {

    private final UiDateTimeFormatter formatter = new UiDateTimeFormatter();

    @Test
    @DisplayName("formats instant in KST without milliseconds")
    void formatKst_formatsInstantInKst() {
        String formatted = formatter.formatKst(Instant.parse("2026-03-19T01:02:03.456Z"));

        assertThat(formatted).isEqualTo("2026-03-19 10:02");
    }

    @Test
    @DisplayName("formats ISO instant string in KST without milliseconds")
    void formatKst_formatsIsoInstantStringInKst() {
        String formatted = formatter.formatKst("2026-03-19T01:02:03.456Z");

        assertThat(formatted).isEqualTo("2026-03-19 10:02");
    }

    @Test
    @DisplayName("returns dash for null or blank values")
    void formatKst_returnsDashForEmptyValues() {
        assertThat(formatter.formatKst((Instant) null)).isEqualTo("-");
        assertThat(formatter.formatKst(" ")).isEqualTo("-");
    }
}

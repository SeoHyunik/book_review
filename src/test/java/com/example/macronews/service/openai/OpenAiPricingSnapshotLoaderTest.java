package com.example.macronews.service.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;

class OpenAiPricingSnapshotLoaderTest {

    @Test
    @DisplayName("pricing snapshot loader should load repository managed JSON")
    void snapshot_loadsRepositoryJson() {
        OpenAiPricingSnapshotLoader loader = new OpenAiPricingSnapshotLoader(
                new ObjectMapper(),
                new ClassPathResource("pricing/openai-pricing.json")
        );

        OpenAiPricingSnapshot snapshot = loader.snapshot();

        assertThat(snapshot.version()).isEqualTo("2026-03-26");
        assertThat(snapshot.unit()).isEqualTo("per_1m_tokens");
        assertThat(snapshot.models().get("gpt-4o-mini").input()).isEqualByComparingTo("0.15");
        assertThat(snapshot.models().get("gpt-4o-mini").output()).isEqualByComparingTo("0.6");
        assertThat(snapshot.models().get("gpt-5.4").input()).isEqualByComparingTo("2.5");
        assertThat(snapshot.models().get("gpt-5.4-mini").output()).isEqualByComparingTo("4.5");
        assertThat(snapshot.models().get("gpt-5.4-nano").cachedInput()).isEqualByComparingTo("0.02");
        assertThat(snapshot.featureProfiles().get("market_summary").input()).isEqualByComparingTo("1.25");
        assertThat(snapshot.featureProfiles().get("market_forecast").output()).isEqualByComparingTo("15.0");
    }

    @Test
    @DisplayName("pricing snapshot loader should reject unsupported unit metadata")
    void snapshot_rejectsUnsupportedUnit() {
        ByteArrayResource invalidResource = new ByteArrayResource("""
                {
                  "version": "2026-03-26",
                  "source": {
                    "type": "manual_snapshot_from_official_pricing_page",
                    "verified_at": "2026-03-26",
                    "notes": "invalid unit"
                  },
                  "currency": "USD",
                  "unit": "per_1k_tokens",
                  "models": {
                    "gpt-4o-mini": {
                      "status": "active_in_app",
                      "source_status": "repository_config_verified",
                      "input": 0.15,
                      "cached_input": null,
                      "output": 0.6
                    }
                  },
                  "feature_profiles": {
                    "macro_interpretation": {
                      "model": "gpt-4o-mini",
                      "source_status": "repository_config_migrated",
                      "input": 0.15,
                      "cached_input": null,
                      "output": 0.6
                    }
                  }
                }
                """.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new OpenAiPricingSnapshotLoader(new ObjectMapper(), invalidResource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported OpenAI pricing snapshot unit");
    }
}

package com.example.macronews.service.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class OpenAiPricingSnapshotLoader {

    private final OpenAiPricingSnapshot snapshot;

    public OpenAiPricingSnapshotLoader(
            ObjectMapper objectMapper,
            @Value("classpath:pricing/openai-pricing.json") Resource pricingResource
    ) {
        this.snapshot = loadSnapshot(objectMapper, pricingResource);
    }

    public OpenAiPricingSnapshot snapshot() {
        return snapshot;
    }

    private OpenAiPricingSnapshot loadSnapshot(ObjectMapper objectMapper, Resource pricingResource) {
        try (InputStream inputStream = pricingResource.getInputStream()) {
            OpenAiPricingSnapshot loaded = objectMapper.readValue(inputStream, OpenAiPricingSnapshot.class);
            validateSnapshot(loaded);
            return loaded;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load OpenAI pricing snapshot", ex);
        }
    }

    private void validateSnapshot(OpenAiPricingSnapshot loaded) {
        if (loaded == null) {
            throw new IllegalStateException("OpenAI pricing snapshot was empty");
        }
        if (!StringUtils.hasText(loaded.version())) {
            throw new IllegalStateException("OpenAI pricing snapshot version is missing");
        }
        if (!"USD".equalsIgnoreCase(defaultText(loaded.currency()))) {
            throw new IllegalStateException("Unsupported OpenAI pricing snapshot currency=" + loaded.currency());
        }
        if (!"per_1m_tokens".equals(defaultText(loaded.unit()))) {
            throw new IllegalStateException("Unsupported OpenAI pricing snapshot unit=" + loaded.unit());
        }
        if (loaded.models() == null || loaded.models().isEmpty()) {
            throw new IllegalStateException("OpenAI pricing snapshot models are missing");
        }
        if (loaded.featureProfiles() == null || loaded.featureProfiles().isEmpty()) {
            log.warn("[OPENAI-PRICING] feature profiles missing from pricing snapshot");
        }
    }

    private String defaultText(String value) {
        return value == null ? "" : value.trim();
    }
}

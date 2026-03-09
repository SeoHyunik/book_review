package com.example.macronews.config.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.util.StringUtils;

@ReadingConverter
@Slf4j
public class LegacyConfidenceToDoubleConverter implements Converter<String, Double> {

    private static final double DEFAULT_CONFIDENCE = 0.5d;

    @Override
    public Double convert(String source) {
        if (!StringUtils.hasText(source)) {
            return DEFAULT_CONFIDENCE;
        }

        String normalized = source.trim().toUpperCase();
        return switch (normalized) {
            case "LOW" -> 0.3d;
            case "MEDIUM" -> 0.6d;
            case "HIGH" -> 0.9d;
            default -> {
                try {
                    yield Double.parseDouble(source.trim());
                } catch (NumberFormatException ex) {
                    log.warn("Unknown legacy confidence value '{}'. Falling back to default {}",
                            source, DEFAULT_CONFIDENCE);
                    yield DEFAULT_CONFIDENCE;
                }
            }
        };
    }
}
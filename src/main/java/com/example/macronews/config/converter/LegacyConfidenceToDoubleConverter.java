package com.example.macronews.config.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
@Slf4j
public class LegacyConfidenceToDoubleConverter implements Converter<Object, Double> {

    private static final double DEFAULT_CONFIDENCE = 0.5d;

    @Override
    public Double convert(Object source) {
        if (source == null) {
            return DEFAULT_CONFIDENCE;
        }

        if (source instanceof Number number) {
            return number.doubleValue();
        }

        if (source instanceof String value) {
            String normalized = value.trim().toUpperCase();
            return switch (normalized) {
                case "LOW" -> 0.3d;
                case "MEDIUM" -> 0.6d;
                case "HIGH" -> 0.9d;
                default -> {
                    try {
                        yield Double.parseDouble(value.trim());
                    } catch (NumberFormatException ex) {
                        log.warn("Unknown legacy confidence value '{}'. Falling back to default {}",
                                value, DEFAULT_CONFIDENCE);
                        yield DEFAULT_CONFIDENCE;
                    }
                }
            };
        }

        log.warn("Unsupported confidence source type '{}'. Falling back to default {}",
                source.getClass().getName(), DEFAULT_CONFIDENCE);
        return DEFAULT_CONFIDENCE;
    }
}
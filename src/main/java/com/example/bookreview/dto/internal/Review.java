package com.example.bookreview.dto.internal;

import com.example.bookreview.util.CurrencyFormatter;
import com.fasterxml.jackson.annotation.JsonGetter;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "reviews")
@Builder
public record Review(
        @Id String id,
        @NotBlank(message = "제목을 입력해주세요") String title,
        @NotBlank(message = "원본 독후감을 입력해주세요") String originalContent,
        String improvedContent,
        Long tokenCount,
        BigDecimal usdCost,
        BigDecimal krwCost,
        String googleFileId,
        LocalDateTime createdAt) {

    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    public Review {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @JsonGetter("formattedUsdCost")
    public String formattedUsdCost() {
        return CurrencyFormatter.formatUsd(usdCost);
    }

    @JsonGetter("formattedKrwCost")
    public String formattedKrwCost() {
        return CurrencyFormatter.formatKrw(krwCost);
    }

    @JsonGetter("formattedCreatedAt")
    public String formattedCreatedAt() {
        if (createdAt == null) {
            return "-";
        }
        return createdAt
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ASIA_SEOUL)
                .format(CREATED_AT_FORMATTER);
    }
}
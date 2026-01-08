package com.example.bookreview.dto.domain;

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
import com.example.bookreview.dto.internal.IntegrationStatus;

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
        String ownerUserId,
        IntegrationStatus integrationStatus,
        LocalDateTime createdAt) {

    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm");
    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    public Review {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        integrationStatus =
                integrationStatus == null ? new IntegrationStatus(null, null, null, null)
                        : integrationStatus;
        // 기존 문서는 ownerUserId가 없을 수 있으므로 null을 그대로 유지한다(마이그레이션 단계).
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
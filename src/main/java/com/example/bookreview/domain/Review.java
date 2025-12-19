package com.example.bookreview.domain;

import com.example.bookreview.util.CurrencyFormatter;
import com.fasterxml.jackson.annotation.JsonGetter;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
}
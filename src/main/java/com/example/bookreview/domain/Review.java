package com.example.bookreview.domain;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reviews")
public class Review {

    @Id
    private String id;

    @NotBlank(message = "제목을 입력해주세요")
    private String title;

    @NotBlank(message = "원본 독후감을 입력해주세요")
    private String originalContent;

    private String improvedContent;

    private Long tokenCount;

    private BigDecimal usdCost;

    private BigDecimal krwCost;

    private String googleFileId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

package com.example.bookreview.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
        @NotBlank(message = "제목을 입력해주세요") String title,
        @NotBlank(message = "원본 독후감을 입력해주세요") String originalContent) {

    public static ReviewRequest empty() {
        return new ReviewRequest("", "");
    }
}

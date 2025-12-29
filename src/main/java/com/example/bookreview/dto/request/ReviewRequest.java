package com.example.bookreview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
    @NotBlank(message = "{review.title.notBlank}")
    @Size(max = 100, message = "{review.title.size}")
    @Pattern(regexp = ReviewRequest.TITLE_FORBIDDEN_CHARS, message = "{review.title.invalidChars}")
    String title,

    @NotBlank(message = "{review.originalContent.notBlank}")
    @Size(max = 5000, message = "{review.originalContent.size}")
    String originalContent
) {
    public static final String TITLE_FORBIDDEN_CHARS = "^[^\\\\/:*?\"<>|#%]+$";

    /**
     * null-safe + 기존 DTO의 defaultValue 동작 유지
     */
    public ReviewRequest {
        title = defaultValue(title);
        originalContent = defaultValue(originalContent);
    }

    /**
     * 기존 코드 호환: ReviewRequest.empty()
     */
    public static ReviewRequest empty() {
        return new ReviewRequest("", "");
    }

    private static String defaultValue(String value) {
        return value == null ? "" : value;
    }

    /**
     * (선택) setter 대체용 - 기존 코드가 수정에 의존할 때 사용
     */
    public ReviewRequest withTitle(String title) {
        return new ReviewRequest(title, this.originalContent);
    }

    public ReviewRequest withOriginalContent(String originalContent) {
        return new ReviewRequest(this.title, originalContent);
    }
}

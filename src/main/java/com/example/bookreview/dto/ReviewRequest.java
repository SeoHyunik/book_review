package com.example.bookreview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReviewRequest {

    private static final String TITLE_FORBIDDEN_CHARS = "[^\\\\/:*?\"<>|#%]+";

    @NotBlank(message = "{review.title.notBlank}")
    @Size(max = 100, message = "{review.title.size}")
    @Pattern(regexp = TITLE_FORBIDDEN_CHARS, message = "{review.title.invalidChars}")
    private String title = "";

    @NotBlank(message = "{review.originalContent.notBlank}")
    @Size(max = 5000, message = "{review.originalContent.size}")
    private String originalContent = "";

    public ReviewRequest(String title, String originalContent) {
        this.title = defaultValue(title);
        this.originalContent = defaultValue(originalContent);
    }

    public static ReviewRequest empty() {
        return new ReviewRequest();
    }

    private String defaultValue(String value) {
        return value == null ? "" : value;
    }
}

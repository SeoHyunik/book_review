package com.example.bookreview.dto.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;

@Builder
public record DeleteReviewResult(
    boolean deleted,
    boolean driveDeleted,
    List<String> warnings
) {

    public DeleteReviewResult {
        if (warnings == null) {
            warnings = Collections.emptyList();
        }
    }

    // ===== Lombok @Getter 호환 =====
    public boolean getDeleted() {
        return deleted;
    }

    public boolean getDriveDeleted() {
        return driveDeleted;
    }

    // ===== 기존 getWarnings() 동작 완전 호환 =====
    public List<String> getWarnings() {
        return Collections.unmodifiableList(new ArrayList<>(warnings));
    }
}

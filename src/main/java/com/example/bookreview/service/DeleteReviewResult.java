package com.example.bookreview.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeleteReviewResult {

    private final boolean deleted;
    private final boolean driveDeleted;
    private final List<String> warnings;

    public List<String> getWarnings() {
        if (warnings == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(warnings));
    }
}

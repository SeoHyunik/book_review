package com.example.bookreview.dto.response;

import com.example.bookreview.dto.internal.DeleteReviewResult;
import java.util.List;

public record DeleteReviewResponse(boolean deleted, boolean driveDeleted, List<String> warnings) {

    public static DeleteReviewResponse from(DeleteReviewResult result) {
        return new DeleteReviewResponse(result.deleted(), result.driveDeleted(),
                result.getWarnings());
    }
}

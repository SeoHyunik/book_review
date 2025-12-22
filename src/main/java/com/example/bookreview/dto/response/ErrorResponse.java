package com.example.bookreview.dto.response;

import java.time.OffsetDateTime;

public record ErrorResponse(int status, String message, String path, boolean success, OffsetDateTime timestamp) {
}

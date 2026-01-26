package com.example.bookreview.dto.internal.openai;

import com.example.bookreview.util.ExternalApiError;

public record ErrorDescriptor(String type, String code, String param) {

    public static ErrorDescriptor from(ExternalApiError error) {
        if (error == null) {
            return new ErrorDescriptor(null, null, null);
        }
        return new ErrorDescriptor(error.type(), error.code(), error.param());
    }
}

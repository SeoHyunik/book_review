package com.example.bookreview.util;

public record ExternalApiError(String message, String type, String code, String param) {
}

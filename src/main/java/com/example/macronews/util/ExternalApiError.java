package com.example.macronews.util;

public record ExternalApiError(String message, String type, String code, String param) {
}

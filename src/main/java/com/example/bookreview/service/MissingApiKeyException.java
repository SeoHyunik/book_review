package com.example.bookreview.service;

/**
 * Signals that an OpenAI API key is missing. This is used to short-circuit outbound calls while
 * allowing callers to decide whether to fail fast or degrade gracefully.
 */
public class MissingApiKeyException extends RuntimeException {

    public MissingApiKeyException(String message) {
        super(message);
    }
}

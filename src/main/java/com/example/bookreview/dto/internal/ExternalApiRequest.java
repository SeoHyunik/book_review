package com.example.bookreview.dto.internal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public record ExternalApiRequest(HttpMethod method, HttpHeaders headers, String url, String body) {}

package com.example.macronews.dto.request;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public record ExternalApiRequest(HttpMethod method, HttpHeaders headers, String url, String body) {}

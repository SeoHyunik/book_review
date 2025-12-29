package com.example.bookreview.dto.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.drive")
public record GoogleDriveProperties(
        String credentialsPath,
        String tokensDirectory,
        String applicationName,
        String parentFolderId) {

    public GoogleDriveProperties {
        if (applicationName == null || applicationName.isBlank()) {
            applicationName = "BookReview";
        }
        if (tokensDirectory == null || tokensDirectory.isBlank()) {
            tokensDirectory = "tokens";
        }
    }
}

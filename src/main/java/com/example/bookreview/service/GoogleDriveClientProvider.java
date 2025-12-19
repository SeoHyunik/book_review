package com.example.bookreview.service;

import com.example.bookreview.config.GoogleDriveProperties;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleDriveClientProvider {

    private final GoogleDriveProperties properties;

    public Drive getDriveService() {
        Assert.hasText(properties.credentialsPath(), "Google credentials path must be configured");
        try (FileInputStream credentialsStream = new FileInputStream(properties.credentialsPath())) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(List.of(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY));

            HttpRequestInitializer initializer = new HttpCredentialsAdapter(credentials);
            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    initializer)
                    .setApplicationName(properties.applicationName())
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            log.error("[DRIVE] Failed to initialize Google Drive client", e);
            throw new RuntimeException("Google Drive 인증 정보를 초기화할 수 없습니다: " + e.getMessage(), e);
        }
    }

    public String parentFolderId() {
        return properties.parentFolderId();
    }
}

package com.example.bookreview.service.google;

import com.example.bookreview.util.GoogleDriveClientProvider;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveServiceImpl implements GoogleDriveService {

    private static final Pattern ALLOWED_CHARS = Pattern.compile("[^0-9A-Za-z가-힣-_]");

    private final GoogleDriveClientProvider driveClientProvider;

    @Override
    public String uploadMarkdown(String filename, String markdownContent) {
        Assert.hasText(filename, "Filename must not be blank");
        Assert.hasText(markdownContent, "Markdown content must not be blank");

        Drive drive = driveClientProvider.getDriveService();
        String safeName = toSlug(filename);
        log.info("[DRIVE] Uploading markdown to Google Drive: filename='{}'", safeName);

        File fileMetadata = new File();
        fileMetadata.setName(safeName);
        fileMetadata.setMimeType("text/markdown");
        if (StringUtils.hasText(driveClientProvider.parentFolderId())) {
            fileMetadata.setParents(List.of(driveClientProvider.parentFolderId()));
        }

        ByteArrayContent contentStream = new ByteArrayContent("text/markdown",
                markdownContent.getBytes(StandardCharsets.UTF_8));

        try {
            File uploaded = drive.files()
                    .create(fileMetadata, contentStream)
                    .setFields("id")
                    .execute();
            if (uploaded == null || !StringUtils.hasText(uploaded.getId())) {
                throw new IllegalStateException("Google Drive returned empty file id");
            }
            log.info("[DRIVE] File uploaded successfully with id={}", uploaded.getId());
            return uploaded.getId();
        } catch (IOException e) {
            log.error("[DRIVE] Failed to upload markdown to Google Drive", e);
            throw new RuntimeException("Google Drive 업로드 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadFile(String fileId) throws NoSuchFileException {
        Assert.hasText(fileId, "File id must not be blank");
        Drive drive = driveClientProvider.getDriveService();
        log.info("[DRIVE] Downloading file from Google Drive: fileId={}", fileId);
        try {
            File file = drive.files().get(fileId).execute();
            if (file == null) {
                throw new NoSuchFileException("Google Drive file not found: " + fileId);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (NoSuchFileException e) {
            log.error("[DRIVE] File not found in Google Drive: {}", fileId);
            throw e;
        } catch (IOException e) {
            log.error("[DRIVE] Failed to download file from Google Drive", e);
            throw new RuntimeException("Google Drive 다운로드 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileId) {
        Assert.hasText(fileId, "File id must not be blank");
        Drive drive = driveClientProvider.getDriveService();
        log.info("[DRIVE] Rolling back Google Drive upload for fileId={}", fileId);
        try {
            drive.files().delete(fileId).execute();
        } catch (IOException e) {
            log.warn("[DRIVE] Failed to delete file {} during rollback", fileId, e);
        }
    }

    private String toSlug(String filename) {
        String baseName = filename.replaceAll("\\.md$", "");
        String sanitized = ALLOWED_CHARS.matcher(baseName.trim().replaceAll("\\s+", "-")).replaceAll("");
        if (!sanitized.toLowerCase(Locale.ROOT).endsWith(".md")) {
            sanitized = sanitized + ".md";
        }
        return sanitized;
    }
}

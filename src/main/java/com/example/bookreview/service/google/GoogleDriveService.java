package com.example.bookreview.service.google;

import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

public interface GoogleDriveService {

    Optional<String> uploadMarkdown(String filename, String markdownContent);

    InputStream downloadFile(String fileId) throws NoSuchFileException;

    void deleteFile(String fileId);
}

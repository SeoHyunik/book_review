package com.example.bookreview.service;

import java.io.InputStream;
import java.nio.file.NoSuchFileException;

public interface GoogleDriveService {

    String uploadMarkdown(String filename, String markdownContent);

    InputStream downloadFile(String fileId) throws NoSuchFileException;

    void deleteFile(String fileId);
}

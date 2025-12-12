package com.example.bookreview.service;

import java.io.InputStream;

public interface GoogleDriveService {

    String uploadMarkdown(String filename, String markdownContent);

    InputStream downloadFile(String fileId);
}

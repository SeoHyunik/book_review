package com.example.bookreview.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public class GoogleDriveService {

    public String uploadMarkdown(String filename, String markdownContent) {
        // TODO: Google Drive API 연동하여 파일 업로드
        return "fake-file-id";
    }

    public InputStream downloadFile(String fileId) {
        // TODO: Google Drive 파일 다운로드 구현
        return new ByteArrayInputStream(("파일 " + fileId + " 의 더미 컨텐츠").getBytes(StandardCharsets.UTF_8));
    }
}
